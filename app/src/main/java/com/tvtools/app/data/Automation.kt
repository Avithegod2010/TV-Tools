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

object AutomationScheduler {
    private const val CACHE_WORK = "daily_cache_trim"
    private const val SHOT_WORK = "daily_screenshot"

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
}
