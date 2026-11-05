package com.tvtools.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Wake-on-LAN magic packet sender. */
object Wol {

    /**
     * Sends a WOL magic packet for [mac] to the broadcast address.
     * @return true when the packet was sent (does not guarantee the TV woke).
     */
    suspend fun send(mac: String, broadcast: String = "255.255.255.255", port: Int = 9): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val macBytes = parseMacBytes(mac) ?: return@withContext false
                val packet = magicPacket(macBytes)
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val address = InetAddress.getByName(broadcast)
                    socket.send(DatagramPacket(packet, packet.size, address, port))
                }
                true
            } catch (_: Exception) {
                false
            }
        }
}

/** Parses `aa:bb:cc:dd:ee:ff` or `aa-bb-cc-dd-ee-ff`; null when malformed. */
internal fun parseMacBytes(mac: String): ByteArray? =
    runCatching {
        mac.trim().split(':', '-')
            .map { it.trim().toInt(16).toByte() }
            .toByteArray()
    }.getOrNull()?.takeIf { it.size == 6 }

/** Standard 6-byte sync stream followed by the MAC repeated 16 times. */
internal fun magicPacket(macBytes: ByteArray): ByteArray {
    require(macBytes.size == 6) { "A MAC address must be 6 bytes" }
    val packet = ByteArray(6 + 16 * 6)
    for (i in 0 until 6) packet[i] = 0xFF.toByte()
    var offset = 6
    repeat(16) {
        for (b in macBytes) {
            packet[offset++] = b
        }
    }
    return packet
}
