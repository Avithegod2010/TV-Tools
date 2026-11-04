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

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Accent(val label: String) {
    GREEN("Green"),
    BLUE("Blue"),
    VIOLET("Violet"),
    ORANGE("Orange"),
    DYNAMIC("Dynamic"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.GREEN,
    val haptics: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    private val KEY_MODE = stringPreferencesKey("theme_mode")
    private val KEY_ACCENT = stringPreferencesKey("accent")
    private val KEY_HAPTICS = booleanPreferencesKey("haptics")

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
            )
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
}
