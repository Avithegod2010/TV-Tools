package com.tvtools.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "tvtools")

/** Persists the last successfully connected device for quick reconnect. */
class Prefs(private val context: Context) {

    private val KEY_NAME = stringPreferencesKey("last_name")
    private val KEY_HOST = stringPreferencesKey("last_host")
    private val KEY_PORT = intPreferencesKey("last_port")
    private val KEY_PROTO = stringPreferencesKey("last_proto")
    private val KEY_MAC = stringPreferencesKey("last_mac")

    suspend fun saveLastDevice(device: TvDevice) {
        context.dataStore.edit { p ->
            p[KEY_NAME] = device.name
            p[KEY_HOST] = device.host
            p[KEY_PORT] = device.port
            p[KEY_PROTO] = device.proto.name
        }
    }

    suspend fun loadLastDevice(): TvDevice? {
        val p = context.dataStore.data.first()
        val host = p[KEY_HOST] ?: return null
        val port = p[KEY_PORT] ?: return null
        return TvDevice(
            name = p[KEY_NAME] ?: host,
            host = host,
            port = port,
            proto = runCatching { DeviceProto.valueOf(p[KEY_PROTO] ?: "TCP") }.getOrDefault(DeviceProto.TCP),
        )
    }

    suspend fun clearLastDevice() {
        context.dataStore.edit { it.clear() }
    }

    /** TV Wi-Fi MAC, used for Wake-on-LAN when the TV is fully off. */
    suspend fun saveLastMac(mac: String) {
        context.dataStore.edit { it[KEY_MAC] = mac }
    }

    suspend fun loadLastMac(): String? {
        return context.dataStore.data.first()[KEY_MAC]
    }

    // ---------------- multi-TV dashboard ----------------

    private val KEY_SAVED = stringSetPreferencesKey("saved_devices")

    suspend fun saveDevice(device: TvDevice) {
        context.dataStore.edit { p ->
            val entry = "${device.name}|${device.host}|${device.port}|${device.proto.name}"
            val others = p[KEY_SAVED].orEmpty()
                .filterNot { it.substringAfter('|').substringBefore('|') == device.host }
            p[KEY_SAVED] = (others + entry).toSet()
        }
    }

    suspend fun loadDevices(): List<TvDevice> {
        return context.dataStore.data.first()[KEY_SAVED].orEmpty().mapNotNull { raw ->
            val parts = raw.split('|')
            if (parts.size < 4) return@mapNotNull null
            TvDevice(
                name = parts[0],
                host = parts[1],
                port = parts[2].toIntOrNull() ?: 5555,
                proto = runCatching { DeviceProto.valueOf(parts[3]) }.getOrDefault(DeviceProto.TCP),
            )
        }
    }

    suspend fun removeDevice(host: String) {
        context.dataStore.edit { p ->
            p[KEY_SAVED] = p[KEY_SAVED].orEmpty()
                .filterNot { it.split('|').getOrNull(1) == host }
                .toSet()
        }
    }
}
