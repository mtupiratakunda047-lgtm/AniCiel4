package com.aniciel.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri

enum class TrackerService(val label: String, val searchUrl: (String) -> String) {
    MYANIMELIST("MyAnimeList", { q -> "https://myanimelist.net/search/all?q=${Uri.encode(q)}" }),
    ANILIST("AniList", { q -> "https://anilist.co/search/anime?search=${Uri.encode(q)}" }),
    KITSU("Kitsu", { q -> "https://kitsu.io/anime?text=${Uri.encode(q)}" }),
    SIMKL("Simkl", { q -> "https://simkl.com/search/?q=${Uri.encode(q)}" }),
    SHIKIMORI("Shikimori", { q -> "https://shikimori.one/animes?search=${Uri.encode(q)}" }),
    BANGUMI("Bangumi", { q -> "https://bgm.tv/subject_search/${Uri.encode(q)}?cat=2" })
}

object Trackers {
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("aniciel_trackers", Context.MODE_PRIVATE)

    fun openSearch(context: Context, service: TrackerService, title: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(service.searchUrl(title)))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun setLinkedId(context: Context, libraryKey: String, service: TrackerService, externalId: String) {
        prefs(context).edit().putString("${service.name}:$libraryKey", externalId).apply()
    }

    fun getLinkedId(context: Context, libraryKey: String, service: TrackerService): String? =
        prefs(context).getString("${service.name}:$libraryKey", null)

    fun allServices(): List<TrackerService> = TrackerService.entries
}
