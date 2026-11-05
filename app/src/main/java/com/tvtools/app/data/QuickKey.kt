package com.tvtools.app.data

/** A remote key that can be pinned to the quick-key strip. */
data class QuickKey(
    val id: String,
    val label: String,
    val keyCode: Int,
)

/**
 * Catalog of keys the user can pin. Key codes are Android `KeyEvent` values
 * sent through `input keyevent`.
 */
object QuickKeys {

    val catalog = listOf(
        QuickKey("back", "Back", 4),
        QuickKey("home", "Home", 3),
        QuickKey("menu", "Menu", 82),
        QuickKey("ok", "OK", 23),
        QuickKey("vol_up", "Vol +", 24),
        QuickKey("vol_down", "Vol −", 25),
        QuickKey("mute", "Mute", 164),
        QuickKey("play", "Play/pause", 85),
        QuickKey("next", "Next", 87),
        QuickKey("prev", "Prev", 88),
        QuickKey("search", "Search", 84),
        QuickKey("info", "Info", 165),
        QuickKey("captions", "Subtitles", 175),
        QuickKey("stop", "Stop", 86),
        QuickKey("power", "Power", 26),
        QuickKey("sleep", "Sleep", 223),
        QuickKey("settings", "Settings", 176),
        QuickKey("assistant", "Assistant", 219),
        QuickKey("tv_input", "TV input", 178),
    )

    val defaultIds = listOf("back", "home", "play", "mute", "search")

    fun byId(id: String): QuickKey? = catalog.firstOrNull { it.id == id }
}

internal fun decodeQuickKeyIds(raw: String): List<String> =
    if (raw.isBlank()) emptyList()
    else raw.split(',').map { it.trim() }.filter { it.isNotBlank() }

internal fun encodeQuickKeyIds(ids: List<String>): String = ids.joinToString(",")
