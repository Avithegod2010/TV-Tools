package com.tvtools.app.data

import java.net.URLDecoder
import java.net.URLEncoder

/** Per-TV look and remote layout. */
data class DeviceProfile(
    val accent: Accent = Accent.GREEN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val remoteSkin: RemoteSkin = RemoteSkin.CIRCULAR,
    val fontScale: Float = 1f,
    val density: UiDensity = UiDensity.COMFORTABLE,
    val highContrast: Boolean = false,
    val colorBlind: ColorBlindMode = ColorBlindMode.OFF,
    val reducedMotion: Boolean = false,
    val expressiveShapes: Boolean = true,
    val soundPack: SoundPack = SoundPack.SOFT,
    val hapticStrength: HapticStrength = HapticStrength.MEDIUM,
    val kidsTheme: Boolean = false,
    val layout: RemoteLayout = RemoteLayout(),
) {
    companion object {
        fun from(settings: AppSettings): DeviceProfile = DeviceProfile(
            accent = settings.accent,
            themeMode = settings.themeMode,
            remoteSkin = settings.remoteSkin,
            fontScale = settings.fontScale,
            density = settings.uiDensity,
            highContrast = settings.highContrast,
            colorBlind = settings.colorBlind,
            reducedMotion = settings.reducedMotion,
            expressiveShapes = settings.expressiveShapes,
            soundPack = settings.soundPack,
            hapticStrength = settings.hapticStrength,
            kidsTheme = settings.kidsTheme,
        )
    }
}

/**
 * Compact, URL-encoded profile format stored as one line per TV.
 * Kept dependency-free so it is JVM-testable and can be copied with a theme export.
 */
object DeviceProfileCodec {

    fun encode(profile: DeviceProfile): String {
        val layout = profile.layout
        val layoutEncoded = listOf(
            layout.columns.toString(),
            layout.dpadSize.toString(),
            layout.oneHanded.toString(),
            layout.expressive.toString(),
            enc(layout.hidden.joinToString(",")),
            enc(layout.quickOrder.joinToString(",")),
        ).joinToString("|")

        val fields = linkedMapOf(
            "v" to "1",
            "accent" to profile.accent.name,
            "theme" to profile.themeMode.name,
            "skin" to profile.remoteSkin.name,
            "font" to profile.fontScale.toString(),
            "density" to profile.density.name,
            "contrast" to profile.highContrast.toString(),
            "cb" to profile.colorBlind.name,
            "motion" to profile.reducedMotion.toString(),
            "shape" to profile.expressiveShapes.toString(),
            "sound" to profile.soundPack.name,
            "haptic" to profile.hapticStrength.name,
            "kids" to profile.kidsTheme.toString(),
            "layout" to layoutEncoded,
        )
        return fields.entries.joinToString(";") { "${it.key}=${enc(it.value)}" }
    }

    fun decode(raw: String): DeviceProfile? {
        if (raw.isBlank()) return null
        val map = raw.split(';').mapNotNull { piece ->
            val index = piece.indexOf('=')
            if (index <= 0) null else piece.substring(0, index) to dec(piece.substring(index + 1))
        }.toMap()
        if (map["v"] != "1") return null
        val layoutParts = map["layout"].orEmpty().split('|')
        val layout = if (layoutParts.size >= 6) {
            RemoteLayout(
                columns = layoutParts[0].toIntOrNull()?.coerceIn(1, 3) ?: 2,
                dpadSize = layoutParts[1].toIntOrNull()?.coerceIn(200, 340) ?: 260,
                oneHanded = layoutParts[2].toBooleanStrictOrNull() ?: false,
                expressive = layoutParts[3].toBooleanStrictOrNull() ?: true,
                hidden = dec(layoutParts[4]).split(',').filter { it.isNotBlank() }.toSet(),
                quickOrder = dec(layoutParts[5]).split(',').filter { it.isNotBlank() }
                    .ifEmpty { QuickKeys.defaultIds },
            )
        } else RemoteLayout()

        return DeviceProfile(
            accent = runCatching { Accent.valueOf(map["accent"] ?: "GREEN") }.getOrDefault(Accent.GREEN),
            themeMode = runCatching { ThemeMode.valueOf(map["theme"] ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM),
            remoteSkin = runCatching { RemoteSkin.valueOf(map["skin"] ?: "CIRCULAR") }
                .getOrDefault(RemoteSkin.CIRCULAR),
            fontScale = map["font"]?.toFloatOrNull()?.coerceIn(0.8f, 2f) ?: 1f,
            density = runCatching { UiDensity.valueOf(map["density"] ?: "COMFORTABLE") }
                .getOrDefault(UiDensity.COMFORTABLE),
            highContrast = map["contrast"]?.toBooleanStrictOrNull() ?: false,
            colorBlind = runCatching { ColorBlindMode.valueOf(map["cb"] ?: "OFF") }
                .getOrDefault(ColorBlindMode.OFF),
            reducedMotion = map["motion"]?.toBooleanStrictOrNull() ?: false,
            expressiveShapes = map["shape"]?.toBooleanStrictOrNull() ?: true,
            soundPack = runCatching { SoundPack.valueOf(map["sound"] ?: "SOFT") }
                .getOrDefault(SoundPack.SOFT),
            hapticStrength = runCatching { HapticStrength.valueOf(map["haptic"] ?: "MEDIUM") }
                .getOrDefault(HapticStrength.MEDIUM),
            kidsTheme = map["kids"]?.toBooleanStrictOrNull() ?: false,
            layout = layout,
        )
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun dec(value: String): String = URLDecoder.decode(value, "UTF-8")
}

/** Remote sections the user can hide, plus the quick-key layout. */
data class RemoteLayout(
    val columns: Int = 2,
    val dpadSize: Int = 260,
    val oneHanded: Boolean = false,
    val expressive: Boolean = true,
    val hidden: Set<String> = emptySet(),
    val quickOrder: List<String> = QuickKeys.defaultIds,
) {
    companion object {
        val sections = listOf(
            "media" to "Now playing & media keys",
            "quick" to "Quick key grid",
            "nav" to "Back / Home / Menu / channels",
            "app" to "App shortcuts",
            "volume" to "Volume & power",
        )
    }
}
