package com.tvtools.app.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvtools.app.ui.common.ArcGauge
import com.tvtools.app.ui.common.CardTitle
import com.tvtools.app.ui.common.InfoCard
import com.tvtools.app.ui.common.LoadingRow
import com.tvtools.app.ui.common.formatBytes
import com.tvtools.app.ui.common.formatUptime
import com.tvtools.app.ui.device.DeviceViewModel

@Composable
fun InfoTab(viewModel: DeviceViewModel) {
    val stats by viewModel.stats.collectAsState()
    val loading by viewModel.statsLoading.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startStatsPolling()
        onDispose { viewModel.stopStatsPolling() }
    }

    val s = stats
    if (loading && s == null) {
        LoadingRow("Reading device info…")
        return
    }
    if (s == null) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionQualityCard()

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(Modifier.weight(1f)) {
                CardTitle("Device")
                Spacer(Modifier.height(6.dp))
                Text(s.model, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (s.brand.isNotBlank()) {
                    Text(s.brand, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            InfoCard(Modifier.weight(1f)) {
                CardTitle("Android")
                Spacer(Modifier.height(6.dp))
                Text(s.androidVersion, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (s.abi.isNotBlank()) {
                    Text(s.abi, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(Modifier.weight(1f)) {
                CardTitle("Storage")
                Spacer(Modifier.height(10.dp))
                ArcGauge(
                    fraction = if (s.storageTotal > 0) s.storageUsed.toFloat() / s.storageTotal else 0f,
                    centerText = formatBytes(s.storageUsed),
                    subText = "of ${formatBytes(s.storageTotal)}",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            InfoCard(Modifier.weight(1f)) {
                CardTitle("RAM")
                Spacer(Modifier.height(10.dp))
                val used = (s.ramTotal - s.ramAvailable).coerceAtLeast(0)
                ArcGauge(
                    fraction = if (s.ramTotal > 0) used.toFloat() / s.ramTotal else 0f,
                    centerText = formatBytes(used),
                    subText = "of ${formatBytes(s.ramTotal)}",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(Modifier.weight(1f)) {
                CardTitle("CPU")
                Spacer(Modifier.height(10.dp))
                ArcGauge(
                    fraction = s.cpuUsagePercent / 100f,
                    centerText = "${s.cpuUsagePercent}%",
                    subText = "usage",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            InfoCard(Modifier.weight(1f)) {
                CardTitle("Network")
                Spacer(Modifier.height(10.dp))
                Text("DOWNLOAD", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatBytes(s.netRxBytes), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("UPLOAD", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatBytes(s.netTxBytes), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
        }

        InfoCard(Modifier.fillMaxWidth()) {
            CardTitle("Uptime")
            Spacer(Modifier.height(6.dp))
            Text(formatUptime(s.uptimeSeconds), style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(16.dp))
    }
}
