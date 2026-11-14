package com.tvtools.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileCodecTest {

    private val full = DeviceProfile(
        accent = Accent.VIOLET,
        themeMode = ThemeMode.AMOLED,
        remoteSkin = RemoteSkin.NEON,
        fontScale = 1.3f,
        density = UiDensity.SPACIOUS,
        highContrast = true,
        colorBlind = ColorBlindMode.DEUTERANOPIA,
        reducedMotion = true,
        expressiveShapes = false,
        soundPack = SoundPack.TV_BEEP,
        hapticStrength = HapticStrength.STRONG,
        kidsTheme = true,
        layout = RemoteLayout(
            columns = 3,
            dpadSize = 320,
            oneHanded = true,
            expressive = false,
            hidden = setOf("media", "app"),
            quickOrder = listOf("home", "back", "power"),
        ),
    )

    @Test
    fun `round trip keeps every field`() {
        val decoded = DeviceProfileCodec.decode(DeviceProfileCodec.encode(full))
        assertEquals(full, decoded)
    }

    @Test
    fun `unknown version is rejected`() {
        assertNull(DeviceProfileCodec.decode("v=2;accent=BLUE"))
        assertNull(DeviceProfileCodec.decode(""))
    }

    @Test
    fun `out of range values are clamped`() {
        val raw = "v=1;accent=NOPE;theme=NOPE;skin=NOPE;font=9;density=NOPE;" +
            "contrast=x;cb=NOPE;motion=x;shape=x;sound=NOPE;haptic=NOPE;kids=x;" +
            "layout=9%7C999%7Ctrue%7Cfalse%7C%7C"
        val decoded = DeviceProfileCodec.decode(raw)!!
        assertEquals(Accent.GREEN, decoded.accent)
        assertEquals(ThemeMode.SYSTEM, decoded.themeMode)
        assertEquals(2f, decoded.fontScale, 0.001f)
        assertEquals(3, decoded.layout.columns)
        assertEquals(340, decoded.layout.dpadSize)
        assertTrue(decoded.layout.quickOrder.isNotEmpty())
    }

    @Test
    fun `theme pack adds and strips its prefix`() {
        val text = ThemePack.encode(full)
        assertTrue(text.startsWith("TVTOOLS-THEME:1:"))
        assertEquals(full, ThemePack.decode(text))
        assertEquals(full, ThemePack.decode("Try my TV look: $text"))
        assertNull(ThemePack.decode("not a theme"))
        assertNull(ThemePack.decode("TVTOOLS-THEME:1:v=9"))
    }

    @Test
    fun `summary mentions the key look choices`() {
        val summary = ThemePack.summary(full)
        assertTrue(summary.contains("Violet"))
        assertTrue(summary.contains("Neon"))
        assertTrue(summary.contains("Spacious"))
        assertTrue(summary.contains("130%"))
    }
}
