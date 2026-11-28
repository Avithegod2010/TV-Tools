package com.tvtools.app.ui.tools

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApkDocumentPickerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val picker = ApkDocumentPicker()
    private val document = Uri.parse("content://example.documents/document/opaque%3A123")

    @Test
    fun opensDocumentsWithTheOriginalProviderUriAsTheLocationHint() {
        val intent = picker.createIntent(context, document)

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals("*/*", intent.type)
        assertEquals(document, IntentCompat.getParcelableExtra(
            intent, DocumentsContract.EXTRA_INITIAL_URI, Uri::class.java,
        ))
    }

    @Test
    fun allowsTheSystemDefaultWhenThereIsNoSavedLocation() {
        assertFalse(picker.createIntent(context, null).hasExtra(DocumentsContract.EXTRA_INITIAL_URI))
    }

    @Test
    fun onlyInstallsSuccessfulSelections() {
        val intent = Intent().setData(document)

        assertNull(picker.parseResult(Activity.RESULT_CANCELED, intent))
        assertNull(picker.parseResult(Activity.RESULT_OK, null))
        assertEquals(document, picker.parseResult(Activity.RESULT_OK, intent))
    }
}
