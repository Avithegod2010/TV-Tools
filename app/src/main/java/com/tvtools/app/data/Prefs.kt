package com.tvtools.app.data

import android.content.Context
import android.net.Uri
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
    private val KEY_LAST_APK_DOCUMENT = stringPreferencesKey("last_apk_document")

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
        context.dataStore.edit {
            it.remove(KEY_NAME)
            it.remove(KEY_HOST)
            it.remove(KEY_PORT)
            it.remove(KEY_PROTO)
            it.remove(KEY_MAC)
        }
    }

    suspend fun saveLastApkDocument(uri: Uri) {
        context.dataStore.edit { it[KEY_LAST_APK_DOCUMENT] = uri.toString() }
    }

    suspend fun loadLastApkDocument(): Uri? =
        context.dataStore.data.first()[KEY_LAST_APK_DOCUMENT]?.let(Uri::parse)

    /** TV Wi-Fi MAC, used for Wake-on-LAN when the TV is fully off. */
    suspend fun saveLastMac(mac: String) {
        context.dataStore.edit { it[KEY_MAC] = mac }
    }

    suspend fun loadLastMac(): String? {
        return context.dataStore.data.first()[KEY_MAC]
    }

    // ---------------- multi-TV dashboard ----------------

    private val KEY_SAVED = stringSetPreferencesKey("saved_devices")
    private val KEY_SAVED_ORDER = stringPreferencesKey("saved_order")
    private val KEY_ROOM_ORDER = stringPreferencesKey("room_order")
    private val KEY_PINNED_HOST = stringPreferencesKey("pinned_host")

    suspend fun saveDevice(device: TvDevice) {
        context.dataStore.edit { p ->
            val entry = "${device.name}|${device.host}|${device.port}|${device.proto.name}"
            val others = p[KEY_SAVED].orEmpty()
                .filterNot { it.substringAfter('|').substringBefore('|') == device.host }
            p[KEY_SAVED] = (others + entry).toSet()
            val order = p[KEY_SAVED_ORDER].orEmpty()
                .split(',')
                .filter { it.isNotBlank() }
                .toMutableList()
            if (device.host !in order) {
                order.add(device.host)
                p[KEY_SAVED_ORDER] = order.joinToString(",")
            }
        }
    }

    suspend fun loadDevices(): List<TvDevice> {
        val prefs = context.dataStore.data.first()
        val order = prefs[KEY_SAVED_ORDER].orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
        return prefs[KEY_SAVED].orEmpty().mapNotNull { raw ->
            val parts = raw.split('|')
            if (parts.size < 4) return@mapNotNull null
            TvDevice(
                name = parts[0],
                host = parts[1],
                port = parts[2].toIntOrNull() ?: 5555,
                proto = runCatching { DeviceProto.valueOf(parts[3]) }.getOrDefault(DeviceProto.TCP),
            )
        }.sortedBy { device ->
            val index = order.indexOf(device.host)
            if (index < 0) Int.MAX_VALUE else index
        }
    }

    suspend fun removeDevice(host: String) {
        context.dataStore.edit { p ->
            p[KEY_SAVED] = p[KEY_SAVED].orEmpty()
                .filterNot { it.split('|').getOrNull(1) == host }
                .toSet()
            p[KEY_SAVED_ORDER] = p[KEY_SAVED_ORDER].orEmpty()
                .split(',')
                .filter { it.isNotBlank() && it != host }
                .joinToString(",")
        }
    }

    /** Move a saved TV one slot left (-1) or right (+1). */
    suspend fun moveSavedDevice(host: String, delta: Int) {
        context.dataStore.edit { p ->
            val order = p[KEY_SAVED_ORDER].orEmpty()
                .split(',')
                .filter { it.isNotBlank() }
                .toMutableList()
            val index = order.indexOf(host)
            if (index < 0) return@edit
            val target = (index + delta).coerceIn(0, order.lastIndex)
            if (target != index) {
                val item = order.removeAt(index)
                order.add(target, item)
                p[KEY_SAVED_ORDER] = order.joinToString(",")
            }
        }
    }

    // ---------------- per-TV accent ----------------

    private val KEY_ACCENTS = androidx.datastore.preferences.core.stringPreferencesKey("device_accents")

    suspend fun saveDeviceAccent(host: String, accentName: String) {
        context.dataStore.edit { p ->
            val current = p[KEY_ACCENTS].orEmpty().split('\n').filter { it.isNotBlank() }.toMutableList()
            current.removeAll { it.substringBefore('=') == host }
            current.add("$host=$accentName")
            p[KEY_ACCENTS] = current.joinToString("\n")
        }
    }

    suspend fun loadDeviceAccent(host: String): String? {
        val raw = context.dataStore.data.first()[KEY_ACCENTS].orEmpty()
        return raw.split('\n').firstOrNull { it.startsWith("$host=") }?.substringAfter('=')
    }

    // ---------------- pairing-port memory ----------------

    private val KEY_PAIRING_PORTS = stringPreferencesKey("pairing_ports")

    /** Remember the pairing port shown on the TV so re-pairing needs only the code. */
    suspend fun savePairingPort(host: String, port: Int) {
        context.dataStore.edit { p ->
            val current = p[KEY_PAIRING_PORTS].orEmpty()
                .split('\n')
                .filter { it.isNotBlank() }
                .filterNot { it.substringBeforeLast('=') == host }
                .toMutableList()
            current.add("$host=$port")
            p[KEY_PAIRING_PORTS] = current.joinToString("\n")
        }
    }

    suspend fun loadPairingPort(host: String): Int? {
        val raw = context.dataStore.data.first()[KEY_PAIRING_PORTS].orEmpty()
        return raw.split('\n')
            .firstOrNull { it.startsWith("$host=") }
            ?.substringAfterLast('=')
            ?.toIntOrNull()
    }

    // ---------------- rooms / groups ----------------

    private val KEY_ROOMS = stringPreferencesKey("device_rooms")

    suspend fun saveDeviceRoom(host: String, room: String) {
        context.dataStore.edit { p ->
            val current = p[KEY_ROOMS].orEmpty()
                .split('\n')
                .filter { it.isNotBlank() }
                .filterNot { it.substringBeforeLast('=') == host }
                .toMutableList()
            val clean = room.trim().replace("\n", " ").replace("=", "-")
            if (clean.isNotBlank()) current.add("$host=$clean")
            p[KEY_ROOMS] = current.joinToString("\n")
        }
    }

    suspend fun loadDeviceRooms(): Map<String, String> {
        val raw = context.dataStore.data.first()[KEY_ROOMS].orEmpty()
        return raw.split('\n')
            .filter { it.contains('=') }
            .associate { it.substringBeforeLast('=') to it.substringAfterLast('=') }
    }

    // ---------------- remote macros ----------------

    private val KEY_MACROS = stringPreferencesKey("remote_macros")

    /**
     * Stored one macro per line as `name|code:delay,code:delay`.
     * Names are sanitised so the separators stay unambiguous.
     */
    suspend fun saveMacro(macro: Macro) {
        val line = encodeMacroLine(macro) ?: return
        val name = line.substringBefore('|')
        context.dataStore.edit { p ->
            val lines = p[KEY_MACROS].orEmpty()
                .split('\n')
                .filter { it.isNotBlank() }
                .filterNot { it.substringBefore('|') == name }
                .toMutableList()
            lines.add(line)
            p[KEY_MACROS] = lines.joinToString("\n")
        }
    }

    suspend fun deleteMacro(name: String) {
        context.dataStore.edit { p ->
            p[KEY_MACROS] = p[KEY_MACROS].orEmpty()
                .split('\n')
                .filter { it.isNotBlank() }
                .filterNot { it.substringBefore('|') == name }
                .joinToString("\n")
        }
    }

    suspend fun loadMacros(): List<Macro> {
        val raw = context.dataStore.data.first()[KEY_MACROS].orEmpty()
        return raw.split('\n')
            .filter { it.isNotBlank() }
            .mapNotNull { decodeMacroLine(it) }
    }

    // ---------------- quick-key layout ----------------

    private val KEY_QUICK_KEYS = stringPreferencesKey("quick_keys")

    /** Null means "never customised" so first run still gets a sensible default row. */
    suspend fun loadQuickKeys(): List<String>? {
        val prefs = context.dataStore.data.first()
        if (KEY_QUICK_KEYS !in prefs) return null
        return decodeQuickKeyIds(prefs[KEY_QUICK_KEYS].orEmpty())
            .filter { QuickKeys.byId(it) != null }
    }

    suspend fun saveQuickKeys(ids: List<String>) {
        context.dataStore.edit { p -> p[KEY_QUICK_KEYS] = encodeQuickKeyIds(ids) }
    }

    // ---------------- Tools tab layout ----------------

    private val KEY_TOOL_FAVORITES = stringPreferencesKey("tool_favorites")

    /** Null means "never customised" so first run gets the default quick tools. */
    suspend fun loadToolFavorites(): List<String>? {
        val prefs = context.dataStore.data.first()
        if (KEY_TOOL_FAVORITES !in prefs) return null
        return prefs[KEY_TOOL_FAVORITES].orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    suspend fun saveToolFavorites(ids: List<String>) {
        context.dataStore.edit { p -> p[KEY_TOOL_FAVORITES] = ids.joinToString(",") }
    }

    // ---------------- per-TV look / remote layout ----------------

    private val KEY_PROFILES = stringPreferencesKey("device_profiles")

    suspend fun saveDeviceProfile(host: String, profile: DeviceProfile) {
        val line = "$host=${DeviceProfileCodec.encode(profile)}"
        context.dataStore.edit { p ->
            val lines = p[KEY_PROFILES].orEmpty()
                .split('\n')
                .filter { it.isNotBlank() }
                .filterNot { it.substringBefore('=') == host }
                .toMutableList()
            lines.add(line)
            p[KEY_PROFILES] = lines.joinToString("\n")
        }
    }

    suspend fun loadDeviceProfile(host: String): DeviceProfile? {
        val raw = context.dataStore.data.first()[KEY_PROFILES].orEmpty()
        val encoded = raw.split('\n')
            .firstOrNull { it.substringBefore('=') == host }
            ?.substringAfter('=', "")
            ?: return null
        return DeviceProfileCodec.decode(encoded)
    }

    suspend fun loadDeviceProfiles(): Map<String, DeviceProfile> {
        val raw = context.dataStore.data.first()[KEY_PROFILES].orEmpty()
        return raw.split('\n').mapNotNull { line ->
            val host = line.substringBefore('=', "")
            if (host.isBlank()) return@mapNotNull null
            val profile = DeviceProfileCodec.decode(line.substringAfter('=', ""))
                ?: return@mapNotNull null
            host to profile
        }.toMap()
    }

    suspend fun forgetDeviceProfile(host: String) {
        context.dataStore.edit { p ->
            p[KEY_PROFILES] = p[KEY_PROFILES].orEmpty()
                .split('\n')
                .filter { it.isNotBlank() }
                .filterNot { it.substringBefore('=') == host }
                .joinToString("\n")
        }
    }

    suspend fun savePinnedHost(host: String?) {
        context.dataStore.edit { p ->
            if (host.isNullOrBlank()) p.remove(KEY_PINNED_HOST) else p[KEY_PINNED_HOST] = host
        }
    }

    suspend fun loadPinnedHost(): String? = context.dataStore.data.first()[KEY_PINNED_HOST]

    // ---------------- room order ----------------

    suspend fun saveRoomOrder(rooms: List<String>) {
        context.dataStore.edit { p ->
            p[KEY_ROOM_ORDER] = rooms.filter { it.isNotBlank() }.joinToString("\u001F")
        }
    }

    suspend fun loadRoomOrder(): List<String> = context.dataStore.data.first()[KEY_ROOM_ORDER]
        .orEmpty()
        .split('\u001F')
        .filter { it.isNotBlank() }
}
