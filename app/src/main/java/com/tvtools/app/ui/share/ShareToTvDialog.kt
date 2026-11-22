package com.tvtools.app.ui.share

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
import com.tvtools.app.PendingShare
import com.tvtools.app.TvToolsApp
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.ui.common.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface ShareState {
    data class Ready(val fileName: String, val size: Long) : ShareState
    data class Working(val label: String, val progress: Float = -1f) : ShareState
    data class Done(val remotePath: String, val localFile: File, val mime: String?) : ShareState
    data class Failed(val message: String) : ShareState
}

/**
 * Receives a share-sheet file and, only after the user confirms, connects to the last TV and
 * uploads it to /sdcard/Download. The sender's name was already validated by [ShareFileNames].
 * For APKs offers install; for media offers opening on the TV.
 */
@Composable
fun ShareToTvDialog(
    share: PendingShare,
    onDismiss: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(share.uri) {
        mutableStateOf<ShareState>(ShareState.Ready(share.displayName, share.size))
    }

    fun send() {
        if (state !is ShareState.Ready) return
        val fileName = share.displayName
        state = ShareState.Working("Connecting to your TV…")
        scope.launch {
            try {
                val adb = AppServices.adb
                if (!adb.isConnected) {
                    val last = AppServices.prefs.loadLastDevice()
                        ?: run {
                            state = ShareState.Failed("No TV saved yet. Open TV Tools and connect to your TV first.")
                            return@launch
                        }
                    state = ShareState.Working("Connecting to ${last.name}…")
                    when (val r = adb.connect(last.host, last.port)) {
                        is AdbClient.ConnectResult.Error -> {
                            state = ShareState.Failed("Could not reach ${last.name}: ${r.message}")
                            return@launch
                        }
                        AdbClient.ConnectResult.PairingRequired -> {
                            state = ShareState.Failed("The TV needs pairing. Open TV Tools to pair it again.")
                            return@launch
                        }
                        AdbClient.ConnectResult.Success -> Unit
                    }
                }

                state = ShareState.Working("Preparing $fileName…")
                val tmp = withContext(Dispatchers.IO) {
                    val f = File(TvToolsApp.instance.cacheDir, ShareFileNames.cacheName(fileName))
                    val stream = context.contentResolver.openInputStream(share.uri)
                        ?: throw IllegalStateException("The sharing app did not provide a readable file.")
                    stream.use { input -> f.outputStream().use { input.copyTo(it) } }
                    f
                }

                val remotePath = ShareFileNames.remotePath(fileName)
                state = ShareState.Working("Sending to TV…", 0f)
                val total = tmp.length().coerceAtLeast(1)
                adb.pushFile(tmp, remotePath) { done ->
                    state = ShareState.Working("Sending to TV…", done.toFloat() / total)
                }
                state = ShareState.Done(remotePath, tmp, share.mime)
            } catch (e: Exception) {
                state = ShareState.Failed(e.message ?: "Share failed")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send to TV") },
        text = {
            Column {
                when (val s = state) {
                    is ShareState.Ready -> {
                        Text(s.fileName, style = MaterialTheme.typography.titleSmall)
                        if (s.size > 0) {
                            Text(
                                formatBytes(s.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Nothing is sent until you tap Send to TV.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is ShareState.Working -> {
                        Text(share.displayName, style = MaterialTheme.typography.titleSmall)
                        if (share.size > 0) {
                            Text(
                                formatBytes(share.size),
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
                is ShareState.Ready -> {
                    Button(onClick = { send() }) { Text("Send to TV") }
                }
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
