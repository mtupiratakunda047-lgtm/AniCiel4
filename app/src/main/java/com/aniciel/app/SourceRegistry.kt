package com.aniciel.app

/**
 * Sources for AniCiel MediaHub NEW — base.apk MovieBox only.
 * No Netplay Worker/R2/IPTV/Firebase.
 */
object SourceRegistry {
    data class Source(val id: String, val label: String, val kind: String, val enabled: Boolean = true)

    val all = listOf(
        Source("moviebox", "MovieBox", "movies"),
        Source("moviebox_series", "MovieBox TV/Series", "movies"),
        Source("moviebox_anime", "MovieBox Animation", "anime"),
        Source("moviebox_kids", "MovieBox Kids", "movies"),
        Source("manga_hub", "Manga", "manga"),
    )
}
