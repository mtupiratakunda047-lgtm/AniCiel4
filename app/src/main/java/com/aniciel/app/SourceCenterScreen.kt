package com.aniciel.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SourceCenterScreen(prefs: AppPreferences, onBack: () -> Unit = {}) {
    var refreshToken by remember { mutableIntStateOf(0) }
    val entries = remember(refreshToken) { SourceRegistry.all }

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Text(
            "Sources",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
        Text(
            "MovieBox APIs only — no VIP / ads / login",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { source ->
                var enabled by remember(source.id) {
                    mutableStateOf(prefs.sourceEnabled(source.id, source.enabled))
                }
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(source.label, style = MaterialTheme.typography.titleMedium)
                            Text(source.kind, style = MaterialTheme.typography.labelMedium)
                        }
                        Switch(
                            checked = enabled,
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
