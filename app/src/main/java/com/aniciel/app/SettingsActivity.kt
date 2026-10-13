package com.aniciel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val appPrefs = remember { AppPreferences(context) }
    var profileName by remember { mutableStateOf(appPrefs.profileName) }
    var compactGrid by remember { mutableStateOf(appPrefs.compactGrid) }
    var adultEnabled by remember { mutableStateOf(appPrefs.adultEnabled) }
    val downloadPath = remember { DownloadSettings.resolvedDir().absolutePath }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Path (fixed): $downloadPath", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Files go to public /Download/AniCiel — no VIP storage.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = profileName,
                onValueChange = {
                    profileName = it
                    appPrefs.profileName = it
                },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Compact grid", Modifier.weight(1f))
                Switch(
                    checked = compactGrid,
                    onCheckedChange = {
                        compactGrid = it
                        appPrefs.compactGrid = it
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Adult section unlocked", Modifier.weight(1f))
                Switch(
                    checked = adultEnabled,
                    onCheckedChange = {
                        adultEnabled = it
                        appPrefs.adultEnabled = it
                    }
                )
            }

            HorizontalDivider()
            Text(
                "AniCiel MediaHub — no account, no VIP, no ads.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
