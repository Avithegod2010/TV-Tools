package com.tvtools.app.data

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

    /** Human-readable one-line summary for profile lists. */
    fun summary(profile: DeviceProfile): String = buildString {
        append(profile.accent.label)
        append(" · ")
        append(profile.themeMode.name.lowercase().replaceFirstChar { it.uppercase() })
        append(" · ")
        append(profile.remoteSkin.label)
        append(" · ")
        append(profile.density.label)
        append(" · ")
        append("${(profile.fontScale * 100).toInt()}% text")
    }
}
