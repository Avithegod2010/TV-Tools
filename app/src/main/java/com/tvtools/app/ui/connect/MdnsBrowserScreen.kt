package com.tvtools.app.ui.connect

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvtools.app.ui.labs.LabScaffold

private data class MdnsEntry(
    val name: String,
    val type: String,
    val host: String?,
    val port: Int,
    val resolving: Boolean,
)

private val COMMON_SERVICE_TYPES = listOf(
    "_adb-tls-connect._tcp",
    "_adb-tls-pairing._tcp",
    "_adb._tcp",
    "_googlecast._tcp",
    "_airplay._tcp",
    "_raop._tcp",
    "_spotify-connect._tcp",
    "_http._tcp",
    "_https._tcp",
    "_printer._tcp",
    "_ipp._tcp",
    "_sftp-ssh._tcp",
    "_ssh._tcp",
    "_smb._tcp",
    "_workstation._tcp",
    "_companion-link._tcp",
    "_hap._tcp",
    "_matter._tcp",
    "_googlezone._tcp",
)

/**
 * Browse mDNS/DNS-SD advertisements the OS can resolve. Android's NSD only
 * browses explicitly named service types, so this screen starts with a list of
 * common TV/streaming/macOS/printer types and lets you add a custom one.
 */
@Composable
fun MdnsBrowserScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val nsd = remember {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }
    val entries = remember { mutableStateMapOf<String, MdnsEntry>() }
    val listeners = remember { mutableStateListOf<NsdManager.DiscoveryListener>() }
    val browsing = remember { mutableStateListOf<String>() }
    var customType by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Browsing…") }
    var restartKey by remember { mutableStateOf(0) }

    fun resolve(service: NsdServiceInfo, type: String) {
        val key = "$type|${service.serviceName}"
        entries[key] = MdnsEntry(service.serviceName, type, null, service.port, true)
        try {
            nsd?.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    entries[key] = MdnsEntry(
                        serviceInfo.serviceName,
                        type,
                        null,
                        serviceInfo.port,
                        false,
                    )
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress ?: return
                    entries[key] = MdnsEntry(
                        serviceInfo.serviceName,
                        type,
                        host.removeSuffix("."),
                        serviceInfo.port,
                        false,
                    )
                }
            })
        } catch (_: Exception) {
            entries[key] = MdnsEntry(service.serviceName, type, null, service.port, false)
        }
    }

    fun startType(type: String) {
        if (type.isBlank() || type in browsing) return
        val manager = nsd ?: return
        browsing.add(type)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolve(serviceInfo, type)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                entries.remove("$type|${serviceInfo.serviceName}")
            }
        }
        try {
            manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            listeners.add(listener)
        } catch (_: Exception) {
        }
    }

    DisposableEffect(restartKey) {
        entries.clear()
        listeners.forEach { listener ->
            try {
                nsd?.stopServiceDiscovery(listener)
            } catch (_: Exception) {
            }
        }
        listeners.clear()
        browsing.clear()
        COMMON_SERVICE_TYPES.forEach { startType(it) }
        status = "Browsing ${browsing.size} service types…"

        onDispose {
            listeners.forEach { listener ->
                try {
                    nsd?.stopServiceDiscovery(listener)
                } catch (_: Exception) {
                }
            }
            listeners.clear()
            browsing.clear()
        }
    }

    LabScaffold("mDNS browser", onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "Devices advertise services like _googlecast._tcp or _adb-tls-connect._tcp. " +
                    "Android can only browse types it knows, so add a custom type if you're hunting something specific.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customType,
                    onValueChange = { customType = it.trim() },
                    label = { Text("Custom type") },
                    placeholder = { Text("_mydevice._tcp") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val type = customType.let { if (it.startsWith("_")) it else "_$it" }
                            .let { if (it.endsWith("._tcp") || it.endsWith("._udp")) it else "$it._tcp" }
                        startType(type)
                        status = "Browsing ${browsing.size} service types…"
                    },
                    enabled = customType.isNotBlank(),
                ) {
                    Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
                IconButton(onClick = { restartKey++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Restart browsing")
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entries.values.any { it.resolving }) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    "$status · ${entries.size} service(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            val sorted = entries.values.sortedWith(
                compareBy({ it.type !in listOf("_adb-tls-connect._tcp", "_adb-tls-pairing._tcp", "_adb._tcp") }, { it.type }, { it.name })
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(sorted, key = { "${it.type}|${it.name}" }) { entry ->
                    val isAdb = entry.type.startsWith("_adb")
                    val isCast = entry.type == "_googlecast._tcp" || entry.type == "_airplay._tcp"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAdb)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    entry.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    when {
                                        entry.host != null -> "${if (entry.host.contains(':')) "[${entry.host}]" else entry.host}:${entry.port}"
                                        entry.resolving -> "resolving…"
                                        else -> "host not resolved — tap to retry"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAdb || isCast) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isAdb) {
                                Text(
                                    "ADB",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else if (isCast) {
                                Text(
                                    "Cast",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
