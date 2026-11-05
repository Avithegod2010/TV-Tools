package com.tvtools.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tvtools.app.AppServices
import com.tvtools.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen remote widget (D-pad + back/home/power). */
open class RemoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_KEY) {
            val code = intent.getIntExtra(EXTRA_KEY, -1)
            val token = intent.getStringExtra(EXTRA_TOKEN)
            if (code < 0 || token != WidgetToken.get(context)) return
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val adb = AppServices.adb
                    if (!adb.isConnected) {
                        val last = AppServices.prefs.loadLastDevice() ?: return@launch
                        adb.connect(last.host, last.port)
                    }
                    if (adb.isConnected) adb.keyEvent(code)
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_remote)
        views.setOnClickPendingIntent(R.id.w_up, keyPending(context, 19))
        views.setOnClickPendingIntent(R.id.w_down, keyPending(context, 20))
        views.setOnClickPendingIntent(R.id.w_left, keyPending(context, 21))
        views.setOnClickPendingIntent(R.id.w_right, keyPending(context, 22))
        views.setOnClickPendingIntent(R.id.w_ok, keyPending(context, 23))
        views.setOnClickPendingIntent(R.id.w_back, keyPending(context, 4))
        views.setOnClickPendingIntent(R.id.w_home, keyPending(context, 3))
        views.setOnClickPendingIntent(R.id.w_power, keyPending(context, 26))
        views.setOnClickPendingIntent(R.id.w_open, openAppPending(context))
        return views
    }

    protected fun keyPending(context: Context, code: Int): PendingIntent {
        val intent = Intent(context, RemoteWidgetProvider::class.java).apply {
            action = ACTION_KEY
            putExtra(EXTRA_KEY, code)
            putExtra(EXTRA_TOKEN, WidgetToken.get(context))
        }
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    protected fun openAppPending(context: Context): PendingIntent {
        val intent = Intent(context, com.tvtools.app.MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 999, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val ACTION_KEY = "com.tvtools.app.widget.KEY"
        const val EXTRA_KEY = "keycode"
        const val EXTRA_TOKEN = "token"

        fun refresh(context: Context) {

            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, RemoteWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val intent = Intent(context, RemoteWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}

/** Compact 2×1 widget: back / home / OK / power. */
class CompactRemoteWidgetProvider : RemoteWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_compact)
            views.setOnClickPendingIntent(R.id.wc_back, keyPending(context, 4))
            views.setOnClickPendingIntent(R.id.wc_home, keyPending(context, 3))
            views.setOnClickPendingIntent(R.id.wc_ok, keyPending(context, 23))
            views.setOnClickPendingIntent(R.id.wc_power, keyPending(context, 26))
            views.setOnClickPendingIntent(R.id.wc_open, openAppPending(context))
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
