package com.tvtools.app.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal data class LanNetwork(
    val handle: Network,
    val interfaceName: String?,
    val addresses: Set<Pair<InetAddress, Int>>,
    val gateways: Set<InetAddress>,
) {
    val ipv4Subnets = addresses.mapNotNull { (address, prefix) ->
        (address as? Inet4Address)?.hostAddress?.let { LanSubnet(it, prefix) }
    }

    fun isSameLanAs(previous: LanNetwork): Boolean = sameLan(
        interfaceName, addresses, gateways, previous.interfaceName, previous.addresses,
        previous.gateways, handle == previous.handle,
    )

    fun containsHost(host: String): Boolean {
        if (':' !in host) return ipv4Subnets.any { it.contains(host) }
        return runCatching { contains(InetAddress.getByName(host)) }.getOrDefault(false)
    }

    fun contains(address: InetAddress): Boolean {
        if (address is Inet4Address) return address.hostAddress?.let { host ->
            ipv4Subnets.any { it.contains(host) }
        } == true
        if (address !is Inet6Address) return false
        return addresses.any { (local, prefix) ->
            local is Inet6Address && (0 until prefix).all { bit ->
                val mask = 1 shl (7 - bit % 8)
                (local.address[bit / 8].toInt() and mask) == (address.address[bit / 8].toInt() and mask)
            }
        }
    }
}

/** Host address renewals are expected. Compare router and directly connected prefixes. */
internal fun sameLan(
    interfaceName: String?,
    addresses: Set<Pair<InetAddress, Int>>,
    gateways: Set<InetAddress>,
    previousInterface: String?,
    previousAddresses: Set<Pair<InetAddress, Int>>,
    previousGateways: Set<InetAddress>,
    sameHandle: Boolean,
): Boolean {
    if (interfaceName == null || interfaceName != previousInterface || gateways != previousGateways) return false
    // A new handle on a gateway-less link provides too little evidence for a reconnect.
    if (!sameHandle && gateways.isEmpty()) return false
    val prefixes = networkPrefixes(addresses)
    return prefixes.isNotEmpty() && prefixes == networkPrefixes(previousAddresses)
}

private fun networkPrefixes(addresses: Set<Pair<InetAddress, Int>>) = addresses
    .filterNot { (address, _) -> address.isLinkLocalAddress }
    .map { (address, length) ->
        val prefix = address.address.mapIndexed { index, byte ->
            val bits = (length - index * 8).coerceIn(0, 8)
            (byte.toInt() and (0xff shl (8 - bits))) and 0xff
        }
        prefix to length
    }.toSet()

/** One process-wide view of the LAN, including Wi-Fi without internet access. */
internal class LanNetworkMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val candidates = mutableMapOf<Network, LanNetwork>()
    private val _current = MutableStateFlow<LanNetwork?>(null)
    val current = _current.asStateFlow()

    init {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                update(network, properties)
            }

            override fun onLost(network: Network) {
                update(network, null)
            }
        }
        try {
            manager?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                    .build(),
                callback,
            )
            manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { select() }
                override fun onLost(network: Network) { select() }
            })
            manager?.activeNetwork?.let { network ->
                val capabilities = manager.getNetworkCapabilities(network)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                ) {
                    // Callbacks supply the complete snapshot. Only prime the initial value if one
                    // has not arrived already.
                    synchronized(this) {
                        if (candidates.isEmpty()) update(network, manager.getLinkProperties(network))
                    }
                }
            }
        } catch (e: RuntimeException) {
            Log.w("TvToolsNetwork", "Local network monitoring unavailable", e)
        }
    }

    @Synchronized
    private fun update(network: Network, properties: LinkProperties?) {
        val addresses = properties?.linkAddresses.orEmpty()
            .filter { !it.address.isLoopbackAddress }
            .map { it.address to it.prefixLength }
            .toSet()
        if (addresses.isEmpty()) {
            candidates.remove(network)
        } else {
            candidates[network] = LanNetwork(
                network,
                properties?.interfaceName,
                addresses,
                properties?.routes.orEmpty().mapNotNull { it.gateway }
                    .filterNot { it.isAnyLocalAddress }.toSet(),
            )
        }
        select()
    }

    @Synchronized
    private fun select() {
        _current.value = candidates[manager?.activeNetwork]
            ?: candidates[_current.value?.handle]
            ?: candidates.values.lastOrNull()
    }
}
