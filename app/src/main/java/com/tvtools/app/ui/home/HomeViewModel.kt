package com.tvtools.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.data.DeviceProto
import com.tvtools.app.data.TvDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class HomeUiState(
    val devices: List<TvDevice> = emptyList(),
    val scanning: Boolean = true,
    val lastConnected: TvDevice? = null,
    val connectingTo: TvDevice? = null,
    val showAll: Boolean = false,
) {
    val visibleDevices: List<TvDevice>
        get() = if (showAll) devices
        else devices.filter { !it.pairing } // main filter hides pairing-only adverts
}

sealed interface HomeEvent {
    data class Connected(val device: TvDevice) : HomeEvent
    data class Error(val message: String) : HomeEvent
    data class PairingRequested(val device: TvDevice) : HomeEvent
    data object PairSuccessConnect : HomeEvent
}

class HomeViewModel : ViewModel() {

    private val adb = AppServices.adb
    private val prefs = AppServices.prefs
    private val discovery = AppServices.discovery

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private var discoveryJob: Job? = null

    init {
        startDiscovery()
        viewModelScope.launch {
            val last = prefs.loadLastDevice()
            _ui.value = _ui.value.copy(lastConnected = last)
            // Auto-reconnect: quietly try the last TV (twice) so the app is usable immediately.
            if (last != null && !adb.isConnected) {
                _ui.value = _ui.value.copy(connectingTo = last)
                var connected = false
                for (attempt in 0..1) {
                    kotlinx.coroutines.delay(if (attempt == 0) 800 else 1500)
                    if (adb.isConnected) {
                        connected = true
                        break
                    }
                    when (adb.connect(last.host, last.port)) {
                        AdbClient.ConnectResult.Success -> {
                            connected = true
                        }
                        AdbClient.ConnectResult.PairingRequired -> break
                        is AdbClient.ConnectResult.Error -> Unit
                    }
                    if (connected) break
                }
                if (connected) {
                    val friendly = friendlyName(last)
                    prefs.saveLastDevice(friendly)
                    _ui.value = _ui.value.copy(connectingTo = null, lastConnected = friendly)
                    _events.emit(HomeEvent.Connected(friendly))
                } else {
                    _ui.value = _ui.value.copy(connectingTo = null)
                }
            }
        }
        // Learn the TV MAC so Wake-on-LAN works later.
        viewModelScope.launch {
            if (adb.isConnected) {
                adb.getMacAddress()?.let { prefs.saveLastMac(it) }
            }
        }
    }

    /** Wake the last TV with a Wake-on-LAN magic packet. */
    fun wakeLastTv() {
        viewModelScope.launch {
            val mac = prefs.loadLastMac()
            if (mac == null) {
                _events.emit(HomeEvent.Error("No TV MAC saved yet — connect to the TV once first."))
                return@launch
            }
            val ok = com.tvtools.app.util.Wol.send(mac)
            _events.emit(
                HomeEvent.Error(
                    if (ok) "Wake packet sent to $mac — give the TV a few seconds, then tap it."
                    else "Couldn't send the wake packet (is Wi-Fi on?)"
                )
            )
        }
    }

    fun startDiscovery() {
        discoveryJob?.cancel()
        _ui.value = _ui.value.copy(devices = emptyList(), scanning = true)
        discoveryJob = viewModelScope.launch {
            discovery.discover(scanSubnetToo = true)
                .catch { _ui.value = _ui.value.copy(scanning = false) }
                .collect { event ->
                    _ui.value = _ui.value.copy(devices = event.devices, scanning = event.scanning)
                }
        }
    }

    fun setShowAll(showAll: Boolean) {
        _ui.value = _ui.value.copy(showAll = showAll)
    }

    fun connect(device: TvDevice) {
        if (device.pairing) {
            _events.tryEmit(HomeEvent.PairingRequested(device))
            return
        }
        _ui.value = _ui.value.copy(connectingTo = device)
        viewModelScope.launch {
            when (val result = adb.connect(device.host, device.port)) {
                AdbClient.ConnectResult.Success -> {
                    val friendly = friendlyName(device)
                    prefs.saveLastDevice(friendly)
                    _ui.value = _ui.value.copy(connectingTo = null, lastConnected = friendly)
                    _events.emit(HomeEvent.Connected(friendly))
                }
                AdbClient.ConnectResult.PairingRequired -> {
                    _ui.value = _ui.value.copy(connectingTo = null)
                    _events.emit(HomeEvent.PairingRequested(findPairingAdvert(device.host) ?: device))
                }
                is AdbClient.ConnectResult.Error -> {
                    _ui.value = _ui.value.copy(connectingTo = null)
                    _events.emit(
                        HomeEvent.Error(
                            "Could not connect to ${device.host}:${device.port}\n${result.message}\n\n" +
                                "Tip: check the TV for an \"Allow debugging\" prompt and try again."
                        )
                    )
                }
            }
        }
    }

    fun connectManual(host: String, port: Int) {
        val device = TvDevice(name = host, host = host, port = port, proto = DeviceProto.TCP)
        connect(device)
    }

    /** Find the advertised pairing port for a host (if the TV is broadcasting one). */
    fun findPairingAdvert(host: String): TvDevice? =
        _ui.value.devices.firstOrNull { it.host == host && it.pairing }

    /** Find the advertised TLS connect port for a host (available after pairing). */
    fun findConnectAdvert(host: String): TvDevice? =
        _ui.value.devices.firstOrNull { it.host == host && !it.pairing && it.proto == DeviceProto.TLS }

    fun pairThenConnect(host: String, pairingPort: Int, code: String) {
        _ui.value = _ui.value.copy(connectingTo = TvDevice(host, host, pairingPort, DeviceProto.TLS))
        viewModelScope.launch {
            val pairResult = adb.pair(host, pairingPort, code)
            if (pairResult.isFailure) {
                _ui.value = _ui.value.copy(connectingTo = null)
                _events.emit(
                    HomeEvent.Error(
                        "Pairing failed: ${pairResult.exceptionOrNull()?.message ?: "wrong code?"}\n\n" +
                            "On the TV: Developer options → Wireless debugging → \"Pair device with pairing code\"."
                    )
                )
                return@launch
            }
            // Paired! Find the connect port via mDNS (may take a moment to appear).
            var target = findConnectAdvert(host)
            var waited = 0
            while (target == null && waited < 6_000) {
                kotlinx.coroutines.delay(500)
                waited += 500
                target = findConnectAdvert(host)
            }
            if (target == null) {
                _ui.value = _ui.value.copy(connectingTo = null)
                _events.emit(HomeEvent.Error("Paired, but the TV didn't advertise a connection port. Pull to refresh and connect again."))
                return@launch
            }
            when (val result = adb.connect(target.host, target.port)) {
                AdbClient.ConnectResult.Success -> {
                    val friendly = friendlyName(target)
                    prefs.saveLastDevice(friendly)
                    _ui.value = _ui.value.copy(connectingTo = null, lastConnected = friendly)
                    _events.emit(HomeEvent.Connected(friendly))
                }
                else -> {
                    _ui.value = _ui.value.copy(connectingTo = null)
                    _events.emit(HomeEvent.Error("Paired successfully but connecting failed. Try tapping the device again."))
                }
            }
        }
    }

    /** Ask the freshly connected TV for its model name, e.g. "SmartTV". */
    private suspend fun friendlyName(device: TvDevice): TvDevice {
        val model = try {
            adb.exec("getprop ro.product.model", timeoutMs = 10_000).trim()
        } catch (_: Exception) {
            ""
        }
        return if (model.isNotBlank() && model != "unknown") device.copy(name = model) else device
    }

    override fun onCleared() {
        discoveryJob?.cancel()
    }
}
