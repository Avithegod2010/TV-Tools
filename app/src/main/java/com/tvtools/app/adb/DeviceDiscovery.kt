package com.tvtools.app.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.tvtools.app.data.DeviceProto
import com.tvtools.app.data.TvDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections

/**
 * Discovers ADB-enabled devices on the LAN:
 *  - mDNS/NSD: `_adb-tls-connect._tcp` (Android 11+ wireless debugging),
 *    `_adb._tcp` (legacy network ADB, e.g. Fire TV), `_adb-tls-pairing._tcp` (pairing port).
 *  - Fallback: parallel scan of the local /24 subnet for open port 5555.
 */
class DeviceDiscovery(private val context: Context) {

    data class DiscoveryEvent(
        val devices: List<TvDevice>,
        val scanning: Boolean,
    )

    fun discover(scanSubnetToo: Boolean = true): Flow<DiscoveryEvent> = callbackFlow {
        val found = Collections.synchronizedList(mutableListOf<TvDevice>())
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val selfHost = localIpAddress() // don't list the phone itself

        fun emit(scanning: Boolean) {
            val snapshot = synchronized(found) { found.toList() }
                .distinctBy { Triple(it.host, it.port, it.proto) }
                .sortedWith(compareBy({ it.pairing }, { it.name }))
            trySend(DiscoveryEvent(snapshot, scanning))
        }

        fun addDevice(device: TvDevice) {
            if (device.host == selfHost) return
            synchronized(found) {
                if (found.none { it.host == device.host && it.port == device.port && it.proto == device.proto }) {
                    found.add(device)
                }
            }
            emit(scanning = true)
        }

        fun removeDevice(host: String, proto: DeviceProto) {
            synchronized(found) { found.removeAll { it.host == host && it.proto == proto } }
            emit(scanning = true)
        }

        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        fun startNsd(serviceType: String, proto: DeviceProto?) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}

                override fun onServiceFound(service: NsdServiceInfo) {
                    nsd.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress ?: return
                            val rawName = serviceInfo.serviceName
                                .removeSuffix(".$serviceType")
                                .ifBlank { host }
                            val name = prettyName(rawName)
                            if (proto != null) {
                                addDevice(TvDevice(name, host, serviceInfo.port, proto))
                            } else {
                                // pairing service advertised → port is a pairing port
                                addDevice(TvDevice(name, host, serviceInfo.port, DeviceProto.TLS, pairing = true))
                            }
                        }
                    })
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    // Host unknown after loss; refresh by name is unreliable — keep list.
                }

                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try { nsd.stopServiceDiscovery(this) } catch (_: Exception) {}
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            try {
                nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                listeners.add(listener)
            } catch (e: Exception) {
                Log.w("Discovery", "NSD failed for $serviceType", e)
            }
        }

        startNsd("_adb-tls-connect._tcp.", DeviceProto.TLS)
        startNsd("_adb._tcp.", DeviceProto.TCP)
        startNsd("_adb-tls-pairing._tcp.", null)

        // --- subnet scan for port 5555 (Fire TV & boxes with manual ADB-over-network) ---
        val scanJob = if (scanSubnetToo) {
            CoroutineScope(Dispatchers.IO).launch {
                val prefix = localSubnetPrefix() ?: return@launch run { emit(false) }
                val semaphore = Semaphore(64)
                val jobs = (1..254).map { i ->
                    launch {
                        semaphore.withPermit {
                            val ip = "$prefix.$i"
                            try {
                                Socket().use { s ->
                                    s.connect(InetSocketAddress(ip, 5555), 350)
                                }
                                addDevice(TvDevice(ip, ip, 5555, DeviceProto.TCP))
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                jobs.forEach { it.join() }
                emit(scanning = false)
            }
        } else null

        awaitClose {
            listeners.forEach { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {} }
            scanJob?.cancel()
        }
    }

    /** mDNS names for TVs are serials ("adb-XXXXXXXX..."); use a friendly label until we know the model. */
    private fun prettyName(raw: String): String {
        val stripped = raw.removePrefix("adb-")
        val looksLikeSerial = stripped.matches(Regex("[A-Za-z0-9-]{8,}")) && !stripped.contains(' ')
        return if (looksLikeSerial) "Android TV" else stripped
    }

    /** Full local Wi-Fi IPv4 address, or null. */
    private fun localIpAddress(): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val linkProps = cm.activeNetwork?.let { cm.getLinkProperties(it) }
            linkProps?.linkAddresses
                ?.firstOrNull { it.address is java.net.Inet4Address }
                ?.address?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    /** Returns "192.168.1" style /24 prefix of the Wi-Fi interface, or null. */
    @Suppress("DEPRECATION")
    private fun localSubnetPrefix(): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val linkProps = cm.activeNetwork?.let { cm.getLinkProperties(it) }
            val addr = linkProps?.linkAddresses
                ?.firstOrNull { it.address is java.net.Inet4Address }
                ?.address?.hostAddress
            addr?.substringBeforeLast('.')
        } catch (e: Exception) {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ip = wm.connectionInfo.ipAddress
                if (ip == 0) null else String.format(
                    "%d.%d.%d",
                    ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
