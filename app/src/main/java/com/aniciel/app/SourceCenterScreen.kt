package com.aniciel.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SourceCenterScreen(
    prefs: AppPreferences,
    onBack: () -> Unit,
) {
    var category by remember { mutableStateOf(SourceCategory.MOVIES) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var statusNote by remember { mutableStateOf("Status reflects the current integration state; live network checks are not run for reference adapters.") }
    val entries = remember(category, refreshToken) { SourceRegistry.forCategory(category) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Source Center", style = MaterialTheme.typography.headlineSmall)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(statusNote, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { statusNote = "Ready/optional adapters are locally configured; reference entries remain disabled until an Android adapter is wired."; refreshToken++ }) {
                Text("Refresh")
            }
        }
        ScrollableTabRow(selectedTabIndex = category.ordinal) {
            SourceCategory.entries.forEach { c ->
                Tab(selected = category == c, onClick = { category = c }, text = { Text(c.label) })
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { source ->
                val defaultOn = source.state == SourceState.READY || source.state == SourceState.OPTIONAL
                var enabled by remember(source.id) { mutableStateOf(prefs.sourceEnabled(source.id, defaultOn)) }
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(source.name, style = MaterialTheme.typography.titleMedium)
                            Text(source.state.label, style = MaterialTheme.typography.labelMedium)
                            if (source.capabilities.isNotEmpty()) {
                                Text(source.capabilities.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                            if (source.note.isNotBlank()) {
                                Text(source.note, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Switch(
                            checked = enabled && source.state != SourceState.OFFLINE,
                            enabled = source.state != SourceState.OFFLINE,
                            onCheckedChange = {
                                enabled = it
                                prefs.setSourceEnabled(source.id, it)
                                refreshToken++
                            }
                        )
                    }
                }
            }
        }
    }
}
