package com.tvtools.app.adb

import com.tvtools.app.data.Prefs
import com.tvtools.app.data.DeviceProto
import com.tvtools.app.data.SettingsRepository
import com.tvtools.app.data.TvDevice
import com.tvtools.app.util.Wol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

internal enum class ConnectionStatus {
    IDLE, NO_NETWORK, NETWORK_CHANGED, RECONNECTING, UNAVAILABLE, AUTHORIZATION_REQUIRED, CONNECTED,
}

/** Owns the one foreground watchdog and one bounded recovery sequence for the app. */
internal class ConnectionRecovery(
    private val adb: AdbClient,
    private val networks: LanNetworkMonitor,
    private val discovery: DeviceDiscovery,
    private val prefs: Prefs,
    settings: SettingsRepository,
    foreground: StateFlow<Boolean>,
    scope: CoroutineScope,
) {
    private val _status = MutableStateFlow(ConnectionStatus.IDLE)
    val status = _status.asStateFlow()
    private val _initialConnection = Channel<TvDevice>(capacity = 1)
    val initialConnection = _initialConnection.receiveAsFlow()

    private val attempt = Mutex()
    private var initialAttempt = true

    init {
        scope.launch { networks.current.collect { adb.dropUnhealthyConnection() } }
        scope.launch {
            val options = settings.state.map { Triple(it.onboardingDone, it.autoConnect, it.wakeOnWifi) }
                .distinctUntilChanged()
            combine(foreground, networks.current, options) { active, lan, preferences ->
                Triple(active && preferences.first, lan, preferences.second to preferences.third)
            }.collectLatest { (active, lan, reconnect) ->
                if (active) watch(lan, autoConnect = reconnect.first, wakeOnWifi = reconnect.second)
            }
        }
    }

    private suspend fun watch(lan: LanNetwork?, autoConnect: Boolean, wakeOnWifi: Boolean) {
        var tried = false
        var healthyChecks = 0
        while (true) {
            if (adb.isConnected) {
                initialAttempt = false
                val active = adb.connections.value
                if (adb.ping()) {
                    _status.value = ConnectionStatus.CONNECTED
                    if (++healthyChecks >= 2) tried = false
                    withTimeoutOrNull(15_000) { adb.connections.first { it != active } }
                    continue
                }
            }
            adb.dropUnhealthyConnection()
            healthyChecks = 0
            when {
                lan == null -> _status.value = ConnectionStatus.NO_NETWORK
                !adb.mayResumeSavedDevice || (!autoConnect && adb.previousDevice == null) ->
                    _status.value = ConnectionStatus.IDLE
                !tried -> {
                    tried = true
                    val target = target()
                    if (target != null) {
                        val openOnSuccess = initialAttempt && adb.previousDevice == null
                        initialAttempt = false
                        if (recover(target, lan, automatically = true, wakeOnWifi = wakeOnWifi)) {
                            if (openOnSuccess) _initialConnection.trySend(target)
                            continue
                        }
                    } else {
                        _status.value = ConnectionStatus.IDLE
                    }
                }
                _status.value == ConnectionStatus.CONNECTED -> _status.value = ConnectionStatus.UNAVAILABLE
            }
            // The next foreground entry, topology change or explicit connection grants a new budget.
            adb.connections.filterNotNull().first()
        }
    }

    suspend fun reconnectManually(): TvDevice? {
        val lan = networks.current.value
        if (lan == null) {
            _status.value = ConnectionStatus.NO_NETWORK
            return null
        }
        val target = target() ?: return null
        return target.takeIf { recover(it, lan, automatically = false, wakeOnWifi = false) }
    }

    private suspend fun target(): TvDevice? {
        val previous = adb.previousDevice
        val last = prefs.loadLastDevice()
        if (previous == null || last?.host == previous.preferencesHost) return last
        return prefs.loadDevices().firstOrNull { it.host == previous.preferencesHost }
            ?: TvDevice(previous.preferencesHost, previous.host, previous.port, DeviceProto.TCP)
    }

    private suspend fun recover(
        target: TvDevice,
        lan: LanNetwork,
        automatically: Boolean,
        wakeOnWifi: Boolean,
    ): Boolean {
        if (!attempt.tryLock()) return false
        val revision = adb.recoveryRevision
        try {
            val previousLan = adb.previousNetwork
            if (previousLan != null && !lan.isSameLanAs(previousLan)) {
                _status.value = ConnectionStatus.NETWORK_CHANGED
                return false
            }
            val directEndpoint = target.takeIf { lan.containsHost(it.host) }
            _status.value = ConnectionStatus.RECONNECTING
            val backoff = if (automatically) longArrayOf(600, 1_500, 4_000) else longArrayOf(0)
            for ((index, waitMs) in backoff.withIndex()) {
                delay(waitMs)
                if (adb.recoveryRevision != revision || networks.current.value != lan) return false
                if (adb.isConnected) return false

                if (index == 1 && wakeOnWifi && directEndpoint != null) {
                    prefs.loadLastMac()?.let { Wol.send(it) }
                }
                // A matching prefix and gateway after rejoin can also describe an unrelated LAN.
                // Only follow a changed mDNS endpoint on the same Android network handle.
                val endpoint = if (previousLan?.handle == lan.handle && target.discoveryId != null) {
                    withTimeoutOrNull(2_000) {
                        discovery.discover(scanSubnetToo = false).map {
                            matchingAdvert(target, it.devices)
                        }.filterNotNull().first()
                    }
                } else null
                val selected = endpoint ?: directEndpoint ?: continue
                val result = adb.connectKnown(
                    AdbClient.ConnectedDevice(selected.host, selected.port, target.host),
                    expectedRevision = revision,
                )
                when (result) {
                    AdbClient.ConnectResult.Success -> {
                        _status.value = ConnectionStatus.CONNECTED
                        return true
                    }
                    AdbClient.ConnectResult.PairingRequired -> {
                        _status.value = ConnectionStatus.AUTHORIZATION_REQUIRED
                        return false
                    }
                    is AdbClient.ConnectResult.Error -> Unit
                }
            }
            _status.value = ConnectionStatus.UNAVAILABLE
            return false
        } finally {
            if (_status.value == ConnectionStatus.RECONNECTING) _status.value = ConnectionStatus.IDLE
            attempt.unlock()
        }
    }
}

internal fun matchingAdvert(target: TvDevice, discovered: List<TvDevice>): TvDevice? {
    val identity = target.discoveryId ?: return null
    return discovered.filter { !it.pairing && it.proto == target.proto && it.discoveryId == identity }
        .distinctBy { it.host to it.port }
        .singleOrNull()
}
