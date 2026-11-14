package com.tvtools.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.settingsStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED, AUTO }

enum class Accent(val label: String) {
    GREEN("Green"),
    BLUE("Blue"),
    VIOLET("Violet"),
    ORANGE("Orange"),
    DYNAMIC("Dynamic"),
    TV("Match TV"),
}

enum class HapticStrength(val label: String) { LIGHT("Light"), MEDIUM("Medium"), STRONG("Strong") }

enum class RemoteSkin(val label: String) {
    CLASSIC("Classic remote"),
    CIRCULAR("Circular"),
    MINIMAL("Minimal"),
    GAMEPAD("Gamepad-first"),
    GLASS("Glass"),
    NEON("Neon"),
    MEDIA("Media first"),
}

enum class UiDensity(val label: String, val scale: Float) {
    COMPACT("Compact", 0.9f),
    COMFORTABLE("Comfortable", 1f),
    SPACIOUS("Spacious", 1.1f),
}

enum class SoundPack(val label: String) {
    SOFT("Soft"),
    MECHANICAL("Mechanical"),
    TV_BEEP("TV beep"),
}

enum class ColorBlindMode(val label: String) {
    OFF("Off"),
    DEUTERANOPIA("Deuteranopia"),
    PROTANOPIA("Protanopia"),
    TRITANOPIA("Tritanopia"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.GREEN,
    val haptics: Boolean = true,
    val hapticStrength: HapticStrength = HapticStrength.MEDIUM,
    val sounds: Boolean = false,
    val remoteSkin: RemoteSkin = RemoteSkin.CIRCULAR,
    val appIcon: String = "default",
    val tvColor: Int = 0xFF166D32.toInt(),
    val autoConnect: Boolean = true,
    val wakeEnabled: Boolean = false,
    val wakeMinutes: Int = 7 * 60,
    val wakeOnWifi: Boolean = false,
    val guestMode: Boolean = false,
    val bubble: Boolean = false,
    val onboardingDone: Boolean = false,
    val automationCache: Boolean = false,
    val automationScreenshot: Boolean = false,
    /** How many screenshots to keep in the local gallery. 0 = unlimited. */
    val screenshotLimit: Int = 25,
    val uiDensity: UiDensity = UiDensity.COMFORTABLE,
    /** Multiplier for font scaling, 0.8–2.0. */
    val fontScale: Float = 1f,
    val highContrast: Boolean = false,
    val colorBlind: ColorBlindMode = ColorBlindMode.OFF,
    val reducedMotion: Boolean = false,
    val expressiveShapes: Boolean = true,
    val soundPack: SoundPack = SoundPack.SOFT,
    val kidsTheme: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private val KEY_MODE = stringPreferencesKey("theme_mode")
    private val KEY_ACCENT = stringPreferencesKey("accent")
    private val KEY_HAPTICS = booleanPreferencesKey("haptics")
    private val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
    private val KEY_AUTO_CACHE = booleanPreferencesKey("auto_cache")
    private val KEY_AUTO_SHOT = booleanPreferencesKey("auto_screenshot")
    private val KEY_HAPTIC_STRENGTH = stringPreferencesKey("haptic_strength")
    private val KEY_SOUNDS = booleanPreferencesKey("sounds")
    private val KEY_SKIN = stringPreferencesKey("remote_skin")
    private val KEY_ICON = stringPreferencesKey("app_icon")
    private val KEY_TV_COLOR = androidx.datastore.preferences.core.intPreferencesKey("tv_color")
    private val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
    private val KEY_WAKE_ENABLED = booleanPreferencesKey("wake_enabled")
    private val KEY_WAKE_MINUTES = androidx.datastore.preferences.core.intPreferencesKey("wake_minutes")
    private val KEY_WAKE_ON_WIFI = booleanPreferencesKey("wake_on_wifi")
    private val KEY_GUEST = booleanPreferencesKey("guest_mode")
    private val KEY_BUBBLE = booleanPreferencesKey("overlay_bubble")
    private val KEY_SHOT_LIMIT = androidx.datastore.preferences.core.intPreferencesKey("screenshot_limit")
    private val KEY_DENSITY = stringPreferencesKey("ui_density")
    private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
    private val KEY_CONTRAST = booleanPreferencesKey("high_contrast")
    private val KEY_COLOR_BLIND = stringPreferencesKey("color_blind")
    private val KEY_REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    private val KEY_EXPRESSIVE = booleanPreferencesKey("expressive_shapes")
    private val KEY_SOUND_PACK = stringPreferencesKey("sound_pack")
    private val KEY_KIDS_THEME = booleanPreferencesKey("kids_theme")

    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            val p = context.settingsStore.data.first()
            _state.value = AppSettings(
                themeMode = runCatching { ThemeMode.valueOf(p[KEY_MODE] ?: "SYSTEM") }
                    .getOrDefault(ThemeMode.SYSTEM),
                accent = runCatching { Accent.valueOf(p[KEY_ACCENT] ?: "GREEN") }
                    .getOrDefault(Accent.GREEN),
                haptics = p[KEY_HAPTICS] ?: true,
                onboardingDone = p[KEY_ONBOARDING] ?: false,
                automationCache = p[KEY_AUTO_CACHE] ?: false,
                automationScreenshot = p[KEY_AUTO_SHOT] ?: false,
                hapticStrength = runCatching { HapticStrength.valueOf(p[KEY_HAPTIC_STRENGTH] ?: "MEDIUM") }
                    .getOrDefault(HapticStrength.MEDIUM),
                sounds = p[KEY_SOUNDS] ?: false,
                remoteSkin = runCatching { RemoteSkin.valueOf(p[KEY_SKIN] ?: "CIRCULAR") }
                    .getOrDefault(RemoteSkin.CIRCULAR),
                appIcon = p[KEY_ICON] ?: "default",
                tvColor = p[KEY_TV_COLOR] ?: 0xFF166D32.toInt(),
                autoConnect = p[KEY_AUTO_CONNECT] ?: true,
                wakeEnabled = p[KEY_WAKE_ENABLED] ?: false,
                wakeMinutes = p[KEY_WAKE_MINUTES] ?: (7 * 60),
                wakeOnWifi = p[KEY_WAKE_ON_WIFI] ?: false,
                guestMode = p[KEY_GUEST] ?: false,
                bubble = p[KEY_BUBBLE] ?: false,
                screenshotLimit = p[KEY_SHOT_LIMIT] ?: 25,
                uiDensity = runCatching { UiDensity.valueOf(p[KEY_DENSITY] ?: "COMFORTABLE") }
                    .getOrDefault(UiDensity.COMFORTABLE),
                fontScale = (p[KEY_FONT_SCALE] ?: 1f).coerceIn(0.8f, 2f),
                highContrast = p[KEY_CONTRAST] ?: false,
                colorBlind = runCatching { ColorBlindMode.valueOf(p[KEY_COLOR_BLIND] ?: "OFF") }
                    .getOrDefault(ColorBlindMode.OFF),
                reducedMotion = p[KEY_REDUCED_MOTION] ?: false,
                expressiveShapes = p[KEY_EXPRESSIVE] ?: true,
                soundPack = runCatching { SoundPack.valueOf(p[KEY_SOUND_PACK] ?: "SOFT") }
                    .getOrDefault(SoundPack.SOFT),
                kidsTheme = p[KEY_KIDS_THEME] ?: false,
            )
            if (_state.value.wakeEnabled) {
                AutomationScheduler.setWake(true, _state.value.wakeMinutes)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _state.value = _state.value.copy(themeMode = mode)
        scope.launch { context.settingsStore.edit { it[KEY_MODE] = mode.name } }
    }

    fun setAccent(accent: Accent) {
        _state.value = _state.value.copy(accent = accent)
        scope.launch { context.settingsStore.edit { it[KEY_ACCENT] = accent.name } }
    }

    fun setHaptics(enabled: Boolean) {
        _state.value = _state.value.copy(haptics = enabled)
        scope.launch { context.settingsStore.edit { it[KEY_HAPTICS] = enabled } }
    }

    fun setOnboardingDone() {
        _state.value = _state.value.copy(onboardingDone = true)
        scope.launch { context.settingsStore.edit { it[KEY_ONBOARDING] = true } }
    }

    fun setAutomationCache(enabled: Boolean) {
        _state.value = _state.value.copy(automationCache = enabled)
        AutomationScheduler.setCacheTrim(enabled)
        scope.launch { context.settingsStore.edit { it[KEY_AUTO_CACHE] = enabled } }
    }

    fun setAutomationScreenshot(enabled: Boolean) {
        _state.value = _state.value.copy(automationScreenshot = enabled)
        AutomationScheduler.setScreenshot(enabled)
        scope.launch { context.settingsStore.edit { it[KEY_AUTO_SHOT] = enabled } }
    }

    fun setHapticStrength(strength: HapticStrength) {
        _state.value = _state.value.copy(hapticStrength = strength)
        scope.launch { context.settingsStore.edit { it[KEY_HAPTIC_STRENGTH] = strength.name } }
    }

    fun setSounds(enabled: Boolean) {
        _state.value = _state.value.copy(sounds = enabled)
        scope.launch { context.settingsStore.edit { it[KEY_SOUNDS] = enabled } }
    }

    fun setRemoteSkin(skin: RemoteSkin) {
        _state.value = _state.value.copy(remoteSkin = skin)
        scope.launch { context.settingsStore.edit { it[KEY_SKIN] = skin.name } }
    }

    fun setAppIcon(icon: String) {
        _state.value = _state.value.copy(appIcon = icon)
        scope.launch { context.settingsStore.edit { it[KEY_ICON] = icon } }
    }

    fun setTvColor(argb: Int) {
        _state.value = _state.value.copy(tvColor = argb, accent = Accent.TV)
        scope.launch {
            context.settingsStore.edit {
                it[KEY_TV_COLOR] = argb
                it[KEY_ACCENT] = Accent.TV.name
            }
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        _state.value = _state.value.copy(autoConnect = enabled)
        scope.launch { context.settingsStore.edit { it[KEY_AUTO_CONNECT] = enabled } }
    }

    fun setWakeEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(wakeEnabled = enabled)
        AutomationScheduler.setWake(enabled, _state.value.wakeMinutes)
        scope.launch { context.settingsStore.edit { it[KEY_WAKE_ENABLED] = enabled } }
    }

    fun setWakeMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(0, 23 * 60 + 59)
        _state.value = _state.value.copy(wakeMinutes = clamped)
        if (_state.value.wakeEnabled) AutomationScheduler.setWake(true, clamped)
        scope.launch { context.settingsStore.edit { it[KEY_WAKE_MINUTES] = clamped } }
    }

    fun setWakeOnWifi(enabled: Boolean) {
        _state.value = _state.value.copy(wakeOnWifi = enabled)
        scope.launch { context.settingsStore.edit { it[KEY_WAKE_ON_WIFI] = enabled } }
    }

    fun setGuestMode(enabled: Boolean) {
        _state.value = _state.value.copy(guestMode = enabled)
        scope.launch { context.settingsStore.edit { it[KEY_GUEST] = enabled } }
    }

    fun setBubble(enabled: Boolean) {
        _state.value = _state.value.copy(bubble = enabled)
        scope.launch { context.settingsStore.edit { it[KEY_BUBBLE] = enabled } }
    }

    /** Keep only the newest [limit] screenshots (0 = keep every capture). */
    fun setScreenshotLimit(limit: Int) {
        val safe = limit.coerceAtLeast(0)
        _state.value = _state.value.copy(screenshotLimit = safe)
        scope.launch {
            context.settingsStore.edit { it[KEY_SHOT_LIMIT] = safe }
            if (safe > 0) ScreenshotStore.prune(context, safe)
        }
    }

    // ---------------- typography, contrast, motion, feedback ----------------

    fun setUiDensity(density: UiDensity) {
        _state.value = _state.value.copy(uiDensity = density)
        scope.launch { context.settingsStore.edit { it[KEY_DENSITY] = density.name } }
    }

    fun setFontScale(scale: Float) {
        val safe = scale.coerceIn(0.8f, 2f)
        _state.value = _state.value.copy(fontScale = safe)
        scope.launch { context.settingsStore.edit { it[KEY_FONT_SCALE] = safe } }
    }

    fun setHighContrast(enabled: Boolean) {
        _state.value = _state.value.copy(highContrast = enabled)
        scope.launch { context.settingsStore.edit { it[KEY_CONTRAST] = enabled } }
    }

    fun setColorBlind(mode: ColorBlindMode) {
        _state.value = _state.value.copy(colorBlind = mode)
        scope.launch { context.settingsStore.edit { it[KEY_COLOR_BLIND] = mode.name } }
    }

    fun setReducedMotion(enabled: Boolean) {
        _state.value = _state.value.copy(reducedMotion = enabled)
        scope.launch { context.settingsStore.edit { it[KEY_REDUCED_MOTION] = enabled } }
    }

    fun setExpressiveShapes(enabled: Boolean) {
        _state.value = _state.value.copy(expressiveShapes = enabled)
        scope.launch { context.settingsStore.edit { it[KEY_EXPRESSIVE] = enabled } }
    }

    fun setSoundPack(pack: SoundPack) {
        _state.value = _state.value.copy(soundPack = pack)
        scope.launch { context.settingsStore.edit { it[KEY_SOUND_PACK] = pack.name } }
    }

    fun setKidsTheme(enabled: Boolean) {
        _state.value = _state.value.copy(
            kidsTheme = enabled,
            guestMode = enabled,
            accent = if (enabled) Accent.ORANGE else _state.value.accent,
            fontScale = if (enabled) _state.value.fontScale.coerceAtLeast(1.15f) else _state.value.fontScale,
            expressiveShapes = if (enabled) true else _state.value.expressiveShapes,
        )
        scope.launch {
            context.settingsStore.edit {
                it[KEY_KIDS_THEME] = enabled
                it[KEY_GUEST] = enabled
                if (enabled) {
                    it[KEY_ACCENT] = Accent.ORANGE.name
                    it[KEY_FONT_SCALE] = _state.value.fontScale
                    it[KEY_EXPRESSIVE] = true
                }
            }
        }
    }

    // ---------------- per-TV look ----------------

    fun currentProfile(): DeviceProfile = DeviceProfile.from(_state.value)

    /** Apply a saved per-TV look and persist it as the live look. */
    fun applyProfile(profile: DeviceProfile) {
        val state = _state.value.copy(
            accent = profile.accent,
            themeMode = profile.themeMode,
            remoteSkin = profile.remoteSkin,
            fontScale = profile.fontScale,
            uiDensity = profile.density,
            highContrast = profile.highContrast,
            colorBlind = profile.colorBlind,
            reducedMotion = profile.reducedMotion,
            expressiveShapes = profile.expressiveShapes,
            soundPack = profile.soundPack,
            hapticStrength = profile.hapticStrength,
            kidsTheme = profile.kidsTheme,
            guestMode = profile.kidsTheme || _state.value.guestMode,
        )
        _state.value = state
        scope.launch {
            context.settingsStore.edit {
                it[KEY_ACCENT] = state.accent.name
                it[KEY_MODE] = state.themeMode.name
                it[KEY_SKIN] = state.remoteSkin.name
                it[KEY_FONT_SCALE] = state.fontScale
                it[KEY_DENSITY] = state.uiDensity.name
                it[KEY_CONTRAST] = state.highContrast
                it[KEY_COLOR_BLIND] = state.colorBlind.name
                it[KEY_REDUCED_MOTION] = state.reducedMotion
                it[KEY_EXPRESSIVE] = state.expressiveShapes
                it[KEY_SOUND_PACK] = state.soundPack.name
                it[KEY_HAPTIC_STRENGTH] = state.hapticStrength.name
                it[KEY_KIDS_THEME] = state.kidsTheme
                it[KEY_GUEST] = state.guestMode
            }
        }
    }
}
