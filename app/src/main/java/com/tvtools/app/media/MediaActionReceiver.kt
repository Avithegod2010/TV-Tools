package com.tvtools.app.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tvtools.app.widget.NowPlayingWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Transport actions from the ongoing media notification.
 *
 * Sends the equivalent ADB media key to the last TV, then refreshes the cached
 * now-playing state so both the notification and the home-screen widget agree.
 */
class MediaActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val keyCode = when (intent.action) {
            MediaNotificationController.ACTION_PREVIOUS -> KEY_MEDIA_PREVIOUS
            MediaNotificationController.ACTION_PLAY_PAUSE -> KEY_MEDIA_PLAY_PAUSE
            MediaNotificationController.ACTION_NEXT -> KEY_MEDIA_NEXT
            else -> return
        }

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = TvMedia.press(app, keyCode)
                NowPlayingWidgetProvider.render(app, snapshot)
                MediaNotificationController.show(app, snapshot)
            } catch (_: Exception) {
                // Ignore: the TV may have gone away mid-press.
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val KEY_MEDIA_PREVIOUS = 88
        const val KEY_MEDIA_PLAY_PAUSE = 85
        const val KEY_MEDIA_NEXT = 87
    }
}
