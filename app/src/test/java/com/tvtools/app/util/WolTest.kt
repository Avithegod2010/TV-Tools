package com.tvtools.app.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WolTest {

    @Test
    fun `parses colon and dash separated macs`() {
        val expected = byteArrayOf(-86, -69, -52, -35, -18, -1) // aa:bb:cc:dd:ee:ff
        assertArrayEquals(expected, parseMacBytes("AA:BB:CC:DD:EE:FF"))
        assertArrayEquals(expected, parseMacBytes("aa-bb-cc-dd-ee-ff"))
        assertArrayEquals(expected, parseMacBytes("  aa:bb:cc:dd:ee:ff  "))
    }

    @Test
    fun `rejects malformed macs`() {
        assertNull(parseMacBytes(""))
        assertNull(parseMacBytes("aa:bb:cc"))
        assertNull(parseMacBytes("aa:bb:cc:dd:ee:ff:11"))
        assertNull(parseMacBytes("zz:bb:cc:dd:ee:ff"))
        assertNull(parseMacBytes("aa bb cc dd ee ff"))
    }

    @Test
    fun `magic packet has sync stream and sixteen repetitions`() {
        val mac = parseMacBytes("01:02:03:04:05:06")
        requireNotNull(mac)

        val packet = magicPacket(mac)

        assertEquals(102, packet.size)
        for (i in 0 until 6) assertEquals(0xFF.toByte(), packet[i])
        repeat(16) { repetition ->
            assertArrayEquals(mac, packet.copyOfRange(6 + repetition * 6, 12 + repetition * 6))
        }
    }

    @Test
    fun `magic packet rejects wrong mac length`() {
        assertThrows(IllegalArgumentException::class.java) {
            magicPacket(byteArrayOf(1, 2, 3))
        }
    }
}
