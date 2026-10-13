package com.aniciel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aniciel.app.ui.theme.AniCielTheme
import com.aniciel.app.ui.theme.CardBlack
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AniCielTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val volumes = remember { DownloadSettings.listVolumes(context) }
    var selectedIndex by remember { mutableStateOf(DownloadSettings.selectedVolumeIndex(context)) }
    var subdirText by remember { mutableStateOf(DownloadSettings.subdirectory(context)) }
    var savedNotice by remember { mutableStateOf(false) }
    val appPrefs = remember { AppPreferences(context) }
    var profileName by remember { mutableStateOf(appPrefs.profileName) }
    var compactGrid by remember { mutableStateOf(appPrefs.compactGrid) }
    var adultEnabled by remember { mutableStateOf(appPrefs.adultEnabled) }
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Download storage",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Choose where new downloads are saved. Existing downloads stay where they are.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        if (volumes.isEmpty()) {
            Text("No writable storage found.", color = MaterialTheme.colorScheme.error)
        } else {
            volumes.forEach { volume ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = volume.index == selectedIndex,
                            onClick = { selectedIndex = volume.index }
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = volume.index == selectedIndex,
                        onClick = { selectedIndex = volume.index },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(volume.label, fontWeight = FontWeight.Bold)
                        Text(
                            volume.rootPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Subfolder name", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = subdirText,
            onValueChange = { subdirText = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("downloads") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            "Nested folders are OK, e.g. anime/2026",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                DownloadSettings.save(context, selectedIndex, subdirText)
                savedNotice = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            enabled = volumes.isNotEmpty()
        ) {
            Text("Save")
        }

        if (savedNotice) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Saved. New downloads will go to: ${DownloadSettings.resolvedDir(context).absolutePath}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(CardBlack, shape = MaterialTheme.shapes.small)
                    .padding(10.dp)
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("AniCiel profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = profileName,
            onValueChange = { profileName = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Local profile name") }
        )
        Text("Local-only profile; no authentication required.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Compact library grid")
                Text("Show four columns instead of three.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = compactGrid, onCheckedChange = { compactGrid = it; appPrefs.compactGrid = it })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Adult section")
                Text("Keep adult content separated in its own tab.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = adultEnabled, onCheckedChange = { adultEnabled = it; appPrefs.adultEnabled = it })
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { appPrefs.profileName = profileName },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("Save profile") }

        Spacer(Modifier.height(28.dp))
        Text("Player backend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "ExoPlayer is default. mpv uses libmpv (downloaded when you build the APK).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        var backend by remember { mutableStateOf(appPrefs.playerBackend) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = backend == PlayerBackend.EXO,
                onClick = { backend = PlayerBackend.EXO; appPrefs.playerBackend = PlayerBackend.EXO },
                label = { Text("ExoPlayer") }
            )
            FilterChip(
                selected = backend == PlayerBackend.MPV,
                onClick = { backend = PlayerBackend.MPV; appPrefs.playerBackend = PlayerBackend.MPV },
                label = { Text("mpv") }
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("Trackers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Opens the official site search in your browser.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Trackers.allServices().forEach { svc ->
            TextButton(onClick = { Trackers.openSearch(context, svc, profileName.ifBlank { "anime" }) }) {
                Text(svc.label)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("App update", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Button(onClick = {
            scope.launch {
                updateStatus = "Checking…"
                val r = UpdateChecker.check("0.5.1")
                r.onSuccess { info ->
                    updateStatus = if (info == null) "Up to date (or no release found)"
                    else "Update: ${info.name}"
                    info?.let { UpdateChecker.openRelease(context, it) }
                }.onFailure { updateStatus = it.message ?: "Check failed" }
            }
        }) { Text("Check for updates") }
        updateStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall) }

        Spacer(Modifier.height(24.dp))
        Text("AniCiel 0.5.1", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
