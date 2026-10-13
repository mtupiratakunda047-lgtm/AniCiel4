package com.aniciel.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Library updates tab (item 8) — storage-light.
 * Refreshes metadata for library entries via Engine.details (no extension network).
 */
@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(LibraryStore.all()) }
    var refreshing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refreshAll() {
        scope.launch {
            refreshing = true
            status = "Checking library…"
            val lines = mutableListOf<String>()
            val list = LibraryStore.all()
            list.forEachIndexed { i, e ->
                status = "Updating ${i + 1}/${list.size}: ${e.title}"
                val provider = e.key.substringBefore(':', "moviebox")
                val id = e.key.substringAfter(':', e.key)
                val outcome = withContext(Dispatchers.IO) { Engine.details(id, provider) }
                outcome.onSuccess { d ->
                    LibraryStore.upsert(
                        e.copy(
                            title = d.title.ifBlank { e.title },
                            posterUrl = d.posterUrl ?: e.posterUrl,
                            lastWatchedAt = e.lastWatchedAt
                        )
                    )
                    val epCount = d.seasons.sumOf { it.episodes.size }
                    lines += "${d.title}: $epCount episodes on source"
                }.onFailure {
                    lines += "${e.title}: ${it.message ?: "failed"}"
                }
            }
            results = lines
            entries = LibraryStore.all()
            status = "Done"
            refreshing = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Updates", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (!refreshing) refreshAll() }, enabled = !refreshing) {
                Icon(Icons.Default.Refresh, "Refresh library")
            }
        }
        if (refreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status ?: "", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No library entries to update", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results.ifEmpty { entries.map { "${it.title} · last ep ${it.lastEpisode}" } }) { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                }
            }
        }
    }
}
