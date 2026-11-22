package com.tvtools.app

import android.content.Context
import android.net.Uri
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.adb.DeviceDiscovery
import com.tvtools.app.adb.DeviceInfoProvider
import com.tvtools.app.data.PlaybackController
import com.tvtools.app.data.Prefs
import com.tvtools.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** A file an external app shared into TV Tools, with the sender's name already sanitised. */
data class PendingShare(
    val uri: Uri,
    val mime: String?,
    val displayName: String,
    val size: Long,
)

/** Simple service locator — one place that owns long-lived singletons. */
object AppServices {

    lateinit var adb: AdbClient
        private set
    lateinit var discovery: DeviceDiscovery
        private set
    lateinit var infoProvider: DeviceInfoProvider
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var playback: PlaybackController
        private set

    /** Incoming Android share-sheet payload (uri + mime + sender-supplied name, already sanitised). */
    val pendingShare = MutableStateFlow<PendingShare?>(null)

    fun init(context: Context) {
        if (::adb.isInitialized) return
        adb = AdbClient(context.applicationContext)
        discovery = DeviceDiscovery(context.applicationContext)
        infoProvider = DeviceInfoProvider(adb)
        prefs = Prefs(context.applicationContext)
        settings = SettingsRepository(context.applicationContext)
        playback = PlaybackController(adb)
    }
}
