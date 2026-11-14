package com.tvtools.app.ui.mirror

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.tvtools.app.adb.AdbClient
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/** Size/bitrate presets. Lower is smoother on cheap TV Wi-Fi. */
enum class MirrorQuality(val label: String, val width: Int, val height: Int, val bitRate: Int) {
    FAST("Fast · 360p", 640, 360, 800_000),
    BALANCED("Balanced · 480p", 854, 480, 1_200_000),
    SHARP("Sharp · 720p", 1280, 720, 2_000_000),
}

/**
 * Streams `screenrecord` H.264 to a Surface via MediaCodec.
 *
 * `screenrecord` starts with SPS/PPS/IDR; those are used as csd-0 and the rest
 * of the Annex-B stream is fed to the decoder. This replaces the ~1 fps
 * screencap/PNG path with a low-resolution video stream.
 */
internal class MirrorStream(
    private val adb: AdbClient,
    private val quality: MirrorQuality,
    private val surface: Surface,
    private val onFrame: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private var stream: AdbStream? = null
    private var codec: MediaCodec? = null
    private var decoderJob: Job? = null

    @Volatile
    private var stopped = false

    /** Returns true when the stream ended normally (time limit), false on error. */
    suspend fun run(): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = "screenrecord --output-format=h264 " +
                "--size ${quality.width}x${quality.height} " +
                "--bit-rate ${quality.bitRate} --time-limit 180 -"
            val s = adb.openCommandStream(command)
            stream = s
            val reader = H264NalReader(s.openInputStream())
            val header = withTimeoutOrNull(7_000) {
                withContext(Dispatchers.IO) { readHeader(reader) }
            } ?: throw IOException("No H.264 header (screenrecord unavailable)")
            val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                quality.width,
                quality.height,
            )
            format.setByteBuffer("csd-0", ByteBuffer.wrap(header.sps + header.pps))
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 * 1024 * 1024)
            decoder.configure(format, surface, null, 0)
            decoder.start()
            codec = decoder
            val queue = Channel<ByteArray>(48)
            queue.trySend(header.firstIdr)
            decoderJob = CoroutineScope(Dispatchers.Default).launch {
                try {
                    decodeLoop(decoder, queue)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Teardown can also throw while the codec is being released;
                    // only report a real decoder failure before that.
                    if (!stopped) {
                        onError("Decoder: ${e.message ?: "failed"}")
                        stop()
                    }
                }
            }
            while (!stopped) {
                val nal = reader.next() ?: break
                queue.send(nal)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!stopped) onError(e.message ?: "Mirror stream failed")
            false
        } finally {
            stop()
        }
    }

    private fun readHeader(reader: H264NalReader): Header {
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        while (true) {
            val nal = reader.next() ?: throw IOException("Stream ended before SPS/PPS")
            if (nal.size < 5) continue
            when (nal[4].toInt() and 0x1F) {
                7 -> sps = nal
                8 -> pps = nal
                5 -> {
                    val s = sps
                    val p = pps
                    if (s != null && p != null) return Header(s, p, nal)
                }
            }
        }
    }

    private suspend fun decodeLoop(decoder: MediaCodec, queue: Channel<ByteArray>) {
        val info = MediaCodec.BufferInfo()
        var ptsUs = 0L
        while (currentCoroutineContext().isActive) {
            var worked = false
            val nal = queue.tryReceive().getOrNull()
            if (nal != null) {
                val inputIndex = decoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inputIndex)
                    if (buffer != null) {
                        buffer.clear()
                        buffer.put(nal)
                        decoder.queueInputBuffer(inputIndex, 0, nal.size, ptsUs, 0)
                        ptsUs += 33_000
                        worked = true
                    }
                }
            }

            var outputIndex = decoder.dequeueOutputBuffer(info, 0)
            while (outputIndex >= 0) {
                decoder.releaseOutputBuffer(outputIndex, true)
                onFrame()
                worked = true
                outputIndex = decoder.dequeueOutputBuffer(info, 0)
            }
            if (!worked) delay(4)
        }
    }

    fun stop() {
        stopped = true
        decoderJob?.cancel()
        decoderJob = null
        try {
            stream?.close()
        } catch (_: Exception) {
        }
        stream = null
        try {
            codec?.stop()
        } catch (_: Exception) {
        }
        try {
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null
    }

    private data class Header(
        val sps: ByteArray,
        val pps: ByteArray,
        val firstIdr: ByteArray,
    )
}

/**
 * Splits an Annex-B H.264 stream into NAL units, each normalised to a
 * four-byte start code. Reads through a buffered stream, so it is cheap.
 */
internal class H264NalReader(input: InputStream) {

    private val stream = BufferedInputStream(input, 64 * 1024)
    private val current = ByteArrayOutputStream()
    private var started = false
    private var zeros = 0

    fun next(): ByteArray? {
        while (true) {
            val b = stream.read()
            if (b < 0) {
                val last = if (started && current.size() > 4) current.toByteArray() else null
                current.reset()
                started = false
                zeros = 0
                return last
            }
            if (b == 0) {
                zeros++
                if (started) current.write(0)
                continue
            }
            if (b == 1 && zeros >= 2) {
                val previous = if (started) current.toByteArray() else null
                current.reset()
                current.write(0)
                current.write(0)
                current.write(0)
                current.write(1)
                started = true
                zeros = 0
                if (previous != null) {
                    var end = previous.size
                    while (end > 0 && previous[end - 1] == 0.toByte()) end--
                    if (end > 4) return previous.copyOfRange(0, end)
                }
                continue
            }
            zeros = 0
            if (started) current.write(b)
        }
    }
}
