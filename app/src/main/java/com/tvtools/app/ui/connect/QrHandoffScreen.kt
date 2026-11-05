package com.tvtools.app.ui.connect

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.tvtools.app.AppServices
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.data.DeviceProto
import com.tvtools.app.data.TvDevice
import com.tvtools.app.ui.labs.LabScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hand a TV profile to another phone without typing an IP:
 *  - shows a QR code with the saved TV's host/port,
 *  - scans a QR from a photo taken with the system camera (no camera permission needed),
 *  - connects to the TV described by the scanned profile.
 *
 * The QR contains no credentials — only the local address the other phone
 * must be able to reach (same Wi-Fi).
 */
@Composable
fun QrHandoffScreen(onBack: () -> Unit, onConnected: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var device by remember { mutableStateOf<TvDevice?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) { device = AppServices.prefs.loadLastDevice() }

    fun handleText(text: String) {
        val profile = parseProfile(text)
        if (profile == null) {
            status = "That QR isn't a TV Tools profile."
            return
        }
        busy = true
        status = "Connecting to ${profile.name}…"
        scope.launch {
            when (val result = AppServices.adb.connect(profile.host, profile.port)) {
                AdbClient.ConnectResult.Success -> {
                    val model = runCatching {
                        AppServices.adb.exec("getprop ro.product.model", timeoutMs = 8_000).trim()
                    }.getOrNull().orEmpty()
                    val friendly = profile.copy(name = model.ifBlank { profile.name })
                    AppServices.prefs.saveLastDevice(friendly)
                    AppServices.prefs.saveDevice(friendly)
                    status = "Connected ✓"
                    busy = false
                    onConnected(friendly.name)
                }
                AdbClient.ConnectResult.PairingRequired -> {
                    busy = false
                    status = "This TV needs pairing first — open Home, tap the TV and enter the pairing code once."
                }
                is AdbClient.ConnectResult.Error -> {
                    busy = false
                    status = "Couldn't connect: ${result.message}"
                }
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingFile
        if (ok && file != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { decodeQr(file) }
                file.delete()
                if (text == null) status = "No QR code found in that photo." else handleText(text)
            }
        }
    }

    fun scanWithCamera() {
        try {
            val file = File(context.cacheDir, "qr_${System.currentTimeMillis()}.jpg")
            pendingFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            takePicture.launch(uri)
        } catch (e: Exception) {
            status = "No camera app available: ${e.message}"
        }
    }

    fun pasteFromClipboard() {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        val text = cm?.primaryClip?.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) status = "Clipboard is empty." else handleText(text)
    }

    LabScaffold("Share TV via QR", onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Point another phone's camera at this code. Both phones must be on the same Wi-Fi as the TV.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            val current = device
            val link = current?.let { profileLink(it) }
            if (link != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    val bmp = remember(link) { qrBitmap(link, 640) }
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "TV profile QR code",
                        modifier = Modifier.padding(16.dp).size(240.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "${current.name} • ${current.host}:${current.port}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, link)
                        }
                        context.startActivity(Intent.createChooser(send, "Share TV profile"))
                    }) { Text("Share link") }
                    OutlinedButton(onClick = {
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("TV profile", link))
                        status = "Link copied — paste it on the other phone."
                    }) { Text("Copy link") }
                }
            } else {
                Text(
                    "Connect to a TV once — its profile will appear here as a QR code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Connect with a QR", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Scan a code shown on another phone running TV Tools.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { scanWithCamera() }, enabled = !busy) { Text("Scan with camera") }
                        TextButton(onClick = { pasteFromClipboard() }, enabled = !busy) { Text("Paste link") }
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }

            status?.let {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp)
                ) {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun profileLink(device: TvDevice): String =
    "tvtools://tv?host=${Uri.encode(device.host)}&port=${device.port}" +
        "&proto=${device.proto.name}&name=${Uri.encode(device.name)}"

private fun parseProfile(text: String): TvDevice? {
    val uri = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "tvtools" || uri.host != "tv") return null
    val host = uri.getQueryParameter("host") ?: return null
    val port = uri.getQueryParameter("port")?.toIntOrNull() ?: return null
    val name = uri.getQueryParameter("name") ?: host
    val proto = runCatching {
        DeviceProto.valueOf(uri.getQueryParameter("proto") ?: "TCP")
    }.getOrDefault(DeviceProto.TCP)
    return TvDevice(name = name, host = host, port = port, proto = proto)
}

private fun qrBitmap(content: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix.get(x, y)) 0xFF102016.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

private fun decodeQr(file: File): String? {
    return try {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val source = RGBLuminanceSource(bmp.width, bmp.height, pixels)
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(
                        BarcodeFormat.QR_CODE,
                        BarcodeFormat.DATA_MATRIX,
                    ),
                    DecodeHintType.TRY_HARDER to true,
                )
            )
        }
        reader.decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: Exception) {
        null
    }
}
