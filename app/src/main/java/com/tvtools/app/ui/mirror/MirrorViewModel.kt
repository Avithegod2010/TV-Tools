package com.tvtools.app.ui.mirror

import android.graphics.BitmapFactory
import android.view.Surface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class MirrorUiState(
    val bitmap: ImageBitmap? = null,
    val running: Boolean = false,
    val fps: Float = 0f,
    val error: String? = null,
    val fastMode: Boolean = true,
    val hasFrame: Boolean = false,
    val quality: MirrorQuality = MirrorQuality.FAST,
)

/**
 * Smooth mirroring: `screenrecord` H.264 at a low resolution is decoded straight
 * to a TextureView via MediaCodec. If the TV rejects screenrecord, it falls back
 * to the older screencap loop (which is slower but works everywhere).
 */
class MirrorViewModel : ViewModel() {

    private val adb = AppServices.adb
    private val frameFile = File(TvToolsApp.instance.cacheDir, "mirror_frame.png")

    private val _ui = MutableStateFlow(MirrorUiState())
    val ui: StateFlow<MirrorUiState> = _ui.asStateFlow()

    private var surface: Surface? = null
    private var wantRunning = false
    private var fastJob: Job? = null
    private var slowJob: Job? = null
    private var decoder: MirrorStream? = null
    private val frames = AtomicInteger(0)
    private var lastStats = 0L

    var tvWidth = 0
        private set
    var tvHeight = 0
        private set

    /** Called by the TextureView when its output surface is created/destroyed. */
    fun setSurface(newSurface: Surface?) {
        if (surface === newSurface) return
        if (newSurface == null) {
            fastJob?.cancel()
            fastJob = null
            decoder?.stop()
            decoder = null
            _ui.value = _ui.value.copy(hasFrame = false)
        }
        surface?.release()
        surface = newSurface
        if (wantRunning && newSurface != null) startFast()
    }

    fun start() {
        wantRunning = true
        fetchScreenSize()
        if (!_ui.value.running) {
            _ui.value = _ui.value.copy(running = true, error = null)
        }
        if (surface != null) {
            startFast()
        } else {
            _ui.value = _ui.value.copy(fastMode = true, hasFrame = false)
            viewModelScope.launch {
                delay(2_500)
                if (wantRunning && surface == null && fastJob == null) startSlow()
            }
        }
    }

    fun stop() {
        wantRunning = false
        fastJob?.cancel()
        fastJob = null
        decoder?.stop()
        decoder = null
        slowJob?.cancel()
        slowJob = null
        _ui.value = _ui.value.copy(running = false, hasFrame = false)
    }

    fun toggle() {
        if (_ui.value.running) stop() else start()
    }

    fun setQuality(quality: MirrorQuality) {
        if (quality == _ui.value.quality) return
        _ui.value = _ui.value.copy(quality = quality)
        if (wantRunning) {
            stop()
            start()
        }
    }

    private fun fetchScreenSize() {
        if (tvWidth > 0) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { adb.getScreenSize() }.onSuccess { (w, h) ->
                tvWidth = w
                tvHeight = h
            }
        }
    }

    private fun startFast() {
        val target = surface ?: return
        if (fastJob?.isActive == true) return
        slowJob?.cancel()
        slowJob = null
        _ui.value = _ui.value.copy(
            running = true,
            fastMode = true,
            hasFrame = false,
            error = null,
            fps = 0f,
        )
        frames.set(0)
        lastStats = System.currentTimeMillis()

        fastJob = viewModelScope.launch(Dispatchers.IO) {
            while (wantRunning && _ui.value.fastMode) {
                val stream = MirrorStream(
                    adb = adb,
                    quality = _ui.value.quality,
                    surface = target,
                    onFrame = {
                        val now = System.currentTimeMillis()
                        frames.incrementAndGet()
                        if (!_ui.value.hasFrame) {
                            _ui.value = _ui.value.copy(hasFrame = true)
                        }
                        if (lastStats == 0L) lastStats = now
                        if (now - lastStats >= 2_000) {
                            val fps = frames.getAndSet(0) * 1000f / (now - lastStats)
                            lastStats = now
                            _ui.value = _ui.value.copy(fps = fps, running = true)
                        }
                    },
                    onError = { message ->
                        if (wantRunning) {
                            _ui.value = _ui.value.copy(
                                fastMode = false,
                                error = "Fast mirror unavailable: $message",
                                hasFrame = false,
                            )
                            viewModelScope.launch { startSlow() }
                        }
                    },
                )
                decoder = stream
                val normalEnd = stream.run()
                decoder = null
                if (!normalEnd || !wantRunning || !_ui.value.fastMode) break
                // screenrecord's 180 s limit: restart with a fresh stream.
                delay(400)
            }
        }

        // If MediaCodec never produces a frame, fall back instead of spinning forever.
        viewModelScope.launch {
            delay(10_000)
            if (wantRunning && _ui.value.fastMode && !_ui.value.hasFrame) {
                _ui.value = _ui.value.copy(
                    fastMode = false,
                    error = "Fast mirror did not start; using slow mode",
                )
                startSlow()
            }
        }
    }

    private fun startSlow() {
        if (slowJob?.isActive == true) return
        _ui.value = _ui.value.copy(
            running = true,
            fastMode = false,
            error = null,
            hasFrame = false,
        )
        slowJob = viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            var windowStart = System.currentTimeMillis()
            while (isActive && wantRunning) {
                val started = System.currentTimeMillis()
                try {
                    adb.screencap(frameFile)
                    val bitmap = BitmapFactory.decodeFile(frameFile.absolutePath)
                    if (bitmap != null) {
                        count++
                        val now = System.currentTimeMillis()
                        if (now - windowStart > 3_000) {
                            _ui.value = _ui.value.copy(
                                fps = count * 1000f / (now - windowStart),
                                running = true,
                            )
                            count = 0
                            windowStart = now
                        }
                        _ui.value = _ui.value.copy(
                            bitmap = bitmap.asImageBitmap(),
                            running = true,
                            error = null,
                        )
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        _ui.value = _ui.value.copy(
                            running = false,
                            error = "Connection lost: ${e.message}",
                        )
                    }
                    break
                }
                val elapsed = System.currentTimeMillis() - started
                delay((450 - elapsed).coerceAtLeast(80))
            }
        }
    }

    /** [relX]/[relY] are 0..1 relative to the TV screen. */
    fun tap(relX: Float, relY: Float) {
        if (tvWidth <= 0 || tvHeight <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                adb.inputTap(
                    (relX.coerceIn(0.02f, 0.98f) * tvWidth).toInt(),
                    (relY.coerceIn(0.02f, 0.98f) * tvHeight).toInt(),
                )
            } catch (_: Exception) {
            }
        }
    }

    fun swipe(relX1: Float, relY1: Float, relX2: Float, relY2: Float) {
        if (tvWidth <= 0 || tvHeight <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                adb.inputSwipe(
                    (relX1.coerceIn(0f, 1f) * tvWidth).toInt(),
                    (relY1.coerceIn(0f, 1f) * tvHeight).toInt(),
                    (relX2.coerceIn(0f, 1f) * tvWidth).toInt(),
                    (relY2.coerceIn(0f, 1f) * tvHeight).toInt(),
                    200,
                )
            } catch (_: Exception) {
            }
        }
    }

    fun key(code: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                adb.keyEvent(code)
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        stop()
        surface?.release()
        surface = null
    }
}
