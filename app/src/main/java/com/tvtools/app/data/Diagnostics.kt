package com.tvtools.app.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only crash capture. Writes the last uncaught exception plus a small
 * environment block to app-private storage so users can paste it into a bug
 * report. Nothing is uploaded and nothing leaves the device unless the user
 * copies or shares the text themselves.
 */
object Diagnostics {

    private const val MAX_CHARS = 64_000
    private const val FILE_NAME = "last-crash.txt"
    private const val DIR_NAME = "diagnostics"

    /** Chains onto the previous handler so the system still shows its crash dialog. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrash(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun environment(context: Context): String {
        val info = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionCode = when {
            info == null -> "?"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> info.longVersionCode.toString()
            else -> @Suppress("DEPRECATION") info.versionCode.toString()
        }
        return buildString {
            appendLine("TV Tools ${info?.versionName ?: "?"} ($versionCode)")
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        }.trim()
    }

    /** Pure formatter so the report layout can be unit tested without Android. */
    internal fun formatCrash(
        thread: Thread,
        error: Throwable,
        environment: String,
        timestamp: Long = System.currentTimeMillis(),
    ): String {
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { error.printStackTrace(it) }
        }.toString()
        val whenText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(timestamp))
        val report = buildString {
            appendLine("TV Tools crash report")
            appendLine("When: $whenText")
            appendLine(environment)
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(stack)
        }
        return if (report.length <= MAX_CHARS) report else report.take(MAX_CHARS) + "\n… truncated"
    }

    fun writeCrash(context: Context, thread: Thread, error: Throwable) {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        File(dir, FILE_NAME).writeText(formatCrash(thread, error, environment(context)))
    }

    fun lastCrash(context: Context): String? {
        val file = File(File(context.filesDir, DIR_NAME), FILE_NAME)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        File(File(context.filesDir, DIR_NAME), FILE_NAME).delete()
    }

    /** Environment block plus the last crash — what the "Copy diagnostics" button pastes. */
    fun copyReport(context: Context): String {
        val crash = lastCrash(context)
        return buildString {
            appendLine("== Environment ==")
            appendLine(environment(context))
            appendLine()
            if (crash == null) {
                appendLine("== Last crash ==")
                appendLine("No crash recorded.")
            } else {
                appendLine("== Last crash ==")
                append(crash)
            }
        }
    }
}
