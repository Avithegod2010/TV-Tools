package com.tvtools.app.ui.info

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvtools.app.AppServices
import com.tvtools.app.ui.common.CardTitle
import com.tvtools.app.ui.common.InfoCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Small connection-health card: TCP latency to the TV's ADB port, the last
 * ADB handshake time, and the phone's Wi-Fi RSSI (when the OS exposes it).
 */
@Composable
fun ConnectionQualityCard() {
    val device = AppServices.adb.connectedDevice
    val handshake = AppServices.adb.lastConnectMs
    var latency by remember { mutableStateOf<Long?>(null) }
    var rssi by remember { mutableStateOf<Int?>(null) }
    var measuring by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(device?.host, device?.port, refresh) {
        val d = device ?: return@LaunchedEffect
        measuring = true
        latency = tcpLatency(d.host, d.port)
        rssi = wifiRssi(com.tvtools.app.TvToolsApp.instance)
        measuring = false
    }

    val score = qualityScore(latency, handshake, rssi)
    val label = when {
        score >= 85 -> "Excellent"
        score >= 65 -> "Good"
        score >= 45 -> "Fair"
        else -> "Poor"
    }

    InfoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CardTitle("Connection")
            Spacer(Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                "$score",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("/100", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { index ->
                val filled = score >= (index + 1) * 20 - 10
                Box(
                    Modifier
                        .width(30.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            buildString {
                append("Ping ")
                append(latency?.let { "$it ms" } ?: "—")
                append("  ·  ADB handshake ")
                append(if (handshake > 0) "$handshake ms" else "—")
                append("  ·  Wi-Fi ")
                append(rssi?.let { "$it dBm" } ?: "—")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { refresh++ }, enabled = !measuring) {
                Text(if (measuring) "Measuring…" else "Measure again")
            }
            if (measuring) {
                androidx.compose.material3.CircularProgressIndicator(
                    Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

private val rttSamples = 3

private suspend fun tcpLatency(host: String, port: Int): Long? = withContext(Dispatchers.IO) {
    val times = ArrayList<Long>()
    repeat(rttSamples) {
        runCatching {
            Socket().use { socket ->
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), 1_500)
                times.add((System.nanoTime() - start) / 1_000_000)
            }
        }
    }
    times.minOrNull()
}

@Suppress("DEPRECATION")
private fun wifiRssi(context: Context): Int? {
    return try {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val value = wm?.connectionInfo?.rssi ?: return null
        if (value in -100..-20) value else null
    } catch (_: Exception) {
        null
    }
}

private fun qualityScore(latency: Long?, handshake: Long, rssi: Int?): Int {
    val latencyScore = when {
        latency == null -> 40
        latency <= 20 -> 100
        latency >= 400 -> 10
        else -> (100 - (latency - 20) * 90 / 380).toInt()
    }
    val handshakeScore = when {
        handshake <= 0 -> 60
        handshake <= 150 -> 100
        handshake >= 3_000 -> 10
        else -> (100 - (handshake - 150) * 90 / 2_850).toInt()
    }
    val signalScore = rssi?.let { ((it + 100) * 2).coerceIn(0, 100) } ?: 70
    return (latencyScore * 0.45 + handshakeScore * 0.35 + signalScore * 0.20)
        .toInt().coerceIn(0, 100)
}
