package com.aniciel.app

/**
 * Sources for NEW AniCiel (MediaHub + base.apk only).
 * No Netplay Worker / R2 / IPTV / Firebase extras.
 */
object SourceRegistry {
    data class Source(
        val id: String,
        val label: String,
        val kind: String, // movies | anime | manga
        val enabled: Boolean = true,
    )

    val all = listOf(
        Source("moviebox", "MovieBox", "movies"),
        Source("moviebox_anime", "MovieBox Anime", "anime"),
        // Manga uses separate providers in full app; placeholder hub only here
        Source("manga_hub", "Manga", "manga"),
    )
}
