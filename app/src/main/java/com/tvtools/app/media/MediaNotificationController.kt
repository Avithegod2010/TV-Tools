package com.tvtools.app.media

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tvtools.app.MainActivity
import com.tvtools.app.R

/**
 * Ongoing "now playing" notification with previous / play-pause / next actions.
 *
 * Not a foreground service on purpose: the TV decodes the media, this is only a
 * mirror plus three remote-control shortcuts. Everything is a silent no-op when
 * the user has not granted POST_NOTIFICATIONS or disabled notifications.
 *
 * Typical use from the UI:
 *   MediaNotificationController.start(context)
 *   MediaNotificationController.update(context, title, artist, playing)
 *   MediaNotificationController.stop(context)
 */
object MediaNotificationController {

    const val ACTION_PREVIOUS = "com.tvtools.app.media.action.PREVIOUS"
    const val ACTION_PLAY_PAUSE = "com.tvtools.app.media.action.PLAY_PAUSE"
    const val ACTION_NEXT = "com.tvtools.app.media.action.NEXT"

    private const val CHANNEL_ID = "media_playback"
    private const val NOTIFICATION_ID = 7782

    private const val REQ_PREVIOUS = 41
    private const val REQ_PLAY_PAUSE = 42
    private const val REQ_NEXT = 43
    private const val REQ_OPEN = 44

    @Volatile
    private var active = false

    /** Show the ongoing notification using the last cached now-playing state. */
    fun start(context: Context) {
        val app = context.applicationContext
        active = true
        val snapshot = TvMedia.snapshot(app)
        post(app, snapshot.title, snapshot.artist, snapshot.playing)
    }

    /** Show the ongoing notification for a freshly read media session. */
    fun start(context: Context, title: String, artist: String, playing: Boolean) {
        active = true
        post(context.applicationContext, title, artist, playing)
    }

    /** Refresh the visible notification. Safe to call often; it only alerts once. */
    fun update(context: Context, title: String, artist: String, playing: Boolean) {
        if (!active) return
        post(context.applicationContext, title, artist, playing)
    }

    /** Remove the notification and stop mirroring until the next [start]. */
    fun stop(context: Context) {
        active = false
        runCatching {
            NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        }
    }

    /**
     * Repaint after a widget / notification action, but only when the
     * notification is already showing. Called from the shared receivers.
     */
    internal fun show(context: Context, snapshot: NowPlayingSnapshot) {
        if (!active) return
        post(context.applicationContext, snapshot.title, snapshot.artist, snapshot.playing)
    }

    private fun post(context: Context, title: String, artist: String, playing: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        if (!canPost(context, manager)) return
        ensureChannel(context)

        val safeTitle = title.ifBlank { context.getString(R.string.now_playing_nothing) }
        val safeText = artist.ifBlank {
            when {
                title.isBlank() -> context.getString(R.string.now_playing_notification_idle)
                playing -> context.getString(R.string.now_playing_on_tv)
                else -> context.getString(R.string.now_playing_paused)
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_media)
            .setContentTitle(safeTitle)
            .setContentText(safeText)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(ContextCompat.getColor(context, R.color.widget_np_accent))
            .addAction(
                android.R.drawable.ic_media_previous,
                context.getString(R.string.media_previous),
                action(context, ACTION_PREVIOUS, REQ_PREVIOUS),
            )
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                context.getString(if (playing) R.string.media_pause else R.string.media_play),
                action(context, ACTION_PLAY_PAUSE, REQ_PLAY_PAUSE),
            )
            .addAction(
                android.R.drawable.ic_media_next,
                context.getString(R.string.media_next),
                action(context, ACTION_NEXT, REQ_NEXT),
            )

        // Permission can be revoked between the check and here; never crash a widget tick.
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    private fun canPost(context: Context, manager: NotificationManagerCompat): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.media_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.media_channel_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun action(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MediaActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQ_OPEN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
