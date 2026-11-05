package com.tvtools.app.ui.labs

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import com.tvtools.app.data.DeviceStats
import com.tvtools.app.ui.common.LoadingRow
import com.tvtools.app.ui.common.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// ---------------- shared scaffold ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScaffold(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = { action?.invoke() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
}

// ---------------- DLNA / casting ----------------

data class DlnaRenderer(val name: String, val location: String, val controlUrl: String)

data class CastUiState(
    val scanning: Boolean = false,
    val renderers: List<DlnaRenderer> = emptyList(),
    val status: String? = null,
)

class CastViewModel : ViewModel() {
    private val _ui = MutableStateFlow(CastUiState())
    val ui = _ui.asStateFlow()

    fun scan() {
        if (_ui.value.scanning) return
        _ui.value = _ui.value.copy(scanning = true, renderers = emptyList(), status = "Searching for DLNA renderers…")
        viewModelScope.launch(Dispatchers.IO) {
            val found = ArrayList<Pair<String, String>>() // location -> name
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 1200
                    val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
                    val data = msg.toByteArray()
                    repeat(3) { socket.send(DatagramPacket(data, data.size, InetAddress.getByName("239.255.255.250"), 1900)) }
                    val end = System.currentTimeMillis() + 4000
                    val buf = ByteArray(4096)
                    while (System.currentTimeMillis() < end) {
                        try {
                            val packet = DatagramPacket(buf, buf.size)
                            socket.receive(packet)
                            val text = String(packet.data, 0, packet.length)
                            val location = Regex("(?i)LOCATION:\\s*(.+)").find(text)?.groupValues?.get(1)?.trim() ?: continue
                            found.add(location to location)
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
            val renderers = found.distinctBy { it.first }.mapNotNull { (loc, _) ->
                try { resolveRenderer(loc) } catch (_: Exception) { null }
            }
            _ui.value = CastUiState(
                scanning = false,
                renderers = renderers,
                status = if (renderers.isEmpty()) "No DLNA renderer found — make sure the TV is on and supports DLNA/DMR." else null,
            )
        }
    }

    private fun resolveRenderer(location: String): DlnaRenderer? {
        val base = URL(location)
        val conn = (base.openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000; readTimeout = 3000
        }
        val xml = conn.inputStream.bufferedReader().use { it.readText() }
        val name = Regex("<friendlyName>([^<]+)</friendlyName>").find(xml)?.groupValues?.get(1)?.trim()
            ?: return null
        val block = Regex("<service>([\\s\\S]*?)</service>").findAll(xml).firstOrNull {
            it.groupValues[1].contains("AVTransport")
        }?.groupValues?.get(1) ?: return null
        val control = Regex("<controlURL>([^<]+)</controlURL>").find(block)?.groupValues?.get(1)?.trim()
            ?: return null
        val controlUrl = if (control.startsWith("http")) control else URL(base, control).toString()
        return DlnaRenderer(name, location, controlUrl)
    }

    fun cast(uri: Uri, renderer: DlnaRenderer) {
        _ui.value = _ui.value.copy(status = "Preparing…")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = TvToolsApp.instance
                val name = queryName(context, uri)
                val file = File(context.cacheDir, "cast_$name")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                val server = MediaHttpServer(file)
                server.start()
                _ui.value = _ui.value.copy(status = "Serving ${file.name} (${formatBytes(file.length())})…")
                val phoneIp = localIp(context) ?: throw java.io.IOException("No Wi-Fi IP")
                val url = "http://$phoneIp:${server.port}/${file.name}"
                soap(renderer.controlUrl, "SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>$url</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>")
                soap(renderer.controlUrl, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
                _ui.value = _ui.value.copy(status = "Casting to ${renderer.name} — keep this screen open.")
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(status = "Cast failed: ${e.message}")
            }
        }
    }

    private fun soap(controlUrl: String, action: String, args: String) {
        val body = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">$args</u:$action></s:Body></s:Envelope>"""
        val conn = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 4000; readTimeout = 4000
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPACTION", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.responseCode
    }

    private fun queryName(context: Context, uri: Uri): String {
        var name = "media_${System.currentTimeMillis()}.mp4"
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i)?.let { name = it }
            }
        }
        return name
    }

    private fun localIp(context: Context): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.activeNetwork?.let { cm.getLinkProperties(it) }?.linkAddresses
                ?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress
        } catch (_: Exception) { null }
    }
}

/** Minimal single-file HTTP server for DLNA playback. */
class MediaHttpServer(private val file: File) {
    private var server: ServerSocket? = null
    var port: Int = 0
        private set

    fun start() {
        val socket = ServerSocket(0)
        server = socket
        port = socket.localPort
        Thread {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    Thread { serve(client) }.start()
                } catch (_: Exception) { break }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun serve(client: Socket) {
        try {
            client.use { c ->
                val input = c.getInputStream().bufferedReader()
                var line = input.readLine() ?: return
                while (line.isNotEmpty()) line = input.readLine() ?: break
                val bytes = file.readBytes()
                val header = "HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                c.getOutputStream().use { out ->
                    out.write(header.toByteArray())
                    out.write(bytes)
                }
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
fun CastScreen(onBack: () -> Unit, vm: CastViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    var pickerOpen by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> pendingUri = uri }

    LabScaffold("Cast media to TV", onBack, action = {
        IconButton(onClick = vm::scan) { Icon(Icons.Default.Refresh, "Scan") }
    }) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Find DLNA renderers on your network, pick a video or music file and stream it from your phone to the TV.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch("*/*") }) { Text(if (pendingUri == null) "Pick media" else "Change media") }
                Button(onClick = vm::scan, enabled = !ui.scanning) { Text(if (ui.scanning) "Scanning…" else "Scan") }
            }
            if (ui.scanning) LoadingRow("Looking for renderers…")
            ui.status?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))) {
                    Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (ui.renderers.isNotEmpty()) {
                Text("Renderers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ui.renderers.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge)
                            Text(r.controlUrl, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            enabled = pendingUri != null,
                            onClick = { pendingUri?.let { vm.cast(it, r) } },
                        ) { Text("Cast") }
                    }
                }
            }
        }
    }
}

// ---------------- Speed test ----------------

data class SpeedUiState(
    val running: Boolean = false,
    val pingMs: Double? = null,
    val uploadMbps: Double? = null,
    val downloadMbps: Double? = null,
    val message: String? = null,
)

class SpeedTestViewModel : ViewModel() {
    private val _ui = MutableStateFlow(SpeedUiState())
    val ui = _ui.asStateFlow()

    fun run() {
        if (_ui.value.running) return
        _ui.value = SpeedUiState(running = true)
        viewModelScope.launch(Dispatchers.IO) {
            val adb = AppServices.adb
            val context = TvToolsApp.instance
            try {
                val last = AppServices.prefs.loadLastDevice()
                if (!adb.isConnected && last != null) adb.connect(last.host, last.port)
                val ip = last?.host ?: "192.168.0.1"
                val pingOut = try { adb.exec("ping -c 5 -W 2 $ip 2>/dev/null", timeoutMs = 20_000) } catch (_: Exception) { "" }
                val ping = Regex("time=([0-9.]+)").findAll(pingOut).mapNotNull { it.groupValues[1].toDoubleOrNull() }.average().takeIf { !it.isNaN() }

                val size = 8 * 1024 * 1024
                val tmp = File(context.cacheDir, "speedtest.bin")
                tmp.outputStream().use { out ->
                    val chunk = ByteArray(64 * 1024)
                    var written = 0
                    val random = java.util.Random()
                    while (written < size) {
                        random.nextBytes(chunk)
                        val n = min(chunk.size, size - written)
                        out.write(chunk, 0, n)
                        written += n
                    }
                }
                var t0 = System.currentTimeMillis()
                adb.pushFile(tmp, "/data/local/tmp/tvtools_speed.bin")
                var up = size.toDouble() / 1_000_000.0 / ((System.currentTimeMillis() - t0) / 1000.0).coerceAtLeast(0.001)
                tmp.delete()

                t0 = System.currentTimeMillis()
                val back = File(context.cacheDir, "speedtest_back.bin")
                adb.pullFile("/data/local/tmp/tvtools_speed.bin", back)
                val down = size.toDouble() / 1_000_000.0 / ((System.currentTimeMillis() - t0) / 1000.0).coerceAtLeast(0.001)
                back.delete()
                try { adb.exec("rm -f /data/local/tmp/tvtools_speed.bin") } catch (_: Exception) {}

                _ui.value = SpeedUiState(
                    running = false,
                    pingMs = ping,
                    uploadMbps = up * 8,
                    downloadMbps = down * 8,
                    message = "Measured over the ADB/Wi-Fi link (8 MB transfer).",
                )
            } catch (e: Exception) {
                _ui.value = SpeedUiState(running = false, message = "Speed test failed: ${e.message}")
            }
        }
    }
}

@Composable
fun SpeedTestScreen(onBack: () -> Unit, vm: SpeedTestViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val ui by vm.ui.collectAsState()
    LabScaffold("Speed test", onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Measures latency and throughput between your phone and the TV over the same Wi-Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = vm::run, enabled = !ui.running) { Text(if (ui.running) "Testing…" else "Run speed test") }
            if (ui.running) LoadingRow("Sending 8 MB to the TV and back…")
            ui.pingMs?.let { MetricCard("Latency", "%.0f ms".format(it)) }
            ui.uploadMbps?.let { MetricCard("Upload → TV", "%.1f Mbps".format(it)) }
            ui.downloadMbps?.let { MetricCard("Download ← TV", "%.1f Mbps".format(it)) }
            ui.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    )) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------- Live TV (TIF dump) ----------------

class LiveTvViewModel : ViewModel() {
    private val _text = MutableStateFlow<String?>(null)
    val text = _text.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    init { load() }

    fun load() {
        _loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val out = AppServices.adb.exec("dumpsys tv_input 2>/dev/null | head -c 12000", timeoutMs = 25_000)
                _text.value = out.ifBlank { "No TV Input Framework data — this TV may not expose live channels." }
            } catch (e: Exception) {
                _text.value = "Couldn't read TV inputs: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }
}

@Composable
fun LiveTvScreen(onBack: () -> Unit, vm: LiveTvViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val text by vm.text.collectAsState()
    val loading by vm.loading.collectAsState()
    LabScaffold("Live TV inputs", onBack, action = {
        IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, "Reload") }
    }) {
        when {
            loading -> LoadingRow("Reading TV inputs…")
            else -> Text(
                text.orEmpty(),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
    }
}

// ---------------- Mouse & keyboard ----------------

class MouseKeyboardViewModel : ViewModel() {
    private val adb = AppServices.adb
    var tvW = 1920
    var tvH = 1080

    init {
        viewModelScope.launch {
            runCatching { adb.getScreenSize().let { (w, h) -> tvW = w; tvH = h } }
        }
    }

    fun tap(rx: Float, ry: Float) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { adb.inputTap((rx * tvW).toInt(), (ry * tvH).toInt()) } }
    }

    fun move(rx: Float, ry: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { adb.exec("input motionevent MOVE ${(rx * tvW).toInt()} ${(ry * tvH).toInt()}", timeoutMs = 10_000) }
        }
    }

    fun down(rx: Float, ry: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { adb.exec("input motionevent DOWN ${(rx * tvW).toInt()} ${(ry * tvH).toInt()}", timeoutMs = 10_000) }
        }
    }

    fun up(rx: Float, ry: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { adb.exec("input motionevent UP ${(rx * tvW).toInt()} ${(ry * tvH).toInt()}", timeoutMs = 10_000) }
        }
    }

    fun text(value: String) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { adb.inputText(value) } }
    }

    fun key(code: Int) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { adb.keyEvent(code) } }
    }
}

@Composable
fun MouseKeyboardScreen(onBack: () -> Unit, vm: MouseKeyboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    var text by remember { mutableStateOf("") }
    var lastMove by remember { mutableLongStateOf(0L) }
    LabScaffold("Mouse & keyboard", onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Use the pad as a TV mouse (tap = click, drag = move) and send text to the TV.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.key(19) }) { Text("▲") }
                OutlinedButton(onClick = { vm.key(20) }) { Text("▼") }
                OutlinedButton(onClick = { vm.key(21) }) { Text("◀") }
                OutlinedButton(onClick = { vm.key(22) }) { Text("▶") }
                OutlinedButton(onClick = { vm.key(23) }) { Text("OK") }
                OutlinedButton(onClick = { vm.key(4) }) { Text("Back") }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { off ->
                                vm.tap(off.x / size.width, off.y / size.height)
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { off ->
                                vm.down(off.x / size.width, off.y / size.height)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val now = System.currentTimeMillis()
                                if (now - lastMove > 90) {
                                    lastMove = now
                                    vm.move(change.position.x / size.width, change.position.y / size.height)
                                }
                            },
                            onDragEnd = {
                                vm.up(0.5f, 0.5f)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Mouse pad", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type text for the TV…") },
                    singleLine = true,
                )
                Button(onClick = { vm.text(text); text = "" }, enabled = text.isNotBlank()) { Text("Send") }
            }
        }
    }
}

// ---------------- Health monitor ----------------

class HealthViewModel : ViewModel() {
    private val adb = AppServices.adb
    private val _stats = MutableStateFlow<DeviceStats?>(null)
    val stats = _stats.asStateFlow()
    private val _samples = MutableStateFlow<List<Float>>(emptyList())
    val cpuSamples = _samples.asStateFlow()
    private val _ramSamples = MutableStateFlow<List<Float>>(emptyList())
    val ramSamples = _ramSamples.asStateFlow()
    private var job: kotlinx.coroutines.Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            while (true) {
                try {
                    if (!adb.isConnected) break
                    val s = AppServices.infoProvider.getStats()
                    _stats.value = s
                    _samples.value = (_samples.value + (s.cpuUsagePercent / 100f)).takeLast(30)
                    val ramUsed = (s.ramTotal - s.ramAvailable).toFloat() / s.ramTotal.coerceAtLeast(1)
                    _ramSamples.value = (_ramSamples.value + ramUsed).takeLast(30)
                } catch (_: Exception) {
                }
                delay(5000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    override fun onCleared() = stop()
}

@Composable
fun HealthScreen(onBack: () -> Unit, vm: HealthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val s by vm.stats.collectAsState()
    val cpu by vm.cpuSamples.collectAsState()
    val ram by vm.ramSamples.collectAsState()
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }
    LabScaffold("TV health", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val st = s
            if (st == null) {
                LoadingRow("Reading health…")
                return@Column
            }
            val storagePct = if (st.storageTotal > 0) st.storageUsed.toFloat() / st.storageTotal else 0f
            val ramPct = if (st.ramTotal > 0) (st.ramTotal - st.ramAvailable).toFloat() / st.ramTotal else 0f
            HealthCard("CPU", "${st.cpuUsagePercent}%", cpu.lastOrNull() ?: 0f, cpu, Color(0xFF4ADE80))
            HealthCard("RAM", "${(ramPct * 100).toInt()}%", ramPct, ram, Color(0xFF60A5FA))
            HealthCard("Storage", "${(storagePct * 100).toInt()}%", storagePct, emptyList(), Color(0xFFF59E0B))
            val alerts = buildList {
                if (storagePct > 0.9f) add("⚠ Storage is over 90% full")
                if (ramPct > 0.9f) add("⚠ RAM is almost exhausted")
                if (st.cpuUsagePercent > 90) add("⚠ CPU is very busy (${st.cpuUsagePercent}%)")
            }
            if (alerts.isEmpty()) {
                Text("✅ Everything looks healthy", style = MaterialTheme.typography.bodyMedium)
            } else {
                alerts.forEach {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun HealthCard(title: String, valueText: String, pct: Float, samples: List<Float>, color: Color) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    )) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(valueText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            if (samples.size >= 2) {
                Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                    val w = size.width
                    val h = size.height
                    val path = Path()
                    samples.forEachIndexed { i, v ->
                        val x = w * i / (samples.size - 1)
                        val y = h - (v.coerceIn(0f, 1f) * h)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width = 4f))
                }
            } else {
                LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------- Multi-user ----------------

data class TvUser(val id: Int, val name: String, val running: Boolean)

class UsersViewModel : ViewModel() {
    private val adb = AppServices.adb
    private val _users = MutableStateFlow<List<TvUser>>(emptyList())
    val users = _users.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val out = adb.exec("pm list users 2>/dev/null", timeoutMs = 15_000)
                val list = Regex("UserInfo\\{(\\d+):([^:}]+)").findAll(out).map {
                    val id = it.groupValues[1].toInt()
                    TvUser(id, it.groupValues[2].trim(), out.contains("UserInfo{$id:") && out.lines().any { l -> l.contains("UserInfo{$id:") && l.contains("running") })
                }.toList()
                _users.value = list
            } catch (e: Exception) {
                _message.value = "Couldn't list users: ${e.message}"
            }
        }
    }

    fun switchUser(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val out = adb.exec("am switch-user $id", timeoutMs = 20_000)
                _message.value = if (out.isBlank()) "Switched to user $id" else out.trim()
                load()
            } catch (e: Exception) {
                _message.value = "Switch failed: ${e.message}"
            }
        }
    }
}

@Composable
fun UsersScreen(onBack: () -> Unit, vm: UsersViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val users by vm.users.collectAsState()
    val msg by vm.message.collectAsState()
    LabScaffold("TV users", onBack, action = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, "Reload") } }) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Switch between user profiles on the TV.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            users.forEach { u ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name, style = MaterialTheme.typography.bodyLarge)
                        Text("id ${u.id}" + if (u.running) " · active" else "", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(onClick = { vm.switchUser(u.id) }, enabled = !u.running) { Text("Switch") }
                }
            }
            if (users.isEmpty()) LoadingRow("Reading users…")
        }
    }
}

// ---------------- IR blaster ----------------

class IrViewModel : ViewModel() {
    private val _hasIr = MutableStateFlow(false)
    val hasIr = _hasIr.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        val context = TvToolsApp.instance
        val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? android.hardware.ConsumerIrManager
        _hasIr.value = ir?.hasIrEmitter() == true
    }

    fun send(freq: Int, patternText: String) {
        val context = TvToolsApp.instance
        val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? android.hardware.ConsumerIrManager
        if (ir == null || !ir.hasIrEmitter()) {
            _message.value = "This phone has no IR blaster."
            return
        }
        try {
            val pattern = patternText.split(',', ' ', '\n').mapNotNull { it.trim().toIntOrNull() }.toIntArray()
            if (pattern.isEmpty()) throw IllegalArgumentException("Pattern is empty")
            ir.transmit(freq, pattern)
            _message.value = "IR pattern sent (${pattern.size} bursts)"
        } catch (e: Exception) {
            _message.value = "IR failed: ${e.message}"
        }
    }
}

@Composable
fun IrScreen(onBack: () -> Unit, vm: IrViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val hasIr by vm.hasIr.collectAsState()
    val msg by vm.message.collectAsState()
    var freq by remember { mutableStateOf("38000") }
    var pattern by remember { mutableStateOf("9000,4500,560,560,560,1690") }
    LabScaffold("IR blaster", onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!hasIr) {
                Text("This phone doesn't have an IR blaster. This feature is available on phones with one (e.g. Xiaomi/Redmi models).", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Send a raw IR pattern to the TV.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = freq, onValueChange = { freq = it.filter(Char::isDigit) }, label = { Text("Frequency (Hz)") })
                OutlinedTextField(value = pattern, onValueChange = { pattern = it }, label = { Text("Pattern (µs, comma separated)") })
                Button(onClick = { vm.send(freq.toIntOrNull() ?: 38000, pattern) }) { Text("Send IR") }
            }
            msg?.let { Text(it) }
        }
    }
}

// ---------------- Kids mode ----------------

class KidsViewModel : ViewModel() {
    private val adb = AppServices.adb
    private val _apps = MutableStateFlow<List<String>>(emptyList())
    val apps = _apps.asStateFlow()
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected = _selected.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val out = adb.exec("pm list packages -3 2>/dev/null", timeoutMs = 15_000)
                _apps.value = out.replace("\r\n", "\n").lines()
                    .map { it.removePrefix("package:") }.filter { it.isNotBlank() }
            } catch (e: Exception) {
                _message.value = "Couldn't list apps: ${e.message}"
            }
        }
    }

    fun toggle(pkg: String) {
        _selected.value = _selected.value.let { if (pkg in it) it - pkg else it + pkg }
    }

    fun apply(enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            var ok = 0
            _selected.value.forEach { pkg ->
                try {
                    if (enable) adb.disableApp(pkg) else adb.enableApp(pkg)
                    ok++
                } catch (_: Exception) {
                }
            }
            _message.value = if (enable) "Kids mode ON — $ok apps disabled" else "Kids mode OFF — $ok apps re-enabled"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsScreen(onBack: () -> Unit, vm: KidsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val apps by vm.apps.collectAsState()
    val selected by vm.selected.collectAsState()
    val msg by vm.message.collectAsState()
    LabScaffold("Kids mode", onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Pick distracting apps and disable them on the TV. Turn kids mode off to re-enable them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.apply(true) }, enabled = selected.isNotEmpty()) { Text("Kids mode ON") }
                OutlinedButton(onClick = { vm.apply(false) }, enabled = selected.isNotEmpty()) { Text("Kids mode OFF") }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(apps.size) { i ->
                    val pkg = apps[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pkg in selected, onCheckedChange = { vm.toggle(pkg) })
                        Text(pkg, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ---------------- Launchpad (favorites) ----------------

class LaunchpadViewModel : ViewModel() {
    private val adb = AppServices.adb
    private val context = TvToolsApp.instance
    private val _all = MutableStateFlow<List<String>>(emptyList())
    val all = _all.asStateFlow()
    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites = _favorites.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val out = adb.exec("pm list packages -3 2>/dev/null", timeoutMs = 15_000)
                val pkgs = out.replace("\r\n", "\n").lines().map { it.removePrefix("package:") }.filter { it.isNotBlank() }.sorted()
                _all.value = pkgs
                val sp = context.getSharedPreferences("launchpad", Context.MODE_PRIVATE)
                val saved = sp.getStringSet("favs", emptySet())?.toList().orEmpty()
                _favorites.value = saved.filter { it in pkgs }
            }
        }
    }

    fun toggle(pkg: String) {
        val list = _favorites.value.toMutableList()
        if (pkg in list) list.remove(pkg) else list.add(pkg)
        _favorites.value = list
        context.getSharedPreferences("launchpad", Context.MODE_PRIVATE).edit()
            .putStringSet("favs", list.toSet()).apply()
    }

    fun move(pkg: String, up: Boolean) {
        val list = _favorites.value.toMutableList()
        val i = list.indexOf(pkg)
        if (i < 0) return
        val j = if (up) i - 1 else i + 1
        if (j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        _favorites.value = list
        context.getSharedPreferences("launchpad", Context.MODE_PRIVATE).edit()
            .putStringSet("favs", list.toSet()).apply()
    }

    fun launch(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { adb.launchApp(pkg) } }
    }
}

@Composable
fun LaunchpadScreen(onBack: () -> Unit, vm: LaunchpadViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val all by vm.all.collectAsState()
    val favorites by vm.favorites.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    LabScaffold("Launchpad", onBack, action = { TextButton(onClick = { showPicker = true }) { Text("Edit") } }) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (favorites.isEmpty()) {
                Text("Add your favourite TV apps for one-tap launching.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { showPicker = true }) { Text("Add apps") }
            }
            favorites.forEach { pkg ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(pkg, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { vm.move(pkg, true) }) { Text("↑") }
                    TextButton(onClick = { vm.move(pkg, false) }) { Text("↓") }
                    Button(onClick = { vm.launch(pkg) }) { Text("Open") }
                }
            }
        }
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Favourite apps") },
            text = {
                LazyColumn(Modifier.height(400.dp)) {
                    items(all.size) { i ->
                        val pkg = all[i]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = pkg in favorites, onCheckedChange = { vm.toggle(pkg) })
                            Text(pkg, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Done") } },
        )
    }
}

// ---------------- Screen-off / ambient helpers ----------------

suspend fun ambientStart(adb: com.tvtools.app.adb.AdbClient): String {
    return try {
        val out = adb.exec("cmd dream start 2>&1", timeoutMs = 15_000)
        if (out.contains("Unknown", true) || out.contains("Error", true)) "This TV doesn't expose an ambient/dream command." else "Ambient mode started"
    } catch (e: Exception) {
        "Ambient mode failed: ${e.message}"
    }
}

suspend fun setScreenTimeout(adb: com.tvtools.app.adb.AdbClient, ms: Int): String {
    return try {
        adb.exec("settings put system screen_off_timeout $ms", timeoutMs = 15_000)
        "Screen timeout set"
    } catch (e: Exception) {
        "Timeout change failed: ${e.message}"
    }
}


