package com.tvtools.app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.data.DeviceStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceViewModel : ViewModel() {

    private val adb = AppServices.adb
    private val infoProvider = AppServices.infoProvider

    private val _stats = MutableStateFlow<DeviceStats?>(null)
    val stats: StateFlow<DeviceStats?> = _stats.asStateFlow()

    private val _statsLoading = MutableStateFlow(true)
    val statsLoading: StateFlow<Boolean> = _statsLoading.asStateFlow()

    private val _disconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val disconnected: SharedFlow<Unit> = _disconnected.asSharedFlow()

    private val _reconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reconnected: SharedFlow<Unit> = _reconnected.asSharedFlow()

    private var watchdogJob: Job? = null

    init {
        // Learn the TV's MAC now so Wake-on-LAN works even after it's powered off.
        viewModelScope.launch {
            try {
                if (adb.isConnected) {
                    adb.getMacAddress()?.let { AppServices.prefs.saveLastMac(it) }
                }
            } catch (_: Exception) {
            }
        }
        // Cheap TVs drop the ADB socket silently; a heartbeat catches it before
        // the next key press or screenshot fails with a cryptic error.
        startWatchdog()
        // Resume polling the TV's media session if a queue is active.
        AppServices.playback.startTracking()
        // Screenshots are no longer captured in the background: the TV is only
        // captured when the user taps "Take screenshot" in Tools.
    }

    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = viewModelScope.launch {
            var failures = 0
            while (true) {
                delay(15_000)
                val alive = adb.isConnected && adb.ping()
                failures = if (alive) {
                    0
                } else {
                    failures + 1
                }
                if (failures < 2) continue

                when (adb.reconnect()) {
                    AdbClient.ConnectResult.Success -> {
                        failures = 0
                        _reconnected.tryEmit(Unit)
                    }
                    else -> {
                        _disconnected.tryEmit(Unit)
                        break
                    }
                }
            }
        }
    }

    private var pollJob: Job? = null

    /** (Re)start stats polling while the Info tab is visible. */
    fun startStatsPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                try {
                    if (!adb.isConnected) {
                        _disconnected.tryEmit(Unit)
                        break
                    }
                    _stats.value = infoProvider.getStats()
                    _statsLoading.value = false
                } catch (e: Exception) {
                    // transient error: keep last stats, try again
                    if (!adb.isConnected) {
                        _disconnected.tryEmit(Unit)
                        break
                    }
                }
                delay(3000)
            }
        }
    }

    fun stopStatsPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refreshStatsOnce() {
        viewModelScope.launch {
            try {
                _stats.value = infoProvider.getStats()
                _statsLoading.value = false
            } catch (_: Exception) {
            }
        }
    }

    fun disconnect() {
        stopStatsPolling()
        watchdogJob?.cancel()
        watchdogJob = null
        viewModelScope.launch {
            adb.disconnect()
            _disconnected.emit(Unit)
        }
    }

    override fun onCleared() {
        watchdogJob?.cancel()
        watchdogJob = null
        AppServices.playback.stopTracking()
        super.onCleared()
    }
}
