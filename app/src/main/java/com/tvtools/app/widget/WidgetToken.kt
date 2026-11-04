package com.tvtools.app.widget

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Per-install secret embedded in the widget's PendingIntents. The widget receiver only
 * accepts key broadcasts carrying it, so other apps can't drive the TV through the widget.
 */
object WidgetToken {

    fun get(context: Context): String {
        val file = File(context.filesDir, "widget_token")
        return try {
            if (!file.exists()) file.writeText(UUID.randomUUID().toString())
            file.readText().trim()
        } catch (_: Exception) {
            ""
        }
    }
}
