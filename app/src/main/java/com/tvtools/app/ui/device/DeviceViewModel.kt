package com.tvtools.app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
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
        viewModelScope.launch {
            adb.disconnect()
            _disconnected.emit(Unit)
        }
    }
}
