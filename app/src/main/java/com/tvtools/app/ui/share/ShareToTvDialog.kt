package com.tvtools.app.ui.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.ui.common.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface ShareState {
    data class Working(val label: String, val progress: Float = -1f) : ShareState
    data class Done(val remotePath: String, val localFile: File, val mime: String?) : ShareState
    data class Failed(val message: String) : ShareState
}

/**
 * Receives a share-sheet file, auto-connects to the last TV and uploads it to /sdcard/Download.
 * For APKs offers install; for media offers opening on the TV.
 */
@Composable
fun ShareToTvDialog(
    uri: Uri,
    mime: String?,
    onDismiss: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ShareState>(ShareState.Working("Connecting to your TV…")) }
    var fileName by remember { mutableStateOf("file") }
    var fileSize by remember { mutableStateOf(0L) }

    LaunchedEffect(uri) {
        try {
            val meta = queryMeta(context, uri)
            fileName = meta.first
            fileSize = meta.second

            val adb = AppServices.adb
            if (!adb.isConnected) {
                val last = AppServices.prefs.loadLastDevice()
                    ?: run {
                        state = ShareState.Failed("No TV saved yet. Open TV Tools and connect to your TV first.")
                        return@LaunchedEffect
                    }
                state = ShareState.Working("Connecting to ${last.name}…")
                when (val r = adb.connect(last.host, last.port)) {
                    is AdbClient.ConnectResult.Error -> {
                        state = ShareState.Failed("Could not reach ${last.name}: ${r.message}")
                        return@LaunchedEffect
                    }
                    AdbClient.ConnectResult.PairingRequired -> {
                        state = ShareState.Failed("The TV needs pairing. Open TV Tools to pair it again.")
                        return@LaunchedEffect
                    }
                    AdbClient.ConnectResult.Success -> Unit
                }
            }

            state = ShareState.Working("Preparing $fileName…")
            val tmp = withContext(Dispatchers.IO) {
                val f = File(TvToolsApp.instance.cacheDir, "share_$fileName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    f.outputStream().use { input.copyTo(it) }
                }
                f
            }

            val remotePath = "/sdcard/Download/$fileName"
            state = ShareState.Working("Sending to TV…", 0f)
            val total = tmp.length().coerceAtLeast(1)
            adb.pushFile(tmp, remotePath) { done ->
                state = ShareState.Working("Sending to TV…", done.toFloat() / total)
            }
            state = ShareState.Done(remotePath, tmp, mime)
        } catch (e: Exception) {
            state = ShareState.Failed(e.message ?: "Share failed")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state is ShareState.Failed) "Send to TV" else "Send to TV") },
        text = {
            Column {
                when (val s = state) {
                    is ShareState.Working -> {
                        Text(fileName, style = MaterialTheme.typography.titleSmall)
                        if (fileSize > 0) {
                            Text(
                                formatBytes(fileSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(s.label, style = MaterialTheme.typography.bodyMedium)
                        if (s.progress >= 0f) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { s.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    is ShareState.Done -> {
                        Text("✅ Sent to the TV", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.remotePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is ShareState.Failed -> {
                        Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is ShareState.Done -> {
                    Button(onClick = {
                        val adb = AppServices.adb
                        val isApk = s.mime?.contains("android.package") == true ||
                            s.remotePath.endsWith(".apk", true)
                        scope.launch(Dispatchers.IO) {
                            try {
                                if (isApk) {
                                    adb.exec("pm install -r '${s.remotePath}'", timeoutMs = 180_000)
                                } else {
                                    adb.openFileOnTv(s.remotePath, s.mime)
                                }
                            } catch (_: Exception) {
                            }
                        }
                        onDismiss()
                    }) {
                        Text(if (s.remotePath.endsWith(".apk", true)) "Install on TV" else "Open on TV")
                    }
                }
                is ShareState.Failed -> {
                    Button(onClick = { onDismiss(); onOpenDevices() }) { Text("Open devices") }
                }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun queryMeta(context: Context, uri: Uri): Pair<String, Long> {
    var name = "file_${System.currentTimeMillis()}"
    var size = 0L
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) c.getString(ni)?.let { name = it }
                if (si >= 0) size = c.getLong(si)
            }
        }
    }
    return name to size
}
