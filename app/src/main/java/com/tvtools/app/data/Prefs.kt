package com.tvtools.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
}
