package com.tvtools.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tvtools.app.data.Accent
import com.tvtools.app.data.AppSettings
import com.tvtools.app.data.ThemeMode

// ---------------- shared neutrals ----------------
private val Ink = Color(0xFF171B16)
private val InkDark = Color(0xFFE2E4DE)
private val Red40 = Color(0xFFBA1A1A)
private val Red90 = Color(0xFFFFDAD6)
private val Red80Dark = Color(0xFFFFB4AB)
private val Red30Dark = Color(0xFF93000A)

private fun lightScheme(
    primary: Color,
    onPrimary: Color = Color.White,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondaryContainer: Color,
    background: Color,
    surfaceVariant: Color = Color(0xFFE0E4DC),
): androidx.compose.material3.ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = Color(0xFF516350),
    onSecondary = Color.White,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = Color(0xFF111F10),
    tertiary = Color(0xFF39656B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCEBF1),
    onTertiaryContainer = Color(0xFF001F23),
    error = Red40,
    errorContainer = Red90,
    background = background,
    onBackground = Ink,
    surface = background,
    onSurface = Ink,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = Color(0xFF43483F),
    outline = Color(0xFF73796F),
    inverseSurface = Color(0xFF2D322C),
    inverseOnSurface = Color(0xFFEEF2EA),
    inversePrimary = primaryContainer,
)

private fun darkScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondaryContainer: Color,
    background: Color = Color(0xFF10140F),
): androidx.compose.material3.ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = Color(0xFFB9CCB5),
    onSecondary = Color(0xFF243424),
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = Color(0xFFD4E8D0),
    tertiary = Color(0xFFA1CED4),
    onTertiary = Color(0xFF00363C),
    tertiaryContainer = Color(0xFF1F4D53),
    onTertiaryContainer = Color(0xFFBCEBF1),
    error = Red80Dark,
    errorContainer = Red30Dark,
    background = background,
    onBackground = InkDark,
    surface = background,
    onSurface = InkDark,
    surfaceVariant = Color(0xFF41463E),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9389),
    inverseSurface = InkDark,
    inverseOnSurface = Color(0xFF2D322C),
    inversePrimary = primary,
)

private fun accentScheme(accent: Accent, dark: Boolean): androidx.compose.material3.ColorScheme = when (accent) {
    Accent.DYNAMIC, Accent.GREEN -> if (dark) darkScheme(
        primary = Color(0xFF86DA96),
        onPrimary = Color(0xFF002106),
        primaryContainer = Color(0xFF0D5320),
        onPrimaryContainer = Color(0xFFA2F6B0),
        secondaryContainer = Color(0xFF3A4B39),
    ) else lightScheme(
        primary = Color(0xFF166D32),
        primaryContainer = Color(0xFFA2F6B0),
        onPrimaryContainer = Color(0xFF063911),
        secondaryContainer = Color(0xFFD4E8D0),
        background = Color(0xFFF6FBF1),
    )

    Accent.BLUE -> if (dark) darkScheme(
        primary = Color(0xFF9CCAFF),
        onPrimary = Color(0xFF003257),
        primaryContainer = Color(0xFF0E4C7E),
        onPrimaryContainer = Color(0xFFD1E4FF),
        secondaryContainer = Color(0xFF34465B),
        background = Color(0xFF101418),
    ) else lightScheme(
        primary = Color(0xFF1565C0),
        primaryContainer = Color(0xFFD1E4FF),
        onPrimaryContainer = Color(0xFF001D36),
        secondaryContainer = Color(0xFFDCE7F5),
        background = Color(0xFFF8FAFD),
    )

    Accent.VIOLET -> if (dark) darkScheme(
        primary = Color(0xFFCFBDFF),
        onPrimary = Color(0xFF36256E),
        primaryContainer = Color(0xFF4D3D86),
        onPrimaryContainer = Color(0xFFE9DDFF),
        secondaryContainer = Color(0xFF4A4458),
        background = Color(0xFF141218),
    ) else lightScheme(
        primary = Color(0xFF6A4CBF),
        primaryContainer = Color(0xFFE9DDFF),
        onPrimaryContainer = Color(0xFF22005D),
        secondaryContainer = Color(0xFFE8DEF8),
        background = Color(0xFFFBF8FF),
    )

    Accent.ORANGE -> if (dark) darkScheme(
        primary = Color(0xFFFFB77C),
        onPrimary = Color(0xFF552100),
        primaryContainer = Color(0xFF7C3E00),
        onPrimaryContainer = Color(0xFFFFDCC2),
        secondaryContainer = Color(0xFF564334),
        background = Color(0xFF17120E),
    ) else lightScheme(
        primary = Color(0xFFB45309),
        primaryContainer = Color(0xFFFFDCC2),
        onPrimaryContainer = Color(0xFF2D1600),
        secondaryContainer = Color(0xFFF5E0D0),
        background = Color(0xFFFFFBF8),
    )
}

@Composable
fun TvToolsTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = if (settings.accent == Accent.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        accentScheme(settings.accent, dark)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
