package com.tvtools.app.ui.connect

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvtools.app.AppServices
import com.tvtools.app.R
import com.tvtools.app.ui.labs.LabScaffold
import java.util.concurrent.atomic.AtomicBoolean

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
    val lan = AppServices.networks.current.collectAsStateWithLifecycle().value
    var customType by remember { mutableStateOf("") }
    var restartKey by remember { mutableStateOf(0) }
    val customTypes = remember { mutableStateListOf<String>() }
    val entries = remember(lan, restartKey) { mutableStateMapOf<String, MdnsEntry>() }
    val listeners = remember(lan, restartKey) { mutableListOf<NsdManager.DiscoveryListener>() }
    val browsing = remember(lan, restartKey) { mutableStateListOf<String>() }
    val found = remember(lan, restartKey) { mutableMapOf<String, NsdServiceInfo>() }
    val active = remember(lan, restartKey) { AtomicBoolean(false) }
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }

    fun isCurrent() = active.get() && AppServices.networks.current.value == lan

    fun resolve(service: NsdServiceInfo, type: String) {
        val network = lan ?: return
        if (!isCurrent()) return
        val key = "$type|${service.serviceName}"
        found[key] = service
        entries[key] = MdnsEntry(service.serviceName, type, null, service.port, true)
        try {
            nsd?.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    executor.execute {
                        if (isCurrent() && found[key] === service) {
                            entries[key] = MdnsEntry(service.serviceName, type, null, service.port, false)
                        }
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    executor.execute {
                        if (isCurrent() && found[key] === service) {
                            val addresses = if (Build.VERSION.SDK_INT >= 34) serviceInfo.hostAddresses.orEmpty()
                            else listOfNotNull(serviceInfo.host)
                            val host = addresses.firstOrNull(network::contains)?.hostAddress
                            if (host == null) entries.remove(key)
                            else entries[key] = MdnsEntry(serviceInfo.serviceName, type, host, serviceInfo.port, false)
                        }
                    }
                }
            })
        } catch (_: Exception) {
            entries[key] = MdnsEntry(service.serviceName, type, null, service.port, false)
        }
    }

    fun startType(type: String) {
        val network = lan ?: return
        if (!isCurrent() || type.isBlank() || type in browsing) return
        val manager = nsd ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                executor.execute { if (isCurrent()) browsing.remove(type) }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                executor.execute { resolve(serviceInfo, type) }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                executor.execute {
                    if (isCurrent()) {
                        val key = "$type|${serviceInfo.serviceName}"
                        found.remove(key)
                        entries.remove(key)
                    }
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, network.handle, executor, listener)
            } else {
                manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            }
            browsing.add(type)
            listeners.add(listener)
        } catch (_: Exception) {
        }
    }

    DisposableEffect(lan, restartKey) {
        active.set(lan != null)
        (COMMON_SERVICE_TYPES + customTypes).forEach { startType(it) }
        onDispose {
            active.set(false)
            listeners.forEach { listener ->
                try {
                    nsd?.stopServiceDiscovery(listener)
                } catch (_: Exception) {
                }
            }
            listeners.clear()
        }
    }

    LabScaffold(stringResource(R.string.mdns_service_browser), onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                stringResource(R.string.devices_advertise_services_like_googlecast_tcp_or),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customType,
                    onValueChange = { customType = it.trim() },
                    label = { Text(stringResource(R.string.custom_type)) },
                    placeholder = { Text("_mydevice._tcp") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val type = customType.let { if (it.startsWith("_")) it else "_$it" }
                            .let { if (it.endsWith("._tcp") || it.endsWith("._udp")) it else "$it._tcp" }
                        if (type !in COMMON_SERVICE_TYPES && type !in customTypes) customTypes.add(type)
                        startType(type)
                    },
                    enabled = customType.isNotBlank(),
                ) {
                    Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add))
                }
                IconButton(onClick = { restartKey++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.restart_browsing))
                }
            }
            Spacer(Modifier.height(6.dp))
            val status = if (lan == null) {
                stringResource(R.string.network_no_lan)
            } else if (browsing.isEmpty()) {
                stringResource(R.string.mdns_no_types)
            } else {
                pluralStringResource(R.plurals.mdns_browsing_types, browsing.size, browsing.size)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entries.values.any { it.resolving }) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    pluralStringResource(R.plurals.mdns_service_count, entries.size, status, entries.size),
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
                                        entry.resolving -> stringResource(R.string.mdns_resolving)
                                        else -> stringResource(R.string.mdns_host_unresolved)
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
                                    stringResource(R.string.cast),
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
