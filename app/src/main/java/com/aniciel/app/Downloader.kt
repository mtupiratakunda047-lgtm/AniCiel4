package com.aniciel.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class DownloadException(message: String) : Exception(message)

/**
 * Kotlin download engine. Runs entirely in this process (no native code), so every
 * failure ends up as a readable message on the download's row instead of a silent 0 MB.
 *
 *  - Plain files: resumable HTTP with Range requests.
 *  - HLS (.m3u8): fetches segments in parallel batches, supports AES-128 encrypted
 *    streams and fMP4, resumes by segment, then remuxes the result to a real .mp4.
 *  - DASH (.mpd): uses DashDownloader for clear MPEG-DASH, with segment checkpoints
 *    and Android MediaMuxer remuxing of audio/video representations.
 */
object Downloader {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private const val BATCH = 6

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val deleted = ConcurrentHashMap.newKeySet<String>()
    private val slots = Semaphore(2) // downloads running at the same time

    fun start(id: String) {
        deleted.remove(id)
        val previous = jobs[id]
        if (previous != null && previous.isActive) return
        DownloadsStore.update(id) { it.copy(state = "queued", error = null, bytesPerSecond = 0.0) }
        val job = scope.launch {
            previous?.join()
            slots.withPermit { runItem(id) }
        }
        jobs[id] = job
    }

    fun pause(id: String) {
        jobs[id]?.cancel()
        DownloadsStore.update(id) {
            if (it.state == "completed") it else it.copy(state = "paused", bytesPerSecond = 0.0, error = null)
        }
    }

    /** Stops the job (used before deleting files). Does not touch the store. */
    fun cancel(id: String) {
        deleted.add(id)
        jobs[id]?.cancel()
    }

    // ------------------------------------------------------------------

    private suspend fun runItem(id: String) {
        val item = DownloadsStore.get(id) ?: return
        DownloadsStore.update(id) { it.copy(state = "running", error = null, bytesPerSecond = 0.0) }
        try {
            val part = File(item.filePath + ".part")
            part.parentFile?.mkdirs()
            val hls = item.url.contains(".m3u8", ignoreCase = true)
            val dash = isDashUrl(item.url)
            when {
                hls -> downloadHls(id, item, part)
                dash -> downloadDash(id, item, part)
                else -> downloadPlain(id, item, part)
            }
            finish(id, item, part, hls)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                if (id !in deleted) {
                    DownloadsStore.update(id) {
                        if (it.state == "completed") it else it.copy(state = "paused", bytesPerSecond = 0.0)
                    }
                }
            }
            throw e
        } catch (e: Throwable) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            DownloadsStore.update(id) { it.copy(state = "failed", error = message, bytesPerSecond = 0.0) }
        }
    }

    private fun isDashUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return path.endsWith(".mpd", ignoreCase = true)
    }

    private suspend fun downloadDash(id: String, item: DownloadItem, part: File) {
        DownloadsStore.update(id) { it.copy(state = "processing", error = null) }
        DashDownloader.download(item.url, item.headers, part) { done, total ->
            DownloadsStore.update(id, save = false) {
                it.copy(
                    downloaded = done,
                    total = total,
                    bytesPerSecond = 0.0
                )
            }
        }
    }

    private suspend fun finish(id: String, item: DownloadItem, part: File, hls: Boolean) {
        var dest = File(item.filePath)
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        if (hls) {
            DownloadsStore.update(id) { it.copy(state = "processing", bytesPerSecond = 0.0) }
            val ok = withContext(Dispatchers.IO) { Remuxer.toMp4(part, dest) }
            if (ok) {
                part.delete()
            } else {
                // Couldn't repackage; keep the raw stream, which players still open.
                dest = File(item.filePath.removeSuffix(".mp4") + ".ts")
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
            }
        } else {
            verifyIsVideo(part)?.let { problem ->
                part.delete()
                throw DownloadException(problem)
            }
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
        }
        File(item.filePath + ".part.seg").delete()
        val size = dest.length()
        if (size <= 0L) {
            dest.delete()
            throw DownloadException("The download finished but the file is empty")
        }
        DownloadsStore.update(id) {
            it.copy(
                state = "completed",
                filePath = dest.absolutePath,
                downloaded = size,
                total = size,
                bytesPerSecond = 0.0,
                error = null
            )
        }
    }

    /**
     * Returns a readable problem if [file] clearly isn't a video: too small, or text/HTML/JSON/a playlist
     * (what servers send instead of a video when a link is blocked or expired). Any large binary file
     * is accepted, so unusual-but-valid containers aren't thrown away.
     */
    private fun verifyIsVideo(file: File): String? {
        val size = file.length()
        val head = ByteArray(256)
        val read = try { file.inputStream().use { it.read(head) } } catch (_: Exception) { -1 }
        val sample = if (read > 0) head.copyOf(read) else ByteArray(0)
        val printable = sample.count { v ->
            val c = v.toInt() and 0xFF
            c in 32..126 || c == 9 || c == 10 || c == 13
        }
        val looksLikeText = sample.isNotEmpty() && printable * 100 / sample.size >= 92
        val kb = maxOf(1L, size / 1024)
        if (looksLikeText || size < 20_000) {
            val snippet = String(sample, Charsets.UTF_8).replace(Regex("\\s+"), " ").trim().take(120)
            return "This source sent $kb KB of " + (if (looksLikeText) "text" else "data") +
                " instead of a video" + if (snippet.isNotBlank()) ": \"$snippet\"" else ""
        }
        return null
    }

    // ------------------------------------------------------------------ plain file

    private suspend fun downloadPlain(id: String, item: DownloadItem, part: File) {
        var offset = if (part.exists()) part.length() else 0L
        val conn = open(item.url, item.headers, if (offset > 0) offset else null, identity = true)
        try {
            val code = conn.responseCode
            if (code == 416 && offset > 0) return // already have every byte
            if (code !in 200..299) throw DownloadException("Server returned HTTP $code")
            val type = conn.contentType?.lowercase() ?: ""
            if (type.startsWith("text/") || type.contains("json")) {
                throw DownloadException("The source returned a web page instead of a video (the link may have expired)")
            }
            val resumed = code == 206 && offset > 0
            if (!resumed) offset = 0
            val length = conn.contentLengthLong
            val total = if (length > 0) offset + length else 0L

            var downloaded = offset
            val started = System.currentTimeMillis()
            var lastReport = 0L
            FileOutputStream(part, resumed).use { out ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        downloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 400) {
                            lastReport = now
                            val bps = (downloaded - offset) * 1000.0 / (now - started).coerceAtLeast(1)
                            DownloadsStore.update(id, save = false) {
                                it.copy(downloaded = downloaded, total = total, bytesPerSecond = bps)
                            }
                        }
                    }
                }
            }
            if (total > 0 && downloaded < total) {
                throw DownloadException(
                    "Connection lost at ${downloaded / 1_048_576} of ${total / 1_048_576} MB - tap resume"
                )
            }
            DownloadsStore.update(id, save = false) { it.copy(downloaded = downloaded, total = downloaded) }
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------------ HLS

    private class Segment(val url: String, val keyUri: String?, val ivHex: String?, val sequence: Long)

    private class MediaPlaylist(val initUri: String?, val segments: List<Segment>)

    private suspend fun downloadHls(id: String, item: DownloadItem, part: File) {
        var playlistUrl = item.url
        var text = fetchText(playlistUrl, item.headers)
        if (text.contains("#EXT-X-STREAM-INF")) {
            playlistUrl = pickBestVariant(text, playlistUrl)
                ?: throw DownloadException("No playable video stream found in the playlist")
            text = fetchText(playlistUrl, item.headers)
        }
        val playlist = parseMedia(text, playlistUrl)

        val entries = ArrayList<Segment>()
        playlist.initUri?.let { entries.add(Segment(it, null, null, 0)) }
        entries.addAll(playlist.segments)

        // Encryption keys (usually one) are fetched once up front.
        val keys = HashMap<String, ByteArray>()
        for (uri in entries.mapNotNull { it.keyUri }.distinct()) {
            keys[uri] = fetchBytes(uri, item.headers)
        }

        val meta = File(part.path + ".seg")
        var index = 0
        var downloaded = 0L
        if (part.exists() && meta.exists()) {
            val bits = meta.readText().trim().split(":")
            val doneSegments = bits.getOrNull(0)?.toIntOrNull() ?: 0
            val doneBytes = bits.getOrNull(1)?.toLongOrNull() ?: 0L
            if (doneSegments in 1..entries.size && doneBytes in 1..part.length()) {
                RandomAccessFile(part, "rw").use { it.setLength(doneBytes) }
                index = doneSegments
                downloaded = doneBytes
            }
        }
        if (index == 0) {
            part.delete()
            meta.delete()
        }

        val started = System.currentTimeMillis()
        var sessionBytes = 0L
        FileOutputStream(part, index > 0).use { out ->
            while (index < entries.size) {
                currentCoroutineContext().ensureActive()
                val batch = entries.subList(index, minOf(index + BATCH, entries.size))
                val chunks = coroutineScope {
                    batch.map { seg -> async(Dispatchers.IO) { fetchSegment(seg, item.headers, keys) } }.awaitAll()
                }
                for (bytes in chunks) {
                    out.write(bytes)
                    downloaded += bytes.size
                    sessionBytes += bytes.size
                }
                out.flush()
                index += batch.size
                meta.writeText("$index:$downloaded")
                val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1)
                val estimate = downloaded * entries.size / index
                DownloadsStore.update(id, save = false) {
                    it.copy(downloaded = downloaded, total = estimate, bytesPerSecond = sessionBytes * 1000.0 / elapsed)
                }
            }
        }
        DownloadsStore.update(id, save = false) { it.copy(downloaded = downloaded, total = downloaded) }
    }

    private suspend fun fetchSegment(seg: Segment, headers: List<Pair<String, String>>, keys: Map<String, ByteArray>): ByteArray {
        val raw = fetchBytes(seg.url, headers)
        val keyUri = seg.keyUri ?: return raw
        val key = keys[keyUri] ?: throw DownloadException("Missing decryption key")
        return try {
            decrypt(raw, key, seg.ivHex, seg.sequence)
        } catch (e: Exception) {
            throw DownloadException("Couldn't decrypt a video segment")
        }
    }

    private fun decrypt(data: ByteArray, key: ByteArray, ivHex: String?, sequence: Long): ByteArray {
        val iv = ByteArray(16)
        if (ivHex != null) {
            val hex = ivHex.removePrefix("0x").removePrefix("0X").padStart(32, '0')
            for (i in 0 until 16) iv[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        } else {
            for (i in 0 until 8) iv[15 - i] = ((sequence shr (8 * i)) and 0xFF).toByte()
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun parseAttrs(text: String): Map<String, String> {
        val map = HashMap<String, String>()
        Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)").findAll(text).forEach { m ->
            map[m.groupValues[1]] = m.groupValues[2].trim('"')
        }
        return map
    }

    private fun resolve(base: String, relative: String): String =
        try { URL(URL(base), relative).toString() } catch (_: Exception) { relative }

    private fun parseMedia(text: String, base: String): MediaPlaylist {
        var sequence = 0L
        var keyUri: String? = null
        var ivHex: String? = null
        var initUri: String? = null
        var ended = false
        val segments = ArrayList<Segment>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                    sequence = line.substringAfter(":").trim().toLongOrNull() ?: 0L
                line.startsWith("#EXT-X-KEY:") -> {
                    val attrs = parseAttrs(line.substringAfter(":"))
                    when (val method = (attrs["METHOD"] ?: "NONE").uppercase()) {
                        "NONE" -> { keyUri = null; ivHex = null }
                        "AES-128" -> {
                            val uri = attrs["URI"] ?: throw DownloadException("Encrypted stream has no key address")
                            keyUri = resolve(base, uri)
                            ivHex = attrs["IV"]
                        }
                        else -> throw DownloadException("This stream uses $method encryption, which isn't supported")
                    }
                }
                line.startsWith("#EXT-X-MAP:") ->
                    initUri = parseAttrs(line.substringAfter(":"))["URI"]?.let { resolve(base, it) }
                line.startsWith("#EXT-X-BYTERANGE") ->
                    throw DownloadException("Byte-range streams aren't supported")
                line.startsWith("#EXT-X-ENDLIST") -> ended = true
                line.startsWith("#") -> Unit
                else -> {
                    segments.add(Segment(resolve(base, line), keyUri, ivHex, sequence))
                    sequence++
                }
            }
        }
        if (segments.isEmpty()) throw DownloadException("The playlist has no video segments")
        if (!ended) throw DownloadException("This is a live stream and can't be downloaded")
        return MediaPlaylist(initUri, segments)
    }

    private fun pickBestVariant(text: String, base: String): String? {
        var bestUrl: String? = null
        var bestScore = -1L
        var pendingScore = 0L
        var expecting = false
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = parseAttrs(line.substringAfter(":"))
                val height = attrs["RESOLUTION"]?.substringAfter('x')?.toLongOrNull() ?: 0L
                val bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L
                pendingScore = height * 1_000_000_000L + bandwidth
                expecting = true
            } else if (expecting && line.isNotEmpty() && !line.startsWith("#")) {
                if (pendingScore > bestScore) {
                    bestScore = pendingScore
                    bestUrl = resolve(base, line)
                }
                expecting = false
            }
        }
        return bestUrl
    }

    // ------------------------------------------------------------------ HTTP helpers

    private fun open(
        url: String,
        headers: List<Pair<String, String>>,
        rangeStart: Long?,
        identity: Boolean
    ): HttpURLConnection {
        var current = url
        repeat(6) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "*/*")
            if (identity) conn.setRequestProperty("Accept-Encoding", "identity")
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (rangeStart != null) conn.setRequestProperty("Range", "bytes=$rangeStart-")
            val code = conn.responseCode
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw DownloadException("Server redirected without a destination")
                current = resolve(current, location)
            } else {
                return conn
            }
        }
        throw DownloadException("Too many redirects")
    }

    private fun fetchTextBlocking(url: String, headers: List<Pair<String, String>>): String {
        val conn = open(url, headers, null, identity = false)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw DownloadException("Server returned HTTP $code for the playlist")
            return conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun fetchText(url: String, headers: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) { fetchTextBlocking(url, headers) }

    private suspend fun fetchBytes(url: String, headers: List<Pair<String, String>>): ByteArray {
        var last = "unknown error"
        for (attempt in 0 until 3) {
            currentCoroutineContext().ensureActive()
            try {
                return withContext(Dispatchers.IO) {
                    val conn = open(url, headers, null, identity = true)
                    try {
                        val code = conn.responseCode
                        if (code !in 200..299) throw DownloadException("Server returned HTTP $code")
                        conn.inputStream.use { it.readBytes() }
                    } finally {
                        conn.disconnect()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e.message ?: e.javaClass.simpleName
                delay(500L * (attempt + 1))
            }
        }
        throw DownloadException("A video segment kept failing ($last)")
    }
}
