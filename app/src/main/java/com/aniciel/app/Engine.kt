package com.aniciel.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class CatalogItem(
    val id: String,
    val provider: String,
    val title: String,
    val mediaType: String,
    val year: String?,
    val posterUrl: String?,
    val seasonCount: Int?
)

data class EpisodeInfo(val season: Int, val number: Int, val title: String?)
data class SeasonInfo(val number: Int, val episodes: List<EpisodeInfo>)

data class MediaDetails(
    val id: String,
    val provider: String,
    val title: String,
    val mediaType: String,
    val year: String?,
    val description: String?,
    val tagline: String?,
    val imdbRating: String?,
    val director: String?,
    val stars: String?,
    val duration: String?,
    val posterUrl: String?,
    val genres: List<String>,
    val seasons: List<SeasonInfo>
)

data class MirrorInfo(
    val label: String,
    val url: String,
    val headers: List<Pair<String, String>>,
    val directFile: Boolean
) {
    val isHls: Boolean get() = url.contains(".m3u8", ignoreCase = true)
}

data class ReleaseInfo(
    val quality: String,
    val language: String?,
    val sizeBytes: Long?,
    val resourceId: String?,
    val mirrors: List<MirrorInfo>
)

data class SubtitleOption(val name: String, val url: String)

data class DownloadStatus(
    val state: String,
    val downloaded: Long,
    val total: Long,
    val bytesPerSecond: Double,
    val error: String?
)

data class FavoriteItem(
    val subjectId: String,
    val provider: String,
    val title: String,
    val coverUrl: String?,
    val stype: Long
)

data class ResolvedSource(
    val url: String,
    val label: String,
    val headers: List<Pair<String, String>>
)

/**
 * Pure-Kotlin MovieBox engine (no native .so required).
 * Hosts + signing from base.apk. No VIP / coins / ads / login.
 */
object Engine {

    private val SECRET = byteArrayOf(
        0xef.toByte(), 0xa8.toByte(), 0x91.toByte(), 0x97.toByte(),
        0x4e, 0xec.toByte(), 0xd3.toByte(), 0x14,
        0x8d.toByte(), 0xf6.toByte(), 0x3a, 0xa6.toByte(),
        0x11, 0x60, 0x2d, 0xef.toByte(),
        0xd1.toByte(), 0x01, 0x25, 0x9b.toByte(),
        0xa5.toByte(), 0x21, 0x02, 0x2c,
        0x57, 0xae.toByte(), 0x05, 0x66,
        0xbd.toByte(), 0x8e.toByte()
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile private var dataDir: File? = null
    private val downloadState = ConcurrentHashMap<String, DownloadStatus>()

    fun init(dir: String) {
        dataDir = File(dir).also { it.mkdirs() }
    }

    fun search(query: String, page: Int = 0): Result<List<CatalogItem>> = runCatching {
        val body = JSONObject()
            .put("keyword", query)
            .put("page", page + 1)
            .put("perPage", 20)
            .toString()
        parseSearchOrList(apiPost(MovieBoxApi.SUBJECT_API_SEARCH, body))
    }

    fun homepage(tabId: String = "2", page: Int = 1): Result<List<CatalogItem>> = runCatching {
        val body = JSONObject()
            .put("page", page)
            .put("perPage", 20)
            .put("tabId", tabId)
            .toString()
        parseSearchOrList(apiPost(MovieBoxApi.SUBJECT_API_TRENDING_V2, body))
    }

    fun details(subjectId: String, provider: String): Result<MediaDetails> = runCatching {
        val body = JSONObject().put("subjectId", subjectId).toString()
        val root = JSONObject(apiPost(MovieBoxApi.SUBJECT_API_GET, body))
        val data = root.optJSONObject("data") ?: root
        val seasonsArr = data.optJSONArray("seasons") ?: data.optJSONArray("seasonList") ?: JSONArray()
        val seasons = (0 until seasonsArr.length()).map { i ->
            val s = seasonsArr.getJSONObject(i)
            val num = s.optInt("season", s.optInt("number", i + 1))
            val epArr = s.optJSONArray("episodes") ?: s.optJSONArray("episodeList") ?: JSONArray()
            val episodes = (0 until epArr.length()).map { j ->
                val e = epArr.getJSONObject(j)
                EpisodeInfo(
                    season = num,
                    number = e.optInt("episode", e.optInt("number", j + 1)),
                    title = e.optStringOrNull("title") ?: e.optStringOrNull("name")
                )
            }
            SeasonInfo(number = num, episodes = episodes)
        }
        val genresArr = data.optJSONArray("genre") ?: data.optJSONArray("genres") ?: JSONArray()
        val genres = (0 until genresArr.length()).mapNotNull { i ->
            when (val v = genresArr.get(i)) {
                is String -> v
                is JSONObject -> v.optString("name", null)
                else -> null
            }
        }
        MediaDetails(
            id = subjectId,
            provider = provider.ifBlank { "moviebox" },
            title = data.optString("title", data.optString("name", "Unknown")),
            mediaType = data.optString("subjectType", data.optString("media_type", "movie")),
            year = data.optStringOrNull("year") ?: data.optStringOrNull("releaseDate"),
            description = data.optStringOrNull("description") ?: data.optStringOrNull("intro"),
            tagline = data.optStringOrNull("tagline"),
            imdbRating = data.optStringOrNull("imdbRating") ?: data.optStringOrNull("score"),
            director = data.optStringOrNull("director"),
            stars = data.optStringOrNull("stars") ?: data.optStringOrNull("actors"),
            duration = data.optStringOrNull("duration"),
            posterUrl = resolveImage(data.optStringOrNull("cover") ?: data.optStringOrNull("poster")),
            genres = genres,
            seasons = seasons
        )
    }

    fun listReleases(subjectId: String, provider: String, season: Int, episode: Int): Result<List<ReleaseInfo>> = runCatching {
        val body = JSONObject()
            .put("subjectId", subjectId)
            .put("se", season)
            .put("ep", episode)
            .toString()
        val root = JSONObject(apiPost(MovieBoxApi.SUBJECT_API_RESOURCE, body))
        val data = root.optJSONObject("data") ?: root
        val list = data.optJSONArray("list") ?: data.optJSONArray("resources") ?: JSONArray()
        (0 until list.length()).map { i ->
            val r = list.getJSONObject(i)
            val mirrorsArr = r.optJSONArray("mirrors") ?: r.optJSONArray("streams") ?: JSONArray()
            val mirrors = mutableListOf<MirrorInfo>()
            if (mirrorsArr.length() == 0) {
                val url = r.optStringOrNull("url") ?: r.optStringOrNull("playUrl")
                if (!url.isNullOrBlank()) {
                    mirrors += MirrorInfo(
                        label = r.optString("resolution", r.optString("quality", "Default")),
                        url = url,
                        headers = defaultStreamHeaders(),
                        directFile = !url.contains(".m3u8", ignoreCase = true)
                    )
                }
            } else {
                for (j in 0 until mirrorsArr.length()) {
                    val m = mirrorsArr.getJSONObject(j)
                    val url = m.optStringOrNull("url") ?: m.optStringOrNull("playUrl") ?: continue
                    mirrors += MirrorInfo(
                        label = m.optString("label", m.optString("resolution", "Stream")),
                        url = url,
                        headers = defaultStreamHeaders(),
                        directFile = !url.contains(".m3u8", ignoreCase = true)
                    )
                }
            }
            ReleaseInfo(
                quality = r.optString("resolution", r.optString("quality", "Auto")),
                language = r.optStringOrNull("language") ?: r.optStringOrNull("lang"),
                sizeBytes = r.optLong("size", 0L).takeIf { it > 0 },
                resourceId = r.optStringOrNull("resourceId") ?: r.optStringOrNull("id"),
                mirrors = mirrors
            )
        }.filter { it.mirrors.isNotEmpty() }
    }

    fun resolve(subjectId: String, provider: String, season: Int, episode: Int): Result<ResolvedSource> = runCatching {
        val first = listReleases(subjectId, provider, season, episode).getOrThrow()
            .firstOrNull()?.mirrors?.firstOrNull()
            ?: throw RuntimeException("No playable source")
        ResolvedSource(url = first.url, label = first.label, headers = first.headers)
    }

    fun subtitles(subjectId: String, resourceId: String, season: Int, episode: Int): Result<List<SubtitleOption>> = runCatching {
        val body = JSONObject()
            .put("subjectId", subjectId)
            .put("resourceId", resourceId)
            .put("se", season)
            .put("ep", episode)
            .toString()
        val root = JSONObject(apiPost(MovieBoxApi.SUBJECT_API_GET_EXT_CAPTIONS, body))
        val data = root.optJSONObject("data") ?: root
        val arr = data.optJSONArray("list") ?: data.optJSONArray("captions") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val url = o.optStringOrNull("url") ?: o.optStringOrNull("file") ?: return@mapNotNull null
            SubtitleOption(name = o.optString("name", o.optString("lang", "Subtitle")), url = url)
        }
    }

    fun startDownload(source: ResolvedSource, destPath: String): Result<String> = runCatching {
        val id = UUID.randomUUID().toString()
        downloadState[id] = DownloadStatus("running", 0, 0, 0.0, null)
        // Actual file download is owned by Downloader / DashDownloader screens.
        Thread {
            downloadState[id] = DownloadStatus("completed", 1, 1, 0.0, null)
        }.start()
        id
    }

    fun downloadStatus(id: String): Result<DownloadStatus> = runCatching {
        downloadState[id] ?: DownloadStatus("failed", 0, 0, 0.0, "unknown id")
    }

    fun cancelDownload(id: String) {
        downloadState[id] = DownloadStatus("cancelled", 0, 0, 0.0, null)
    }

    fun favoritesList(): Result<List<FavoriteItem>> = runCatching {
        val f = favoritesFile() ?: return@runCatching emptyList()
        if (!f.exists()) return@runCatching emptyList()
        val arr = JSONArray(f.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FavoriteItem(
                subjectId = o.getString("subject_id"),
                provider = o.optString("provider", "moviebox"),
                title = o.getString("title"),
                coverUrl = o.optStringOrNull("cover_url"),
                stype = o.optLong("stype", 1)
            )
        }
    }

    fun favoritesToggle(subjectId: String, provider: String): Result<Boolean> = runCatching {
        val current = favoritesList().getOrElse { emptyList() }.toMutableList()
        val idx = current.indexOfFirst { it.subjectId == subjectId && it.provider == provider }
        val nowFavorite = if (idx >= 0) {
            current.removeAt(idx)
            false
        } else {
            current.add(FavoriteItem(subjectId, provider, subjectId, null, 1))
            true
        }
        val arr = JSONArray()
        current.forEach { item ->
            arr.put(
                JSONObject()
                    .put("subject_id", item.subjectId)
                    .put("provider", item.provider)
                    .put("title", item.title)
                    .put("cover_url", item.coverUrl)
                    .put("stype", item.stype)
            )
        }
        favoritesFile()?.writeText(arr.toString())
        nowFavorite
    }

    // ---- HTTP + signing ----

    private fun apiPost(path: String, body: String): String {
        var lastError: Exception? = null
        for (host in MovieBoxApi.HOSTS) {
            try {
                val url = host.trimEnd('/') + path
                val ts = System.currentTimeMillis()
                val clientToken = generateXClientToken(ts / 1000)
                val signature = sign("POST", url, body, ts)
                val (clientInfo, ua) = clientInfoAndUa()
                val req = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("User-Agent", ua)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Connection", "keep-alive")
                    .header("x-client-token", clientToken)
                    .header("x-tr-signature", signature)
                    .header("x-client-info", clientInfo)
                    .header("x-client-status", "0")
                    .header("x-forwarded-for", spoofedIp())
                    .build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw RuntimeException("HTTP ${resp.code}: ${text.take(200)}")
                    }
                    val root = runCatching { JSONObject(text) }.getOrNull()
                    if (root != null && root.has("code")) {
                        val code = root.optInt("code")
                        if (code != 0 && code != 200) {
                            throw RuntimeException(root.optString("msg", root.optString("message", "API error")))
                        }
                    }
                    return text
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("All hosts failed")
    }

    private fun generateXClientToken(tsSec: Long): String {
        val ts = tsSec.toString()
        val hash = md5Hex(ts.reversed().toByteArray())
        return "$ts,$hash"
    }

    private fun sign(method: String, url: String, body: String?, timestampMs: Long): String {
        val pathAndQuery = try {
            val u = java.net.URI(url)
            val path = u.path ?: "/"
            val q = u.query
            if (q.isNullOrBlank()) path else "$path?${sortedQuery(q)}"
        } catch (_: Exception) {
            url
        }
        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        val (bodyHash, bodyLen) = if (bodyBytes != null) {
            val truncated = if (bodyBytes.size > 102_400) bodyBytes.copyOf(102_400) else bodyBytes
            md5Hex(truncated) to bodyBytes.size.toString()
        } else {
            "" to ""
        }
        val canonical = listOf(
            method.uppercase(),
            "application/json",
            "application/json; charset=utf-8",
            pathAndQuery,
            bodyHash,
            bodyLen,
            timestampMs.toString()
        ).joinToString("\n")
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(SECRET, "HmacMD5"))
        val raw = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)
    }

    private fun sortedQuery(query: String): String {
        val map = TreeMap<String, MutableList<String>>()
        query.split('&').forEach { part ->
            val eq = part.indexOf('=')
            if (eq > 0) {
                val k = part.substring(0, eq)
                val v = part.substring(eq + 1)
                map.getOrPut(k) { mutableListOf() }.add(v)
            }
        }
        return map.flatMap { (k, vs) -> vs.map { "$k=$it" } }.joinToString("&")
    }

    private fun md5Hex(bytes: ByteArray): String {
        val dig = MessageDigest.getInstance("MD5").digest(bytes)
        return dig.joinToString("") { "%02x".format(it) }
    }

    private fun clientInfoAndUa(): Pair<String, String> {
        val androidVer = listOf("11", "12", "13").random()
        val device = listOf("2201117TY", "22101316G", "M2012K11AG").random()
        val versionCode = listOf(50020117, 50020118, 50020119).random()
        val network = listOf("NETWORK_WIFI", "NETWORK_MOBILE").random()
        val tz = listOf("Asia/Kolkata", "Asia/Shanghai", "Europe/London").random()
        val clientInfo =
            """{"package_name":"com.community.oneroom","version_code":$versionCode,"os":"Android","os_version":"$androidVer","device_id":"$device","network":"$network","timezone":"$tz"}"""
        val ua =
            "Mozilla/5.0 (Linux; Android $androidVer; $device) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36"
        return clientInfo to ua
    }

    private fun spoofedIp(): String {
        val a = (1..223).random()
        val b = (0..255).random()
        val c = (0..255).random()
        val d = (1..254).random()
        return "$a.$b.$c.$d"
    }

    private fun defaultStreamHeaders(): List<Pair<String, String>> = listOf(
        "Referer" to MovieBoxApi.STREAM_REFERER,
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
    )

    private fun resolveImage(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) return path
        return MovieBoxApi.CDN.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun parseSearchOrList(json: String): List<CatalogItem> {
        val root = JSONObject(json)
        val data = root.optJSONObject("data") ?: root
        val arr = data.optJSONArray("list")
            ?: data.optJSONArray("items")
            ?: data.optJSONArray("subjects")
            ?: data.optJSONArray("result")
            ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optStringOrNull("subjectId")
                ?: o.optStringOrNull("id")
                ?: o.optJSONObject("id")?.optStringOrNull("value")
                ?: return@mapNotNull null
            CatalogItem(
                id = id,
                provider = "moviebox",
                title = o.optString("title", o.optString("name", "Untitled")),
                mediaType = o.optString("subjectType", o.optString("media_type", "movie")),
                year = o.optStringOrNull("year"),
                posterUrl = resolveImage(o.optStringOrNull("cover") ?: o.optStringOrNull("poster")),
                seasonCount = o.optInt("seasonCount", 0).takeIf { it > 0 }
            )
        }
    }

    private fun favoritesFile(): File? {
        val dir = dataDir ?: return null
        return File(dir, "favorites.json")
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) {
        val s = getString(key)
        if (s.isBlank() || s == "null") null else s
    } else null
