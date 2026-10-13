package com.aniciel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** MediaHub mockup palette only (not Netplay / old AniCiel blue). */
val HubMovies = Color(0xFF3B82F6)
val HubAnime = Color(0xFFA855F7)
val HubManga = Color(0xFF22C55E)
val HubAdult = Color(0xFFF43F5E)
val HubHome = Color(0xFF60A5FA)

val AppBg = Color(0xFF0A0E17)
val SurfaceBg = Color(0xFF0F1623)
val CardBg = Color(0xFF151C2E)
val CardElevated = Color(0xFF1A2336)
val BorderSubtle = Color(0xFF243049)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)

val CielBlue = HubMovies
val CielBlueBright = HubHome
val AppBlack = AppBg
val SurfaceBlack = SurfaceBg
val CardBlack = CardBg

private val Scheme = darkColorScheme(
    primary = HubMovies,
    onPrimary = Color.White,
    secondary = HubAnime,
    tertiary = HubManga,
    background = AppBg,
    onBackground = TextPrimary,
    surface = SurfaceBg,
    onSurface = TextPrimary,
    surfaceVariant = CardBg,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = HubAdult,
    onError = Color.White,
)

@Composable
fun AniCielTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
