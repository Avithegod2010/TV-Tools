package com.tvtools.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Global switch mirroring the user's haptics preference. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Returns a haptic tick function respecting the user preference. */
@Composable
fun rememberHaptics(): () -> Unit {
    val enabled = LocalHapticsEnabled.current
    val feedback: HapticFeedback = LocalHapticFeedback.current
    return remember(enabled) {
        { if (enabled) feedback.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}
