package com.tvtools.app.adb

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.tvtools.app.R
import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * High-level, coroutine-friendly API over [TvAdbConnectionManager].
 * Every blocking call runs on [Dispatchers.IO].
 */
class AdbClient internal constructor(context: Context, private val networks: LanNetworkMonitor) {

    private val appContext = context.applicationContext
    private val manager = TvAdbConnectionManager.getInstance(appContext)

    data class ConnectedDevice(val host: String, val port: Int, val preferencesHost: String = host)

    private class Session(val device: ConnectedDevice, val network: LanNetwork?, val transport: AdbConnection) {
        // libadb's stream ID allocator is not thread-safe.
        val opening = Mutex()
        @Volatile var screenSize: Pair<Int, Int>? = null
    }

    private class OperationSession(val session: Session) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<OperationSession>
    }

    private val connectionMutex = Mutex()
    private val connectionWorker = Semaphore(1)
    private val sessionGuard = Any()

    @Volatile private var session: Session? = null
    @Volatile private var lastDevice: ConnectedDevice? = null
    @Volatile private var lastNetwork: LanNetwork? = null
    @Volatile private var resumeSavedDevice = true
    private val revision = MutableStateFlow(0L)
    private val _connections = MutableStateFlow<ConnectedDevice?>(null)
    internal val connections = _connections.asStateFlow()
    internal val recoveryRevision: Long get() = revision.value
    internal val mayResumeSavedDevice: Boolean get() = resumeSavedDevice
    internal val previousDevice: ConnectedDevice? get() = lastDevice
    internal val previousNetwork: LanNetwork? get() = lastNetwork

    val connectedDevice: ConnectedDevice?
        get() = currentSession()?.device

    @Volatile
    var lastConnectMs: Long = 0
        private set

    val isConnected: Boolean get() = connectedDevice != null

    sealed class ConnectResult {
        data object Success : ConnectResult()
        data object PairingRequired : ConnectResult()
        data class Error(val message: String) : ConnectResult()
    }

    suspend fun pair(host: String, port: Int, code: String): Result<Unit> {
        val request = revision.updateAndGet { it + 1 }
        connectionMutex.lock()
        return try {
            val network = networks.current.value
            whileCurrent(network, request) {
                val paired = runConnectionWork(timeoutMs = 15_000) {
                    runInterruptible {
                        val address = probe(host, port, network, authorizedOnly = false)
                        manager.pair(address, port, code.trim())
                    }
                }
                when (paired) {
                    true -> Result.success(Unit)
                    false -> Result.failure(IOException(message(R.string.network_pairing_rejected)))
                    null -> Result.failure(IOException(message(R.string.network_handshake_timeout)))
                }
            }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            Result.failure(IOException(message(R.string.network_changed)))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connectionMutex.unlock()
        }
    }

    suspend fun connect(host: String, port: Int): ConnectResult =
        establish(ConnectedDevice(host, port), authorizedOnly = false, expectedRevision = null)

    internal suspend fun connectKnown(
        device: ConnectedDevice,
        expectedRevision: Long,
        authorizedOnly: Boolean = true,
    ): ConnectResult = establish(device, authorizedOnly, expectedRevision)

    private suspend fun establish(
        device: ConnectedDevice,
        authorizedOnly: Boolean,
        expectedRevision: Long?,
    ): ConnectResult {
        val request = expectedRevision ?: revision.updateAndGet { it + 1 }
        if (expectedRevision == null) connectionMutex.lock()
        else if (!connectionMutex.tryLock()) return ConnectResult.Error(message(R.string.network_busy))
        try {
            if (revision.value != request) return ConnectResult.Error(message(R.string.network_changed))

            val network = networks.current.value
            if (connectedDevice?.let { it.host == device.host && it.port == device.port } == true) {
                return ConnectResult.Success
            }
            val started = SystemClock.elapsedRealtime()
            val replaced = detach()
            if (replaced != null) withTimeoutOrNull(500) { closeTransport(replaced.transport).join() }
            val transport = AtomicReference<AdbConnection?>()
            var installed = false
            return try {
                whileCurrent(network, request) {
                    val connected = runConnectionWork(if (authorizedOnly) 8_000 else 15_000) {
                        try {
                            runInterruptible {
                                val address = probe(device.host, device.port, network, authorizedOnly)
                                val candidate = manager.createConnection(address, device.port)
                                transport.set(candidate)
                                candidate.connect(if (authorizedOnly) 5 else 12, TimeUnit.SECONDS, authorizedOnly)
                            }
                        } finally {
                            if (!currentCoroutineContext().isActive) {
                                transport.getAndSet(null)?.let(::closeTransport)
                            }
                        }
                    }
                    if (connected != true) {
                        return@whileCurrent ConnectResult.Error(message(R.string.network_handshake_timeout))
                    }
                    // Clear abandoned fast-input handles after closing their transport.
                    fastMutex.withLock { closeFastShellLocked() }
                    inputServerMutex.withLock { closeInputServerState() }
                    synchronized(sessionGuard) {
                        if (networks.current.value != network || revision.value != request) {
                            return@synchronized ConnectResult.Error(message(R.string.network_changed))
                        }
                        session = Session(device, network, checkNotNull(transport.get()))
                        lastDevice = device
                        lastNetwork = network
                        resumeSavedDevice = true
                        lastConnectMs = SystemClock.elapsedRealtime() - started
                        installed = true
                        _connections.value = device
                        ConnectResult.Success
                    }
                }
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                ConnectResult.Error(message(R.string.network_changed))
            } catch (_: AdbPairingRequiredException) {
                ConnectResult.PairingRequired
            } catch (_: AdbAuthenticationFailedException) {
                ConnectResult.PairingRequired
            } catch (_: Exception) {
                ConnectResult.Error(message(R.string.network_tv_unreachable))
            } finally {
                if (!installed) transport.getAndSet(null)?.let(::closeTransport)
            }
        } finally {
            connectionMutex.unlock()
        }
    }

    /** libadb opens TCP before its own handshake timer and cannot accept a Network socket. */
    private fun probe(host: String, port: Int, network: LanNetwork?, authorizedOnly: Boolean): String {
        val endpoint = InetSocketAddress(host, port)
        return Socket().use { socket ->
            socket.connect(endpoint, 1_500)
            if (authorizedOnly && network?.addresses?.none { it.first == socket.localAddress } != false) {
                throw IOException(message(R.string.network_changed))
            }
            endpoint.address?.hostAddress ?: host
        }
    }

    private fun message(id: Int): String = appContext.getString(id)

    // Do not let a blocking libadb constructor hold the caller or accumulate worker threads.
    private suspend fun <T> runConnectionWork(timeoutMs: Long, operation: suspend () -> T): T? {
        if (!connectionWorker.tryAcquire()) throw IOException(message(R.string.network_busy))
        val worker = fastScope.async { operation() }
        worker.invokeOnCompletion { connectionWorker.release() }
        return try {
            withTimeoutOrNull(timeoutMs) { worker.await() }
        } finally {
            worker.cancel()
        }
    }

    private suspend fun <T> whileCurrent(
        network: LanNetwork?, request: Long, operation: suspend () -> T,
    ): T = coroutineScope {
        val work = async { operation() }
        val changed = launch {
            combine(networks.current, revision) { current, id -> current != network || id != request }
                .first { it }
            work.cancel()
        }
        try {
            work.await()
        } finally {
            changed.cancel()
        }
    }

    private fun currentSession(): Session? = session?.takeIf {
        it.network == networks.current.value && it.transport.isConnected && it.transport.isConnectionEstablished
    }

    private suspend fun operationSession(): Session {
        val active = currentSession()
        val expected = currentCoroutineContext()[OperationSession]?.session
        if (expected != null && expected !== active) throw IOException(message(R.string.network_changed))
        return active ?: throw IOException(message(R.string.network_no_session))
    }

    private suspend fun <T> onSession(operation: suspend () -> T): T =
        withContext(Dispatchers.IO + OperationSession(operationSession())) { operation() }

    private fun detach(expected: Session? = null): Session? = synchronized(sessionGuard) {
        val previous = session ?: return@synchronized null
        if (expected != null && previous !== expected) return@synchronized null
        session = null
        _connections.value = null
        previous
    }

    private fun closeTransport(transport: AdbConnection): Job =
        fastScope.launch { runCatching { transport.close() } }

    private fun closeOnDeadline(active: Session, timeoutMs: Long): Job = fastScope.launch {
        delay(timeoutMs)
        detach(active)?.let { closeTransport(it.transport) }
    }

    internal fun dropUnhealthyConnection() {
        val obsolete = session ?: return
        if (currentSession() !== obsolete) detach(obsolete)?.let { closeTransport(it.transport) }
    }

    suspend fun disconnect() {
        revision.updateAndGet { it + 1 }
        resumeSavedDevice = false
        connectionMutex.withLock {
            lastDevice = null
            lastNetwork = null
            detach()?.let { closeTransport(it.transport) }
        }
    }

    /** Retry the last active endpoint, without prompting to authorize an unknown TV. */
    suspend fun reconnect(): ConnectResult? {
        val device = lastDevice ?: return null
        if (lastNetwork != networks.current.value) return ConnectResult.Error(message(R.string.network_changed))
        return connectKnown(device, recoveryRevision)
    }

    /** Verify an actual reply on this session, independent of queued remote input. */
    internal suspend fun ping(): Boolean {
        val active = session ?: return false
        val alive = try {
            withContext(OperationSession(active)) {
                exec("printf tvtools-heartbeat", timeoutMs = 4_000) == "tvtools-heartbeat"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!alive) detach(active)?.let { closeTransport(it.transport) }
        return alive
    }

    private suspend fun openStream(destination: String, timeoutMs: Long = 5_000): AdbStream {
        val active = operationSession()
        val deadline = closeOnDeadline(active, timeoutMs)
        val stream = try {
            withTimeoutOrNull(timeoutMs) {
                active.opening.withLock {
                    if (currentSession() !== active) throw IOException(message(R.string.network_changed))
                    runInterruptible(Dispatchers.IO) { active.transport.open(destination) }
                }
            }
        } finally {
            deadline.cancel()
        }
        if (stream == null) {
            detach(active)?.let { closeTransport(it.transport) }
            throw IOException(message(R.string.network_stream_timeout))
        }
        if (currentSession() !== active) {
            runCatching { stream.close() }
            throw IOException(message(R.string.network_changed))
        }
        return stream
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

    // Persistent TV-side injector. A tiny jar runs under app_process as the shell
    // user and calls InputManager.injectInputEvent directly. That removes the
    // per-key `input`/ART process spawn, which is the main remaining latency on
    // older TVs. If it cannot start, every key falls back to the input shell.
    private val inputServerMutex = Mutex()
    private var inputServer: AdbStream? = null
    private var inputServerOut: OutputStream? = null
    private var inputServerDrain: Job? = null

    @Volatile
    private var inputServerReady = false

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

    private suspend fun ensureFastShell(): AdbStream {
        fastStream?.let { return it }
        val stream = openStream("shell:")
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

    /** No mutex required: safe to call from the drain's finally block. */
    private fun closeInputServerState() {
        inputServerDrain?.cancel()
        inputServerDrain = null
        try {
            inputServerOut?.close()
        } catch (_: Exception) {
        }
        inputServerOut = null
        try {
            inputServer?.close()
        } catch (_: Exception) {
        }
        inputServer = null
        inputServerReady = false
    }

    /**
     * Start (or reuse) the persistent TV-side key injector.
     *
     * Pushes a ~2 KB jar to /data/local/tmp and runs it with app_process. It talks
     * over stdin, so no port is exposed. Returns false when the TV blocks the
     * mechanism (old firmware, missing app_process), in which case callers keep
     * using the input shell.
     */
    suspend fun startInputServer(): Boolean = if (!isConnected) false else onSession {
        if (inputServerReady) return@onSession true
        inputServerMutex.withLock {
            if (inputServerReady) return@withLock true
            var stream: AdbStream? = null
            try {
                val jar = File(appContext.cacheDir, "tvtools-input.jar")
                if (!jar.exists() || jar.length() == 0L) {
                    appContext.assets.open("tvtools-input.jar").use { input ->
                        jar.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                val remoteJar = "/data/local/tmp/tvtools-input.jar"
                val remoteSize = try {
                    exec("stat -c %s $remoteJar 2>/dev/null", timeoutMs = 5_000)
                        .trim().toLongOrNull()
                } catch (_: Exception) {
                    null
                }
                if (remoteSize != jar.length()) {
                    pushFile(jar, remoteJar)
                }

                stream = openStream(
                    "shell:CLASSPATH=$remoteJar app_process /system/bin " +
                        "com.tvtools.app.input.InputServer"
                )
                val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
                val first = withTimeoutOrNull(6_000) {
                    runInterruptible(Dispatchers.IO) { reader.readLine() }
                }
                if (first != "TVT_READY") {
                    Log.w("TvToolsInput", "injector did not start: ${first ?: "timeout"}")
                    try {
                        stream.close()
                    } catch (_: Exception) {
                    }
                    return@withLock false
                }

                inputServer = stream
                inputServerOut = stream.openOutputStream()
                inputServerReady = true
                val owned = stream
                inputServerDrain = fastScope.launch {
                    try {
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.startsWith("TVT_FAIL") || line.startsWith("TVT_ERR")) {
                                Log.w("TvToolsInput", line)
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        if (inputServer === owned) closeInputServerState()
                    }
                }
                Log.i("TvToolsInput", "persistent injector ready")
                true
            } catch (e: Exception) {
                Log.w("TvToolsInput", "injector unavailable: ${e.message}", e)
                try {
                    stream?.close()
                } catch (_: Exception) {
                }
                closeInputServerState()
                false
            }
        }
    }

    /** Write one or more Android keycodes to the injector, one line at a time. */
    private suspend fun writeInputServer(keys: String): Boolean = withContext(fastDispatcher) {
        inputServerMutex.withLock {
            if (!inputServerReady) return@withLock false
            try {
                val out = inputServerOut ?: return@withLock false
                out.write((keys + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
                lastInputAt = SystemClock.elapsedRealtime()
                true
            } catch (e: Exception) {
                Log.w("TvToolsInput", "injector write failed: ${e.message}")
                closeInputServerState()
                false
            }
        }
    }

    /** Open both fast paths before the first key so the first press feels instant. */
    suspend fun warmUpInput() {
        // Keep the shell warm first: it is the fallback while the injector starts.
        writeFast("true", trackCompletion = false)
        startInputServer()
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
     * Close the underlying transport at the deadline even if libadb is blocked
     * sending a stream close packet.
     */
    suspend fun exec(command: String, timeoutMs: Long = 45_000): String = onSession {
        val active = operationSession()
        val deadline = closeOnDeadline(active, timeoutMs)
        try {
            val stream = openStream("shell:$command", timeoutMs = minOf(timeoutMs, 5_000))
            try {
                val result = withTimeoutOrNull(timeoutMs) { runInterruptible { readStreamFully(stream) } }
                if (result == null || currentSession() !== active) {
                    throw IOException(appContext.getString(R.string.network_command_timeout, timeoutMs / 1000))
                }
                result
            } finally {
                runCatching { stream.close() }
            }
        } finally {
            deadline.cancel()
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
        openStream("shell:")
    }

    /** Run a long-lived command whose stdout is binary (e.g. screenrecord). */
    suspend fun openCommandStream(command: String): AdbStream = withContext(Dispatchers.IO) {
        openStream("shell:$command")
    }

    /**
     * Open a raw ADB `exec:` service. No PTY is allocated, so binary output
     * (H.264, tar, …) is not translated or buffered by a terminal.
     */
    suspend fun openExecStream(command: String): AdbStream = withContext(Dispatchers.IO) {
        openStream("exec:$command")
    }

    suspend fun execLines(command: String): List<String> =
        exec(command).replace("\r\n", "\n").split("\n").filter { it.isNotBlank() }

    // ---------------- sync (file ops) ----------------

    private suspend fun <T> withSync(block: (AdbSync) -> T): T {
        val stream = openStream("sync:")
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
        val stream = openStream("shell:cat > '$quoted'")
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
     * Remote-friendly key press. Prefers the persistent injector (a few ms);
     * falls back to the input shell, where repeats are bounded so a long hold
     * cannot leave a backlog after release.
     */
    suspend fun keyEvent(code: Int) {
        if (inputServerReady && writeInputServer(code.toString())) return
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
        if (inputServerReady && writeInputServer(code.toString())) return
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
        if (inputServerReady && writeInputServer(keys)) return
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

    /** TV screen resolution, cached only for the current session. */
    suspend fun getScreenSize(): Pair<Int, Int> = onSession {
        val active = operationSession()
        active.screenSize?.let { return@onSession it }
        val out = exec("wm size", timeoutMs = 15_000)
        val match = Regex("(\\d{3,5})x(\\d{3,5})").find(out)
        val size = if (match != null) {
            match.groupValues[1].toInt() to match.groupValues[2].toInt()
        } else 1920 to 1080
        active.screenSize = size
        size
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
    suspend fun screencap(local: File) = onSession {
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
        onSession {
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
        onSession {
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
