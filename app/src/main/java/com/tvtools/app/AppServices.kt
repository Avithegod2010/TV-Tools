package com.tvtools.app

import android.content.Context
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.adb.ConnectionRecovery
import com.tvtools.app.adb.DeviceDiscovery
import com.tvtools.app.adb.DeviceInfoProvider
import com.tvtools.app.adb.LanNetworkMonitor
import com.tvtools.app.data.PlaybackController
import com.tvtools.app.data.Prefs
import com.tvtools.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

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
    internal lateinit var networks: LanNetworkMonitor
        private set
    internal lateinit var recovery: ConnectionRecovery
        private set

    /** Incoming Android share-sheet payload (uri + mime). */
    val pendingShare = MutableStateFlow<Pair<Uri, String?>?>(null)

    fun init(context: Context) {
        if (::adb.isInitialized) return
        val app = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val foreground = MutableStateFlow(false)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground.value = true }
            override fun onStop(owner: LifecycleOwner) { foreground.value = false }
        })
        networks = LanNetworkMonitor(app)
        adb = AdbClient(app, networks)
        discovery = DeviceDiscovery(app, networks, foreground, scope)
        infoProvider = DeviceInfoProvider(adb)
        prefs = Prefs(app)
        settings = SettingsRepository(app)
        recovery = ConnectionRecovery(adb, networks, discovery, prefs, settings, foreground, scope)
        playback = PlaybackController(adb)
    }
}
