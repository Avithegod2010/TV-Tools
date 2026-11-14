package com.tvtools.app.ui.mirror

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class H264NalReaderTest {

    private fun startCode4() = byteArrayOf(0, 0, 0, 1)
    private fun startCode3() = byteArrayOf(0, 0, 1)

    @Test
    fun `splits four-byte start codes and reports nal types`() {
        val sps = startCode4() + byteArrayOf(0x67, 0x42, 0x00, 0x28)
        val pps = startCode4() + byteArrayOf(0x68, 0xEE.toByte(), 0x38, 0x80.toByte())
        val idr = startCode4() + byteArrayOf(0x65, 1, 2, 3, 4)
        val slice = startCode4() + byteArrayOf(0x41, 9, 8, 7)
        val stream = ByteArrayInputStream(sps + pps + idr + slice)
        val reader = H264NalReader(stream)

        val first = reader.next()!!
        val second = reader.next()!!
        val third = reader.next()!!
        val fourth = reader.next()!!

        assertEquals(7, first[4].toInt() and 0x1F)
        assertEquals(8, second[4].toInt() and 0x1F)
        assertEquals(5, third[4].toInt() and 0x1F)
        assertEquals(1, fourth[4].toInt() and 0x1F)
        assertArrayEquals(idr, third)
        assertNull(reader.next())
    }

    @Test
    fun `normalises three-byte start codes to four bytes`() {
        val sps = startCode3() + byteArrayOf(0x67, 0x11, 0x22)
        val idr = startCode3() + byteArrayOf(0x65, 0x33, 0x44)
        val reader = H264NalReader(ByteArrayInputStream(sps + idr))

        val first = reader.next()!!
        val second = reader.next()!!

        assertEquals(0, first[0].toInt())
        assertEquals(0, first[1].toInt())
        assertEquals(0, first[2].toInt())
        assertEquals(1, first[3].toInt())
        assertEquals(7, first[4].toInt() and 0x1F)
        assertEquals(5, second[4].toInt() and 0x1F)
        assertArrayEquals(byteArrayOf(0x65, 0x33, 0x44), second.copyOfRange(4, second.size))
    }

    @Test
    fun `empty stream returns null`() {
        assertNull(H264NalReader(ByteArrayInputStream(ByteArray(0))).next())
    }
}
