package com.tvtools.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BeepWavTest {

    @Test
    fun `writes a valid 16-bit mono pcm wav`() {
        val file = File.createTempFile("tvtools-beep", ".wav")
        try {
            BeepWav.write(file, seconds = 1)
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            assertEquals('R'.code.toByte(), bytes[0])
            assertEquals('I'.code.toByte(), bytes[1])
            assertEquals('F'.code.toByte(), bytes[2])
            assertEquals('F'.code.toByte(), bytes[3])
            assertEquals(36 + (bytes.size - 44), buffer.getInt(4))
            assertEquals("WAVE", String(bytes, 8, 4))
            assertEquals("fmt ", String(bytes, 12, 4))
            assertEquals(16, buffer.getInt(16))
            assertEquals(1, buffer.getShort(20).toInt())       // PCM
            assertEquals(1, buffer.getShort(22).toInt())       // mono
            assertEquals(22_050, buffer.getInt(24))
            assertEquals(22_050 * 2, buffer.getInt(28))        // byte rate
            assertEquals(2, buffer.getShort(32).toInt())       // block align
            assertEquals(16, buffer.getShort(34).toInt())      // bits per sample
            assertEquals("data", String(bytes, 36, 4))
            assertEquals(bytes.size - 44, buffer.getInt(40))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `duration controls the payload size`() {
        val one = File.createTempFile("tvtools-beep1", ".wav")
        val two = File.createTempFile("tvtools-beep2", ".wav")
        try {
            BeepWav.write(one, 1)
            BeepWav.write(two, 2)
            assertEquals(44 + 22_050 * 2, one.length().toInt())
            assertEquals(44 + 2 * 22_050 * 2, two.length().toInt())
        } finally {
            one.delete()
            two.delete()
        }
    }

    @Test
    fun `beeps start and end at silence to avoid clicks`() {
        val file = File.createTempFile("tvtools-beep3", ".wav")
        try {
            BeepWav.write(file, 1)
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0, buffer.getShort(44).toInt())
            // The 0.4 s beep ends before the 0.65 s cycle; check the first gap sample.
            val gapSample = (22_050 * 0.5).toInt() * 2 + 44
            assertEquals(0, buffer.getShort(gapSample).toInt())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `seconds are clamped to a sane range`() {
        val file = File.createTempFile("tvtools-beep4", ".wav")
        try {
            BeepWav.write(file, 0)
            assertTrue(file.length() >= 44)
            assertEquals(44 + 22_050 * 2, file.length().toInt())
        } finally {
            file.delete()
        }
    }
}
