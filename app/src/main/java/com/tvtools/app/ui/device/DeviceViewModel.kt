package com.tvtools.app.ui.device

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.data.DeviceStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

    internal val connectionStatus = AppServices.recovery.status
    internal val connectedDevice = adb.connections

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
        // Apply the saved look for this TV as soon as its screen opens.
        viewModelScope.launch {
            val host = adb.connectedDevice?.preferencesHost ?: return@launch
            runCatching {
                AppServices.prefs.loadDeviceProfile(host)?.let {
                    AppServices.settings.applyProfile(it)
                }
            }
        }
        // Push/start the persistent TV-side key injector in the background so the
        // first remote press is already on the fast path.
        viewModelScope.launch { runCatching { adb.startInputServer() } }
        // Resume polling the TV's media session if a queue is active.
        AppServices.playback.startTracking()
        // Screenshots are no longer captured in the background: the TV is only
        // captured when the user taps "Take screenshot" in Tools.
    }

    fun reconnect() {
        viewModelScope.launch { AppServices.recovery.reconnectManually() }
    }

    private var pollJob: Job? = null

    /** (Re)start stats polling while the Info tab is visible. */
    fun startStatsPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            ProcessLifecycleOwner.get().lifecycle.currentStateFlow.collectLatest { state ->
                if (!state.isAtLeast(Lifecycle.State.STARTED)) return@collectLatest
                while (true) {
                    try {
                        if (adb.isConnected) {
                            _stats.value = infoProvider.getStats()
                            _statsLoading.value = false
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Keep the last stats while the shared recovery checks the connection.
                    }
                    delay(3000)
                }
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

    override fun onCleared() {
        AppServices.playback.stopTracking()
        super.onCleared()
    }
}
