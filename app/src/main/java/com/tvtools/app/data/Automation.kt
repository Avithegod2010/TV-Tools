package com.tvtools.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import java.util.concurrent.TimeUnit

/** Daily background jobs that reconnect to the last TV and do maintenance. */
class CacheTrimWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val adb = AppServices.adb
        val last = AppServices.prefs.loadLastDevice() ?: return Result.success()
        if (!adb.isConnected) adb.connect(last.host, last.port)
        if (!adb.isConnected) return Result.retry()
        runCatching { adb.exec("pm trim-caches 32G 2>&1", timeoutMs = 120_000) }
        return Result.success()
    }
}

class ScreenshotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val adb = AppServices.adb
        val last = AppServices.prefs.loadLastDevice() ?: return Result.success()
        if (!adb.isConnected) adb.connect(last.host, last.port)
        if (!adb.isConnected) return Result.retry()
        return try {
            val file = java.io.File(TvToolsApp.instance.cacheDir, "auto_screen.png")
            adb.screencap(file)
            val resolver = TvToolsApp.instance.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "tv_auto_${System.currentTimeMillis()}.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    "${android.os.Environment.DIRECTORY_PICTURES}/TV Tools"
                )
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { u ->
                resolver.openOutputStream(u)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            }
            file.delete()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

/** Sends a Wake-on-LAN magic packet to the last TV's saved MAC. */
class WakeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val mac = AppServices.prefs.loadLastMac() ?: return Result.success()
        val sent = com.tvtools.app.util.Wol.send(mac)
        return if (sent) Result.success() else Result.retry()
    }
}

object AutomationScheduler {
    private const val CACHE_WORK = "daily_cache_trim"
    private const val SHOT_WORK = "daily_screenshot"
    private const val WAKE_WORK = "daily_wake_tv"

    fun setCacheTrim(enabled: Boolean) {
        val wm = WorkManager.getInstance(TvToolsApp.instance)
        if (!enabled) {
            wm.cancelUniqueWork(CACHE_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<CacheTrimWorker>(24, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(CACHE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun setScreenshot(enabled: Boolean) {
        val wm = WorkManager.getInstance(TvToolsApp.instance)
        if (!enabled) {
            wm.cancelUniqueWork(SHOT_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<ScreenshotWorker>(24, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(SHOT_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Daily Wake-on-LAN at [minutes] past midnight (local time). */
    fun setWake(enabled: Boolean, minutes: Int) {
        val wm = WorkManager.getInstance(TvToolsApp.instance)
        if (!enabled) {
            wm.cancelUniqueWork(WAKE_WORK)
            return
        }
        val now = java.util.Calendar.getInstance()
        val target = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, (minutes / 60).coerceIn(0, 23))
            set(java.util.Calendar.MINUTE, (minutes % 60).coerceIn(0, 59))
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val delayMs = (target.timeInMillis - now.timeInMillis).coerceAtLeast(0)
        val request = PeriodicWorkRequestBuilder<WakeWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WAKE_WORK, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }
}
