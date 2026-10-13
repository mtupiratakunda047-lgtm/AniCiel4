package com.aniciel.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class LibraryEntry(
    val key: String,
    val title: String,
    val posterUrl: String?,
    val category: String,
    val libraryCategory: String = "Default",
    val lastSeason: Int = 0,
    val lastEpisode: Int = 0,
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val progressPercent: Float = 0f,
    val mergedKeys: List<String> = emptyList(),
    val notes: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("title", title)
        put("posterUrl", posterUrl ?: JSONObject.NULL)
        put("category", category)
        put("libraryCategory", libraryCategory)
        put("lastSeason", lastSeason)
        put("lastEpisode", lastEpisode)
        put("lastPositionMs", lastPositionMs)
        put("durationMs", durationMs)
        put("lastWatchedAt", lastWatchedAt)
        put("isFavorite", isFavorite)
        put("progressPercent", progressPercent.toDouble())
        put("mergedKeys", JSONArray(mergedKeys))
        put("notes", notes)
    }

    companion object {
        fun fromJson(o: JSONObject): LibraryEntry = LibraryEntry(
            key = o.getString("key"),
            title = o.getString("title"),
            posterUrl = o.optString("posterUrl").takeIf { it.isNotBlank() && it != "null" },
            category = o.optString("category", "Anime"),
            libraryCategory = o.optString("libraryCategory", "Default"),
            lastSeason = o.optInt("lastSeason", 0),
            lastEpisode = o.optInt("lastEpisode", 0),
            lastPositionMs = o.optLong("lastPositionMs", 0L),
            durationMs = o.optLong("durationMs", 0L),
            lastWatchedAt = o.optLong("lastWatchedAt", System.currentTimeMillis()),
            isFavorite = o.optBoolean("isFavorite", false),
            progressPercent = o.optDouble("progressPercent", 0.0).toFloat(),
            mergedKeys = o.optJSONArray("mergedKeys")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            notes = o.optString("notes", "")
        )
    }
}

object LibraryStore {
    private val items = ConcurrentHashMap<String, LibraryEntry>()
    private val categories = linkedSetOf("Default", "Watching", "Completed", "On Hold", "Dropped", "Plan to Watch")
    private var file: File? = null
    private var catFile: File? = null
    private val lock = Any()

    fun init(context: Context) {
        file = File(context.filesDir, "library.json")
        catFile = File(context.filesDir, "library_categories.json")
        load()
    }

    fun all(): List<LibraryEntry> = items.values.sortedByDescending { it.lastWatchedAt }
    fun byCategory(cat: String): List<LibraryEntry> =
        items.values.filter { it.libraryCategory == cat }.sortedByDescending { it.lastWatchedAt }
    fun continueWatching(limit: Int = 20): List<LibraryEntry> =
        items.values.filter { it.lastPositionMs > 0 || it.lastEpisode > 0 }
            .sortedByDescending { it.lastWatchedAt }.take(limit)
    fun favorites(): List<LibraryEntry> =
        items.values.filter { it.isFavorite }.sortedByDescending { it.lastWatchedAt }
    fun get(key: String): LibraryEntry? = items[key]
    fun categoryNames(): List<String> = categories.toList()

    fun addCategory(name: String) {
        val n = name.trim()
        if (n.isNotEmpty()) { categories.add(n); persistCategories() }
    }

    fun removeCategory(name: String) {
        if (name == "Default") return
        categories.remove(name)
        items.replaceAll { _, e -> if (e.libraryCategory == name) e.copy(libraryCategory = "Default") else e }
        persistCategories(); persist()
    }

    fun setLibraryCategory(key: String, cat: String) {
        val e = items[key] ?: return
        items[key] = e.copy(libraryCategory = cat.ifBlank { "Default" })
        persist()
    }

    fun upsert(entry: LibraryEntry) { items[entry.key] = entry; persist() }

    fun updateProgress(
        key: String, title: String, posterUrl: String?, category: String,
        season: Int, episode: Int, positionMs: Long, durationMs: Long
    ) {
        val existing = items[key]
        val percent = if (durationMs > 0) (positionMs.toFloat() / durationMs * 100f).coerceIn(0f, 100f)
            else existing?.progressPercent ?: 0f
        items[key] = (existing ?: LibraryEntry(key, title, posterUrl, category)).copy(
            title = title,
            posterUrl = posterUrl ?: existing?.posterUrl,
            category = category,
            lastSeason = season,
            lastEpisode = episode,
            lastPositionMs = positionMs,
            durationMs = durationMs,
            lastWatchedAt = System.currentTimeMillis(),
            progressPercent = percent,
            isFavorite = existing?.isFavorite ?: false,
            libraryCategory = existing?.libraryCategory ?: "Default",
            mergedKeys = existing?.mergedKeys ?: emptyList(),
            notes = existing?.notes ?: ""
        )
        persist()
    }

    fun toggleFavorite(key: String, title: String, posterUrl: String?, category: String): Boolean {
        val existing = items[key]
        val newFav = !(existing?.isFavorite ?: false)
        items[key] = (existing ?: LibraryEntry(key, title, posterUrl, category)).copy(
            isFavorite = newFav, lastWatchedAt = System.currentTimeMillis()
        )
        persist()
        return newFav
    }

    fun merge(targetKey: String, otherKeys: List<String>) {
        val target = items[targetKey] ?: return
        val others = otherKeys.mapNotNull { items[it] }
        if (others.isEmpty()) return
        val latest = (listOf(target) + others).maxByOrNull { it.lastWatchedAt } ?: target
        val allMerged = (target.mergedKeys + otherKeys + others.flatMap { it.mergedKeys }).distinct()
        items[targetKey] = target.copy(
            title = target.title.ifBlank { latest.title },
            posterUrl = target.posterUrl ?: latest.posterUrl,
            lastSeason = latest.lastSeason,
            lastEpisode = latest.lastEpisode,
            lastPositionMs = latest.lastPositionMs,
            durationMs = latest.durationMs,
            progressPercent = latest.progressPercent,
            lastWatchedAt = System.currentTimeMillis(),
            mergedKeys = allMerged,
            isFavorite = target.isFavorite || others.any { it.isFavorite }
        )
        otherKeys.forEach { items.remove(it) }
        persist()
    }

    fun migrate(oldKey: String, newProvider: String, newId: String): String? {
        val e = items[oldKey] ?: return null
        val newKey = "$newProvider:$newId"
        if (newKey == oldKey) return oldKey
        items.remove(oldKey)
        items[newKey] = e.copy(
            key = newKey,
            mergedKeys = (e.mergedKeys + oldKey).distinct(),
            lastWatchedAt = System.currentTimeMillis()
        )
        persist()
        return newKey
    }

    fun remove(key: String) { items.remove(key); persist() }

    private fun load() {
        synchronized(lock) {
            items.clear()
            file?.takeIf { it.exists() }?.let { f ->
                try {
                    val arr = JSONArray(f.readText())
                    for (i in 0 until arr.length()) {
                        val e = LibraryEntry.fromJson(arr.getJSONObject(i))
                        items[e.key] = e
                    }
                } catch (_: Exception) { }
            }
            catFile?.takeIf { it.exists() }?.let { f ->
                try {
                    val arr = JSONArray(f.readText())
                    categories.clear()
                    for (i in 0 until arr.length()) categories.add(arr.getString(i))
                    if (!categories.contains("Default")) categories.add("Default")
                } catch (_: Exception) { }
            }
        }
    }

    private fun persist() {
        synchronized(lock) {
            try {
                val arr = JSONArray()
                items.values.forEach { arr.put(it.toJson()) }
                file?.writeText(arr.toString(2))
            } catch (_: Exception) { }
        }
        WidgetUpdater.refresh()
    }

    private fun persistCategories() {
        synchronized(lock) {
            try { catFile?.writeText(JSONArray(categories.toList()).toString(2)) } catch (_: Exception) { }
        }
    }
}
