package com.aniciel.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

enum class EnqueueResult { STARTED, RESUMED, DUPLICATE }

private fun safeName(value: String): String =
    value.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        .replace(' ', '_')
        .trim('.', '_', ' ')
        .ifBlank { "video" }
        .take(80)

/**
 * Creates a download record and starts it. Episodes of one anime share a folder
 * (<downloads>/<Anime title>/...). The same episode + quality can't be queued twice;
 * a different quality of the same episode is fine.
 */
fun enqueueDownload(
    context: Context,
    baseDir: File,
    provider: String,
    subjectId: String,
    title: String,
    posterUrl: String?,
    season: Int,
    episode: Int,
    quality: String,
    mirror: MirrorInfo
): EnqueueResult {
    DownloadsStore.init(context.applicationContext)
    val animeKey = "$provider:$subjectId"
    val existing = DownloadsStore.find(animeKey, season, episode, quality)
    if (existing != null) {
        return if (existing.state == "failed" || existing.state == "paused") {
            Downloader.start(existing.id)
            DownloadService.track(context)
            EnqueueResult.RESUMED
        } else {
            EnqueueResult.DUPLICATE
        }
    }

    val folder = File(baseDir, safeName(title))
    val stem = safeName(title)
    val q = safeName(quality)
    val fileName = if (season == 0 && episode == 0) "${stem}_$q.mp4" else "${stem}_S${season}E${episode}_$q.mp4"
    val item = DownloadItem(
        id = UUID.randomUUID().toString(),
        animeKey = animeKey,
        animeTitle = title,
        posterUrl = posterUrl,
        season = season,
        episode = episode,
        quality = quality,
        url = mirror.url,
        headers = mirror.headers,
        filePath = File(folder, fileName).absolutePath,
        state = "queued"
    )
    DownloadsStore.add(item)
    Downloader.start(item.id)
    DownloadService.track(context)
    return EnqueueResult.STARTED
}

/** A mirror we can really download: a direct file or an HLS playlist (not a web page). */
fun downloadableMirror(release: ReleaseInfo): MirrorInfo? =
    release.mirrors.firstOrNull { it.directFile }
        ?: release.mirrors.firstOrNull { it.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) }

/** Picks the release whose quality label matches, else the one with the closest resolution. */
fun closestRelease(list: List<ReleaseInfo>, quality: String): ReleaseInfo? {
    list.firstOrNull { it.quality == quality }?.let { return it }
    val wanted = Regex("\\d+").find(quality)?.value?.toIntOrNull() ?: return list.firstOrNull()
    return list.minByOrNull {
        val h = Regex("\\d+").find(it.quality)?.value?.toIntOrNull() ?: Int.MAX_VALUE / 2
        kotlin.math.abs(h - wanted)
    }
}

object ShareUtil {
    private fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Opens the system share sheet (ShareIt, WhatsApp, Bluetooth, ...) for one or many videos. */
    fun shareFiles(context: Context, files: List<File>) {
        val existing = files.filter { it.exists() && it.length() > 0 }
        if (existing.isEmpty()) {
            Toast.makeText(context, "Nothing to share - the files are missing", Toast.LENGTH_SHORT).show()
            return
        }
        val uris = ArrayList(existing.map { uriFor(context, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        intent.type = "video/*"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, "Share via")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Copies a finished video into the public Movies/AniCiel folder so it also shows up in
     * Gallery, file managers and other apps. Needs Android 10+ (MediaStore).
     */
    fun saveToGallery(context: Context, item: DownloadItem): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val src = File(item.filePath)
        if (!src.exists() || src.length() <= 0) return false
        val mime = if (src.extension.equals("ts", ignoreCase = true)) "video/mp2t" else "video/mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, src.name)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AniCiel/${safeName(item.animeTitle)}")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val out = resolver.openOutputStream(uri) ?: throw IllegalStateException("no output stream")
            out.use { o -> src.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            false
        }
    }
}
