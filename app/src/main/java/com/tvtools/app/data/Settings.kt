package com.tvtools.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    CLASSIC("Classic"),
    CIRCULAR("Circular"),
    MINIMAL("Minimal"),
    GAMEPAD("Gamepad-first"),
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
}
