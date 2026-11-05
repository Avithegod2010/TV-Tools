package com.tvtools.app.data

import com.tvtools.app.AppServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-scoped sleep timer: fades the TV volume down, then sends KEYCODE_SLEEP.
 * Runs on its own scope so it keeps counting while the user leaves the remote
 * sheet (or the app's foreground activity changes).
 */
object SleepTimer {

    private const val KEY_SLEEP = 223

    data class State(
        val endsAtMs: Long,
        val fadeSeconds: Int,
    ) {
        fun remainingMs(now: Long = System.currentTimeMillis()): Long =
            (endsAtMs - now).coerceAtLeast(0)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    /** Starts a timer for [minutes]; the last [fadeSeconds] ramp the volume to zero. */
    fun start(minutes: Int, fadeSeconds: Int = 20) {
        cancel()
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        val fade = fadeSeconds.coerceIn(0, 120)
        _state.value = State(endAt, fade)
        job = scope.launch {
            val adb = AppServices.adb
            val leadMs = fade * 1_000L
            val fadeStart = endAt - leadMs
            delay((fadeStart - System.currentTimeMillis()).coerceAtLeast(0))

            val originalVolume = runCatching { adb.getMediaVolume() }.getOrNull() ?: 15
            if (fade > 0) {
                val steps = fade.coerceIn(3, 30)
                // Walk from the TV's current level to zero so we never jump louder.
                for (i in steps downTo 0) {
                    val level = (originalVolume * i) / steps
                    runCatching { adb.setMediaVolume(level) }
                    delay(leadMs / steps)
                }
            }

            runCatching { adb.keyEvent(KEY_SLEEP) }
            _state.value = null

            // Put the volume back so the next power-on is not silent.
            delay(2_000)
            runCatching { adb.setMediaVolume(originalVolume) }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = null
    }
}
