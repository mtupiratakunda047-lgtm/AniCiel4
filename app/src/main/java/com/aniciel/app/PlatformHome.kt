package com.aniciel.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aniciel.app.ui.theme.CardBlack
import com.aniciel.app.ui.theme.CielBlue
import com.aniciel.app.ui.theme.SurfaceBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Primary hubs matching the platform mockup (AniCiel branding, not MediaHub). */
enum class Hub(val label: String, val emoji: String, val accent: Color) {
    HOME("Home", "🏠", CielBlue),
    MOVIES("Movies", "🎬", Color(0xFF42A5F5)),
    ANIME("Anime", "📺", Color(0xFFAB47BC)),
    MANGA("Manga", "📖", Color(0xFF66BB6A)),
    ADULT("Adult", "🔞", Color(0xFFEF5350))
}

private val GenreMovies = listOf("All", "Action", "Adventure", "Comedy", "Sci-Fi")
private val GenreAnime = listOf("All", "Action", "Fantasy", "Adventure", "Romance")
private val GenreManga = listOf("All", "Action", "Romance", "Fantasy", "Drama")

@Composable
fun PlatformRoot(
    prefs: AppPreferences,
    onOpenSettings: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenItem: (CatalogItem) -> Unit,
    onPlay: (String, String, Int, Int) -> Unit
) {
    var hub by remember { mutableStateOf(Hub.HOME) }
    var pendingAdult by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val continueList = remember { LibraryStore.continueWatching(12) }

    fun loadHome() {
        loading = true
        error = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { Engine.homepage() }
            loading = false
            outcome.onSuccess { catalog = it }.onFailure { error = it.message }
        }
    }

    fun runSearch() {
        if (query.isBlank()) {
            loadHome()
            return
        }
        loading = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { Engine.search(query) }
            loading = false
            outcome.onSuccess { catalog = it }.onFailure { error = it.message }
        }
    }

    LaunchedEffect(Unit) { loadHome() }

    if (pendingAdult) {
        AdultAgeGateFullScreen(
            onYes = {
                prefs.adultEnabled = true
                pendingAdult = false
                hub = Hub.ADULT
            },
            onNo = { pendingAdult = false }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        PlatformTopBar(
            onOpenSources = onOpenSources,
            onOpenSettings = onOpenSettings,
            onOpenLibrary = onOpenLibrary,
            onOpenUpdates = onOpenUpdates,
            onOpenExtensions = onOpenExtensions,
            onOpenFavorites = onOpenFavorites,
            onOpenDownloads = onOpenDownloads
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search movies and all enabled sources...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = CielBlue) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CielBlue,
                unfocusedBorderColor = Color(0xFF333333),
                focusedContainerColor = SurfaceBlack,
                unfocusedContainerColor = SurfaceBlack,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { runSearch() }
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            )
        )

        Box(Modifier.weight(1f)) {
            when {
                loading && catalog.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CielBlue)
                }
                error != null && catalog.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "Error", color = Color(0xFFEF9A9A))
                }
                hub == Hub.HOME -> HomeHubContent(
                    catalog = catalog,
                    continueList = continueList,
                    onOpenItem = onOpenItem,
                    onSelectHub = { h ->
                        if (h == Hub.ADULT && !prefs.adultEnabled) pendingAdult = true
                        else hub = h
                    }
                )
                hub == Hub.MOVIES -> CategoryFeed(
                    title = "Movies",
                    subtitle = "Blockbuster stories, endless entertainment",
                    accent = Hub.MOVIES.accent,
                    genres = GenreMovies,
                    catalog = catalog,
                    continueList = continueList,
                    mode = FeedMode.VIDEO,
                    onOpenItem = onOpenItem,
                    onPlay = onPlay
                )
                hub == Hub.ANIME -> CategoryFeed(
                    title = "Anime",
                    subtitle = "Epic stories, unforgettable characters",
                    accent = Hub.ANIME.accent,
                    genres = GenreAnime,
                    catalog = catalog,
                    continueList = continueList,
                    mode = FeedMode.VIDEO,
                    onOpenItem = onOpenItem,
                    onPlay = onPlay
                )
                hub == Hub.MANGA -> CategoryFeed(
                    title = "Manga",
                    subtitle = "Great stories, one chapter at a time",
                    accent = Hub.MANGA.accent,
                    genres = GenreManga,
                    catalog = catalog,
                    continueList = continueList,
                    mode = FeedMode.READ,
                    onOpenItem = onOpenItem,
                    onPlay = onPlay
                )
                hub == Hub.ADULT -> {
                    if (!prefs.adultEnabled) {
                        // Safety: never show adult feed without confirmation
                        LaunchedEffect(Unit) { pendingAdult = true; hub = Hub.HOME }
                    } else {
                        CategoryFeed(
                            title = "Adult",
                            subtitle = "Exclusive content · 18+ only",
                            accent = Hub.ADULT.accent,
                            genres = listOf("All", "Categories", "Trending", "New"),
                            catalog = catalog,
                            continueList = emptyList(),
                            mode = FeedMode.VIDEO,
                            onOpenItem = onOpenItem,
                            onPlay = onPlay
                        )
                    }
                }
            }
        }

        NavigationBar(containerColor = Color(0xFF0A0A0C)) {
            Hub.entries.filter { it != Hub.HOME }.forEach { item ->
                NavigationBarItem(
                    selected = hub == item,
                    onClick = {
                        if (item == Hub.ADULT && !prefs.adultEnabled) pendingAdult = true
                        else hub = item
                    },
                    icon = { Text(item.emoji, fontSize = 18.sp) },
                    label = { Text(item.label, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = item.accent,
                        selectedTextColor = item.accent,
                        indicatorColor = item.accent.copy(alpha = 0.15f)
                    )
                )
            }
        }
        // Home accessible via logo tap
    }
}

@Composable
private fun PlatformTopBar(
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "AniCiel",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = CielBlue
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenSources) {
                Icon(Icons.Default.ChecklistRtl, "Sources", tint = CielBlue)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, "Settings", tint = CielBlue)
            }
            IconButton(onClick = onOpenLibrary) {
                Icon(Icons.Default.VideoLibrary, "Library", tint = CielBlue)
            }
            IconButton(onClick = onOpenUpdates) {
                Icon(Icons.Default.Notifications, "Alerts", tint = CielBlue)
            }
            IconButton(onClick = onOpenExtensions) {
                Icon(Icons.Default.Extension, "Plugins", tint = CielBlue)
            }
            IconButton(onClick = onOpenFavorites) {
                Icon(Icons.Default.Favorite, "Favorites", tint = CielBlue)
            }
            IconButton(onClick = onOpenDownloads) {
                Icon(Icons.Default.Download, "Downloads", tint = CielBlue)
            }
        }
    }
}

@Composable
private fun HomeHubContent(
    catalog: List<CatalogItem>,
    continueList: List<LibraryEntry>,
    onOpenItem: (CatalogItem) -> Unit,
    onSelectHub: (Hub) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(
                "Your World of Entertainment",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Movies, Anime, Manga & More",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF90A4AE)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HubTile(Hub.MOVIES, Modifier.weight(1f)) { onSelectHub(Hub.MOVIES) }
                HubTile(Hub.ANIME, Modifier.weight(1f)) { onSelectHub(Hub.ANIME) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HubTile(Hub.MANGA, Modifier.weight(1f)) { onSelectHub(Hub.MANGA) }
                HubTile(Hub.ADULT, Modifier.weight(1f)) { onSelectHub(Hub.ADULT) }
            }
        }
        item { SectionHeader("Featured") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(catalog.take(8)) { item ->
                    PosterCardRich(item, badge = badgeFor(item), onClick = { onOpenItem(item) })
                }
            }
        }
        item { SectionHeader("Trending") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(catalog.drop(2).take(10)) { item ->
                    PosterCardRich(item, badge = badgeFor(item), onClick = { onOpenItem(item) })
                }
            }
        }
        if (continueList.isNotEmpty()) {
            item { SectionHeader("Continue Watching") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(continueList) { e ->
                        ContinueCard(e)
                    }
                }
            }
        }
        item { SectionHeader("Recently Added") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(catalog.shuffled().take(10)) { item ->
                    PosterCardRich(item, badge = "NEW", onClick = { onOpenItem(item) })
                }
            }
        }
    }
}

private enum class FeedMode { VIDEO, READ }

@Composable
private fun CategoryFeed(
    title: String,
    subtitle: String,
    accent: Color,
    genres: List<String>,
    catalog: List<CatalogItem>,
    continueList: List<LibraryEntry>,
    mode: FeedMode,
    onOpenItem: (CatalogItem) -> Unit,
    onPlay: (String, String, Int, Int) -> Unit
) {
    var genre by remember { mutableStateOf(genres.first()) }
    val featured = catalog.firstOrNull()

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF90A4AE))
            }
        }
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.forEach { g ->
                    FilterChip(
                        selected = genre == g,
                        onClick = { genre = g },
                        label = { Text(g) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
        }
        if (featured != null) {
            item {
                FeaturedBanner(
                    item = featured,
                    accent = accent,
                    actionLabel = if (mode == FeedMode.READ) "Read" else if (title == "Anime") "Watch" else "Play",
                    onOpen = { onOpenItem(featured) },
                    onAction = {
                        if (mode == FeedMode.VIDEO) onPlay(featured.id, featured.provider, 1, 1)
                        else onOpenItem(featured)
                    }
                )
            }
        }
        item { SectionHeader(if (mode == FeedMode.READ) "Latest Chapters" else "Trending $title") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(catalog.take(12)) { item ->
                    PosterCardRich(item, badge = badgeFor(item), onClick = { onOpenItem(item) })
                }
            }
        }
        if (continueList.isNotEmpty() && mode == FeedMode.VIDEO) {
            item { SectionHeader("Continue Watching") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(continueList) { ContinueCard(it) }
                }
            }
        }
        item { SectionHeader("Recently Added") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(catalog.drop(3).take(12)) { item ->
                    PosterCardRich(item, badge = "NEW", onClick = { onOpenItem(item) })
                }
            }
        }
        item { SectionHeader("Recommended for You") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(catalog.shuffled().take(10)) { item ->
                    PosterCardRich(item, onClick = { onOpenItem(item) })
                }
            }
        }
    }
}

@Composable
private fun HubTile(hub: Hub, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(hub.accent.copy(alpha = 0.2f))
            .border(1.dp, hub.accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hub.emoji, fontSize = 22.sp)
            Text(hub.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun FeaturedBanner(
    item: CatalogItem,
    accent: Color,
    actionLabel: String,
    onOpen: () -> Unit,
    onAction: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text("Featured", color = accent, style = MaterialTheme.typography.labelMedium)
            Text(
                item.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(item.year, item.mediaType.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = Color(0xFFB0BEC5), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(actionLabel, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PosterCardRich(
    item: CatalogItem,
    badge: String? = null,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardBlack)
            )
            if (badge != null) {
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (!item.year.isNullOrBlank()) {
            Text(item.year, style = MaterialTheme.typography.labelSmall, color = Color(0xFF90A4AE))
        }
    }
}

@Composable
private fun ContinueCard(entry: LibraryEntry) {
    Column(
        Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBlack)
            .padding(8.dp)
    ) {
        AsyncImage(
            model = entry.posterUrl,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Text(
            entry.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            "S${entry.lastSeason} · E${entry.lastEpisode}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF90A4AE)
        )
        LinearProgressIndicator(
            progress = (entry.progressPercent / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = CielBlue,
            trackColor = Color(0xFF333333)
        )
    }
}

@Composable
fun AdultAgeGateFullScreen(onYes: () -> Unit, onNo: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A0000), Color.Black, Color(0xFF0D0000))
                )
            )
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(44.dp))
                    .background(Color(0x33E53935))
                    .border(2.dp, Color(0xFFE53935), RoundedCornerShape(44.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("18+", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontSize = 28.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Are you 18 years old\nor older?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "This section contains adult content.\nPlease confirm your age to continue.",
                color = Color(0xFFB0BEC5),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onYes,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Text("YES — I'm 18+", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onNo,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("NO — Exit")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "You must be 18 or older to access this section.",
                color = Color(0xFF78909C),
                fontSize = 12.sp
            )
        }
    }
}

private fun badgeFor(item: CatalogItem): String? {
    val t = item.title.lowercase()
    return when {
        t.contains("4k") -> "4K"
        item.mediaType.contains("movie", true) -> "HD"
        else -> null
    }
}
