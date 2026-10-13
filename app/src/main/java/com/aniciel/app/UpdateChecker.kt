package com.aniciel.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update check (item 20) — checks a GitHub Releases API URL.
 * Does not embed a full installer (keeps size small). Opens the release page.
 */
data class UpdateInfo(val tag: String, val name: String, val url: String, val notes: String)

object UpdateChecker {
    /** Override in settings if you host your own releases. */
    var releasesApiUrl: String =
        "https://api.github.com/repos/mtupiratakunda047-lgtm/Public-/releases/latest"

    suspend fun check(currentVersion: String): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(releasesApiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "AniCiel-UpdateChecker")
            }
            if (conn.responseCode !in 200..299) return@runCatching null
            val body = conn.inputStream.bufferedReader().readText()
            val o = JSONObject(body)
            val tag = o.optString("tag_name", "").removePrefix("v")
            if (tag.isBlank() || tag == currentVersion) return@runCatching null
            UpdateInfo(
                tag = tag,
                name = o.optString("name", tag),
                url = o.optString("html_url", ""),
                notes = o.optString("body", "").take(500)
            )
        }
    }

    fun openRelease(context: Context, info: UpdateInfo) {
        if (info.url.isBlank()) return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
