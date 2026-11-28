package com.tvtools.app.adb

/** An IPv4 link prefix. A fallback scan stays near the phone on very large LANs. */
internal data class LanSubnet(val localAddress: String, val prefixLength: Int) {
    private val local = requireNotNull(ipv4Number(localAddress))
    private val size = 1L shl (32 - prefixLength)
    private val first = local and (0xffffffffL xor (size - 1))
    private val last = first + size - 1

    fun contains(host: String): Boolean = ipv4Number(host)?.let { it in first..last } == true

    fun scanHosts(limit: Int = 254): List<String> {
        val usableFirst = if (prefixLength < 31) first + 1 else first
        val usableLast = if (prefixLength < 31) last - 1 else last
        val rangeStart = (local - limit / 2).coerceIn(
            usableFirst, maxOf(usableFirst, usableLast - limit),
        )
        return (rangeStart..minOf(usableLast, rangeStart + limit))
            .filter { it != local }
            .take(limit)
            .map { value -> (24 downTo 0 step 8).joinToString(".") { "${(value shr it) and 255}" } }
    }
}

internal fun ipv4Number(host: String): Long? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    var address = 0L
    for (part in parts) {
        val octet = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        address = (address shl 8) or octet.toLong()
    }
    return address
}
