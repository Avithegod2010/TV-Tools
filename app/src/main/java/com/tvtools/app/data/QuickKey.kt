package com.tvtools.app.data

import androidx.annotation.StringRes
import com.tvtools.app.R

/** A remote key that can be pinned to the quick-key strip. */
data class QuickKey(
    val id: String,
    @StringRes val label: Int,
    val keyCode: Int,
)

/**
 * Catalog of keys the user can pin. Key codes are Android `KeyEvent` values
 * sent through `input keyevent`.
 */
object QuickKeys {

    val catalog = listOf(
        QuickKey("back", R.string.back, 4),
        QuickKey("home", R.string.home, 3),
        QuickKey("menu", R.string.menu, 82),
        QuickKey("ok", R.string.ok, 23),
        QuickKey("vol_up", R.string.quick_volume_up, 24),
        QuickKey("vol_down", R.string.quick_volume_down, 25),
        QuickKey("mute", R.string.mute, 164),
        QuickKey("play", R.string.play_pause, 85),
        QuickKey("next", R.string.quick_next, 87),
        QuickKey("prev", R.string.quick_previous, 88),
        QuickKey("search", R.string.search, 84),
        QuickKey("info", R.string.info, 165),
        QuickKey("captions", R.string.subtitles, 175),
        QuickKey("stop", R.string.stop, 86),
        QuickKey("power", R.string.power, 26),
        QuickKey("sleep", R.string.sleep, 223),
        QuickKey("settings", R.string.settings, 176),
        QuickKey("assistant", R.string.assistant, 219),
        QuickKey("tv_input", R.string.tv_input, 178),
    )

    val defaultIds = listOf("back", "home", "play", "mute", "search")

    fun byId(id: String): QuickKey? = catalog.firstOrNull { it.id == id }
}

internal fun decodeQuickKeyIds(raw: String): List<String> =
    if (raw.isBlank()) emptyList()
    else raw.split(',').map { it.trim() }.filter { it.isNotBlank() }

internal fun encodeQuickKeyIds(ids: List<String>): String = ids.joinToString(",")
