package com.tvtools.app.ui.labs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tvtools.app.AppServices

@Composable
fun AutomationsScreen(onBack: () -> Unit) {
    val settings by AppServices.settings.state.collectAsState()
    var showExportInfo by remember { mutableStateOf(false) }
    var showWakeTime by remember { mutableStateOf(false) }

    LabScaffold("Automations", onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Daily jobs run while the app is installed. The TV must be reachable on Wi-Fi; if not, the job retries later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Wake",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 20.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wake TV at a set time")
                    Text(
                        "Sends a Wake-on-LAN packet every day at ${formatMinutes(settings.wakeMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.wakeEnabled,
                    onCheckedChange = { AppServices.settings.setWakeEnabled(it) },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showWakeTime = true }) {
                    Text("Time: ${formatMinutes(settings.wakeMinutes)}")
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Needs the TV's MAC — connect once over ADB to learn it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wake when I get home")
                    Text(
                        "When Wi-Fi returns and the TV doesn't answer, send a wake packet once",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.wakeOnWifi,
                    onCheckedChange = { AppServices.settings.setWakeOnWifi(it) },
                )
            }

            Text(
                "Maintenance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily cache trim")
                    Text(
                        "Runs pm trim-caches every 24h",
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
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showWakeTime) {
        WakeTimeDialog(
            initialMinutes = settings.wakeMinutes,
            onDismiss = { showWakeTime = false },
            onConfirm = { minutes ->
                AppServices.settings.setWakeMinutes(minutes)
                showWakeTime = false
            },
        )
    }

    if (showExportInfo) {
        AlertDialog(
            onDismissRequest = { showExportInfo = false },
            title = { Text("Background jobs") },
            text = {
                Text(
                    "Scheduled with WorkManager (24h, minimum periodic interval). Each run connects to your last TV over ADB, performs the task, then disconnects. Some TVs drop wireless debugging after a reboot — enable 'Wireless debugging' again for jobs to resume. Wake-on-LAN works when the TV is off but still powered and reachable on the same network."
                )
            },
            confirmButton = { TextButton(onClick = { showExportInfo = false }) { Text("Got it") } },
        )
    }
}

@Composable
private fun WakeTimeDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hour by remember { mutableStateOf("%02d".format(initialMinutes / 60)) }
    var minute by remember { mutableStateOf("%02d".format(initialMinutes % 60)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wake time") },
        text = {
            Column {
                Row {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "24-hour time, local to this phone. WorkManager runs it approximately on time (battery saver may delay it).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 7
                val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onConfirm(h * 60 + m)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatMinutes(minutes: Int): String {
    val safe = minutes.coerceIn(0, 23 * 60 + 59)
    return "%02d:%02d".format(safe / 60, safe % 60)
}
