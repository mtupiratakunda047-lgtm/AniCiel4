package com.aniciel.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aniciel.app.ui.theme.CardElevated
import com.aniciel.app.ui.theme.HubAdult
import com.aniciel.app.ui.theme.TextPrimary

/** MediaHub age gate — children stay out of Adult section. No account required. */
@Composable
fun AdultAgeGateDialog(onConfirm: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        containerColor = CardElevated,
        title = { Text("18+", color = HubAdult, style = MaterialTheme.typography.headlineMedium) },
        text = {
            Text(
                "Are you 18 years old or older?\n\nThis section contains adult content. Please confirm your age to continue.",
                color = TextPrimary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = HubAdult, contentColor = Color.White)
            ) { Text("YES — I'm 18+") }
        },
        dismissButton = { OutlinedButton(onClick = onDecline) { Text("NO — Exit") } }
    )
}
