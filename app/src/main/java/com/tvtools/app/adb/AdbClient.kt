package com.tvtools.app.adb

import android.content.Context
import android.os.SystemClock
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-level, coroutine-friendly API over [TvAdbConnectionManager].
 * Every blocking call runs on [Dispatchers.IO].
 */
class AdbClient(context: Context) {

    private val appContext = context.applicationContext
    private val manager = TvAdbConnectionManager.getInstance(appContext)

    data class ConnectedDevice(val host: String, val port: Int)

    var connectedDevice: ConnectedDevice? = null
        private set

    /** Last address we successfully connected to, kept across a silent socket drop. */
    @Volatile
    private var lastDevice: ConnectedDevice? = null

    /** Duration of the last successful ADB handshake, in milliseconds (0 = unknown). */
    var lastConnectMs: Long = 0
        private set

    val isConnected: Boolean get() = manager.isConnected

    // ---------------- connection ----------------

    sealed class ConnectResult {
        data object Success : ConnectResult()
        data object PairingRequired : ConnectResult()
        data class Error(val message: String) : ConnectResult()
    }

    suspend fun pair(host: String, port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ok = manager.pair(host, port, code.trim())
            if (ok) Result.success(Unit) else Result.failure(IOException("Pairing rejected"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun connect(host: String, port: Int): ConnectResult = withContext(Dispatchers.IO) {
        try {
            fastMutex.withLock { closeFastShellLocked() }
            if (manager.isConnected) manager.disconnect()
            val started = System.currentTimeMillis()
            val ok = manager.connect(host, port)
            if (ok) {
                lastConnectMs = System.currentTimeMillis() - started
                connectedDevice = ConnectedDevice(host, port)
                lastDevice = ConnectedDevice(host, port)
                ConnectResult.Success
            } else ConnectResult.Error("Connection refused")
        } catch (e: AdbPairingRequiredException) {
            ConnectResult.PairingRequired
        } catch (e: Exception) {
            ConnectResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            fastMutex.withLock { closeFastShellLocked() }
        } catch (_: Exception) {
        }
        try {
            manager.disconnect()
        } catch (_: Exception) {
        }
        connectedDevice = null
        lastDevice = null
    }

    /**
     * Reconnect to the last known TV after a silent socket drop.
     * Returns null when this process never connected, so callers can navigate home.
     */
    suspend fun reconnect(): ConnectResult? {
        val device = lastDevice ?: connectedDevice ?: return null
        return connect(device.host, device.port)
    }

    /**
     * Heartbeat over the persistent input shell. A marker has to come back from
     * the TV, so this detects a dead socket even when [isConnected] still says true.
     */
    suspend fun ping(timeoutMs: Long = 4_000): Boolean {
        if (!manager.isConnected) return false
        val before = fastPending.get()
        if (!writeFast("true")) return false
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (fastPending.get() <= before) return true
            delay(100)
        }
        return false
    }

    // ---------------- fast input channel ----------------
    //
    // Every `input` key press used to open a brand-new ADB shell (stream setup +
    // shell spawn), which made the remote feel sluggish on cheap TVs. Inputs now
    // go through one long-lived shell. Commands are written and forgotten, so the
    // UI never waits for the TV to finish; a DONE marker bounds the queue while a
    // key is held so a long hold can't leave a backlog after release.

    // One thread keeps key presses in the exact order the user pressed them.
    private val fastDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tvtools-input").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val fastScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fastMutex = Mutex()
    private var fastStream: AdbStream? = null
    private var fastOut: OutputStream? = null
    private var fastDrain: Job? = null
    private val fastPending = AtomicInteger(0)
    private val fastMarker = "__TVT_DONE__"

    @Volatile
    private var pendingSince = 0L

    @Volatile
    private var lastKeyCode = -1

    @Volatile
    private var lastKeyAt = 0L

    /** Uptime of the last input sent to the TV — used to pause status polling. */
    @Volatile
    var lastInputAt: Long = 0L
        private set

    /** True when the remote was used within [withinMs]. */
    fun recentlyInteracted(withinMs: Long = 2_500): Boolean =
        lastInputAt > 0 && SystemClock.elapsedRealtime() - lastInputAt < withinMs

    private fun ensureFastShell(): AdbStream {
        fastStream?.let { return it }
        val stream = manager.openStream("shell:")
        fastStream = stream
        fastOut = stream.openOutputStream()
        fastPending.set(0)
        fastDrain = fastScope.launch {
            val buf = ByteArray(8 * 1024)
            val tail = StringBuilder()
            try {
                val input = stream.openInputStream()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    tail.append(String(buf, 0, n, Charsets.UTF_8))
                    var index = tail.indexOf(fastMarker)
                    while (index >= 0) {
                        fastPending.updateAndGet { (it - 1).coerceAtLeast(0) }
                        tail.delete(0, index + fastMarker.length)
                        index = tail.indexOf(fastMarker)
                    }
                    // Never let unread chatter grow without bound.
                    if (tail.length > 4_096) tail.delete(0, tail.length - 1_024)
                }
            } catch (_: Exception) {
            } finally {
                if (fastStream === stream) {
                    fastStream = null
                    fastOut = null
                    fastPending.set(0)
                }
            }
        }
        return stream
    }

    /** Assumes [fastMutex] is held. */
    private fun closeFastShellLocked() {
        fastDrain?.cancel()
        fastDrain = null
        try {
            fastOut?.close()
        } catch (_: Exception) {
        }
        fastOut = null
        try {
            fastStream?.close()
        } catch (_: Exception) {
        }
        fastStream = null
        fastPending.set(0)
    }

    /** Open the input shell before the first key so the first press feels instant. */
    suspend fun warmUpInput() {
        writeFast("true", trackCompletion = false)
    }

    private suspend fun writeFast(command: String, trackCompletion: Boolean = true): Boolean =
        withContext(fastDispatcher) {
            fastMutex.withLock {
                try {
                    val stream = ensureFastShell()
                    val out = fastOut ?: stream.openOutputStream().also { fastOut = it }
                    if (trackCompletion && fastPending.getAndIncrement() == 0) {
                        pendingSince = SystemClock.elapsedRealtime()
                    }
                    val payload = if (trackCompletion) {
                        "$command; echo $fastMarker\n"
                    } else {
                        "$command\n"
                    }
                    out.write(payload.toByteArray(Charsets.UTF_8))
                    out.flush()
                    lastInputAt = SystemClock.elapsedRealtime()
                    true
                } catch (_: Exception) {
                    closeFastShellLocked()
                    false
                }
            }
        }

    // ---------------- shell ----------------

    /**
     * Execute a one-shot shell command, returning full stdout as String.
     * Times out safely: the stream is force-closed on timeout so a stuck TV
     * command can never block the caller forever.
     */
    suspend fun exec(command: String, timeoutMs: Long = 45_000): String = withContext(Dispatchers.IO) {
        val stream = manager.openStream("shell:$command")
        try {
            val reader = async { readStreamFully(stream) }
            try {
                withTimeout(timeoutMs) { reader.await() }
            } catch (e: TimeoutCancellationException) {
                try {
                    stream.close()
                } catch (_: Exception) {
                }
                reader.cancel()
                throw IOException("Command timed out after ${timeoutMs / 1000}s")
            }
        } finally {
            try {
                stream.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun readStreamFully(stream: AdbStream): String {
        val out = ByteArrayOutputStream()
        val input = stream.openInputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = try {
                input.read(buf)
            } catch (e: IOException) {
                // libadb throws "Stream closed." when the remote closes with an empty queue — treat as EOF
                if (e.message?.contains("closed", ignoreCase = true) == true) -1 else throw e
            }
            if (n < 0) break
            if (n > 0) out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** Open an interactive shell stream (caller owns it, must close). */
    suspend fun openShell(): AdbStream = withContext(Dispatchers.IO) {
        manager.openStream("shell:")
    }

    suspend fun execLines(command: String): List<String> =
        exec(command).replace("\r\n", "\n").split("\n").filter { it.isNotBlank() }

    // ---------------- sync (file ops) ----------------

    private fun <T> withSync(block: (AdbSync) -> T): T {
        val stream = manager.openStream("sync:")
        try {
            return AdbSync(stream).let(block)
        } finally {
            try {
                stream.close()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun listFiles(path: String): List<AdbSync.RemoteEntry> = withContext(Dispatchers.IO) {
        withSync { it.list(path) }
            .filter { it.name != "." && it.name != ".." }
    }

    suspend fun statFile(path: String): AdbSync.Stat = withContext(Dispatchers.IO) {
        withSync { it.stat(path) }
    }

    suspend fun pullFile(remotePath: String, local: File, expectedSize: Long = -1,
                         onProgress: ((Long) -> Unit)? = null) = withContext(Dispatchers.IO) {
        withSync { it.pull(remotePath, local, expectedSize, onProgress) }
    }

    /**
     * Push a file to the TV. Uses `cat > remotePath` over a shell stream instead of the
     * `sync:` protocol: on several TV boxes the sync SEND path stalls (WRTE-ready race)
     * while plain shell streaming works reliably.
     */
    suspend fun pushFile(local: File, remotePath: String,
                         onProgress: ((Long) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val quoted = remotePath.replace("'", "'\\''")
        val stream = manager.openStream("shell:cat > '$quoted'")
        try {
            val out = stream.openOutputStream()
            val input = local.inputStream()
            val buf = ByteArray(64 * 1024)
            var done = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                out.flush()
                done += n
                onProgress?.invoke(done)
            }
            input.close()
            out.close() // sends EOF to remote cat
        } finally {
            try {
                stream.close()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun deleteFile(path: String): String = exec("rm -rf '${path.replace("'", "'\\''")}'")

    suspend fun mkdir(path: String): String = exec("mkdir -p '${path.replace("'", "'\\''")}'")

    suspend fun rename(from: String, to: String): String =
        exec("mv '${from.replace("'", "'\\''")}' '${to.replace("'", "'\\''")}'")

    // ---------------- convenience ----------------

    /**
     * Remote-friendly key press: writes to the persistent input shell and returns
     * immediately. Repeats caused by holding a key are dropped once two earlier
     * presses are still queued, so releasing the key feels instant.
     */
    suspend fun keyEvent(code: Int) {
        val now = SystemClock.elapsedRealtime()
        val repeat = code == lastKeyCode && now - lastKeyAt in 0..320
        lastKeyCode = code
        lastKeyAt = now
        if (repeat && fastPending.get() >= 2) {
            val stalled = pendingSince > 0 && now - pendingSince > 4_000
            if (!stalled) return
            // The TV stopped answering markers; don't stay stuck forever.
            fastPending.set(0)
            pendingSince = 0
        }
        if (!writeFast("input keyevent $code")) {
            try {
                exec("input keyevent $code", timeoutMs = 15_000)
            } catch (_: Exception) {
            }
        }
    }

    /** Key press that is never dropped — used by macros and timed sequences. */
    suspend fun keyEventExact(code: Int) {
        if (!writeFast("input keyevent $code")) {
            try {
                exec("input keyevent $code", timeoutMs = 15_000)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Send the same key [count] times in a single `input` invocation.
     * Far cheaper than [count] separate process spawns on the TV.
     */
    suspend fun keyEventRepeated(code: Int, count: Int) {
        if (count <= 0) return
        val batch = count.coerceAtMost(40)
        val keys = List(batch) { code.toString() }.joinToString(" ")
        if (!writeFast("input keyevent $keys")) {
            try {
                exec("input keyevent $keys", timeoutMs = 15_000)
            } catch (_: Exception) {
            }
        }
    }

    /** Send text to the TV (spaces escaped for the shell `input` command). */
    suspend fun inputText(text: String) {
        if (text.isBlank()) return
        val escaped = text
            .replace("\\", "\\\\")
            .replace("'", "'\\''")
            .replace(" ", "%s")
        if (!writeFast("input text '$escaped'")) {
            try {
                exec("input text '$escaped'", timeoutMs = 20_000)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun inputTap(x: Int, y: Int) {
        if (!writeFast("input tap $x $y")) {
            try {
                exec("input tap $x $y", timeoutMs = 20_000)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun inputSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 250) {
        if (!writeFast("input swipe $x1 $y1 $x2 $y2 $durationMs")) {
            try {
                exec("input swipe $x1 $y1 $x2 $y2 $durationMs", timeoutMs = 25_000)
            } catch (_: Exception) {
            }
        }
    }

    private var cachedScreenSize: Pair<Int, Int>? = null

    /** TV screen resolution (cached). */
    suspend fun getScreenSize(): Pair<Int, Int> {
        cachedScreenSize?.let { return it }
        val out = exec("wm size", timeoutMs = 15_000)
        val match = Regex("(\\d{3,5})x(\\d{3,5})").find(out)
        val size = if (match != null) {
            match.groupValues[1].toInt() to match.groupValues[2].toInt()
        } else 1920 to 1080
        cachedScreenSize = size
        return size
    }

    /**
     * Wi-Fi (or ethernet) MAC address — needed for Wake-on-LAN.
     * `ip -o link` works on Android TVs where the sysfs address file is restricted
     * or returns the fake 02:00:00:00:00:00 address.
     */
    suspend fun getMacAddress(): String? {
        return try {
            val out = exec(
                "ip -o link show wlan0 2>/dev/null; ip -o link show eth0 2>/dev/null; " +
                    "cat /sys/class/net/wlan0/address 2>/dev/null; cat /sys/class/net/eth0/address 2>/dev/null",
                timeoutMs = 10_000,
            )
            val macRegex = Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")
            out.replace("\r\n", "\n").lineSequence()
                .mapNotNull { macRegex.find(it)?.value?.lowercase() }
                .firstOrNull { it != "00:00:00:00:00:00" && it != "02:00:00:00:00:00" && it != "ff:ff:ff:ff:ff:ff" }
        } catch (_: Exception) {
            null
        }
    }

    /** Open a file that already exists on the TV with its default app. */
    suspend fun openFileOnTv(remotePath: String, mimeType: String? = null) {
        val mime = mimeType ?: "*/*"
        exec("am start -a android.intent.action.VIEW -d 'file://$remotePath' -t '$mime'", timeoutMs = 20_000)
    }

    suspend fun rebootTv(): String = exec("reboot", timeoutMs = 20_000)

    /** Take a screenshot: save on device then pull via sync (binary safe). */
    suspend fun screencap(local: File) = withContext(Dispatchers.IO) {
        val remote = "/data/local/tmp/tvtools_screen.png"
        exec("screencap -p $remote", timeoutMs = 120_000)
        try {
            withSync { it.pull(remote, local) }
        } finally {
            try {
                exec("rm -f $remote", timeoutMs = 15_000)
            } catch (_: Exception) {
            }
        }
    }

    /** Push an APK and install it. Returns pm output (contains "Success" on success). */
    suspend fun installApk(localApk: File, onProgress: ((Long) -> Unit)? = null): String =
        withContext(Dispatchers.IO) {
            val remote = "/data/local/tmp/tvtools_install.apk"
            withSync { it.push(localApk, remote, 420, onProgress) } // 0644
            try {
                exec("pm install -r '$remote'")
            } finally {
                exec("rm -f '$remote'")
            }
        }

    /** All APK paths of a package (base + splits), in `pm path` order. */
    suspend fun getPackagePaths(pkg: String): List<String> {
        return exec("pm path $pkg", timeoutMs = 15_000)
            .replace("\r\n", "\n").lineSequence()
            .map { it.trim().removePrefix("package:") }
            .filter { it.isNotBlank() }
            .toList()
    }

    /**
     * Install a split APK set (.apks/.xapk contents, or base + split files).
     * Pushes every APK to /data/local/tmp and runs `pm install-multiple`.
     */
    suspend fun installSplitApk(apks: List<File>, onProgress: ((Long) -> Unit)? = null): String =
        withContext(Dispatchers.IO) {
            require(apks.isNotEmpty()) { "No APK files" }
            val remotePaths = ArrayList<String>()
            try {
                var pushed = 0L
                val total = apks.sumOf { it.length() }.coerceAtLeast(1)
                apks.forEachIndexed { index, file ->
                    val remote = "/data/local/tmp/tvtools_split_$index.apk"
                    pushFile(file, remote) { done ->
                        onProgress?.invoke(pushed + done)
                    }
                    pushed += file.length()
                    onProgress?.invoke(pushed)
                    remotePaths.add(remote)
                }
                exec(
                    "pm install-multiple -r -g ${remotePaths.joinToString(" ")}",
                    timeoutMs = 300_000,
                )
            } finally {
                try {
                    exec("rm -f ${remotePaths.joinToString(" ")} 2>/dev/null", timeoutMs = 20_000)
                } catch (_: Exception) {
                }
            }
        }

    suspend fun uninstallApp(pkg: String): String = exec("pm uninstall $pkg")
    suspend fun forceStopApp(pkg: String): String = exec("am force-stop $pkg")
    suspend fun clearAppData(pkg: String): String = exec("pm clear $pkg")
    suspend fun disableApp(pkg: String): String = exec("pm disable-user --user 0 $pkg")
    suspend fun enableApp(pkg: String): String = exec("pm enable $pkg")
    suspend fun launchApp(pkg: String): String =
        exec("monkey -p $pkg -c android.intent.category.LAUNCHER 1")

    suspend fun openAppSettingsOnTv(pkg: String): String =
        exec("am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$pkg")

    // ---------------- remote extras ----------------

    /** Send several key codes together (Android's `input keycombination`). */
    suspend fun inputKeyCombo(vararg codes: Int): String {
        return try {
            exec("input keycombination ${codes.joinToString(" ")}", timeoutMs = 15_000)
        } catch (_: Exception) {
            ""
        }
    }

    /** Open the TV's Google Assistant / voice search. */
    suspend fun assistant() {
        val out = try {
            exec("am start -a android.intent.action.VOICE_ASSIST", timeoutMs = 15_000)
        } catch (_: Exception) {
            ""
        }
        if (out.contains("Error", true) || out.contains("Exception", true) || out.isBlank()) {
            keyEvent(219) // KEYCODE_ASSIST
        }
    }

    suspend fun mouseMove(x: Int, y: Int) {
        // Air mouse fires every ~120 ms: never queue stale moves.
        if (fastPending.get() >= 2) return
        if (!writeFast("input motionevent MOVE $x $y")) {
            try {
                exec("input motionevent MOVE $x $y", timeoutMs = 12_000)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun mouseDown(x: Int, y: Int) {
        if (!writeFast("input motionevent DOWN $x $y")) {
            try {
                exec("input motionevent DOWN $x $y", timeoutMs = 12_000)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun mouseUp(x: Int, y: Int) {
        if (!writeFast("input motionevent UP $x $y")) {
            try {
                exec("input motionevent UP $x $y", timeoutMs = 12_000)
            } catch (_: Exception) {
            }
        }
    }

    /** Package currently in the foreground (best effort across Android TV versions). */
    suspend fun foregroundPackage(): String? {
        val out = try {
            exec(
                "dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity|mFocusedApp' | head -5",
                timeoutMs = 12_000,
            )
        } catch (_: Exception) {
            ""
        }
        Regex("([a-zA-Z][\\w.]*)/[\\w.$]+").find(out)?.groupValues?.get(1)?.let { return it }
        return Regex("package=([\\w.]+)").find(out)?.groupValues?.get(1)
    }

    /** Parse the active media session (title, artist, position, duration). */
    suspend fun nowPlaying(): com.tvtools.app.data.NowPlaying? {
        val dump = try {
            exec("dumpsys media_session", timeoutMs = 20_000)
        } catch (_: Exception) {
            return null
        }
        return com.tvtools.app.data.parseMediaSession(dump)
    }

    /** Best-effort seek: players move in ~10 s steps for FF/rewind key events. */
    suspend fun seekRelative(deltaMs: Long) {
        val steps = (kotlin.math.abs(deltaMs) / 10_000).coerceIn(1, 30).toInt()
        val key = if (deltaMs >= 0) 90 else 89
        keyEventRepeated(key, steps)
    }

    /** Set media volume (0–15) on the TV. */
    suspend fun setMediaVolume(level: Int) {
        val safe = level.coerceIn(0, 15)
        try {
            val out = exec("media volume --stream 3 --set $safe 2>&1", timeoutMs = 12_000)
            if (out.contains("Error", true) || out.contains("Unknown", true)) {
                exec("cmd media_session volume --stream 3 --set $safe 2>&1", timeoutMs = 12_000)
            }
        } catch (_: Exception) {
        }
    }

    /** Current media volume (0–15), or null when the TV doesn't report it. */
    suspend fun getMediaVolume(): Int? {
        val outputs = listOf(
            "cmd media_session volume --stream 3 --get 2>&1",
            "media volume --stream 3 --get 2>&1",
        )
        for (command in outputs) {
            val out = try {
                exec(command, timeoutMs = 10_000)
            } catch (_: Exception) {
                ""
            }
            Regex("volume is (\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    suspend fun mediaCommand(command: String): String =
        exec("cmd media_session dispatch $command", timeoutMs = 15_000)
}
