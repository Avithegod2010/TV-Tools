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
                val macBytes = mac.split(":", "-")
                    .map { it.trim().toInt(16).toByte() }
                if (macBytes.size != 6) return@withContext false

                val packet = ByteArray(6 + 16 * 6)
                for (i in 0 until 6) packet[i] = 0xFF.toByte()
                var offset = 6
                repeat(16) {
                    for (b in macBytes) {
                        packet[offset++] = b
                    }
                }
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
