package com.aniciel.app

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ExtensionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var list by remember { mutableStateOf<List<ExtensionInfo>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            val r = Extensions.fetchIndex(context)
            loading = false
            r.onSuccess { list = it }
                .onFailure { error = it.message ?: "Failed to load repo" }
        }
    }

    LaunchedEffect(Unit) { reload() }

    val shown = remember(list, filter) {
        if (filter.isBlank()) list
        else list.filter {
            it.name.contains(filter, true) || it.pkg.contains(filter, true) || it.lang.contains(filter, true)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Extensions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (!loading) reload() }) { Icon(Icons.Default.Refresh, "Reload") }
        }
        Text(
            "Install extension APKs from a repo. Source ZIP stays small; the final app APK can be larger. " +
                "Aniyomi extensions need adapters to drive AniCiel’s player (install works now).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Filter by name, lang, package…") },
            singleLine = true
        )
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No extensions in index", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.pkg }) { ext ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ext.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${ext.lang} · v${ext.version}" + if (ext.nsfw) " · NSFW" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(ext.pkg, style = MaterialTheme.typography.labelSmall)
                                if (ext.installed) {
                                    Text("Installed", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (installing == ext.pkg) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = {
                                    scope.launch {
                                        installing = ext.pkg
                                        val dl = Extensions.downloadApk(context, ext)
                                        installing = null
                                        dl.onSuccess { file ->
                                            try {
                                                Extensions.installApk(context, file)
                                                Toast.makeText(context, "Install prompt opened", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, e.message ?: "Install failed", Toast.LENGTH_LONG).show()
                                            }
                                        }.onFailure {
                                            Toast.makeText(context, it.message ?: "Download failed", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Download, "Install")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
