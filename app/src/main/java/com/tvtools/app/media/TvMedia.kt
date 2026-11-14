package com.tvtools.app.media

import android.content.Context
import com.tvtools.app.AppServices
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.data.NowPlaying
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cached view of what the TV is playing.
 *
 * Home-screen widgets and the media notification cannot hold an ADB connection,
 * so every read is written here first: the surfaces paint instantly from the
 * cache and then refresh in the background.
 */
data class NowPlayingSnapshot(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val updatedAtMs: Long = 0L,
) {
    val hasMedia: Boolean get() = title.isNotBlank()

    companion object {
        fun of(np: NowPlaying?): NowPlayingSnapshot = NowPlayingSnapshot(
            title = np?.title.orEmpty(),
            artist = np?.artist.orEmpty(),
            playing = np?.isPlaying == true,
            updatedAtMs = System.currentTimeMillis(),
        )
    }
}

/**
 * Small bridge between the ADB stack and the notification/widgets.
 *
 * Every entry point tolerates a cold process: services are (re)initialized
 * defensively, the last known TV is reconnected when it is not already
 * connected, and failures degrade to the cached snapshot instead of throwing.
 */
internal object TvMedia {

    private const val PREFS = "now_playing"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_PLAYING = "playing"
    private const val KEY_UPDATED = "updated_at"
    private const val READ_WAIT_MS = 500L

    private val serviceLock = Any()
    private val connectLock = Mutex()

    fun snapshot(context: Context): NowPlayingSnapshot {
        val p = prefs(context)
        return NowPlayingSnapshot(
            title = p.getString(KEY_TITLE, "").orEmpty(),
            artist = p.getString(KEY_ARTIST, "").orEmpty(),
            playing = p.getBoolean(KEY_PLAYING, false),
            updatedAtMs = p.getLong(KEY_UPDATED, 0L),
        )
    }

    fun store(context: Context, snapshot: NowPlayingSnapshot) {
        prefs(context).edit()
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putBoolean(KEY_PLAYING, snapshot.playing)
            .putLong(
                KEY_UPDATED,
                if (snapshot.updatedAtMs > 0L) snapshot.updatedAtMs else System.currentTimeMillis(),
            )
            .apply()
    }

    /** Connect to the last known TV when possible; null when none is reachable. */
    suspend fun connect(context: Context): AdbClient? = connectLock.withLock {
        ensureServices(context)
        val adb = runCatching { AppServices.adb }.getOrNull() ?: return@withLock null
        if (adb.isConnected) return@withLock adb
        val last = runCatching { AppServices.prefs.loadLastDevice() }.getOrNull() ?: return@withLock null
        runCatching { adb.connect(last.host, last.port) }
        adb.takeIf { it.isConnected }
    }

    /** Read the TV's media session, cache it and return it (cache when offline). */
    suspend fun refresh(context: Context): NowPlayingSnapshot {
        val adb = connect(context) ?: return snapshot(context)
        val np = runCatching { adb.nowPlaying() }.getOrNull()
        val snapshot = if (np != null) {
            NowPlayingSnapshot.of(np)
        } else {
            // A dumpsys hiccup should not blank the widget when the in-app
            // controller polled the same TV seconds ago.
            recentControllerSnapshot() ?: NowPlayingSnapshot.of(null)
        }
        store(context, snapshot)
        return snapshot
    }

    /** Send a media key, give the TV a moment to react, then return the new state. */
    suspend fun press(context: Context, keyCode: Int): NowPlayingSnapshot {
        val adb = connect(context) ?: return snapshot(context)
        runCatching { adb.keyEvent(keyCode) }
        delay(READ_WAIT_MS)
        val snapshot = NowPlayingSnapshot.of(runCatching { adb.nowPlaying() }.getOrNull())
        store(context, snapshot)
        return snapshot
    }

    private fun ensureServices(context: Context) {
        synchronized(serviceLock) {
            runCatching { AppServices.init(context.applicationContext) }
        }
    }

    /** State polled by the in-app [com.tvtools.app.data.PlaybackController], if it is fresh. */
    private fun recentControllerSnapshot(): NowPlayingSnapshot? {
        val np = runCatching { AppServices.playback.state.value.nowPlaying }.getOrNull() ?: return null
        val age = System.currentTimeMillis() - np.updatedAtMs
        if (age !in 0L..30_000L) return null
        return NowPlayingSnapshot.of(np)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
