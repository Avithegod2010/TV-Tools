package com.tvtools.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.data.DeviceProto
import com.tvtools.app.data.Macro
import com.tvtools.app.data.NowPlaying
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
import java.io.File

data class HomeUiState(
    val devices: List<TvDevice> = emptyList(),
    val savedDevices: List<TvDevice> = emptyList(),
    val scanning: Boolean = true,
    val lastConnected: TvDevice? = null,
    val connectingTo: TvDevice? = null,
    val showAll: Boolean = false,
    val lastThumbPath: String? = null,
    val batchStatus: Map<String, BatchState> = emptyMap(),
    val pinnedHost: String? = null,
    val nowPlaying: NowPlaying? = null,
    val macros: List<Macro> = emptyList(),
    val previewBusy: Boolean = false,
) {
    val visibleDevices: List<TvDevice>
        get() = if (showAll) devices
        else devices.filter { !it.pairing } // main filter hides pairing-only adverts

    /** Pinned TV wins; otherwise fall back to the last connected one. */
    val heroDevice: TvDevice?
        get() = savedDevices.firstOrNull { it.host == pinnedHost } ?: lastConnected
}

sealed interface HomeEvent {
    data class Connected(val device: TvDevice) : HomeEvent
    data class Error(val message: String) : HomeEvent
    data class Message(val text: String) : HomeEvent
    data class PairingRequested(val device: TvDevice) : HomeEvent
    data object PairSuccessConnect : HomeEvent
}

/** Result of the last batch check for a saved TV. */
enum class BatchState { CHECKING, ONLINE, OFFLINE }

class HomeViewModel : ViewModel() {

    private val adb = AppServices.adb
    private val prefs = AppServices.prefs
    private val discovery = AppServices.discovery

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private var discoveryJob: Job? = null
    internal val connectionStatus = AppServices.recovery.status
    internal val initialConnection = AppServices.recovery.initialConnection

    init {
        viewModelScope.launch {
            val last = prefs.loadLastDevice()
            val saved = prefs.loadDevices()
            val pinned = prefs.loadPinnedHost()
            val hero = saved.firstOrNull { it.host == pinned } ?: last
            _ui.value = _ui.value.copy(
                lastConnected = last,
                savedDevices = saved,
                pinnedHost = pinned,
                macros = prefs.loadMacros(),
                lastThumbPath = hero?.let { thumbFile(it.host)?.takeIf { f -> f.exists() }?.absolutePath },
            )
        }
    }

    fun reconnectLast() {
        if (_ui.value.connectingTo != null) return
        viewModelScope.launch {
            val device = AppServices.recovery.reconnectManually() ?: return@launch
            if (acceptInitialConnection(device)) _events.emit(HomeEvent.Connected(device))
        }
    }

    internal suspend fun acceptInitialConnection(device: TvDevice): Boolean {
        if (adb.connectedDevice?.preferencesHost != device.host) return false
        applyDeviceAccent(device.host)
        return adb.connectedDevice?.preferencesHost == device.host
    }

    /** Remove a saved TV from the dashboard (does not affect the last-connected card). */
    fun removeSavedDevice(host: String) {
        viewModelScope.launch {
            prefs.removeDevice(host)
            _ui.value = _ui.value.copy(savedDevices = prefs.loadDevices())
        }
    }

    /** Pin a TV to the dashboard hero card. Pass null to clear the pin. */
    fun pinDevice(host: String?) {
        viewModelScope.launch {
            prefs.savePinnedHost(host)
            val saved = prefs.loadDevices()
            val hero = saved.firstOrNull { it.host == host } ?: prefs.loadLastDevice()
            _ui.value = _ui.value.copy(
                pinnedHost = host,
                savedDevices = saved,
                lastThumbPath = hero?.let { thumbFile(it.host)?.takeIf { f -> f.exists() }?.absolutePath },
                nowPlaying = null,
            )
        }
    }

    /** Move a saved TV left (-1) or right (+1) in the dashboard order. */
    fun moveSavedDevice(host: String, delta: Int) {
        viewModelScope.launch {
            prefs.moveSavedDevice(host, delta)
            _ui.value = _ui.value.copy(savedDevices = prefs.loadDevices())
        }
    }

    fun refreshMacros() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(macros = prefs.loadMacros())
        }
    }

    /**
     * Manual live preview for the hero card: connect if needed, grab one frame,
     * read the media session, then restore the previously connected TV.
     */
    fun refreshOverview(device: TvDevice) {
        if (_ui.value.previewBusy) return
        _ui.value = _ui.value.copy(previewBusy = true)
        viewModelScope.launch {
            val origin = adb.connectedDevice
            val switched = origin?.host != device.host
            val result = runCatching {
                if (switched) {
                    val connected = adb.connect(device.host, device.port)
                    if (connected != AdbClient.ConnectResult.Success) {
                        error("The TV is offline — wake it and retry.")
                    }
                }
                val file = thumbFile(device.host) ?: error("No cache folder")
                adb.screencap(file)
                val track = runCatching { adb.nowPlaying() }.getOrNull()
                file to track
            }
            if (switched && origin != null) {
                runCatching { adb.connect(origin.host, origin.port) }
            }
            result.onSuccess { (file, track) ->
                _ui.value = _ui.value.copy(
                    lastThumbPath = file.absolutePath,
                    nowPlaying = track,
                    previewBusy = false,
                )
            }.onFailure { error ->
                _ui.value = _ui.value.copy(previewBusy = false)
                _events.emit(HomeEvent.Error("Preview failed: ${error.message}"))
            }
        }
    }

    /** Replay a macro on the connected TV. */
    fun runMacro(macro: Macro) {
        viewModelScope.launch {
            if (!adb.isConnected) {
                _events.emit(HomeEvent.Error("Connect to a TV first, then tap the macro."))
                return@launch
            }
            runCatching {
                for (step in macro.steps) {
                    if (step.delayMs > 0) kotlinx.coroutines.delay(step.delayMs)
                    adb.keyEventExact(step.keyCode)
                }
            }.onSuccess {
                _events.emit(HomeEvent.Message("Ran \"${macro.name}\""))
            }.onFailure {
                _events.emit(HomeEvent.Error("Macro failed: ${it.message}"))
            }
        }
    }

    /** Wake the TV (WOL if a MAC is stored) and retry connecting for ~25s. */
    fun wakeAndReconnect() {
        if (_ui.value.connectingTo != null) return
        viewModelScope.launch {
            val last = prefs.loadLastDevice()
            if (last == null) {
                _events.emit(HomeEvent.Error("No TV saved yet — connect once first."))
                return@launch
            }
            val mac = prefs.loadLastMac()
            if (mac != null) com.tvtools.app.util.Wol.send(mac)
            _ui.value = _ui.value.copy(connectingTo = last)
            repeat(8) { attempt ->
                kotlinx.coroutines.delay(if (attempt == 0) 1500 else 3000)
                if (adb.isConnected) {
                    val friendly = friendlyName(last)
                    prefs.saveLastDevice(friendly)
                    _ui.value = _ui.value.copy(connectingTo = null, lastConnected = friendly)
                    _events.emit(HomeEvent.Connected(friendly))
                    return@launch
                }
                when (adb.connect(last.host, last.port)) {
                    AdbClient.ConnectResult.Success -> {
                        val friendly = friendlyName(last)
                        prefs.saveLastDevice(friendly)
                        _ui.value = _ui.value.copy(connectingTo = null, lastConnected = friendly)
                        _events.emit(HomeEvent.Connected(friendly))
                        return@launch
                    }
                    AdbClient.ConnectResult.PairingRequired -> {
                        _ui.value = _ui.value.copy(connectingTo = null)
                        _events.emit(HomeEvent.Error("The TV needs pairing again. Tap the TV to pair."))
                        return@launch
                    }
                    is AdbClient.ConnectResult.Error -> Unit
                }
            }
            _ui.value = _ui.value.copy(connectingTo = null)
            _events.emit(HomeEvent.Error("Couldn't reach the TV. Make sure it's on and ADB/Wireless debugging is enabled."))
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
        if (discoveryJob?.isActive == true) {
            discovery.refreshSubnet()
            return
        }
        _ui.value = _ui.value.copy(devices = emptyList(), scanning = true)
        discoveryJob = viewModelScope.launch {
            discovery.discover(scanSubnetToo = true)
                .catch { _ui.value = _ui.value.copy(scanning = false) }
                .collect { event ->
                    _ui.value = _ui.value.copy(devices = event.devices, scanning = event.scanning)
                    // Remember advertised pairing ports so re-pairing only needs the code.
                    event.devices.filter { it.pairing }.forEach { advert ->
                        val key = "${advert.host}:${advert.port}"
                        if (pairingPortsSaved.add(key)) {
                            viewModelScope.launch { prefs.savePairingPort(advert.host, advert.port) }
                        }
                    }
                }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _ui.value = _ui.value.copy(devices = emptyList(), scanning = false)
    }

    private val pairingPortsSaved = mutableSetOf<String>()

    fun setShowAll(showAll: Boolean) {
        _ui.value = _ui.value.copy(showAll = showAll)
    }

    fun connect(device: TvDevice) {
        if (_ui.value.connectingTo != null) return
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
                    prefs.saveDevice(friendly)
                    applyDeviceAccent(device.host)
                    _ui.value = _ui.value.copy(
                        connectingTo = null,
                        lastConnected = friendly,
                        savedDevices = prefs.loadDevices(),
                    )
                    _events.emit(HomeEvent.Connected(friendly))
                }
                AdbClient.ConnectResult.PairingRequired -> {
                    _ui.value = _ui.value.copy(connectingTo = null)
                    _events.emit(HomeEvent.PairingRequested(findPairingAdvert(device.host) ?: device))
                }
                is AdbClient.ConnectResult.Error -> {
                    _ui.value = _ui.value.copy(connectingTo = null)
                    _events.emit(HomeEvent.Error(result.message))
                }
            }
        }
    }

    fun connectManual(host: String, port: Int) {
        val device = TvDevice(name = host, host = host, port = port, proto = DeviceProto.TCP)
        connect(device)
    }

    /**
     * Probes every saved TV, connects to the responsive ones in turn (so names and
     * ports refresh) and leaves the last responsive TV connected. One ADB socket
     * means simultaneous sessions aren't possible, but this gives a live status
     * map of the whole house in one tap.
     */
    fun connectAllSaved() {
        val devices = _ui.value.savedDevices
        if (devices.isEmpty()) {
            _events.tryEmit(HomeEvent.Error("No saved TVs yet — connect to one first."))
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(batchStatus = devices.associate { it.host to BatchState.CHECKING })
            var online = 0
            var lastOnline: TvDevice? = null
            for (device in devices) {
                val reachable = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    tcpProbe(device.host, device.port)
                }
                if (!reachable) {
                    _ui.value = _ui.value.copy(
                        batchStatus = _ui.value.batchStatus + (device.host to BatchState.OFFLINE)
                    )
                    continue
                }
                online++
                val result = adb.connect(device.host, device.port)
                if (result == AdbClient.ConnectResult.Success) {
                    val friendly = friendlyName(device)
                    prefs.saveLastDevice(friendly)
                    prefs.saveDevice(friendly)
                    lastOnline = friendly
                    _ui.value = _ui.value.copy(
                        batchStatus = _ui.value.batchStatus + (device.host to BatchState.ONLINE),
                        lastConnected = friendly,
                        savedDevices = prefs.loadDevices(),
                    )
                } else {
                    _ui.value = _ui.value.copy(
                        batchStatus = _ui.value.batchStatus + (device.host to BatchState.OFFLINE)
                    )
                }
            }
            val text = when {
                lastOnline == null -> "No saved TV answered. Check that the TVs are on and on this Wi-Fi."
                online == 1 -> "1 TV online — connected to ${lastOnline.name}."
                else -> "$online TVs online — currently connected to ${lastOnline.name}."
            }
            _events.emit(HomeEvent.Message(text))
        }
    }

    private fun tcpProbe(host: String, port: Int): Boolean = try {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), 900) }
        true
    } catch (_: Exception) {
        false
    }

    /** Find the advertised pairing port for a host (if the TV is broadcasting one). */
    fun findPairingAdvert(host: String): TvDevice? =
        _ui.value.devices.firstOrNull { it.host == host && it.pairing }

    /** Find the advertised TLS connect port for a host (available after pairing). */
    fun findConnectAdvert(host: String): TvDevice? =
        _ui.value.devices.firstOrNull { it.host == host && !it.pairing && it.proto == DeviceProto.TLS }

    fun pairThenConnect(host: String, pairingPort: Int, code: String) {
        if (_ui.value.connectingTo != null) return
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
            // Paired! Remember the port so re-pairing is one tap next time.
            prefs.savePairingPort(host, pairingPort)
            // Find the connect port via mDNS (may take a moment to appear).
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
                    prefs.saveDevice(friendly)
                    _ui.value = _ui.value.copy(
                        connectingTo = null,
                        lastConnected = friendly,
                        savedDevices = prefs.loadDevices(),
                    )
                    _events.emit(HomeEvent.Connected(friendly))
                }
                else -> {
                    _ui.value = _ui.value.copy(connectingTo = null)
                    _events.emit(HomeEvent.Error("Paired successfully but connecting failed. Try tapping the device again."))
                }
            }
        }
    }

    private suspend fun applyDeviceAccent(host: String) {
        prefs.loadDeviceAccent(host)?.let { name ->
            runCatching { com.tvtools.app.data.Accent.valueOf(name) }
                .getOrNull()?.let { AppServices.settings.setAccent(it) }
        }
    }

    /** Cached live thumbnail file for a TV. */
    fun thumbFile(host: String): File? {
        return try {
            val dir = File(TvToolsApp.instance.filesDir, "tvthumbs").apply { mkdirs() }
            File(dir, "${host.hashCode()}.png")
        } catch (_: Exception) {
            null
        }
    }

    /** Ask the freshly connected TV for its model name, e.g. "SmartTV". */
    private suspend fun friendlyName(device: TvDevice): TvDevice {
        val identified = if (device.discoveryId == null) {
            _ui.value.devices.firstOrNull {
                !it.pairing && it.host == device.host && it.port == device.port && it.discoveryId != null
            }?.let { device.copy(proto = it.proto, discoveryId = it.discoveryId) } ?: device
        } else device
        val model = try {
            adb.exec("getprop ro.product.model", timeoutMs = 10_000).trim()
        } catch (_: Exception) {
            ""
        }
        return if (model.isNotBlank() && model != "unknown") identified.copy(name = model) else identified
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }
}
