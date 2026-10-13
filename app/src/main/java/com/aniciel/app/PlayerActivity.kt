package com.aniciel.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aniciel.app.ui.theme.AniCielTheme
import com.aniciel.app.ui.theme.AppBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val PlayGreen = Color(0xFF2ED67A)
private val Scrim = Color(0x99000000)

private sealed class PlayerSource {
    data class Online(val subjectId: String, val provider: String, val season: Int, val episode: Int) : PlayerSource()
    data class Offline(val paths: List<String>, val titles: List<String>, val start: Int) : PlayerSource()
}

class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SUBJECT_ID = "subject_id"
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_SEASON = "season"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_FILES = "files"
        const val EXTRA_TITLES = "titles"
        const val EXTRA_START = "start_index"

        /** Plays finished downloads from disk (a whole folder becomes a playlist). */
        fun offlineIntent(context: Context, paths: List<String>, titles: List<String>, start: Int): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_FILES, paths.toTypedArray())
                putExtra(EXTRA_TITLES, titles.toTypedArray())
                putExtra(EXTRA_START, start)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DownloadsStore.init(applicationContext)
        LibraryStore.init(applicationContext)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val files = intent.getStringArrayExtra(EXTRA_FILES)
        val source: PlayerSource = if (files != null && files.isNotEmpty()) {
            val titles = intent.getStringArrayExtra(EXTRA_TITLES)?.toList() ?: files.map { File(it).name }
            PlayerSource.Offline(files.toList(), titles, intent.getIntExtra(EXTRA_START, 0))
        } else {
            val subjectId = intent.getStringExtra(EXTRA_SUBJECT_ID) ?: run { finish(); return }
            PlayerSource.Online(
                subjectId,
                intent.getStringExtra(EXTRA_PROVIDER) ?: "moviebox",
                intent.getIntExtra(EXTRA_SEASON, 0),
                intent.getIntExtra(EXTRA_EPISODE, 0)
            )
        }

        setContent {
            AniCielTheme {
                Surface(color = AppBlack) {
                    PlayerScreen(source)
                }
            }
        }
    }
}

private fun subtitleMimeType(name: String): String = when {
    name.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
    name.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
    name.endsWith(".ttml", ignoreCase = true) || name.endsWith(".xml", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
    name.endsWith(".ass", ignoreCase = true) || name.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
}

private fun qualityRank(quality: String): Int {
    val digits = Regex("\\d+").find(quality)?.value?.toIntOrNull()
    return digits ?: 500 // unknown labels sort to the middle rather than the extremes
}

private fun isAdaptiveManifest(url: String): Boolean =
    url.substringBefore('?').let { it.endsWith(".m3u8", ignoreCase = true) || it.endsWith(".mpd", ignoreCase = true) }

/** Prefers an adaptive HLS/DASH manifest (ExoPlayer adapts bitrate to the network), else a direct file. */
private fun bestMirror(release: ReleaseInfo): MirrorInfo? =
    release.mirrors.firstOrNull { isAdaptiveManifest(it.url) }
        ?: release.mirrors.firstOrNull { it.directFile }
        ?: release.mirrors.firstOrNull()

private fun fmtTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

private enum class Panel { NONE, MORE, SPEED, SUBTITLES, TIMER, QUALITY, AUDIO }

@Composable
private fun PlayerScreen(source: PlayerSource) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE) }

    val appPrefs = remember { AppPreferences(context) }
    val useMpv = appPrefs.playerBackend == PlayerBackend.MPV
    var mpvRef by remember { mutableStateOf<MpvPlayerView?>(null) }
    val player = remember { ExoPlayer.Builder(context).build() }

    // Online-only state (declared early so DisposableEffect can capture it)
    var onlineTitle by remember { mutableStateOf("") }
    var onlinePoster by remember { mutableStateOf<String?>(null) }
    var onlineCategory by remember { mutableStateOf("Anime") }
    var currentOnline by remember {
        mutableStateOf(source as? PlayerSource.Online)
    }
    val offlineEarly = source as? PlayerSource.Offline
    var titleText by remember {
        mutableStateOf(offlineEarly?.let { it.titles.getOrNull(it.start) } ?: "")
    }

    DisposableEffect(Unit) {
        onDispose {
            // Persist continue-watching progress when leaving the player
            val online = currentOnline
            if (online != null && player.currentPosition > 5_000) {
                LibraryStore.updateProgress(
                    key = "${online.provider}:${online.subjectId}",
                    title = onlineTitle.ifBlank { titleText },
                    posterUrl = onlinePoster,
                    category = onlineCategory,
                    season = online.season,
                    episode = online.episode,
                    positionMs = player.currentPosition,
                    durationMs = player.duration.coerceAtLeast(0)
                )
            }
            player.release()
        }
    }

    // ---- UI state ----
    var controlsVisible by remember { mutableStateOf(true) }
    var touchTick by remember { mutableStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var speed by remember { mutableStateOf(1f) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var loopMode by remember { mutableStateOf(0) } // 0 off, 1 repeat one, 2 repeat all, 3 shuffle
    var muted by remember { mutableStateOf(false) }
    var nightMode by remember { mutableStateOf(false) }
    var mirror by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var indicator by remember { mutableStateOf<String?>(null) }
    var indicatorIcon by remember { mutableStateOf<ImageVector?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentIndex by remember { mutableStateOf(0) }
    var textTracks by remember { mutableStateOf<List<Pair<String, Tracks.Group>>>(emptyList()) }
    var audioTracks by remember { mutableStateOf<List<Pair<String, Tracks.Group>>>(emptyList()) }
    var subtitlesOff by remember { mutableStateOf(false) }
    var sleepAt by remember { mutableStateOf(0L) }
    var abStep by remember { mutableStateOf(0) } // 0 off, 1 A set, 2 A+B set
    var abA by remember { mutableStateOf(0L) }
    var abB by remember { mutableStateOf(0L) }
    var showTutorial by remember { mutableStateOf(!prefs.getBoolean("tutorial_seen", false)) }

    // Online-only state
    var releases by remember { mutableStateOf<List<ReleaseInfo>?>(null) }
    var currentQuality by remember { mutableStateOf<String?>(null) }
    // Flat episode list for Next / Prev navigation (online)
    var episodeList by remember { mutableStateOf<List<EpisodeInfo>>(emptyList()) }
    var currentEpIndex by remember { mutableStateOf(0) }
    val offline = source as? PlayerSource.Offline

    // ---- Brightness / volume ----
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    var brightness by remember {
        mutableStateOf(
            run {
                val current = activity.window.attributes.screenBrightness
                if (current >= 0f) current else try {
                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                } catch (_: Exception) { 0.5f }
            }
        )
    }

    fun touch() { touchTick++ }
    fun show(text: String, icon: ImageVector?) { indicator = text; indicatorIcon = icon; touch() }
    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0L, if (player.duration > 0) player.duration else Long.MAX_VALUE)
        player.seekTo(target)
    }
    fun applyBrightness(v: Float) {
        brightness = v.coerceIn(0.02f, 1f)
        val lp = activity.window.attributes
        lp.screenBrightness = brightness
        activity.window.attributes = lp
    }
    fun applyVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).roundToInt(), 0)
        if (volume > 0f && muted) { muted = false; player.volume = 1f }
    }
    fun setLoop(mode: Int) {
        loopMode = mode
        player.repeatMode = when (mode) {
            1 -> Player.REPEAT_MODE_ONE
            2 -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        player.shuffleModeEnabled = mode == 3
    }

    // ---- Player listener ----
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
                offline?.titles?.getOrNull(currentIndex)?.let { titleText = it }
                abStep = 0
                offline?.let { off ->
                    val t = off.titles.getOrNull(currentIndex) ?: return@let
                    LibraryStore.updateProgress(
                        key = "offline:${off.paths.getOrNull(currentIndex) ?: currentIndex}",
                        title = t.substringBefore(" · ").ifBlank { t },
                        posterUrl = null,
                        category = "Downloads",
                        season = 0,
                        episode = currentIndex + 1,
                        positionMs = 0,
                        durationMs = 0
                    )
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                error = "Can't play this video: ${e.errorCodeName}"
            }
            override fun onTracksChanged(tracks: Tracks) {
                textTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.mapIndexed { i, g ->
                    val f = g.getTrackFormat(0)
                    (f.label ?: f.language ?: "Track ${i + 1}") to g
                }
                audioTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }.mapIndexed { i, g ->
                    val f = g.getTrackFormat(0)
                    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }
                        ?.let { code -> java.util.Locale.forLanguageTag(code).displayLanguage.takeIf { it.isNotBlank() } }
                    val name = f.label ?: lang ?: "Audio ${i + 1}"
                    val channels = when (f.channelCount) { 1 -> " · Mono"; 2 -> " · Stereo"; 6 -> " · 5.1"; else -> "" }
                    (name + channels) to g
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // ---- Load media ----
    suspend fun playOnline(online: PlayerSource.Online, release: ReleaseInfo, mirror: MirrorInfo, resumeMs: Long) {
        val subtitles = release.resourceId?.let { rid ->
            withContext(Dispatchers.IO) { Engine.subtitles(online.subjectId, rid, online.season, online.episode) }.getOrNull()
        }.orEmpty()
        val subtitleConfigs = subtitles.map {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                .setMimeType(subtitleMimeType(it.url))
                .setLabel(it.name)
                .build()
        }
        val mediaItem = MediaItem.Builder()
            .setUri(mirror.url)
            .setSubtitleConfigurations(subtitleConfigs)
            .apply { if (mirror.url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        if (mirror.headers.isNotEmpty()) dataSourceFactory.setDefaultRequestProperties(mirror.headers.toMap())
        val mediaSource = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
        if (useMpv) {
            mpvRef?.load(mirror.url, mirror.headers.toMap())
            if (resumeMs > 0) mpvRef?.seekTo(resumeMs / 1000.0)
            mpvRef?.play()
        } else {
            player.setMediaSource(mediaSource, resumeMs.coerceAtLeast(0))
            player.prepare()
            player.playWhenReady = true
        }
    }

    LaunchedEffect(source) {
        when (source) {
            is PlayerSource.Offline -> {
                val items = source.paths.map { MediaItem.fromUri(Uri.fromFile(File(it))) }
                player.setMediaItems(items, source.start.coerceIn(0, items.size - 1), 0L)
                currentIndex = source.start.coerceIn(0, items.size - 1)
                player.prepare()
                player.playWhenReady = true
            }
            is PlayerSource.Online -> {
                currentOnline = source
                val detailsOutcome = withContext(Dispatchers.IO) {
                    Engine.details(source.subjectId, source.provider)
                }
                detailsOutcome.onSuccess { details ->
                    onlineTitle = details.title
                    onlinePoster = details.posterUrl
                    onlineCategory = details.mediaType.replaceFirstChar { it.uppercase() }.ifBlank { "Anime" }
                    titleText = details.title
                    val flat = details.seasons.flatMap { it.episodes }
                    episodeList = flat
                    currentEpIndex = flat.indexOfFirst {
                        it.season == source.season && it.number == source.episode
                    }.coerceAtLeast(0)
                }
                val outcome = withContext(Dispatchers.IO) {
                    Engine.listReleases(source.subjectId, source.provider, source.season, source.episode)
                }
                outcome.onSuccess { list ->
                    releases = list
                    val preferred = list.sortedBy { qualityRank(it.quality) }.getOrNull(list.size / 2) ?: list.firstOrNull()
                    val mirrorInfo = preferred?.let { bestMirror(it) }
                    if (preferred != null && mirrorInfo != null) {
                        currentQuality = preferred.quality
                        scope.launch { playOnline(source, preferred, mirrorInfo, 0) }
                    } else {
                        error = "No playable source found"
                    }
                }.onFailure { error = it.message ?: "Couldn't load this video" }
            }
        }
    }

    // Switch to another episode (online Next / Prev)
    fun switchEpisode(delta: Int) {
        val online = currentOnline ?: return
        if (episodeList.isEmpty()) return
        val newIndex = (currentEpIndex + delta).coerceIn(0, episodeList.lastIndex)
        if (newIndex == currentEpIndex) return
        // Save progress of current episode before leaving
        LibraryStore.updateProgress(
            key = "${online.provider}:${online.subjectId}",
            title = onlineTitle.ifBlank { titleText },
            posterUrl = onlinePoster,
            category = onlineCategory,
            season = online.season,
            episode = online.episode,
            positionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0)
        )
        val target = episodeList[newIndex]
        currentEpIndex = newIndex
        val nextOnline = PlayerSource.Online(online.subjectId, online.provider, target.season, target.number)
        currentOnline = nextOnline
        titleText = onlineTitle.ifBlank { "S${target.season} E${target.number}" }
        error = null
        releases = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                Engine.listReleases(nextOnline.subjectId, nextOnline.provider, nextOnline.season, nextOnline.episode)
            }
            outcome.onSuccess { list ->
                releases = list
                val preferred = list.sortedBy { qualityRank(it.quality) }.getOrNull(list.size / 2) ?: list.firstOrNull()
                val mirrorInfo = preferred?.let { bestMirror(it) }
                if (preferred != null && mirrorInfo != null) {
                    currentQuality = preferred.quality
                    playOnline(nextOnline, preferred, mirrorInfo, 0)
                } else {
                    error = "No playable source for this episode"
                }
            }.onFailure { error = it.message ?: "Couldn't load episode" }
        }
        touch()
    }

    // ---- Position polling + A-B repeat ----
    LaunchedEffect(player) {
        while (true) {
            if (!dragging) position = player.currentPosition
            val d = player.duration
            if (d > 0) duration = d
            if (abStep == 2 && player.currentPosition >= abB) player.seekTo(abA)
            delay(400)
        }
    }

    // ---- Auto-hide controls ----
    LaunchedEffect(touchTick, controlsVisible, isPlaying, panel) {
        if (controlsVisible && isPlaying && panel == Panel.NONE) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(indicator, touchTick) {
        if (indicator != null) {
            delay(900)
            indicator = null
        }
    }

    // ---- Sleep timer ----
    LaunchedEffect(sleepAt) {
        if (sleepAt > 0) {
            val wait = sleepAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            player.pause()
            Toast.makeText(context, "Sleep timer: paused", Toast.LENGTH_SHORT).show()
            sleepAt = 0
        }
    }

    val subtitleFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val index = player.currentMediaItemIndex
            val current = player.currentMediaItem
            val resume = player.currentPosition
            if (current != null) {
                val sub = MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(subtitleMimeType(uri.lastPathSegment ?: ""))
                    .setLanguage("und")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                val updated = current.buildUpon().setSubtitleConfigurations(listOf(sub)).build()
                player.replaceMediaItem(index, updated)
                player.seekTo(index, resume)
                subtitlesOff = false
            }
        }
    }

    // ======================================================================= UI
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (useMpv) {
            AndroidView(
                factory = { ctx ->
                    MpvPlayerView(ctx).also { mpvRef = it }
                },
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (mirror) -1f else 1f }
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
                update = { it.resizeMode = resizeMode },
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (mirror) -1f else 1f }
            )
        }

        if (nightMode) Box(Modifier.fillMaxSize().background(Color(0x99000000)))

        // ---- Gesture layer ----
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible; touch() },
                        onDoubleTap = { offset ->
                            if (!locked) {
                                val w = size.width
                                when {
                                    offset.x < w * 0.35f -> { seekBy(-10_000); show("−10s", Icons.Default.FastRewind) }
                                    offset.x > w * 0.65f -> { seekBy(10_000); show("+10s", Icons.Default.FastForward) }
                                    else -> if (player.isPlaying) player.pause() else player.play()
                                }
                            }
                        }
                    )
                }
                .pointerInput(locked) {
                    if (!locked) {
                        var leftSide = true
                        detectVerticalDragGestures(
                            onDragStart = { o -> leftSide = o.x < size.width / 2f },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                val delta = -dy / size.height * 1.3f
                                if (leftSide) {
                                    applyBrightness(brightness + delta)
                                    show("${(brightness * 100).roundToInt()}%", Icons.Default.BrightnessMedium)
                                } else {
                                    applyVolume(volume + delta)
                                    show("${(volume * 100).roundToInt()}%", if (volume <= 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp)
                                }
                            }
                        )
                    }
                }
                .pointerInput(locked) {
                    if (!locked) {
                        var accumulated = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accumulated = 0f },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                accumulated += dx
                                val seconds = (accumulated / size.width * 120f).roundToInt()
                                show((if (seconds >= 0) "+" else "−") + abs(seconds) + "s", if (seconds >= 0) Icons.Default.FastForward else Icons.Default.FastRewind)
                            },
                            onDragEnd = {
                                val seconds = (accumulated / size.width * 120f).roundToInt()
                                if (seconds != 0) seekBy(seconds * 1000L)
                                accumulated = 0f
                            }
                        )
                    }
                }
        )

        // ---- Buffering / error ----
        if (buffering && error == null) {
            CircularProgressIndicator(color = PlayGreen, modifier = Modifier.align(Alignment.Center))
        }
        error?.let {
            Text(
                it,
                color = Color(0xFFFF6B6B),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
        }

        // ---- Gesture indicator ----
        indicator?.let { text ->
            Row(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Scrim)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                indicatorIcon?.let { Icon(it, contentDescription = null, tint = Color.White) }
                Spacer(Modifier.width(8.dp))
                Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ---- Locked: only an unlock button ----
        if (locked) {
            if (controlsVisible) {
                RoundIcon(
                    icon = Icons.Default.Lock,
                    desc = "Unlock",
                    tint = PlayGreen,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
                ) { locked = false; touch() }
            }
        } else if (controlsVisible) {
            // Top bar
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Scrim)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { activity.finish() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    titleText,
                    color = Color.White,
                    maxLines = 1,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )
                val rel = releases
                if (rel != null && rel.size > 1) {
                    IconButton(onClick = { panel = Panel.QUALITY }) {
                        Icon(Icons.Default.HighQuality, contentDescription = "Quality", tint = Color.White)
                    }
                }
                if (audioTracks.isNotEmpty()) {
                    IconButton(onClick = { panel = Panel.AUDIO }) {
                        Icon(Icons.Default.Audiotrack, contentDescription = "Audio", tint = Color.White)
                    }
                }
                IconButton(onClick = { panel = Panel.SUBTITLES }) {
                    Icon(Icons.Default.ClosedCaption, contentDescription = "Subtitles", tint = Color.White)
                }
                if (offline != null) {
                    IconButton(onClick = {
                        offline.paths.getOrNull(player.currentMediaItemIndex)?.let { ShareUtil.shareFiles(context, listOf(File(it))) }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                }
                IconButton(onClick = { panel = Panel.MORE }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                }
            }

            // Left column: mute + lock
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                RoundIcon(
                    icon = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    desc = "Mute"
                ) {
                    muted = !muted
                    player.volume = if (muted) 0f else 1f
                    touch()
                }
                RoundIcon(icon = Icons.Default.LockOpen, desc = "Lock") { locked = true; controlsVisible = false }
            }

            // Centre transport — Next/Prev for offline download playlists AND online series
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                val offlineMulti = offline != null && offline.paths.size > 1
                val canPrev = (offline != null && (player.hasPreviousMediaItem() || player.currentMediaItemIndex > 0)) ||
                    (currentOnline != null && currentEpIndex > 0)
                val canNext = (offline != null && (player.hasNextMediaItem() || player.currentMediaItemIndex < offline.paths.lastIndex)) ||
                    (currentOnline != null && episodeList.isNotEmpty() && currentEpIndex < episodeList.lastIndex)

                // Always show Prev/Next when there is more than one download episode or any online series
                if (offlineMulti || currentOnline != null || offline != null) {
                    RoundIcon(icon = Icons.Default.SkipPrevious, desc = "Previous", size = 52) {
                        when {
                            offline != null -> {
                                if (player.hasPreviousMediaItem()) {
                                    player.seekToPreviousMediaItem()
                                } else if (player.currentMediaItemIndex > 0) {
                                    player.seekTo(player.currentMediaItemIndex - 1, 0L)
                                } else {
                                    player.seekTo(0)
                                }
                                touch()
                            }
                            else -> switchEpisode(-1)
                        }
                    }
                }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Scrim).clickable {
                        if (player.isPlaying) player.pause() else player.play()
                        touch()
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                if (offlineMulti || currentOnline != null || offline != null) {
                    RoundIcon(icon = Icons.Default.SkipNext, desc = "Next", size = 52) {
                        when {
                            offline != null -> {
                                if (player.hasNextMediaItem()) {
                                    player.seekToNextMediaItem()
                                } else if (player.currentMediaItemIndex < offline.paths.lastIndex) {
                                    player.seekTo(player.currentMediaItemIndex + 1, 0L)
                                }
                                touch()
                            }
                            else -> switchEpisode(+1)
                        }
                    }
                }
            }

            // Bottom: seek bar + toolbar
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Scrim)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtTime(if (dragging) (dragFraction * duration).toLong() else position), color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = if (dragging) dragFraction else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { dragging = true; dragFraction = it; touch() },
                        onValueChangeFinished = {
                            player.seekTo((dragFraction * duration).toLong())
                            dragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = PlayGreen,
                            activeTrackColor = PlayGreen,
                            inactiveTrackColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                    )
                    Text(fmtTime(duration), color = Color.White, fontSize = 12.sp)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        "${if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()}x",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { panel = Panel.SPEED }.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    IconButton(onClick = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        show(
                            when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                                else -> "Fit"
                            },
                            Icons.Default.AspectRatio
                        )
                    }) { Icon(Icons.Default.AspectRatio, contentDescription = "Aspect ratio", tint = Color.White) }
                    IconButton(onClick = {
                        val next = if (offline != null && offline.paths.size > 1) (loopMode + 1) % 4 else if (loopMode == 1) 0 else 1
                        setLoop(next)
                        show(listOf("Loop off", "Repeat one", "Repeat all", "Shuffle")[next], null)
                    }) {
                        Icon(
                            when (loopMode) { 1 -> Icons.Default.RepeatOne; 3 -> Icons.Default.Shuffle; else -> Icons.Default.Repeat },
                            contentDescription = "Loop",
                            tint = if (loopMode == 0) Color.White else PlayGreen
                        )
                    }
                }
            }
        }

        // ---- Side panels ----
        if (panel != Panel.NONE) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable { panel = Panel.NONE })
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(Color(0xF2202020))
                    .padding(16.dp)
                    .clickable(enabled = false) {}
            ) {
                when (panel) {
                    Panel.SPEED -> {
                        PanelHeader("Playback speed") { panel = Panel.NONE }
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                            PanelOption(if (s == 1f) "Normal (1x)" else "${s}x", speed == s) {
                                speed = s
                                player.setPlaybackSpeed(s)
                                panel = Panel.NONE
                            }
                        }
                    }
                    Panel.SUBTITLES -> {
                        PanelHeader("Subtitle") { panel = Panel.NONE }
                        PanelOption("None", subtitlesOff || textTracks.none { it.second.isSelected }) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            subtitlesOff = true
                            panel = Panel.NONE
                        }
                        textTracks.forEach { (label, group) ->
                            PanelOption(label, !subtitlesOff && group.isSelected) {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                    .build()
                                subtitlesOff = false
                                panel = Panel.NONE
                            }
                        }
                        PanelOption("Select subtitle file…", false) {
                            panel = Panel.NONE
                            subtitleFilePicker.launch(arrayOf("*/*"))
                        }
                    }
                    Panel.AUDIO -> {
                        PanelHeader("Audio / language") { panel = Panel.NONE }
                        audioTracks.forEach { (label, group) ->
                            PanelOption(label, group.isSelected) {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                    .build()
                                panel = Panel.NONE
                            }
                        }
                    }
                    Panel.TIMER -> {
                        PanelHeader("Sleep timer") { panel = Panel.NONE }
                        listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 60 to "60 minutes").forEach { (mins, label) ->
                            PanelOption(label, (mins == 0 && sleepAt == 0L)) {
                                sleepAt = if (mins == 0) 0L else System.currentTimeMillis() + mins * 60_000L
                                panel = Panel.NONE
                            }
                        }
                    }
                    Panel.QUALITY -> {
                        PanelHeader("Quality") { panel = Panel.NONE }
                        val rel = releases.orEmpty()
                        rel.forEach { release ->
                            PanelOption(release.quality, release.quality == currentQuality) {
                                val m = bestMirror(release)
                                val online = source as? PlayerSource.Online
                                if (m != null && online != null) {
                                    val resumeMs = player.currentPosition
                                    currentQuality = release.quality
                                    scope.launch { playOnline(online, release, m, resumeMs) }
                                }
                                panel = Panel.NONE
                            }
                        }
                    }
                    else -> {
                        PanelHeader("Play settings") { panel = Panel.NONE }
                        PanelOption("A-B repeat" + when (abStep) { 1 -> " (A set - tap to set B)"; 2 -> " (on)"; else -> "" }, abStep != 0) {
                            when (abStep) {
                                0 -> { abA = player.currentPosition; abStep = 1; show("A set", null) }
                                1 -> {
                                    abB = player.currentPosition
                                    if (abB > abA + 500) { abStep = 2; show("A-B repeat on", null) } else { abStep = 0 }
                                }
                                else -> { abStep = 0; show("A-B repeat off", null) }
                            }
                            panel = Panel.NONE
                        }
                        PanelOption("Night mode", nightMode) { nightMode = !nightMode; panel = Panel.NONE }
                        PanelOption("Mirror mode", mirror) { mirror = !mirror; panel = Panel.NONE }
                        PanelOption("Sleep timer", sleepAt > 0L) { panel = Panel.TIMER }
                        if (audioTracks.isNotEmpty()) PanelOption("Audio / language", audioTracks.size > 1) { panel = Panel.AUDIO }
                        PanelOption("Subtitle", false) { panel = Panel.SUBTITLES }
                        PanelOption("Playback speed", speed != 1f) { panel = Panel.SPEED }
                        Spacer(Modifier.height(12.dp))
                        Text("Loop", color = Color(0xFFB0BEC5), fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFF3A3A3A)),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(3 to Icons.Default.Shuffle, 1 to Icons.Default.RepeatOne, 2 to Icons.Default.Repeat, 0 to Icons.Default.Close)
                                .forEach { (mode, icon) ->
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(if (loopMode == mode) PlayGreen else Color.Transparent)
                                            .clickable { setLoop(mode) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(icon, contentDescription = null, tint = Color.White) }
                                }
                        }
                    }
                }
            }
        }

        // ---- First-run gesture guide (like PLAYit) ----
        if (showTutorial) {
            Box(Modifier.fillMaxSize().background(Color(0xE6000000)).clickable { }) {
                Row(Modifier.fillMaxSize().padding(24.dp)) {
                    TutorialColumn("Swipe up/down on left", "Brightness", Modifier.weight(1f))
                    TutorialColumn("Double tap left / right", "Rewind / forward 10s", Modifier.weight(1f))
                    TutorialColumn("Swipe up/down on right", "Volume", Modifier.weight(1f))
                }
                Text(
                    "Double tap in the middle to pause/play  ·  Swipe sideways to seek",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
                )
                Text(
                    "Got it",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PlayGreen)
                        .clickable {
                            prefs.edit().putBoolean("tutorial_seen", true).apply()
                            showTutorial = false
                        }
                        .padding(horizontal = 64.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun RoundIcon(
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Int = 44,
    onClick: () -> Unit
) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(Scrim).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
private fun PanelHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
    }
}

@Composable
private fun PanelOption(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) PlayGreen else Color.White,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)
    )
}

@Composable
private fun TutorialColumn(top: String, bottom: String, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(top, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(bottom, color = PlayGreen, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}
