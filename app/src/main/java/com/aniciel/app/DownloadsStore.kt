package com.aniciel.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** One downloaded (or downloading) episode/movie. Persisted to downloads.json so history survives restarts. */
data class DownloadItem(
    val id: String,
    val animeKey: String,          // "<provider>:<subjectId>" - groups episodes into one folder
    val animeTitle: String,
    val posterUrl: String?,
    val season: Int,
    val episode: Int,
    val quality: String,
    val url: String,
    val headers: List<Pair<String, String>>,
    val filePath: String,
    val state: String,             // queued | running | processing | paused | failed | completed
    val downloaded: Long = 0,
    val total: Long = 0,
    val bytesPerSecond: Double = 0.0,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isActive: Boolean get() = state == "queued" || state == "running" || state == "processing"

    fun displayName(): String = when {
        season == 0 && episode == 0 -> "Full movie"
        season <= 1 -> "Episode $episode"
        else -> "S$season · Episode $episode"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("animeKey", animeKey)
        put("animeTitle", animeTitle)
        put("posterUrl", posterUrl ?: JSONObject.NULL)
        put("season", season)
        put("episode", episode)
        put("quality", quality)
        put("url", url)
        put("headers", JSONArray().apply {
            headers.forEach { (k, v) -> put(JSONArray().apply { put(k); put(v) }) }
        })
        put("filePath", filePath)
        put("state", state)
        put("downloaded", downloaded)
        put("total", total)
        put("error", error ?: JSONObject.NULL)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): DownloadItem {
            val headersArr = o.optJSONArray("headers") ?: JSONArray()
            val headers = (0 until headersArr.length()).map { i ->
                val pair = headersArr.getJSONArray(i)
                pair.getString(0) to pair.getString(1)
            }
            return DownloadItem(
                id = o.getString("id"),
                animeKey = o.getString("animeKey"),
                animeTitle = o.getString("animeTitle"),
                posterUrl = if (o.isNull("posterUrl")) null else o.optString("posterUrl"),
                season = o.optInt("season", 0),
                episode = o.optInt("episode", 0),
                quality = o.optString("quality", ""),
                url = o.optString("url", ""),
                headers = headers,
                filePath = o.getString("filePath"),
                state = o.optString("state", "paused"),
                downloaded = o.optLong("downloaded", 0),
                total = o.optLong("total", 0),
                error = if (o.isNull("error")) null else o.optString("error"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

enum class DlMark { NONE, PENDING, DONE }

object DownloadsStore {
    /** Observable list for Compose. Only ever mutated on the main thread. */
    val items = mutableStateListOf<DownloadItem>()

    private val map = ConcurrentHashMap<String, DownloadItem>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var storeFile: File? = null
    private var initialized = false

    private fun runMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    /** Loads saved downloads. Safe to call repeatedly; call from the main thread. */
    fun init(context: Context) {
        synchronized(this) {
            if (initialized) return
            initialized = true
        }
        val file = File(context.applicationContext.filesDir, "downloads.json")
        storeFile = file
        val loaded = ArrayList<DownloadItem>()
        try {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val item = (try { DownloadItem.fromJson(arr.getJSONObject(i)) } catch (_: Exception) { null }) ?: continue
                    loaded.add(fixAfterRestart(item) ?: continue)
                }
            }
        } catch (_: Exception) {
            // A corrupt file shouldn't stop the app; start with what we could read.
        }
        loaded.sortByDescending { it.createdAt }
        loaded.forEach { map[it.id] = it }
        items.clear()
        items.addAll(loaded)
        persist()
    }

    /** Anything that was mid-download when the app died becomes "paused" so it can be resumed. */
    private fun fixAfterRestart(item: DownloadItem): DownloadItem? {
        if (item.state == "completed") {
            val f = File(item.filePath)
            if (f.exists() && f.length() in 1..19_999) f.delete() // bogus 2 KB downloads from earlier versions
            return if (f.exists() && f.length() > 0) item.copy(downloaded = f.length(), total = f.length()) else null
        }
        val part = File(item.filePath + ".part")
        val onDisk = if (part.exists()) part.length() else 0L
        return if (item.isActive) {
            item.copy(
                state = "paused",
                downloaded = onDisk,
                bytesPerSecond = 0.0,
                error = "Interrupted - tap resume to continue"
            )
        } else item
    }

    /**
     * Picks up finished video files sitting in the downloads folder that have no record
     * (for example ones saved by an older version), so they show up in the list.
     */
    fun importOrphans(root: File) {
        if (!root.exists()) return
        val known = map.values.map { it.filePath }.toSet()
        val regex = Regex("^(.+)_S(\\d+)E(\\d+)_(.+)\\.(mp4|ts)$")
        val found = ArrayList<DownloadItem>()
        root.walkTopDown().maxDepth(3).filter { it.isFile }.forEach { f ->
            val ext = f.extension.lowercase()
            if (ext != "mp4" && ext != "ts") return@forEach
            if (f.absolutePath in known) return@forEach
            if (f.length() <= 0) { f.delete(); return@forEach }
            val m = regex.matchEntire(f.name)
            val title = (m?.groupValues?.get(1) ?: f.nameWithoutExtension).replace('_', ' ')
            found.add(
                DownloadItem(
                    id = "import-" + f.absolutePath.hashCode(),
                    animeKey = "local:" + title.lowercase(),
                    animeTitle = title,
                    posterUrl = null,
                    season = m?.groupValues?.get(2)?.toIntOrNull() ?: 0,
                    episode = m?.groupValues?.get(3)?.toIntOrNull() ?: 0,
                    quality = (m?.groupValues?.get(4) ?: "").replace('_', ' '),
                    url = "",
                    headers = emptyList(),
                    filePath = f.absolutePath,
                    state = "completed",
                    downloaded = f.length(),
                    total = f.length(),
                    createdAt = f.lastModified()
                )
            )
        }
        if (found.isEmpty()) return
        found.forEach { map[it.id] = it }
        runMain { items.addAll(found) }
        persist()
    }

    fun get(id: String): DownloadItem? = map[id]

    fun add(item: DownloadItem) {
        map[item.id] = item
        runMain { items.add(0, item) }
        persist()
    }

    fun update(id: String, save: Boolean = true, change: (DownloadItem) -> DownloadItem) {
        synchronized(map) {
            val current = map[id] ?: return
            map[id] = change(current)
        }
        runMain {
            val idx = items.indexOfFirst { it.id == id }
            val latest = map[id]
            if (idx >= 0 && latest != null) items[idx] = latest
        }
        if (save) persist()
    }

    fun remove(id: String) {
        map.remove(id)
        runMain { items.removeAll { it.id == id } }
        persist()
    }

    /** Removes the record and its files from disk (video, partial data, and the anime folder if now empty). */
    fun delete(id: String) {
        val item = map[id] ?: return
        Downloader.cancel(id)
        val f = File(item.filePath)
        f.delete()
        File(item.filePath + ".part").delete()
        File(item.filePath + ".part.seg").delete()
        File(item.filePath.removeSuffix(".mp4") + ".ts").delete()
        f.parentFile?.let { if (it.isDirectory && it.list()?.isEmpty() == true) it.delete() }
        remove(id)
    }

    private fun persist() {
        val file = storeFile ?: return
        val snapshot = map.values.sortedByDescending { it.createdAt }.map { it.toJson() }
        io.execute {
            try {
                val arr = JSONArray()
                snapshot.forEach { arr.put(it) }
                val tmp = File(file.path + ".tmp")
                tmp.writeText(arr.toString())
                if (!tmp.renameTo(file)) {
                    file.writeText(arr.toString())
                    tmp.delete()
                }
            } catch (_: Exception) {
            }
            WidgetUpdater.refresh()
        }
    }

    // ---- Queries (read Compose state, so callers recompose when downloads change) ----

    fun find(animeKey: String, season: Int, episode: Int, quality: String): DownloadItem? =
        items.firstOrNull {
            it.animeKey == animeKey && it.season == season && it.episode == episode && it.quality == quality
        }

    fun episodeMark(animeKey: String, season: Int, episode: Int): DlMark {
        var mark = DlMark.NONE
        for (it in items) {
            if (it.animeKey == animeKey && it.season == season && it.episode == episode) {
                if (it.state == "completed") return DlMark.DONE
                mark = DlMark.PENDING
            }
        }
        return mark
    }

    /** Downloads that haven't finished yet (running, queued, paused or failed). */
    fun pendingCount(): Int = items.count { it.state != "completed" }

    /** Downloads actually working right now (used by the foreground service). */
    fun activeCount(): Int = map.values.count { it.isActive }

    fun activeItems(): List<DownloadItem> = map.values.filter { it.isActive }
}
