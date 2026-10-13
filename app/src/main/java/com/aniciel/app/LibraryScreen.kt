package com.aniciel.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Full library UI with categories, migrate & merge (items 3, 9, 10).
 * Storage-efficient: pure Compose + LibraryStore JSON — no Room, no multi-module.
 */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpen: (LibraryEntry) -> Unit
) {
    var selectedCat by remember { mutableStateOf("All") }
    var categories by remember { mutableStateOf(LibraryStore.categoryNames()) }
    var entries by remember { mutableStateOf(LibraryStore.all()) }
    var showAddCat by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }
    var mergeMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    var migrateKey by remember { mutableStateOf<String?>(null) }
    var migrateProvider by remember { mutableStateOf("") }
    var migrateId by remember { mutableStateOf("") }

    fun refresh() {
        categories = LibraryStore.categoryNames()
        entries = if (selectedCat == "All") LibraryStore.all()
        else LibraryStore.byCategory(selectedCat).ifEmpty {
            if (selectedCat == "Default") LibraryStore.all() else emptyList()
        }
    }

    LaunchedEffect(selectedCat) { refresh() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
            Text("Library", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { mergeMode = !mergeMode; selectedKeys = emptySet() }) {
                Icon(Icons.Default.MergeType, "Merge", tint = if (mergeMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            TextButton(onClick = { showAddCat = true }) { Text("Category") }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCat == "All",
                    onClick = { selectedCat = "All"; refresh() },
                    label = { Text("All") }
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCat == cat,
                    onClick = { selectedCat = cat; refresh() },
                    label = { Text(cat) }
                )
            }
        }

        if (mergeMode && selectedKeys.size >= 2) {
            Button(
                onClick = {
                    val keys = selectedKeys.toList()
                    LibraryStore.merge(keys.first(), keys.drop(1))
                    mergeMode = false
                    selectedKeys = emptySet()
                    refresh()
                },
                modifier = Modifier.padding(12.dp).fillMaxWidth()
            ) { Text("Merge ${selectedKeys.size} entries into first selected") }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Library empty — watch something to fill Continue Watching", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.key }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (mergeMode) {
                                    selectedKeys = if (entry.key in selectedKeys) selectedKeys - entry.key
                                    else selectedKeys + entry.key
                                } else onOpen(entry)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mergeMode) {
                            Checkbox(
                                checked = entry.key in selectedKeys,
                                onCheckedChange = {
                                    selectedKeys = if (it) selectedKeys + entry.key else selectedKeys - entry.key
                                }
                            )
                        }
                        AsyncImage(
                            model = entry.posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp, 80.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                buildString {
                                    append(entry.libraryCategory)
                                    if (entry.lastEpisode > 0) append(" · Ep ${entry.lastEpisode}")
                                    if (entry.progressPercent > 0) append(" · ${entry.progressPercent.toInt()}%")
                                    if (entry.mergedKeys.isNotEmpty()) append(" · merged")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { migrateKey = entry.key; migrateProvider = entry.key.substringBefore(':'); migrateId = entry.key.substringAfter(':') }) {
                            Icon(Icons.Default.SwapHoriz, "Migrate")
                        }
                        IconButton(onClick = { LibraryStore.remove(entry.key); refresh() }) {
                            Icon(Icons.Default.Delete, "Remove")
                        }
                    }
                }
            }
        }
    }

    if (showAddCat) {
        AlertDialog(
            onDismissRequest = { showAddCat = false },
            title = { Text("New category") },
            text = {
                OutlinedTextField(value = newCatName, onValueChange = { newCatName = it }, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    LibraryStore.addCategory(newCatName)
                    newCatName = ""
                    showAddCat = false
                    refresh()
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddCat = false }) { Text("Cancel") } }
        )
    }

    migrateKey?.let { key ->
        AlertDialog(
            onDismissRequest = { migrateKey = null },
            title = { Text("Migrate source") },
            text = {
                Column {
                    Text("Change provider:id while keeping progress.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = migrateProvider, onValueChange = { migrateProvider = it }, label = { Text("Provider") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = migrateId, onValueChange = { migrateId = it }, label = { Text("ID") })
                    Spacer(Modifier.height(8.dp))
                    Text("Move to category:", style = MaterialTheme.typography.labelMedium)
                    categories.forEach { cat ->
                        TextButton(onClick = { LibraryStore.setLibraryCategory(key, cat); refresh() }) { Text(cat) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    LibraryStore.migrate(key, migrateProvider.trim(), migrateId.trim())
                    migrateKey = null
                    refresh()
                }) { Text("Migrate") }
            },
            dismissButton = { TextButton(onClick = { migrateKey = null }) { Text("Cancel") } }
        )
    }
}
