package com.tvtools.app.ui.labs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.unit.dp
import com.tvtools.app.AppServices

@Composable
fun AutomationsScreen(onBack: () -> Unit) {
    val settings by AppServices.settings.state.collectAsState()
    var showExportInfo by remember { mutableStateOf(false) }

    LabScaffold("Automations", onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "Daily jobs run while the app is installed. The TV must be reachable on Wi-Fi; if not, the job retries later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily cache trim")
                    Text(
                        "Runs `pm trim-caches` every 24h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.automationCache,
                    onCheckedChange = { AppServices.settings.setAutomationCache(it) },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily TV screenshot")
                    Text(
                        "Saves a screenshot to Pictures/TV Tools every 24h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.automationScreenshot,
                    onCheckedChange = { AppServices.settings.setAutomationScreenshot(it) },
                )
            }
            TextButton(onClick = { showExportInfo = true }, modifier = Modifier.padding(top = 16.dp)) {
                Text("How it works")
            }
        }
    }
    if (showExportInfo) {
        AlertDialog(
            onDismissRequest = { showExportInfo = false },
            title = { Text("Background jobs") },
            text = {
                Text(
                    "Scheduled with WorkManager (24h, minimum periodic interval). Each run connects to your last TV over ADB, performs the task, then disconnects. Some TVs drop wireless debugging after a reboot — enable 'Wireless debugging' again for jobs to resume."
                )
            },
            confirmButton = { TextButton(onClick = { showExportInfo = false }) { Text("Got it") } },
        )
    }
}
