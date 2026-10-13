package com.aniciel.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aniciel.app.ui.theme.CardBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private class AnimeFolder(
    val key: String,
    val title: String,
    val posterUrl: String?,
    val episodes: List<DownloadItem>
)

private fun fmtSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format("%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes > 0 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "0 MB"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) } // 0 = downloaded, 1 = downloading
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) } // download ids
    var confirmDelete by remember { mutableStateOf(false) }

    val all = DownloadsStore.items.toList()
    val completed = all.filter { it.state == "completed" }
    val pending = all.filter { it.state != "completed" }
    val folders = completed.groupBy { it.animeKey }.map { (key, list) ->
        AnimeFolder(
            key = key,
            title = list.first().animeTitle,
            posterUrl = list.firstNotNullOfOrNull { it.posterUrl },
            episodes = list.sortedWith(compareBy({ it.season }, { it.episode }, { it.quality }))
        )
    }.sortedBy { it.title.lowercase() }

    // Drop selections whose downloads no longer exist.
    LaunchedEffect(completed.size) {
        val ids = completed.map { it.id }.toSet()
        selected = selected.filter { it in ids }.toSet()
    }

    fun exitSelection() {
        selectMode = false
        selected = emptySet()
    }

    BackHandler(enabled = selectMode) { exitSelection() }

    fun play(folder: AnimeFolder, start: DownloadItem) {
        // One playlist entry per episode (prefer largest completed file = best quality).
        // Next/Prev then moves between real episodes, not quality variants of the same episode.
        val byEpisode = linkedMapOf<Pair<Int, Int>, DownloadItem>()
        folder.episodes
            .sortedWith(compareBy({ it.season }, { it.episode }, { -it.downloaded }))
            .forEach { ep -> byEpisode.putIfAbsent(ep.season to ep.episode, ep) }
        val playlist = byEpisode.values.toList()
        if (playlist.isEmpty()) return
        val paths = playlist.map { it.filePath }
        val titles = playlist.map { "${it.animeTitle} · ${it.displayName()}" }
        val startKey = start.season to start.episode
        val index = playlist.indexOfFirst { it.season to it.episode == startKey }.coerceAtLeast(0)
        context.startActivity(PlayerActivity.offlineIntent(context, paths, titles, index))
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // ---- Top bar ----
        if (selectMode) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                IconButton(onClick = { exitSelection() }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel selection", tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "${selected.size} selected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val everything = completed.map { it.id }.toSet()
                    selected = if (selected.size == everything.size) emptySet() else everything
                }) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Select all", tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                // Pending-downloads icon with a count badge (like VidMate's top-right icon).
                IconButton(onClick = { tab = 1 }) {
                    BadgedBox(badge = {
                        if (pending.isNotEmpty()) {
                            Badge { Text("${pending.size}") }
                        }
                    }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Pending downloads",
                            tint = if (pending.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---- Tabs ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = tab == 0,
                onClick = { tab = 0 },
                label = { Text("Downloaded (${completed.size})") }
            )
            FilterChip(
                selected = tab == 1,
                onClick = { tab = 1; if (selectMode) exitSelection() },
                label = { Text("Downloading (${pending.size})") }
            )
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) {
                if (folders.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No downloaded episodes yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (selectMode) 90.dp else 16.dp)
                    ) {
                        folders.forEach { folder ->
                            val open = folder.key in expanded
                            val ids = folder.episodes.map { it.id }
                            val allChecked = ids.isNotEmpty() && ids.all { it in selected }
                            item(key = "folder-" + folder.key) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CardBlack)
                                        .combinedClickable(
                                            onClick = {
                                                if (selectMode) {
                                                    selected = if (allChecked) selected - ids.toSet() else selected + ids
                                                } else {
                                                    expanded = if (open) expanded - folder.key else expanded + folder.key
                                                }
                                            },
                                            onLongClick = {
                                                selectMode = true
                                                selected = selected + ids
                                            }
                                        )
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (folder.posterUrl != null) {
                                        AsyncImage(
                                            model = folder.posterUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(width = 56.dp, height = 76.dp).clip(RoundedCornerShape(6.dp))
                                        )
                                    } else {
                                        Box(
                                            Modifier.size(width = 56.dp, height = 76.dp).clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(folder.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        val count = folder.episodes.size
                                        Text(
                                            "$count episode${if (count == 1) "" else "s"} · ${fmtSize(folder.episodes.sumOf { it.downloaded })}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (selectMode) {
                                        Checkbox(
                                            checked = allChecked,
                                            onCheckedChange = { checked ->
                                                selected = if (checked) selected + ids else selected - ids.toSet()
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                        )
                                    } else {
                                        Icon(
                                            if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (open) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (open) {
                                items(folder.episodes, key = { "ep-" + it.id }) { ep ->
                                    val checked = ep.id in selected
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 18.dp, top = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CardBlack)
                                            .combinedClickable(
                                                onClick = {
                                                    if (selectMode) {
                                                        selected = if (checked) selected - ep.id else selected + ep.id
                                                    } else {
                                                        play(folder, ep)
                                                    }
                                                },
                                                onLongClick = {
                                                    selectMode = true
                                                    selected = selected + ep.id
                                                }
                                            )
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(ep.displayName(), fontWeight = FontWeight.SemiBold)
                                            Text(
                                                listOf(ep.quality, fmtSize(File(ep.filePath).length()))
                                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (selectMode) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { c -> selected = if (c) selected + ep.id else selected - ep.id },
                                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                            )
                                        } else {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (pending.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No pending downloads", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(pending, key = { "pending-" + it.id }) { item ->
                            PendingRow(item)
                        }
                    }
                }
            }

            // ---- Selection action bar ----
            if (selectMode && tab == 0) {
                Surface(color = CardBlack, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val enabled = selected.isNotEmpty()
                        ActionButton(Icons.Default.Share, "Share", enabled) {
                            val files = completed.filter { it.id in selected }.map { File(it.filePath) }
                            ShareUtil.shareFiles(context, files)
                        }
                        ActionButton(Icons.Default.SaveAlt, "Save to gallery", enabled) {
                            val chosen = completed.filter { it.id in selected }
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) { chosen.count { ShareUtil.saveToGallery(context, it) } }
                                Toast.makeText(
                                    context,
                                    if (saved == 0) "Couldn't save (needs Android 10+ and free space)"
                                    else "Saved $saved video${if (saved == 1) "" else "s"} to Movies/AniCiel",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        ActionButton(Icons.Default.Delete, "Delete", enabled) { confirmDelete = true }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = CardBlack,
            title = { Text("Delete ${selected.size} download${if (selected.size == 1) "" else "s"}?") },
            text = { Text("The video files will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selected.toList()
                    confirmDelete = false
                    exitSelection()
                    scope.launch(Dispatchers.IO) { ids.forEach { DownloadsStore.delete(it) } }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun PendingRow(item: DownloadItem) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBlack)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.animeTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(item.displayName(), item.quality).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.isActive) {
                IconButton(onClick = { Downloader.pause(item.id) }) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                val ctx = LocalContext.current
                IconButton(onClick = {
                    Downloader.start(item.id)
                    DownloadService.track(ctx)
                }) {
                    Icon(
                        if (item.state == "failed") Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = if (item.state == "failed") "Retry" else "Resume",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = { DownloadsStore.delete(item.id) }) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }

        val fraction = if (item.total > 0) (item.downloaded.toFloat() / item.total.toFloat()).coerceIn(0f, 1f) else 0f
        if (item.state == "running" && item.total <= 0L) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        val speed = String.format("%.1f", item.bytesPerSecond / 1_048_576)
        val line = when (item.state) {
            "queued" -> "Waiting in queue…"
            "processing" -> "Finishing up (making it playable everywhere)…"
            "running" -> {
                val total = if (item.total > 0) fmtSize(item.total) else "?"
                "${fmtSize(item.downloaded)} / $total · $speed MB/s"
            }
            "paused" -> item.error ?: "Paused · ${fmtSize(item.downloaded)}"
            "failed" -> "Failed: ${item.error ?: "unknown error"}"
            else -> item.state
        }
        Text(
            line,
            style = MaterialTheme.typography.labelSmall,
            color = if (item.state == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
