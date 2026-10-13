package com.aniciel.app

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

/**
 * Clear (non-DRM) MPEG-DASH downloader.
 *
 * This deliberately handles the common interoperable DASH forms used by
 * web video: SegmentTemplate/SegmentTimeline and SegmentList, separate
 * audio/video representations, relative BaseURL values, and $Number$/$Time$/
 * $RepresentationID$/$Bandwidth$ URL templates. It downloads the selected
 * representations and remuxes them without re-encoding.
 *
 * DRM-protected MPDs are rejected rather than attempting to bypass protection.
 */
object DashDownloader {
    private data class Segment(val url: String, val range: String? = null)
    private data class Track(
        val kind: String,
        val mime: String,
        val representationId: String,
        val bandwidth: Long,
        val init: Segment?,
        val media: List<Segment>
    )

    suspend fun download(
        manifestUrl: String,
        headers: List<Pair<String, String>>,
        output: File,
        progress: (downloaded: Long, total: Long) -> Unit
    ) {
        val xml = fetchText(manifestUrl, headers)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Android's built-in parser often does not support these Apache/SAX features;
            // calling setFeature throws ParserConfigurationException with the feature URI
            // as the message (exactly what users see as "Failed: http://apache.org/...").
            listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false
            ).forEach { (feature, value) ->
                try {
                    setFeature(feature, value)
                } catch (_: Exception) {
                    // Feature unsupported on this device — safe to continue.
                }
            }
        }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val mpd = doc.documentElement
        if (mpd.tagName.substringAfter(':') != "MPD") {
            throw DownloadException("The DASH source returned an invalid MPD manifest")
        }
        if (mpd.getElementsByTagName("ContentProtection").length > 0 ||
            mpd.getElementsByTagName("cenc:pssh").length > 0) {
            throw DownloadException("DRM-protected DASH downloads are not supported")
        }
        if (mpd.getAttribute("type").equals("dynamic", true)) {
            throw DownloadException("Live/dynamic DASH downloads are not supported")
        }

        val period = firstChild(mpd, "Period")
            ?: throw DownloadException("DASH manifest has no Period")
        val periodBase = resolve(
            manifestUrl,
            firstChild(period, "BaseURL")?.textContent?.trim()
        )
        val periodDuration = parseIsoDuration(period.getAttribute("duration"))
            ?: parseIsoDuration(mpd.getAttribute("mediaPresentationDuration"))

        val adaptationSets = children(period, "AdaptationSet")
        val video = selectTrack(adaptationSets, periodBase, "video", periodDuration)
        val audio = selectTrack(adaptationSets, periodBase, "audio", periodDuration)
        if (video == null && audio == null) {
            throw DownloadException("The MPD contains no playable audio or video representation")
        }

        output.parentFile?.mkdirs()
        val videoFile = File(output.path + ".dash.video")
        val audioFile = File(output.path + ".dash.audio")
        try {
            var done = 0L
            val total = (video?.media?.size ?: 0) + (audio?.media?.size ?: 0) +
                (if (video?.init != null) 1 else 0) + (if (audio?.init != null) 1 else 0)

            fun report() {
                progress(done, total.toLong())
            }

            if (video != null) {
                downloadTrack(video, headers, videoFile) {
                    done++
                    report()
                }
            }
            if (audio != null) {
                downloadTrack(audio, headers, audioFile) {
                    done++
                    report()
                }
            }

            if (output.exists()) output.delete()
            if (!remux(videoFile.takeIf { it.exists() }, audioFile.takeIf { it.exists() }, output)) {
                throw DownloadException("DASH segments downloaded, but Android could not remux them into MP4")
            }
        } finally {
            videoFile.delete()
            audioFile.delete()
            File(videoFile.path + ".meta").delete()
            File(audioFile.path + ".meta").delete()
        }
    }

    private suspend fun downloadTrack(
        track: Track,
        headers: List<Pair<String, String>>,
        file: File,
        onSegment: () -> Unit
    ): Int {
        val meta = File(file.path + ".meta")
        val expected = (if (track.init != null) 1 else 0) + track.media.size
        var done = meta.readText().trim().toIntOrNull()?.coerceIn(0, expected) ?: 0
        if (done == 0) {
            file.delete()
            meta.delete()
        } else if (!file.exists()) {
            done = 0
            meta.delete()
        }

        FileOutputStream(file, done > 0).use { out ->
            var index = 0
            if (track.init != null) {
                if (index < done) {
                    onSegment()
                } else {
                    currentCoroutineContext().ensureActive()
                    out.write(fetchBytes(track.init.url, headers, track.init.range))
                    done = 1
                    meta.writeText(done.toString())
                    onSegment()
                }
                index++
            }
            for (segment in track.media) {
                if (index < done) {
                    onSegment()
                } else {
                    currentCoroutineContext().ensureActive()
                    out.write(fetchBytes(segment.url, headers, segment.range))
                    done = index + 1
                    meta.writeText(done.toString())
                    onSegment()
                }
                index++
            }
        }
        return done
    }

    private fun selectTrack(
        sets: List<org.w3c.dom.Element>,
        base: String,
        kind: String,
        periodDuration: Double?
    ): Track? {
        val candidates = mutableListOf<Track>()
        for (set in sets) {
            val setMime = attr(set, "mimeType")
            val setContent = attr(set, "contentType")
            val setBase = resolve(base, firstChild(set, "BaseURL")?.textContent?.trim())
            val reps = children(set, "Representation")
            for (rep in reps) {
                val mime = attr(rep, "mimeType").ifBlank { setMime }
                val content = attr(rep, "contentType").ifBlank { setContent }
                val actualKind = when {
                    content.equals(kind, true) -> kind
                    mime.startsWith("$kind/") -> kind
                    else -> continue
                }
                val repBase = resolve(setBase, firstChild(rep, "BaseURL")?.textContent?.trim())
                val template = firstChild(rep, "SegmentTemplate") ?: firstChild(set, "SegmentTemplate")
                val list = firstChild(rep, "SegmentList") ?: firstChild(set, "SegmentList")
                val id = attr(rep, "id").ifBlank { "r${candidates.size}" }
                val bandwidth = attr(rep, "bandwidth").toLongOrNull() ?: 0L

                val (init, media) = when {
                    template != null -> buildFromTemplate(
                        template, repBase, id, bandwidth, periodDuration
                    )
                    list != null -> buildFromList(list, repBase)
                    else -> continue
                }
                if (media.isNotEmpty()) {
                    candidates += Track(actualKind, mime, id, bandwidth, init, media)
                }
            }
        }
        return candidates.maxWithOrNull(compareBy<Track> { it.bandwidth }.thenBy { it.media.size })
    }

    private fun buildFromTemplate(
        template: org.w3c.dom.Element,
        base: String,
        id: String,
        bandwidth: Long,
        periodDuration: Double?
    ): Pair<Segment?, List<Segment>> {
        val mediaTemplate = attr(template, "media")
        if (mediaTemplate.isBlank()) throw DownloadException("DASH SegmentTemplate has no media template")
        val initTemplate = attr(template, "initialization").takeIf { it.isNotBlank() }
        val timescale = attr(template, "timescale").toLongOrNull()?.coerceAtLeast(1L) ?: 1L
        val startNumber = attr(template, "startNumber").toLongOrNull() ?: 1L
        val duration = attr(template, "duration").toLongOrNull()
        val timeline = firstChild(template, "SegmentTimeline")

        val init = initTemplate?.let {
            Segment(expand(it, id, bandwidth, startNumber, 0L), null)
                .let { s -> s.copy(url = resolve(base, s.url)) }
        }

        val segments = mutableListOf<Segment>()
        if (timeline != null) {
            val sNodes = children(timeline, "S")
            var number = startNumber
            var time = 0L
            for ((index, s) in sNodes.withIndex()) {
                val explicitT = attr(s, "t").toLongOrNull()
                if (explicitT != null) time = explicitT
                val d = attr(s, "d").toLongOrNull()
                    ?: throw DownloadException("DASH SegmentTimeline contains S without d")
                var repeat = attr(s, "r").toLongOrNull() ?: 0L
                if (repeat < 0) {
                    val nextT = sNodes.drop(index + 1).firstNotNullOfOrNull { attr(it, "t").toLongOrNull() }
                    repeat = if (nextT != null) {
                        ((nextT - time) / d).coerceAtLeast(1L) - 1L
                    } else if (periodDuration != null) {
                        ceil((periodDuration * timescale - time) / d).toLong().coerceAtLeast(1L) - 1L
                    } else {
                        throw DownloadException("DASH uses an unbounded SegmentTimeline")
                    }
                }
                repeat = repeat.coerceAtMost(100_000L)
                repeatDownTo(repeat) {
                    val url = expand(mediaTemplate, id, bandwidth, number, time)
                    segments += Segment(resolve(base, url))
                    number++
                    time += d
                }
            }
        } else {
            val d = duration ?: throw DownloadException("DASH SegmentTemplate has neither duration nor SegmentTimeline")
            val count = if (periodDuration != null) {
                ceil(periodDuration * timescale / d).toLong()
            } else {
                throw DownloadException("DASH SegmentTemplate needs a SegmentTimeline or duration")
            }.coerceIn(1L, 100_000L)
            var number = startNumber
            var time = 0L
            repeatDownTo(count - 1) {
                segments += Segment(resolve(base, expand(mediaTemplate, id, bandwidth, number, time)))
                number++
                time += d
            }
        }
        return init to segments
    }

    private fun buildFromList(list: org.w3c.dom.Element, base: String): Pair<Segment?, List<Segment>> {
        val init = firstChild(list, "Initialization")?.let {
            Segment(resolve(base, attr(it, "sourceURL")), attr(it, "range").takeIf { r -> r.isNotBlank() })
        }
        val media = children(list, "SegmentURL").mapNotNull {
            val url = attr(it, "media").takeIf(String::isNotBlank) ?: return@mapNotNull null
            Segment(resolve(base, url), attr(it, "mediaRange").takeIf(String::isNotBlank))
        }
        return init to media
    }

    private fun expand(template: String, id: String, bandwidth: Long, number: Long, time: Long): String {
        var out = template.replace("\${'$'}RepresentationID\$", id)
            .replace("\${'$'}Bandwidth\$", bandwidth.toString())
            .replace("\${'$'}Number\$", number.toString())
            .replace("\${'$'}Time\$", time.toString())
        val regex = Regex("""\$(Number|Time|Bandwidth)(%0(\d+)d)?\$""")
        out = regex.replace(template) { m ->
            val key = m.groupValues[1]
            val value = when (key) {
                "Number" -> number
                "Time" -> time
                "Bandwidth" -> bandwidth
                else -> 0L
            }
            val width = m.groupValues.getOrNull(3)?.toIntOrNull()
            if (width != null) value.toString().padStart(width, '0') else value.toString()
        }.replace("\$RepresentationID\$", id)
        return out
    }

    private fun repeatDownTo(count: Long, block: () -> Unit) {
        if (count < 0) return
        repeat((count + 1).toInt()) { block() }
    }

    private fun remux(video: File?, audio: File?, output: File): Boolean {
        val sources = listOfNotNull(
            video?.let { it to "video" },
            audio?.let { it to "audio" }
        )
        val extractors = mutableListOf<MediaExtractor>()
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            val selected = mutableListOf<Pair<MediaExtractor, Int>>()
            for ((file, kind) in sources) {
                val extractor = MediaExtractor()
                extractor.setDataSource(file.absolutePath)
                val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                    mime.startsWith("$kind/")
                } ?: continue
                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)
                if (muxer == null) {
                    muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                }
                val muxTrack = muxer.addTrack(format)
                extractors += extractor
                selected += extractor to muxTrack
            }
            if (selected.isEmpty() || muxer == null) return false

            muxer.start()
            started = true
            for ((extractor, muxTrack) in selected) {
                val buffer = java.nio.ByteBuffer.allocate(4 * 1024 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                        MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(muxTrack, buffer, info)
                    extractor.advance()
                }
            }
            muxer.stop()
            started = false
            output.exists() && output.length() > 0
        } catch (_: Exception) {
            false
        } finally {
            try { if (started) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            extractors.forEach { try { it.release() } catch (_: Exception) {} }
            if (output.length() == 0L) output.delete()
        }
    }

    private fun fetchText(url: String, headers: List<Pair<String, String>>): String {
        val conn = open(url, headers, null)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw DownloadException("Server returned HTTP $code for the MPD")
            val type = conn.contentType?.lowercase() ?: ""
            val body = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            if (!type.contains("dash") && !body.trimStart().startsWith("<")) {
                throw DownloadException("The source did not return a DASH manifest")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchBytes(url: String, headers: List<Pair<String, String>>, range: String?): ByteArray {
        val conn = open(url, headers, range)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw DownloadException("Server returned HTTP $code for DASH segment")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, headers: List<Pair<String, String>>, range: String?): HttpURLConnection {
        var current = url
        repeat(6) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AniCiel/0.3")
            conn.setRequestProperty("Accept", "*/*")
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (!range.isNullOrBlank()) conn.setRequestProperty("Range", "bytes=$range")
            val code = conn.responseCode
            if (code in 301..308 && code != 304) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw DownloadException("DASH redirect has no destination")
                current = resolve(current, location)
            } else return conn
        }
        throw DownloadException("Too many redirects while fetching DASH")
    }

    private fun resolve(base: String, relative: String?): String {
        if (relative.isNullOrBlank()) return base
        return try { URL(URL(base), relative).toString() } catch (_: Exception) { relative }
    }

    private fun attr(e: org.w3c.dom.Element, name: String): String =
        e.getAttribute(name).orEmpty()

    private fun firstChild(parent: org.w3c.dom.Element, name: String): org.w3c.dom.Element? =
        children(parent, name).firstOrNull()

    private fun children(parent: org.w3c.dom.Element, name: String): List<org.w3c.dom.Element> {
        val out = mutableListOf<org.w3c.dom.Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is org.w3c.dom.Element &&
                node.tagName.substringAfter(':') == name) out += node
        }
        return out
    }

    private fun parseIsoDuration(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val m = Regex("""P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?""")
            .matchEntire(value) ?: return null
        val days = m.groupValues[1].toDoubleOrNull() ?: 0.0
        val hours = m.groupValues[2].toDoubleOrNull() ?: 0.0
        val mins = m.groupValues[3].toDoubleOrNull() ?: 0.0
        val secs = m.groupValues[4].toDoubleOrNull() ?: 0.0
        return days * 86400.0 + hours * 3600.0 + mins * 60.0 + secs
    }
}
