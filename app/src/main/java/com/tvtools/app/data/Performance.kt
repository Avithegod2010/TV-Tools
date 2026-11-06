package com.tvtools.app.data

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local-only startup and frame-jank measurements.
 *
 * Nothing is uploaded: the numbers are written to app-private SharedPreferences
 * and shown in Settings so the user can see whether a build actually improved.
 */
object Performance {

    private const val PREFS = "tvtools_performance"
    private const val KEY_STARTUP = "last_startup_ms"
    private const val KEY_FRAMES = "last_frames"
    private const val KEY_JANKY = "last_janky"

    private val frames = AtomicInteger(0)
    private val janky = AtomicInteger(0)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordStartup(context: Context, elapsedMs: Long) {
        if (elapsedMs <= 0) return
        prefs(context).edit().putLong(KEY_STARTUP, elapsedMs).apply()
    }

    fun lastStartupMs(context: Context): Long? {
        val value = prefs(context).getLong(KEY_STARTUP, -1L)
        return if (value > 0) value else null
    }

    fun onFrame(isJank: Boolean) {
        frames.incrementAndGet()
        if (isJank) janky.incrementAndGet()
    }

    /** Persist the current session's counters and start a fresh window. */
    fun flush(context: Context) {
        val total = frames.getAndSet(0)
        val bad = janky.getAndSet(0)
        if (total > 0) {
            prefs(context).edit()
                .putInt(KEY_FRAMES, total)
                .putInt(KEY_JANKY, bad)
                .apply()
        }
    }

    /** Returns (janky frames, total frames) for the last finished session. */
    fun lastJank(context: Context): Pair<Int, Int>? {
        val total = prefs(context).getInt(KEY_FRAMES, 0)
        if (total <= 0) return null
        return prefs(context).getInt(KEY_JANKY, 0) to total
    }

    fun reset(context: Context) {
        frames.set(0)
        janky.set(0)
        prefs(context).edit().clear().apply()
    }
}
