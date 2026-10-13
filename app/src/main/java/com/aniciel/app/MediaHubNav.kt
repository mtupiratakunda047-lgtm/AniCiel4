package com.aniciel.app

import androidx.compose.ui.graphics.Color
import com.aniciel.app.ui.theme.HubAdult
import com.aniciel.app.ui.theme.HubAnime
import com.aniciel.app.ui.theme.HubHome
import com.aniciel.app.ui.theme.HubManga
import com.aniciel.app.ui.theme.HubMovies

/**
 * Bottom navigation from MediaHub mockup.
 * Home → Movies → Anime → Manga → Adult (age-gated)
 * No account / VIP tab.
 */
enum class MediaHub(val label: String, val accent: Color) {
    HOME("Home", HubHome),
    MOVIES("Movies", HubMovies),
    ANIME("Anime", HubAnime),
    MANGA("Manga", HubManga),
    ADULT("Adult", HubAdult),
}

val GenreMovies = listOf("All", "Action", "Adventure", "Comedy", "Sci-Fi")
val GenreAnime = listOf("All", "Action", "Fantasy", "Adventure", "Romance")
val GenreManga = listOf("All", "Action", "Romance", "Fantasy", "Drama")
