package com.tvtools.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tvtools.app.MainActivity
import com.tvtools.app.R
import com.tvtools.app.media.MediaNotificationController
import com.tvtools.app.media.NowPlayingSnapshot
import com.tvtools.app.media.TvMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen "now playing" card: title, artist and previous / play-pause / next.
 *
 * The widget paints the cached snapshot immediately, then asks the TV in the
 * background so a freshly placed widget fills in without blocking the launcher.
 * Tapping the card opens TV Tools; the transport buttons only ever send the
 * standard media key codes (88 / 85 / 87) over ADB.
 */
class NowPlayingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val snapshot = TvMedia.snapshot(context)
        val views = buildViews(context, snapshot)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }

        val pending = goAsync()
        runAsync {
            try {
                val refreshed = TvMedia.refresh(context)
                render(context, refreshed)
                if (refreshed.hasMedia) {
                    MediaNotificationController.start(
                        context,
                        refreshed.title,
                        refreshed.artist,
                        refreshed.playing,
                    )
                } else {
                    MediaNotificationController.stop(context)
                }
            } catch (_: Exception) {
                // A widget tick must never take the process down.
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_KEY) return

        val code = intent.getIntExtra(EXTRA_KEY, -1)
        val token = intent.getStringExtra(EXTRA_TOKEN)
        if (code !in MEDIA_KEYS || token.isNullOrBlank() || token != WidgetToken.get(context)) return

        val app = context.applicationContext
        val pending = goAsync()
        runAsync {
            try {
                val snapshot = TvMedia.press(app, code)
                render(app, snapshot)
                MediaNotificationController.show(app, snapshot)
            } catch (_: Exception) {
                // Ignore: the TV may have gone away mid-press.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        const val ACTION_KEY = "com.tvtools.app.widget.NOW_PLAYING_KEY"
        const val EXTRA_KEY = "keycode"
        const val EXTRA_TOKEN = "token"

        private const val KEY_MEDIA_PREVIOUS = 88
        private const val KEY_MEDIA_PLAY_PAUSE = 85
        private const val KEY_MEDIA_NEXT = 87

        private val MEDIA_KEYS = setOf(KEY_MEDIA_PREVIOUS, KEY_MEDIA_PLAY_PAUSE, KEY_MEDIA_NEXT)

        /** Repaint every placed instance from the cache (no ADB traffic). */
        fun render(context: Context, snapshot: NowPlayingSnapshot = TvMedia.snapshot(context)) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val component = ComponentName(app, NowPlayingWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val views = buildViews(app, snapshot)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        /** Refresh from the TV and repaint. Safe to call from any thread. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            runAsync { runCatching { render(app, TvMedia.refresh(app)) } }
        }

        private fun runAsync(block: suspend () -> Unit) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { block() }
        }

        private fun buildViews(context: Context, snapshot: NowPlayingSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            val hasMedia = snapshot.hasMedia

            views.setTextViewText(
                R.id.np_title,
                if (hasMedia) snapshot.title else context.getString(R.string.now_playing_nothing),
            )
            views.setTextViewText(
                R.id.np_artist,
                when {
                    snapshot.artist.isNotBlank() -> snapshot.artist
                    !hasMedia -> context.getString(R.string.now_playing_open_remote)
                    snapshot.playing -> context.getString(R.string.now_playing_on_tv)
                    else -> context.getString(R.string.now_playing_paused)
                },
            )
            views.setTextViewText(R.id.np_toggle, if (snapshot.playing) "❚❚" else "▶")
            views.setContentDescription(
                R.id.np_toggle,
                context.getString(if (snapshot.playing) R.string.media_pause else R.string.media_play),
            )

            views.setOnClickPendingIntent(R.id.np_prev, keyPending(context, KEY_MEDIA_PREVIOUS))
            views.setOnClickPendingIntent(R.id.np_toggle, keyPending(context, KEY_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.np_next, keyPending(context, KEY_MEDIA_NEXT))
            views.setOnClickPendingIntent(R.id.np_root, openAppPending(context))
            return views
        }

        private fun keyPending(context: Context, code: Int): PendingIntent {
            val intent = Intent(context, NowPlayingWidgetProvider::class.java).apply {
                action = ACTION_KEY
                putExtra(EXTRA_KEY, code)
                putExtra(EXTRA_TOKEN, WidgetToken.get(context))
            }
            return PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun openAppPending(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
            return PendingIntent.getActivity(
                context,
                997,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
