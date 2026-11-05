package com.tvtools.app.util

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Builds a short looping "find me" beep as a plain 16-bit mono PCM WAV so any
 * TV media player can open it — no encoder or bundled asset needed.
 */
object BeepWav {

    fun write(file: File, seconds: Int = 12): File {
        val sampleRate = 22_050
        val totalSamples = sampleRate * seconds.coerceIn(1, 60)
        val toneHz = 880.0
        val beepSamples = (sampleRate * 0.4).toInt()
        val gapSamples = (sampleRate * 0.25).toInt()
        val cycle = beepSamples + gapSamples
        val fadeSamples = (sampleRate * 0.012).toInt().coerceAtLeast(1)

        val pcm = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val phase = i % cycle
            if (phase >= beepSamples) continue
            // Short fade in/out inside each beep so the speaker doesn't click.
            val env = when {
                phase < fadeSamples -> phase.toDouble() / fadeSamples
                phase > beepSamples - fadeSamples -> (beepSamples - phase).toDouble() / fadeSamples
                else -> 1.0
            }.coerceIn(0.0, 1.0)
            val amplitude = 0.4 * Short.MAX_VALUE * env * min(1.0, phase / 64.0)
            pcm[i] = (amplitude * sin(2 * PI * toneHz * i / sampleRate)).toInt().toShort()
        }

        val dataSize = pcm.size * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)                     // PCM chunk size
        buffer.putShort(1)                    // PCM format
        buffer.putShort(1)                    // mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)         // byte rate
        buffer.putShort(2)                    // block align
        buffer.putShort(16)                   // bits per sample
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        for (sample in pcm) buffer.putShort(sample)

        file.parentFile?.mkdirs()
        file.writeBytes(buffer.array())
        return file
    }
}
