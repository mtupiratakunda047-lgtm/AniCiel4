package com.aniciel.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight extension system (source ZIP stays small; APK may grow).
 *
 * - Fetches an index JSON from a repo URL (Aniyomi-style list is fine as data).
 * - Downloads extension APKs and launches the system package installer.
 * - Lists installed packages that look like extensions.
 *
 * Note: Official Aniyomi/Anikku extension APKs implement their own Source API.
 * This module installs and tracks them. Wiring each into AniCiel's Engine
 * requires per-source adapters or a full Aniyomi-compatible runtime (future work).
 */
data class ExtensionInfo(
    val name: String,
    val pkg: String,
    val version: String,
    val versionCode: Int,
    val lang: String,
    val apkUrl: String,
    val nsfw: Boolean = false,
    val installed: Boolean = false
)

object Extensions {
    /** Default public-style index URL — change in settings if needed. */
    var repoIndexUrl: String =
        "https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo/index.min.json"

    private const val PREFS = "aniciel_extensions"

    fun installedPackages(context: Context): List<String> {
        val pm = context.packageManager
        return try {
            val apps = if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
            apps.map { it.packageName }.filter { pkg ->
                pkg.contains("extension", ignoreCase = true) ||
                    pkg.contains("aniyomi", ignoreCase = true) ||
                    pkg.contains("tachiyomi", ignoreCase = true) ||
                    pkg.startsWith("eu.kanade.") ||
                    pkg.startsWith("app.aniyomi.")
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchIndex(context: Context): Result<List<ExtensionInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(repoIndexUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 20000
                setRequestProperty("User-Agent", "AniCiel-Extensions/0.4")
            }
            if (conn.responseCode !in 200..299) error("Repo HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().readText()
            val installed = installedPackages(context).toSet()
            parseIndex(body, installed)
        }
    }

    private fun parseIndex(body: String, installed: Set<String>): List<ExtensionInfo> {
        val trimmed = body.trim()
        val list = mutableListOf<ExtensionInfo>()
        // Aniyomi index.min.json is typically a JSON array of objects
        val arr = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> {
                val o = JSONObject(trimmed)
                o.optJSONArray("extensions") ?: o.optJSONArray("apps") ?: JSONArray()
            }
        }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pkg = o.optString("pkg", o.optString("packageName", ""))
            if (pkg.isBlank()) continue
            val apk = o.optString("apk", "")
            val base = o.optString("repo", "").ifBlank {
                repoIndexUrl.substringBeforeLast("/")
            }
            val apkUrl = when {
                apk.startsWith("http") -> apk
                apk.isNotBlank() -> "$base/$apk"
                else -> o.optString("apkUrl", "")
            }
            list += ExtensionInfo(
                name = o.optString("name", pkg.substringAfterLast('.')),
                pkg = pkg,
                version = o.optString("version", o.optString("versionName", "?")),
                versionCode = o.optInt("code", o.optInt("versionCode", 0)),
                lang = o.optString("lang", "all"),
                apkUrl = apkUrl,
                nsfw = o.optBoolean("nsfw", false),
                installed = pkg in installed
            )
        }
        return list.sortedBy { it.name.lowercase() }
    }

    suspend fun downloadApk(context: Context, info: ExtensionInfo): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (info.apkUrl.isBlank()) error("No APK URL for ${info.name}")
            val dir = File(context.cacheDir, "extensions").apply { mkdirs() }
            val out = File(dir, "${info.pkg}.apk")
            val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 120000
                setRequestProperty("User-Agent", "AniCiel-Extensions/0.4")
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) error("Download HTTP ${conn.responseCode}")
            conn.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out
        }
    }

    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAppSettings(context: Context, pkg: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
