package com.tvtools.app.ui.common

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import com.tvtools.app.AppServices
import com.tvtools.app.data.HapticStrength
import com.tvtools.app.data.SoundPack

/** Global switch mirroring the user's haptics preference. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Haptic tick respecting the user's strength profile, plus optional
 * soft key-click sounds (AudioManager effect, no permissions needed).
 */
@Composable
fun rememberHaptics(): () -> Unit {
    val enabled = LocalHapticsEnabled.current
    val settings by AppServices.settings.state.collectAsState()
    val strength = settings.hapticStrength
    val sounds = settings.sounds
    val soundPack = settings.soundPack
    val view: View = LocalView.current
    val audio = remember(view) { view.context.getSystemService(AudioManager::class.java) }

    return remember(enabled, strength, sounds, soundPack, view) {
        {
            if (enabled) {
                val constant = when (strength) {
                    HapticStrength.LIGHT -> HapticFeedbackConstants.KEYBOARD_TAP
                    HapticStrength.MEDIUM -> HapticFeedbackConstants.VIRTUAL_KEY
                    HapticStrength.STRONG -> HapticFeedbackConstants.LONG_PRESS
                }
                view.performHapticFeedback(constant)
            }
            if (sounds) {
                try {
                    when (soundPack) {
                        SoundPack.SOFT -> audio?.playSoundEffect(AudioManager.FX_KEY_CLICK)
                        SoundPack.MECHANICAL -> audio?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
                        SoundPack.TV_BEEP -> SoundPlayer.tvBeep()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
}

private object SoundPlayer {
    private val tone: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull()
    }

    fun tvBeep() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 45) }
    }
}
