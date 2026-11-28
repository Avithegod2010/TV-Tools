package com.tvtools.app.ui.connect

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvtools.app.AppServices
import com.tvtools.app.R
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.data.DeviceProto
import com.tvtools.app.data.TvDevice
import com.tvtools.app.ui.labs.LabScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import javax.net.SocketFactory

private data class NetHost(
    val ip: String,
    val ports: List<Int>,
    val kind: String,
    val detail: String,
) {
    val adbPort: Int? get() = ports.firstOrNull { it == 5555 || it == 5554 }
    val isAdb: Boolean get() = adbPort != null
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
    val lan by AppServices.networks.current.collectAsStateWithLifecycle()
    var hosts by remember(lan) { mutableStateOf<List<NetHost>>(emptyList()) }
    var scanning by remember(lan) { mutableStateOf(false) }
    var scanned by remember(lan) { mutableIntStateOf(0) }
    var status by remember(lan) { mutableStateOf<String?>(null) }
    var scanJob by remember(lan) { mutableStateOf<Job?>(null) }
    var connectJob by remember(lan) { mutableStateOf<Job?>(null) }
    var connecting by remember(lan) { mutableStateOf<String?>(null) }

    DisposableEffect(lan) {
        onDispose {
            scanJob?.cancel()
            connectJob?.cancel()
        }
    }

    val subnets = lan?.ipv4Subnets.orEmpty()
    val scanTargets = remember(lan) { subnets.flatMap { it.scanHosts() }.distinct().take(254) }
    val gateways = lan?.gateways.orEmpty().filterIsInstance<Inet4Address>()
        .mapNotNull { it.hostAddress }.joinToString(", ")
    val prefix = subnets.joinToString(", ") { "${it.localAddress}/${it.prefixLength}" }

    fun startScan() {
        val network = lan ?: return
        if (scanning || scanTargets.isEmpty() || AppServices.networks.current.value != network) return
        hosts = emptyList()
        scanned = 0
        status = null
        scanning = true
        scanJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val sockets = network.handle.socketFactory
                    val sem = Semaphore(48)
                    coroutineScope {
                        scanTargets.map { ip ->
                            async {
                                sem.withPermit {
                                    val open = PROBE_PORTS.filter { port ->
                                        ensureActive()
                                        AppServices.networks.current.value == network && portOpen(sockets, ip, port)
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (AppServices.networks.current.value == network) {
                                            scanned++
                                            if (open.isNotEmpty()) {
                                                val (kind, detail) = classify(open)
                                                hosts = (hosts + NetHost(ip, open, kind, detail)).sortedWith(
                                                    compareBy<NetHost> { if (it.isAdb) 0 else 1 }
                                                        .thenBy { ipSortKey(it.ip) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }
                if (AppServices.networks.current.value == network && hosts.isEmpty()) {
                    status = context.getString(R.string.network_map_empty)
                }
            } finally {
                scanning = false
            }
        }
    }

    fun connectTo(host: NetHost) {
        val network = lan ?: return
        val port = host.adbPort ?: return
        if (connecting != null || AppServices.networks.current.value != network) return
        connecting = host.ip
        connectJob = scope.launch {
            try {
                val result = AppServices.adb.connect(host.ip, port)
                if (AppServices.networks.current.value != network) return@launch
                when (result) {
                    AdbClient.ConnectResult.Success -> {
                        val model = runCatching {
                            AppServices.adb.exec("getprop ro.product.model", timeoutMs = 8_000).trim()
                        }.getOrNull().orEmpty().ifBlank { host.ip }
                        ensureActive()
                        if (AppServices.networks.current.value != network) return@launch
                        val device = TvDevice(model, host.ip, port, DeviceProto.TCP)
                        AppServices.prefs.saveLastDevice(device)
                        AppServices.prefs.saveDevice(device)
                        onConnected(model)
                    }
                    AdbClient.ConnectResult.PairingRequired ->
                        status = context.getString(R.string.network_map_pairing_required, host.ip)
                    is AdbClient.ConnectResult.Error ->
                        status = context.getString(R.string.network_map_connect_failed, host.ip, result.message)
                }
            } finally {
                connecting = null
            }
        }
    }

    LabScaffold(stringResource(R.string.network_map), onBack) {
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
                                if (subnets.isEmpty()) stringResource(R.string.network_map_no_ipv4)
                                else stringResource(R.string.network_map_prefixes, prefix),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (gateways.isEmpty()) stringResource(R.string.doctor_phone_ip, subnets.firstOrNull()?.localAddress ?: "—")
                                else stringResource(R.string.network_map_phone_and_router, subnets.firstOrNull()?.localAddress ?: "—", gateways),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { startScan() }, enabled = !scanning && scanTargets.isNotEmpty()) {
                            Icon(
                                if (hosts.isEmpty()) Icons.Default.PlayArrow else Icons.Default.Refresh,
                                null,
                                Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (hosts.isEmpty()) stringResource(R.string.scan_network) else stringResource(R.string.scan_again))
                        }
                        if (scanning) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                "$scanned / ${scanTargets.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (scanning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { scanned.toFloat() / scanTargets.size },
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
                                    stringResource(R.string.connect),
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

private fun portOpen(sockets: SocketFactory, ip: String, port: Int): Boolean = try {
    sockets.createSocket().use { it.connect(InetSocketAddress(ip, port), 180) }
    true
} catch (_: Exception) {
    false
}

private fun ipSortKey(ip: String): Long =
    ip.split('.').fold(0L) { acc, part -> acc * 256 + (part.toLongOrNull() ?: 0) }
