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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tvtools.app.AppServices
import com.tvtools.app.R

@Composable
fun AutomationsScreen(onBack: () -> Unit) {
    val settings by AppServices.settings.state.collectAsState()
    var showExportInfo by remember { mutableStateOf(false) }
    var showWakeTime by remember { mutableStateOf(false) }

    LabScaffold(stringResource(R.string.automations), onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.daily_jobs_run_while_the_app_is),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                stringResource(R.string.wake),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 20.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wake_tv_at_a_set_time))
                    Text(
                        stringResource(R.string.send_wake_packet_at, formatMinutes(settings.wakeMinutes)),
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
                    Text(stringResource(R.string.wake_time_value, formatMinutes(settings.wakeMinutes)))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.needs_the_tvs_mac_connect_once_over),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wake_when_i_get_home))
                    Text(
                        stringResource(R.string.when_wi_fi_returns_and_the_tv),
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
                stringResource(R.string.maintenance),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.daily_cache_trim))
                    Text(
                        stringResource(R.string.runs_pm_trim_caches_every_24h),
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
                    Text(stringResource(R.string.daily_tv_screenshot))
                    Text(
                        stringResource(R.string.saves_a_screenshot_to_pictures_tv_tools),
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
                Text(stringResource(R.string.how_it_works))
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
            title = { Text(stringResource(R.string.background_jobs)) },
            text = {
                Text(
                    stringResource(R.string.scheduled_with_workmanager_24h_minimum_periodic_interval)
                )
            },
            confirmButton = { TextButton(onClick = { showExportInfo = false }) { Text(stringResource(R.string.got_it)) } },
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
        title = { Text(stringResource(R.string.wake_time)) },
        text = {
            Column {
                Row {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.hour)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.minute)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.text_24_hour_time_local_to_this_phone),
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
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun formatMinutes(minutes: Int): String {
    val safe = minutes.coerceIn(0, 23 * 60 + 59)
    return "%02d:%02d".format(safe / 60, safe % 60)
}
