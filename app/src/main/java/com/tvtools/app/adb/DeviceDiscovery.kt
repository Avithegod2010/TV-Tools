package com.tvtools.app.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.tvtools.app.R
import com.tvtools.app.data.DeviceProto
import com.tvtools.app.data.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import kotlin.coroutines.resume

/** Shared, network-scoped ADB service discovery and bounded legacy port scan. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDiscovery internal constructor(
    private val context: Context,
    private val networks: LanNetworkMonitor,
    foreground: StateFlow<Boolean>,
    scope: CoroutineScope,
) {
    data class DiscoveryEvent(val devices: List<TvDevice>, val scanning: Boolean)

    private data class Snapshot(
        val network: LanNetwork? = null,
        val devices: List<TvDevice> = emptyList(),
        val scanning: Boolean = false,
    )

    private val currentLan = combine(networks.current, foreground) { lan, active -> lan.takeIf { active } }
    private val scans = MutableStateFlow(0)
    private val sharing = SharingStarted.WhileSubscribed(replayExpirationMillis = 0)
    private val mdns = currentLan.flatMapLatest { lan ->
        if (lan == null) flowOf(Snapshot()) else browse(lan)
    }.shareIn(scope, sharing, replay = 1)
    private val subnet = combine(currentLan, scans) { lan, _ -> lan }.flatMapLatest { lan ->
        if (lan == null) flowOf(Snapshot()) else scan(lan)
    }.shareIn(scope, sharing, replay = 1)

    fun refreshSubnet() = scans.update { it + 1 }

    fun discover(scanSubnetToo: Boolean = true): Flow<DiscoveryEvent> = if (scanSubnetToo) {
        combine(mdns, subnet, currentLan) { advertised, probed, lan ->
            val mdnsSnapshot = advertised.takeIf { it.network == lan } ?: Snapshot()
            val subnetSnapshot = probed.takeIf { it.network == lan } ?: Snapshot()
            DiscoveryEvent(
                (mdnsSnapshot.devices + subnetSnapshot.devices)
                    .distinctBy { listOf(it.host, it.port, it.proto, it.pairing) }
                    .sortedWith(compareBy({ it.pairing }, { it.name })),
                mdnsSnapshot.scanning || subnetSnapshot.scanning,
            )
        }
    } else {
        combine(mdns, currentLan) { snapshot, lan ->
            snapshot.takeIf { it.network == lan }?.let { DiscoveryEvent(it.devices, it.scanning) }
                ?: DiscoveryEvent(emptyList(), lan != null)
        }
    }

    private fun scan(lan: LanNetwork): Flow<Snapshot> = channelFlow {
        trySend(Snapshot(lan, scanning = true))
        val targets = lan.ipv4Subnets.flatMap { it.scanHosts() }.distinct().take(254)
        val discovered = Channel<TvDevice>(Channel.UNLIMITED)
        // At most 24 probes are concurrently running, even on a large link prefix.
        val scanJob = launch(Dispatchers.IO) {
            for (batch in targets.chunked(24)) {
                val probes = batch.map { host ->
                    launch {
                        try {
                            lan.handle.socketFactory.createSocket().use {
                                it.connect(InetSocketAddress(host, 5555), 350)
                            }
                            discovered.trySend(TvDevice(host, host, 5555, DeviceProto.TCP))
                        } catch (_: java.io.IOException) {
                        }
                    }
                }
                probes.forEach { it.join() }
            }
        }
        scanJob.invokeOnCompletion { discovered.close() }
        val found = mutableListOf<TvDevice>()
        for (device in discovered) {
            found.add(device)
            trySend(Snapshot(lan, found.toList(), scanning = true))
        }
        trySend(Snapshot(lan, found.toList()))
    }

    @Suppress("DEPRECATION")
    private fun browse(lan: LanNetwork): Flow<Snapshot> = callbackFlow {
        val nsd = context.getSystemService(NsdManager::class.java)
        if (nsd == null) {
            trySend(Snapshot(lan))
            close()
            return@callbackFlow
        }
        val guard = Any()
        val results = mutableMapOf<Pair<String, String>, TvDevice>()
        val visible = mutableMapOf<Pair<String, String>, NsdServiceInfo>()
        val pending = Channel<Pair<String, NsdServiceInfo>>(Channel.BUFFERED)
        var scanning = true

        fun emit() = trySend(Snapshot(lan, results.values.toList(), scanning))

        fun startNsd(type: String): NsdManager.DiscoveryListener? {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

                override fun onServiceFound(service: NsdServiceInfo) {
                    synchronized(guard) {
                        val key = type to service.serviceName
                        if (key !in visible) {
                            if (pending.trySend(type to service).isSuccess) visible[key] = service
                        }
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    synchronized(guard) {
                        val key = type to service.serviceName
                        visible.remove(key)
                        results.remove(key)
                        emit()
                    }
                }
            }
            return try {
                if (Build.VERSION.SDK_INT >= 33) {
                    nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, lan.handle, context.mainExecutor, listener)
                } else {
                    nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                }
                listener
            } catch (e: RuntimeException) {
                Log.w("Discovery", "NSD unavailable for $type", e)
                null
            }
        }

        synchronized(guard) { emit() }
        val listeners = listOfNotNull(
            startNsd(TLS_CONNECT),
            startNsd(TCP),
            startNsd(TLS_PAIR),
        )
        launch {
            delay(if (listeners.isEmpty()) 0 else 7_000)
            synchronized(guard) {
                scanning = false
                emit()
            }
        }
        // Android versions that allow only one resolution at a time still get all three service types.
        launch {
            for ((type, service) in pending) {
                val key = type to service.serviceName
                if (synchronized(guard) { visible[key] !== service }) continue
                val resolved = withTimeoutOrNull(2_500) { resolve(nsd, service) }
                val addresses = if (Build.VERSION.SDK_INT >= 34) resolved?.hostAddresses.orEmpty()
                else listOfNotNull(resolved?.host)
                val address = addresses.sortedBy { it.address.size }.firstOrNull { candidate ->
                    lan.contains(candidate) && lan.addresses.none { it.first == candidate }
                }
                val host = address?.hostAddress
                synchronized(guard) {
                    if (visible[key] !== service) return@synchronized
                    if (resolved == null || host == null) {
                        visible.remove(key)
                        return@synchronized
                    }
                    val name = service.serviceName.removeSuffix(".$type").ifBlank { host }
                    val friendly = if (name.startsWith("adb-")) context.getString(R.string.network_default_tv_name) else name
                    results[key] = TvDevice(
                        friendly, host, resolved.port,
                        if (type == TCP) DeviceProto.TCP else DeviceProto.TLS,
                        pairing = type == TLS_PAIR,
                        discoveryId = name,
                    )
                    emit()
                }
            }
        }
        awaitClose {
            pending.close()
            for (listener in listeners) runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, service: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    continuation.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    continuation.resume(serviceInfo)
                }
            }
            try {
                nsd.resolveService(service, listener)
                if (Build.VERSION.SDK_INT >= 34) {
                    continuation.invokeOnCancellation { runCatching { nsd.stopServiceResolution(listener) } }
                }
            } catch (_: RuntimeException) {
                continuation.resume(null)
            }
        }

    private companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp."
        const val TCP = "_adb._tcp."
        const val TLS_PAIR = "_adb-tls-pairing._tcp."
    }
}
