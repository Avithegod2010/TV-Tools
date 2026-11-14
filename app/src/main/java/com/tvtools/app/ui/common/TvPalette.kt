package com.tvtools.app.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import com.tvtools.app.AppServices
import java.io.File

/** Samples a saturated colour from a TV frame for the "Match TV" accent. */
object TvPalette {

    /** Blocking; call from Dispatchers.IO. Returns null when not connected. */
    fun capture(context: Context): Int? {
        return try {
            val adb = AppServices.adb
            if (!adb.isConnected) return null
            val file = File(context.cacheDir, "palette.png")
            kotlinx.coroutines.runBlocking { adb.screencap(file) }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val small = android.graphics.Bitmap.createScaledBitmap(bitmap, 48, 27, false)
            val counts = HashMap<Int, Int>()
            for (x in 0 until small.width) {
                for (y in 0 until small.height) {
                    val color = small.getPixel(x, y)
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(color, hsv)
                    if (hsv[1] > 0.25f && hsv[2] in 0.2f..0.9f) {
                        val quantized = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], hsv[2]))
                        counts[quantized] = (counts[quantized] ?: 0) + 1
                    }
                }
            }
            file.delete()
            bitmap.recycle()
            counts.maxByOrNull { it.value }?.key ?: 0xFF166D32.toInt()
        } catch (_: Exception) {
            null
        }
    }
}
