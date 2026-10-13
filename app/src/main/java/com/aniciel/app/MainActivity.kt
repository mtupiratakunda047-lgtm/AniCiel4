package com.aniciel.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aniciel.app.ui.theme.AniCielTheme
import com.aniciel.app.ui.theme.AppBlack
import com.aniciel.app.ui.theme.CardBlack
import com.aniciel.app.ui.theme.CielBlueBright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ContentSection(val label: String, val emoji: String) {
    MOVIES("Movies", "🎬"),
    ANIME("Anime", "📺"),
    MANGA("Manga", "📖"),
    ADULT("Adult", "🔞")
}

private sealed class Screen {
    data object Browse : Screen()
    data class Details(val id: String, val provider: String, val title: String, val posterUrl: String?) : Screen()
    data object Downloads : Screen()
    data object Favorites : Screen()
    data object SourceCenter : Screen()
    data object Library : Screen()
    data object Updates : Screen()
    data object Extensions : Screen()
}

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Engine.init(File(filesDir, "enginedata").absolutePath)
        DownloadsStore.init(applicationContext)
        LibraryStore.init(applicationContext)
        WidgetUpdater.init(applicationContext)
        Thread { DownloadsStore.importOrphans(DownloadSettings.resolvedDir(this@MainActivity)) }.start()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AniCielTheme {
                var showSplash by remember { mutableStateOf(!splashShown) }
                Box(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        onPlay = { id, provider, season, episode ->
                            val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                                putExtra(PlayerActivity.EXTRA_SUBJECT_ID, id)
                                putExtra(PlayerActivity.EXTRA_PROVIDER, provider)
                                putExtra(PlayerActivity.EXTRA_SEASON, season)
                                putExtra(PlayerActivity.EXTRA_EPISODE, episode)
                            }
                            startActivity(intent)
                        },
                        downloadsDir = { DownloadSettings.resolvedDir(this@MainActivity) },
                        onOpenSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
                    )
                }
                if (showSplash) {
                    SplashOverlay(onFinished = {
                        splashShown = true
                        showSplash = false
                    })
                }
                }
            }
        }
    }

    companion object {
        /** True once the launch animation has played, so rotating or reopening doesn't replay it. */
        private var splashShown = false
        const val EXTRA_OPEN = "open"
        const val OPEN_LIBRARY = "library"
        const val OPEN_DOWNLOADS = "downloads"
        const val OPEN_UPDATES = "updates"
    }
}

@Composable
private fun AppRoot(
    onPlay: (String, String, Int, Int) -> Unit,
    downloadsDir: () -> File,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val launchOpen = remember {
        (context as? Activity)?.intent?.getStringExtra(MainActivity.EXTRA_OPEN)
    }
    var screen by remember {
        mutableStateOf<Screen>(
            when (launchOpen) {
                MainActivity.OPEN_LIBRARY -> Screen.Library
                MainActivity.OPEN_DOWNLOADS -> Screen.Downloads
                MainActivity.OPEN_UPDATES -> Screen.Updates
                else -> Screen.Browse
            }
        )
    }
    var section by remember { mutableStateOf(ContentSection.MOVIES) }
    var pendingAdult by remember { mutableStateOf(false) }
    val browseState = remember { BrowseState() }
    val gridState = rememberLazyGridState()

    // System Back returns to Browse from any inner screen instead of exiting the app.
    BackHandler(enabled = screen !is Screen.Browse) { screen = Screen.Browse }

    // On the home screen, Back asks before leaving the app.
    val activity = LocalContext.current as? Activity
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler(enabled = screen is Screen.Browse) { confirmExit = true }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            containerColor = CardBlack,
            title = { Text("Exit AniCiel?") },
            text = { Text("Downloads keep running in the background.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; activity?.finish() }) { Text("Exit") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay") } }
        )
    }

    if (pendingAdult) {
        AdultAgeGateDialog(
            onConfirm = {
                prefs.adultEnabled = true
                pendingAdult = false
                section = ContentSection.ADULT
            },
            onDecline = {
                pendingAdult = false
                // stay on current section; do not open Adult
            }
        )
    }

    when (val s = screen) {
        is Screen.Browse -> PlatformRoot(
            prefs = prefs,
            onOpenSettings = onOpenSettings,
            onOpenSources = { screen = Screen.SourceCenter },
            onOpenDownloads = { screen = Screen.Downloads },
            onOpenFavorites = { screen = Screen.Favorites },
            onOpenLibrary = { screen = Screen.Library },
            onOpenUpdates = { screen = Screen.Updates },
            onOpenExtensions = { screen = Screen.Extensions },
            onOpenItem = { item -> screen = Screen.Details(item.id, item.provider, item.title, item.posterUrl) },
            onPlay = onPlay
        )
        is Screen.Details -> DetailsScreen(
            id = s.id,
            provider = s.provider,
            fallbackTitle = s.title,
            posterUrl = s.posterUrl,
            onBack = { screen = Screen.Browse },
            onPlay = onPlay,
            downloadsDir = downloadsDir,
            onOpenSimilar = { item -> screen = Screen.Details(item.id, item.provider, item.title, item.posterUrl) }
        )
        is Screen.Downloads -> DownloadsScreen(onBack = { screen = Screen.Browse })
        is Screen.Favorites -> FavoritesScreen(
            onBack = { screen = Screen.Browse },
            onOpen = { item -> screen = Screen.Details(item.id, item.provider, item.title, item.posterUrl) }
        )
        is Screen.Library -> LibraryScreen(
            onBack = { screen = Screen.Browse },
            onOpen = { e ->
                val provider = e.key.substringBefore(':', "moviebox")
                val id = e.key.substringAfter(':', e.key)
                screen = Screen.Details(id, provider, e.title, e.posterUrl)
            }
        )
        is Screen.Updates -> UpdatesScreen(onBack = { screen = Screen.Browse })
        is Screen.Extensions -> ExtensionsScreen(onBack = { screen = Screen.Browse })
        is Screen.SourceCenter -> SourceCenterScreen(
            prefs = prefs,
            onBack = { screen = Screen.Browse }
        )
    }
}

/**
 * Browse-screen state lives here (created once in AppRoot) instead of inside
 * BrowseScreen, because BrowseScreen leaves composition whenever another
 * screen is showing. Keeping it outside means your search, results and
 * scroll position are still there when you come back.
 */
private class BrowseState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<CatalogItem>>(emptyList())
    var sectionTitle by mutableStateOf("Trending")
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(false)
    var suggestions by mutableStateOf<List<CatalogItem>>(emptyList())
    var showSuggestions by mutableStateOf(false)
}

@Composable
private fun BrowseScreen(
    state: BrowseState,
    gridState: LazyGridState,
    section: ContentSection,
    prefs: AppPreferences,
    onSectionChanged: (ContentSection) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSources: () -> Unit,
    onOpen: (CatalogItem) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenExtensions: () -> Unit
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(section) {
        state.loading = section == ContentSection.MOVIES
        state.error = null
        if (section == ContentSection.MOVIES) {
            val outcome = withContext(Dispatchers.IO) { Engine.homepage() }
            state.loading = false
            state.loaded = true
            state.sectionTitle = "Trending movies"
            outcome.onSuccess { state.results = it }
                .onFailure { state.error = it.message ?: "failed to load trending" }
        } else {
            state.loaded = true
            state.loading = false
            state.results = emptyList()
            state.sectionTitle = when (section) {
                ContentSection.ANIME -> "Search anime across enabled sources"
                ContentSection.MANGA -> "Manga sources and local library"
                ContentSection.ADULT -> "Adult sources (separate category)"
                ContentSection.MOVIES -> "Trending movies"
            }
        }
    }

    // Live suggestions while typing (debounced so we don't search on every keystroke).
    LaunchedEffect(state.query) {
        val q = state.query.trim()
        if (q.length < 2) {
            state.suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        val outcome = withContext(Dispatchers.IO) { Engine.search(q) }
        outcome.onSuccess { state.suggestions = it.take(6) }
    }

    fun runSearch() {
        if (state.query.isBlank()) return
        state.showSuggestions = false
        state.loading = true
        state.error = null
        state.sectionTitle = "Results for \"${state.query}\" · ${section.label}"
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { Engine.search(state.query) }
            state.loading = false
            outcome.onSuccess { state.results = it }
                .onFailure { state.error = it.message ?: "search failed" }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text(
                "AniCiel",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
            IconButton(onClick = onOpenSources) {
                Icon(Icons.Default.ChecklistRtl, contentDescription = "Source Center", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenLibrary) {
                Icon(Icons.Default.VideoLibrary, contentDescription = "Library", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenUpdates) {
                Icon(Icons.Default.NewReleases, contentDescription = "Updates", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenExtensions) {
                Icon(Icons.Default.Extension, contentDescription = "Extensions", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenFavorites) {
                Icon(Icons.Default.Favorite, contentDescription = "Favorites", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenDownloads) {
                BadgedBox(badge = {
                    val pending = DownloadsStore.pendingCount()
                    if (pending > 0) Badge { Text("$pending") }
                }) {
                    Icon(Icons.Default.Download, contentDescription = "Downloads", tint = MaterialTheme.colorScheme.primary)
                }
            }
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = { state.query = it; state.showSuggestions = it.isNotBlank() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search ${section.label.lowercase()} and all enabled sources...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { runSearch() }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        if (state.showSuggestions && state.suggestions.isNotEmpty() && state.query.trim().length >= 2) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardBlack)
            ) {
                state.suggestions.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                state.showSuggestions = false
                                state.query = item.title
                                onOpen(item)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 34.dp, height = 46.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(item.year, providerLabel(item.provider)).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "See all results for \"${state.query.trim()}\"",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth().clickable { runSearch() }.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            state.sectionTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.error != null -> Text(
                state.error ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Column(Modifier.fillMaxSize()) {
                // Continue Watching row (from local LibraryStore)
                if (prefs.showContinueWatching) {
                    val continueItems = remember { LibraryStore.continueWatching(12) }
                    if (continueItems.isNotEmpty()) {
                        Text(
                            "Continue Watching",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.height(210.dp)
                        ) {
                            items(continueItems) { entry ->
                                Box(Modifier.width(120.dp)) {
                                    PosterCard(
                                        CatalogItem(
                                            id = entry.key.substringAfter(':', entry.key),
                                            provider = entry.key.substringBefore(':', "moviebox"),
                                            title = entry.title,
                                            mediaType = entry.category.lowercase(),
                                            year = null,
                                            posterUrl = entry.posterUrl,
                                            seasonCount = null
                                        ),
                                        onClick = {
                                            onOpen(
                                                CatalogItem(
                                                    id = entry.key.substringAfter(':', entry.key),
                                                    provider = entry.key.substringBefore(':', "moviebox"),
                                                    title = entry.title,
                                                    mediaType = entry.category.lowercase(),
                                                    year = null,
                                                    posterUrl = entry.posterUrl,
                                                    seasonCount = null
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.results.isNotEmpty()) {
                    Text("Featured", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(210.dp)
                    ) {
                        items(state.results.take(8)) { item ->
                            Box(Modifier.width(120.dp)) { PosterCard(item, onClick = { onOpen(item) }) }
                        }
                    }
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(if (prefs.compactGrid) 4 else 3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.results) { item -> PosterCard(item, onClick = { onOpen(item) }) }
                }
            }
        }

        NavigationBar {
            ContentSection.entries.forEach { item ->
                NavigationBarItem(
                    selected = section == item,
                    onClick = { onSectionChanged(item) },
                    icon = { Text(item.emoji) },
                    label = { Text(item.label) }
                )
            }
        }
    }
}

@Composable
private fun PosterCard(item: CatalogItem, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBlack)
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
        )
        Text(
            item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(6.dp)
        )
    }
}

@Composable
private fun DetailsScreen(
    id: String,
    provider: String,
    fallbackTitle: String,
    posterUrl: String?,
    onBack: () -> Unit,
    onPlay: (String, String, Int, Int) -> Unit,
    downloadsDir: () -> File,
    onOpenSimilar: (CatalogItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<MediaDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) } // season, episode
    var isFavorite by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Pair<Int, Int>>()) }
    var showBulkPicker by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        val outcome = withContext(Dispatchers.IO) { Engine.details(id, provider) }
        outcome.onSuccess { details = it }.onFailure { error = it.message }
        withContext(Dispatchers.IO) { Engine.favoritesList() }
            .onSuccess { list -> isFavorite = list.any { it.subjectId == id } }
    }

    downloadTarget?.let { (season, episode) ->
        QualityPickerDialog(
            subjectId = id,
            provider = provider,
            title = details?.title ?: fallbackTitle,
            posterUrl = details?.posterUrl ?: posterUrl,
            season = season,
            episode = episode,
            downloadsDir = downloadsDir,
            onDismiss = { downloadTarget = null },
            onDownloaded = {
                downloadTarget = null
                DownloadService.track(context)
            }
        )
    }

    if (showBulkPicker) {
        BulkQualityDialog(
            subjectId = id,
            provider = provider,
            title = details?.title ?: fallbackTitle,
            posterUrl = details?.posterUrl ?: posterUrl,
            selected = selected,
            downloadsDir = downloadsDir,
            onDismiss = { showBulkPicker = false },
            onStarted = { count ->
                showBulkPicker = false
                selectionMode = false
                selected = emptySet()
                DownloadService.track(context)
                Toast.makeText(
                    context,
                    if (count == 0) "Nothing new to download - those episodes are already downloaded or queued"
                    else "Started $count download${if (count == 1) "" else "s"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                details?.title ?: fallbackTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            val d0 = details
            if (d0 != null && d0.seasons.isNotEmpty()) {
                IconButton(onClick = {
                    selectionMode = !selectionMode
                    if (!selectionMode) selected = emptySet()
                }) {
                    Icon(
                        Icons.Default.ChecklistRtl,
                        contentDescription = "Select episodes",
                        tint = if (selectionMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = {
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) { Engine.favoritesToggle(id, provider) }
                    outcome.onSuccess { isFavorite = it }
                }
            }) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle favorite",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        val d = details
        if (d == null && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }
        if (error != null) {
            Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        if (d == null) return@Column

        Box(Modifier.weight(1f)) {
            Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                Box {
                    AsyncImage(
                        model = d.posterUrl ?: posterUrl,
                        contentDescription = d.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                    )
                    d.imdbRating?.let { rating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppBlack.copy(alpha = 0.7f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = CielBlueBright, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(rating, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                d.tagline?.let {
                    Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.year?.let { InfoChip(it) }
                    d.duration?.let { InfoChip(it) }
                    InfoChip(if (d.seasons.isEmpty()) "Movie" else "Series")
                    InfoChip(providerLabel(provider))
                }
                Spacer(Modifier.height(10.dp))

                if (d.genres.isNotEmpty()) {
                    Text(
                        d.genres.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(8.dp))
                }
                d.description?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }
                d.director?.let {
                    Text("Director: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                d.stars?.let {
                    Text("Cast: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))

                if (d.seasons.isEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onPlay(id, provider, 0, 0) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Play")
                        }
                        OutlinedButton(onClick = { downloadTarget = 0 to 0 }) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(6.dp))
                            Text("Download")
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                } else {
                    d.seasons.forEach { season ->
                        val seasonKeys = season.episodes.map { season.number to it.number }
                        val allSelected = seasonKeys.isNotEmpty() && seasonKeys.all { it in selected }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Text(
                                "Season ${season.number}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            if (selectionMode) {
                                Text("Select all", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Checkbox(
                                    checked = allSelected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + seasonKeys else selected - seasonKeys.toSet()
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        season.episodes.forEach { ep ->
                            val key = season.number to ep.number
                            EpisodeRow(
                                episodeNumber = ep.number,
                                episodeTitle = ep.title,
                                mark = DownloadsStore.episodeMark("$provider:$id", season.number, ep.number),
                                thumbnailUrl = d.posterUrl ?: posterUrl,
                                selectionMode = selectionMode,
                                checked = key in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + key else selected - key
                                },
                                onDownloadClick = { downloadTarget = key },
                                onClick = {
                                    if (selectionMode) {
                                        selected = if (key in selected) selected - key else selected + key
                                    } else {
                                        onPlay(id, provider, season.number, ep.number)
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                SimilarSection(details = d, onOpen = onOpenSimilar)
                Spacer(Modifier.height(if (selectionMode && selected.isNotEmpty()) 90.dp else 24.dp))
            }

            if (selectionMode && selected.isNotEmpty()) {
                Surface(
                    color = CardBlack,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                ) {
                    Button(
                        onClick = { showBulkPicker = true },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Download ${selected.size} episode${if (selected.size == 1) "" else "s"}")
                    }
                }
            }
        }
    }
}

private fun providerLabel(provider: String): String = when (provider) {
    "moviebox" -> "MovieBox"
    "fourkhdhub" -> "4KHDHub"
    "bdix_circleftp" -> "CircleFTP"
    "bdix_dhakaflix" -> "DhakaFlix"
    "dramachi" -> "Dramachi"
    "anidbapp" -> "AniDB.app"
    "addons" -> "Addon"
    else -> provider
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CardBlack)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun EpisodeRow(
    episodeNumber: Int,
    episodeTitle: String?,
    mark: DlMark,
    thumbnailUrl: String?,
    selectionMode: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDownloadClick: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(CardBlack)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 96.dp, height = 56.dp).clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Episode $episodeNumber", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(
                episodeTitle ?: "Tap to play",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when (mark) {
                DlMark.DONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Downloaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                DlMark.PENDING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Downloading…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                DlMark.NONE -> Unit
            }
        }
        if (selectionMode) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
        } else {
            IconButton(onClick = onDownloadClick) {
                Icon(
                    when (mark) {
                        DlMark.DONE -> Icons.Default.CheckCircle
                        DlMark.PENDING -> Icons.Default.Schedule
                        DlMark.NONE -> Icons.Default.FileDownload
                    },
                    contentDescription = "Download",
                    tint = if (mark == DlMark.NONE) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun QualityPickerDialog(
    subjectId: String,
    provider: String,
    title: String,
    posterUrl: String?,
    season: Int,
    episode: Int,
    downloadsDir: () -> File,
    onDismiss: () -> Unit,
    onDownloaded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<ReleaseInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val animeKey = "$provider:$subjectId"

    LaunchedEffect(subjectId, season, episode) {
        val outcome = withContext(Dispatchers.IO) { Engine.listReleases(subjectId, provider, season, episode) }
        outcome.onSuccess { list ->
            // Highest resolution first; every quality the source offers (360p, 480p, 720p...) is shown.
            releases = list.sortedByDescending { r -> Regex("\\d+").find(r.quality)?.value?.toIntOrNull() ?: 0 }
        }.onFailure { error = it.message }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBlack,
        title = { Text("Download quality", color = MaterialTheme.colorScheme.primary) },
        text = {
            when {
                error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                releases == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                releases!!.isEmpty() -> Text("No sources found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(releases!!) { release ->
                        val mirror = downloadableMirror(release)
                        val existing = DownloadsStore.find(animeKey, season, episode, release.quality)
                        val blocked = existing != null && existing.state != "failed" && existing.state != "paused"
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = mirror != null && !blocked) {
                                    val m = mirror ?: return@clickable
                                    val result = enqueueDownload(
                                        context, downloadsDir(), provider, subjectId, title, posterUrl,
                                        season, episode, release.quality, m
                                    )
                                    if (result == EnqueueResult.DUPLICATE) {
                                        Toast.makeText(context, "Already downloaded or queued", Toast.LENGTH_SHORT).show()
                                    }
                                    onDownloaded()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    release.quality,
                                    fontWeight = FontWeight.Bold,
                                    color = if (blocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                val sub = when {
                                    existing?.state == "completed" -> "Already downloaded"
                                    blocked -> "Already in your downloads"
                                    existing != null -> "Tap to resume"
                                    mirror == null -> "Streaming only - can't be downloaded"
                                    release.sizeBytes != null -> "${release.sizeBytes / 1_048_576} MB"
                                    else -> null
                                }
                                sub?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                if (blocked) Icons.Default.CheckCircle else Icons.Default.FileDownload,
                                contentDescription = "Download",
                                tint = if (blocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun BulkQualityDialog(
    subjectId: String,
    provider: String,
    title: String,
    posterUrl: String?,
    selected: Set<Pair<Int, Int>>,
    downloadsDir: () -> File,
    onDismiss: () -> Unit,
    onStarted: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var qualities by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    val first = selected.first()
    val ordered = selected.sortedWith(compareBy({ it.first }, { it.second }))

    LaunchedEffect(subjectId) {
        val outcome = withContext(Dispatchers.IO) { Engine.listReleases(subjectId, provider, first.first, first.second) }
        outcome.onSuccess { list ->
            qualities = list.map { it.quality }.distinct()
                .sortedByDescending { q -> Regex("\\d+").find(q)?.value?.toIntOrNull() ?: 0 }
        }
            .onFailure { error = it.message }
    }

    AlertDialog(
        onDismissRequest = { if (!starting) onDismiss() },
        containerColor = CardBlack,
        title = { Text("Download ${selected.size} episodes", color = MaterialTheme.colorScheme.primary) },
        text = {
            when {
                starting -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                qualities == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                qualities!!.isEmpty() -> Text("No sources found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Column {
                    Text(
                        "Pick a quality. It'll be used for every selected episode where available (falls back to the closest match otherwise).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    qualities!!.forEach { q ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    starting = true
                                    scope.launch {
                                        var count = 0
                                        for ((season, episode) in ordered) {
                                            val result = withContext(Dispatchers.IO) {
                                                Engine.listReleases(subjectId, provider, season, episode)
                                            }
                                            val release = result.getOrNull()?.let { closestRelease(it, q) }
                                            val mirror = release?.let { downloadableMirror(it) }
                                            if (release != null && mirror != null) {
                                                val started = enqueueDownload(
                                                    context, downloadsDir(), provider, subjectId, title, posterUrl,
                                                    season, episode, release.quality, mirror
                                                )
                                                if (started != EnqueueResult.DUPLICATE) count++
                                            }
                                        }
                                        onStarted(count)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(10.dp))
                            Text(q, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!starting) onDismiss() }) { Text("Cancel") }
        }
    )
}

@Composable
private fun FavoritesScreen(onBack: () -> Unit, onOpen: (CatalogItem) -> Unit) {
    var favoriteItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val outcome = withContext(Dispatchers.IO) { Engine.favoritesList() }
        loading = false
        outcome
            .map { favs ->
                favs.map {
                    CatalogItem(
                        id = it.subjectId,
                        provider = it.provider,
                        title = it.title,
                        mediaType = if (it.stype == 2L) "series" else "movie",
                        year = null,
                        posterUrl = it.coverUrl,
                        seasonCount = null
                    )
                }
            }
            .onSuccess { favoriteItems = it }
            .onFailure { error = it.message }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Favorites",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            favoriteItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No favorites yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(favoriteItems) { item -> PosterCard(item, onClick = { onOpen(item) }) }
            }
        }
    }
}


/** "You may also like": related titles found by searching the title's keywords and genres. */
@Composable
private fun SimilarSection(details: MediaDetails, onOpen: (CatalogItem) -> Unit) {
    var items by remember(details.id) { mutableStateOf<List<CatalogItem>?>(null) }

    LaunchedEffect(details.id) {
        val found = withContext(Dispatchers.IO) {
            val seen = HashSet<String>()
            seen.add(details.provider + ":" + details.id)
            val list = ArrayList<CatalogItem>()
            fun add(source: List<CatalogItem>) {
                for (c in source) if (seen.add(c.provider + ":" + c.id)) list.add(c)
            }
            val words = details.title.substringBefore(":").substringBefore(" - ")
                .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
                .split(Regex("\\s+")).filter { it.isNotBlank() }
            val queries = ArrayList<String>()
            if (words.size >= 2) queries.add(words.take(2).joinToString(" "))
            words.firstOrNull { it.length > 3 }?.let { queries.add(it) }
            details.genres.take(2).forEach { queries.add(it) }
            for (q in queries.distinct()) {
                Engine.search(q).getOrNull()?.let { add(it) }
                if (list.size >= 12) break
            }
            // Never leave the section empty: fall back to what's popular right now.
            if (list.size < 6) Engine.homepage().getOrNull()?.let { add(it) }
            list.take(14)
        }
        items = found
    }

    val shown = items
    Text(
        "You may also like",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
    when {
        shown == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        shown.isEmpty() -> Text(
            "No similar titles found right now",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.provider + ":" + it.id }) { item ->
                Column(Modifier.width(110.dp).clickable { onOpen(item) }) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))
                    )
                    Text(
                        item.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
