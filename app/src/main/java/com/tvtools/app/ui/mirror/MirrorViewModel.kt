package com.tvtools.app.ui.mirror

import android.graphics.BitmapFactory
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

data class MirrorUiState(
    val bitmap: ImageBitmap? = null,
    val running: Boolean = false,
    val fps: Float = 0f,
    val error: String? = null,
)

/**
 * Polls `screencap` + sync pull in a loop (fast as the TV allows, ~2-5 fps on good links),
 * and forwards taps/swipes as `input` events for remote control.
 */
class MirrorViewModel : ViewModel() {

    private val adb = AppServices.adb
    private val frameFile = File(TvToolsApp.instance.cacheDir, "mirror_frame.png")

    private val _ui = MutableStateFlow(MirrorUiState())
    val ui: StateFlow<MirrorUiState> = _ui.asStateFlow()

    private var job: Job? = null

    var tvWidth = 0
        private set
    var tvHeight = 0
        private set

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(running = true, error = null)
            var frames = 0
            var windowStart = System.currentTimeMillis()

            while (isActive) {
                val started = System.currentTimeMillis()
                try {
                    adb.screencap(frameFile)
                    val bmp = BitmapFactory.decodeFile(frameFile.absolutePath)
                    if (bmp != null) {
                        frames++
                        val now = System.currentTimeMillis()
                        if (now - windowStart > 4000) {
                            _ui.value = _ui.value.copy(fps = frames * 1000f / (now - windowStart))
                            frames = 0
                            windowStart = now
                        }
                        _ui.value = _ui.value.copy(bitmap = bmp.asImageBitmap(), running = true, error = null)
                    }
                    // Fetch resolution once, after the first frame is already visible.
                    if (tvWidth == 0) {
                        try {
                            val (w, h) = adb.getScreenSize()
                            tvWidth = w
                            tvHeight = h
                        } catch (_: Exception) {
                        }
                    }
                } catch (e: Exception) {
                    _ui.value = _ui.value.copy(
                        running = false,
                        error = "Connection lost: ${e.message}",
                    )
                    break
                }
                val elapsed = System.currentTimeMillis() - started
                delay((450 - elapsed).coerceAtLeast(80))
            }
            _ui.value = _ui.value.copy(running = false)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _ui.value = _ui.value.copy(running = false)
    }

    fun toggle() {
        if (_ui.value.running) stop() else start()
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
    }
}
