package com.tvtools.app.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tvtools.app.adb.AdbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared TV playback state and queue.
 *
 * The TV still does the actual decoding; this controller remembers the queue,
 * sends VIEW intents for the next/previous track and mirrors the TV's media
 * session so the mini-player works on every screen while the device is open.
 */
data class PlaybackState(
    val current: MusicItem? = null,
    val nowPlaying: NowPlaying? = null,
    val queue: List<MusicItem> = emptyList(),
    val index: Int = -1,
    val active: Boolean = false,
) {
    val title: String
        get() = nowPlaying?.title?.takeIf { it.isNotBlank() } ?: current?.title.orEmpty()

    val artist: String
        get() = nowPlaying?.artist?.takeIf { it.isNotBlank() } ?: current?.artist.orEmpty()

    val isPlaying: Boolean get() = nowPlaying?.isPlaying == true

    val durationMs: Long
        get() = nowPlaying?.durationMs?.takeIf { it > 0 } ?: current?.durationMs ?: 0L

    fun currentPositionMs(now: Long = System.currentTimeMillis()): Long {
        val np = nowPlaying ?: return 0L
        return np.currentPositionMs(now)
    }
}

class PlaybackController(private val adb: AdbClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()

    private var pollJob: Job? = null
    private var foreground = true

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground = true
                if (_state.value.active) startPolling()
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground = false
                stopPolling()
            }
        })
    }

    /** Play [item] and use [queue] for the next/previous buttons. */
    fun play(item: MusicItem, queue: List<MusicItem>) {
        val list = (queue.ifEmpty { listOf(item) }).distinctBy { it.path }
        val index = list.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
        _state.value = _state.value.copy(
            current = item,
            queue = list,
            index = index,
            active = true,
        )
        scope.launch {
            runCatching { adb.openFileOnTv(item.path, mimeOf(item)) }
            delay(900)
            refresh()
        }
        startPolling()
    }

    /** One query when the Music screen opens, so an already-playing TV is detected. */
    fun refreshOnce() {
        scope.launch {
            refresh()
            if (_state.value.nowPlaying != null) startPolling()
        }
    }

    fun toggle() {
        scope.launch {
            adb.keyEvent(KEY_MEDIA_PLAY_PAUSE)
            delay(450)
            refresh()
        }
    }

    fun seek(deltaMs: Long) {
        scope.launch { adb.seekRelative(deltaMs) }
    }

    fun next() {
        val s = _state.value
        if (s.queue.isNotEmpty() && s.index in 0 until s.queue.lastIndex) {
            playAt(s.index + 1)
        } else {
            sendKeyAndRefresh(KEY_MEDIA_NEXT)
        }
    }

    fun previous() {
        val s = _state.value
        if (s.queue.isNotEmpty() && s.index > 0) {
            playAt(s.index - 1)
        } else {
            sendKeyAndRefresh(KEY_MEDIA_PREV)
        }
    }

    fun playAt(index: Int) {
        val s = _state.value
        val item = s.queue.getOrNull(index) ?: return
        _state.value = s.copy(current = item, index = index, active = true)
        scope.launch {
            runCatching { adb.openFileOnTv(item.path, mimeOf(item)) }
            delay(900)
            refresh()
        }
        startPolling()
    }

    fun enqueue(item: MusicItem) {
        val s = _state.value
        if (s.queue.any { it.path == item.path }) return
        _state.value = s.copy(queue = s.queue + item)
    }

    /** Insert right after the currently playing track. */
    fun playNext(item: MusicItem) {
        val s = _state.value
        if (s.queue.any { it.path == item.path }) return
        val insertAt = if (s.queue.isEmpty()) 0 else (s.index + 1).coerceIn(0, s.queue.size)
        val queue = s.queue.toMutableList().also { it.add(insertAt, item) }
        _state.value = s.copy(queue = queue)
    }

    fun removeFromQueue(index: Int) {
        val s = _state.value
        if (index !in s.queue.indices) return
        val queue = s.queue.toMutableList().also { it.removeAt(index) }
        val newIndex = when {
            queue.isEmpty() -> -1
            index < s.index -> s.index - 1
            index == s.index -> s.index.coerceAtMost(queue.lastIndex)
            else -> s.index
        }
        _state.value = s.copy(queue = queue, index = newIndex)
    }

    fun clearQueue() {
        _state.value = _state.value.copy(queue = emptyList(), index = -1)
    }

    /** Hide the mini-player and stop polling. */
    fun stop() {
        stopPolling()
        _state.value = PlaybackState()
    }

    fun startTracking() {
        if (_state.value.active) startPolling()
    }

    fun stopTracking() {
        stopPolling()
    }

    private fun sendKeyAndRefresh(code: Int) {
        scope.launch {
            adb.keyEvent(code)
            delay(450)
            refresh()
        }
    }

    private fun startPolling() {
        if (!foreground) return
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                if (!adb.recentlyInteracted(1_500)) refresh()
                delay(6_000)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun refresh() {
        val np = runCatching { adb.nowPlaying() }.getOrNull()
        val s = _state.value
        if (np != null) {
            val match = s.queue.indexOfFirst { it.title.equals(np.title, ignoreCase = true) }
            _state.value = s.copy(
                nowPlaying = np,
                active = true,
                current = if (match >= 0) s.queue[match] else s.current,
                index = if (match >= 0) match else s.index,
            )
        } else if (s.nowPlaying != null) {
            _state.value = s.copy(nowPlaying = null)
        }
    }

    private fun mimeOf(item: MusicItem): String = when (item.extension) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "aac" -> "audio/aac"
        else -> "audio/*"
    }

    private companion object {
        const val KEY_MEDIA_PLAY_PAUSE = 85
        const val KEY_MEDIA_NEXT = 87
        const val KEY_MEDIA_PREV = 88
    }
}
