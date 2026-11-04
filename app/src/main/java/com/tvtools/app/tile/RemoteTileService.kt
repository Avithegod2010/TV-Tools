package com.tvtools.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tvtools.app.AppServices
import com.tvtools.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Quick Settings tile: tap sends HOME to the connected TV (or opens the app). */
class RemoteTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "TV Remote"
            state = if (AppServices.adb.isConnected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val adb = AppServices.adb
        if (adb.isConnected) {
            CoroutineScope(Dispatchers.IO).launch {
                adb.keyEvent(3) // HOME
            }
            qsTile?.apply {
                state = Tile.STATE_ACTIVE
                updateTile()
            }
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        }
    }
}
