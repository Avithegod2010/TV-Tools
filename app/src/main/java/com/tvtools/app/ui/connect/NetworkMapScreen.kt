package com.tvtools.app.ui.connect

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvtools.app.AppServices
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.data.DeviceProto
import com.tvtools.app.data.TvDevice
import com.tvtools.app.ui.labs.LabScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

private data class NetHost(
    val ip: String,
    val ports: List<Int>,
    val kind: String,
    val detail: String,
) {
    val isAdb: Boolean get() = 5555 in ports || 5554 in ports
}

private val PROBE_PORTS = intArrayOf(5555, 5554, 8009, 8008, 7000, 9100, 22, 80, 443)

private fun classify(ports: List<Int>): Pair<String, String> = when {
    5555 in ports -> "Android TV / ADB" to "ADB over network · tap to connect"
    5554 in ports -> "Android TV / emulator" to "ADB port open"
    8009 in ports || 8008 in ports -> "Chromecast / Google TV" to "Cast ports open"
    7000 in ports -> "AirPlay device" to "AirPlay port open"
    9100 in ports -> "Printer" to "JetDirect port open"
    22 in ports -> "SSH host" to "Port 22 open"
    443 in ports || 80 in ports -> "Web device" to "HTTP(S) open"
    else -> "Device" to "Open ports"
}

@Composable
fun NetworkMapScreen(onBack: () -> Unit, onConnected: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hosts by remember { mutableStateOf<List<NetHost>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var scanned by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var connecting by remember { mutableStateOf<String?>(null) }

    val localIp = remember { localIpv4(context) }
    val gateway = remember { gatewayIp(context) }
    val prefix = localIp?.substringBeforeLast('.')

    fun startScan() {
        if (prefix == null) {
            status = "Not on Wi-Fi — connect to the same network as your TV."
            return
        }
        scanJob?.cancel()
        hosts = emptyList()
        scanned = 0
        progress = 0
        status = null
        scanning = true
        scanJob = scope.launch {
            val found = java.util.Collections.synchronizedList(mutableListOf<NetHost>())
            withContext(Dispatchers.IO) {
                val done = AtomicInteger(0)
                val sem = Semaphore(48)
                coroutineScope {
                    (1..254).map { i ->
                        async {
                            sem.withPermit {
                                val ip = "$prefix.$i"
                                val open = PROBE_PORTS.filter { port -> portOpen(ip, port) }
                                if (open.isNotEmpty()) {
                                    val (kind, detail) = classify(open)
                                    found.add(NetHost(ip, open, kind, detail))
                                }
                                val n = done.incrementAndGet()
                                withContext(Dispatchers.Main) {
                                    scanned = n
                                    progress = n
                                    hosts = found.sortedWith(
                                        compareBy<NetHost> { if (it.isAdb) 0 else 1 }
                                            .thenBy { ipSortKey(it.ip) }
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
            scanning = false
            if (found.isEmpty()) status = "No hosts with open ports found on $prefix.0/24."
        }
    }

    fun connectTo(host: NetHost) {
        connecting = host.ip
        scope.launch {
            when (val result = AppServices.adb.connect(host.ip, 5555)) {
                AdbClient.ConnectResult.Success -> {
                    val model = runCatching {
                        AppServices.adb.exec("getprop ro.product.model", timeoutMs = 8_000).trim()
                    }.getOrNull().orEmpty().ifBlank { host.ip }
                    val device = TvDevice(model, host.ip, 5555, DeviceProto.TCP)
                    AppServices.prefs.saveLastDevice(device)
                    AppServices.prefs.saveDevice(device)
                    onConnected(model)
                }
                AdbClient.ConnectResult.PairingRequired -> status = "${host.ip} needs pairing — use Home → Add IP and enter the pairing code."
                is AdbClient.ConnectResult.Error -> status = "Couldn't connect to ${host.ip}: ${result.message}"
            }
            connecting = null
        }
    }

    LabScaffold("Network map", onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lan, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (localIp != null) "Your network: $prefix.0/24" else "No Wi-Fi network",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                buildString {
                                    append("This phone: ")
                                    append(localIp ?: "—")
                                    if (gateway != null) append("  ·  router: $gateway")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { startScan() }, enabled = !scanning) {
                            Icon(
                                if (hosts.isEmpty()) Icons.Default.PlayArrow else Icons.Default.Refresh,
                                null,
                                Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (hosts.isEmpty()) "Scan network" else "Scan again")
                        }
                        if (scanning) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                "$scanned / 254",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (scanning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress / 254f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            status?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(hosts, key = { it.ip }) { host ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = host.isAdb && connecting == null) {
                                if (host.isAdb) connectTo(host)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (host.isAdb)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (host.isAdb) Icons.Default.Tv else Icons.Default.Lan,
                                null,
                                tint = if (host.isAdb) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(host.ip, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${host.kind} · ${host.ports.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    host.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (connecting == host.ip) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else if (host.isAdb) {
                                Text(
                                    "Connect",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun portOpen(ip: String, port: Int): Boolean = try {
    Socket().use { it.connect(InetSocketAddress(ip, port), 180) }
    true
} catch (_: Exception) {
    false
}

private fun ipSortKey(ip: String): Long =
    ip.split('.').fold(0L) { acc, part -> acc * 256 + (part.toLongOrNull() ?: 0) }

private fun localIpv4(context: Context): String? = try {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    cm.activeNetwork?.let { cm.getLinkProperties(it) }
        ?.linkAddresses
        ?.firstOrNull { it.address is Inet4Address }
        ?.address?.hostAddress
} catch (_: Exception) {
    null
}

private fun gatewayIp(context: Context): String? = try {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    cm.activeNetwork?.let { cm.getLinkProperties(it) }
        ?.routes
        ?.firstOrNull { it.isDefaultRoute }
        ?.gateway?.hostAddress
} catch (_: Exception) {
    null
}
