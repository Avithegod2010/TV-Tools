package com.tvtools.app.data

import android.content.Context
import com.tvtools.app.R

/**
 * A portable "look" that can be shared between phones as plain text.
 * Wraps the same compact profile codec used for per-TV profiles.
 */
object ThemePack {

    private const val PREFIX = "TVTOOLS-THEME:1:"

    fun encode(profile: DeviceProfile): String = PREFIX + DeviceProfileCodec.encode(profile)

    fun decode(raw: String): DeviceProfile? {
        val text = raw.trim()
        val start = text.indexOf(PREFIX)
        if (start < 0) return null
        val payload = text.substring(start + PREFIX.length).substringBefore('\n').trim()
        return DeviceProfileCodec.decode(payload)
    }

    /** An English summary for callers without an Android resource context. */
    fun summary(profile: DeviceProfile): String = listOf(
        profile.accent.label,
        profile.themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
        profile.remoteSkin.label,
        profile.density.name.lowercase().replaceFirstChar { it.uppercase() },
        "${(profile.fontScale * 100).toInt()}% text",
    ).joinToString(" · ")

    /** Human-readable one-line summary for localized profile lists. */
    fun summary(profile: DeviceProfile, context: Context): String = context.getString(
        R.string.theme_profile_summary,
        context.getString(profile.accent.labelRes),
        context.getString(profile.themeMode.labelRes),
        context.getString(profile.remoteSkin.labelRes),
        context.getString(profile.density.labelRes),
        (profile.fontScale * 100).toInt(),
    )
}
