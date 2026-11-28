package com.tvtools.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    @Test
    fun resolvesEnglishAndRussianResourcesAndFormatsValues() {
        val english = localeContext("en")
        val russian = localeContext("ru")

        assertEquals("Settings", english.getString(R.string.settings))
        assertEquals("Настройки", russian.getString(R.string.settings))
        assertEquals("Выбрано: 12", russian.getString(R.string.items_selected, 12))
        assertEquals("TV Tools", russian.getString(R.string.app_name))
    }

    @Test
    fun usesEnglishResourcesAsFallback() {
        assertEquals("Settings", localeContext("fr").getString(R.string.settings))
    }

    @Test
    fun selectsRussianPluralForms() {
        val resources = localeContext("ru").resources
        assertEquals("Удалить 1 снимок?", resources.getQuantityString(R.plurals.delete_screenshots, 1, 1))
        assertEquals("Удалить 2 снимка?", resources.getQuantityString(R.plurals.delete_screenshots, 2, 2))
        assertEquals("Удалить 5 снимков?", resources.getQuantityString(R.plurals.delete_screenshots, 5, 5))
        assertEquals("Удалить 21 снимок?", resources.getQuantityString(R.plurals.delete_screenshots, 21, 21))
    }

    private fun localeContext(language: String): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(language)))
        return context.createConfigurationContext(config)
    }
}
