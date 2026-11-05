package com.tvtools.app.ui.labs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import com.tvtools.app.ui.common.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Vector

data class SftpFile(val name: String, val path: String, val isDir: Boolean, val size: Long)

data class SftpUiState(
    val host: String = "",
    val port: String = "22",
    val user: String = "",
    val password: String = "",
    val connected: Boolean = false,
    val path: String = "/",
    val files: List<SftpFile> = emptyList(),
    val busy: Boolean = false,
    val status: String? = null,
)

class SftpViewModel : ViewModel() {
    private var session: Session? = null
    private var sftp: ChannelSftp? = null

    private val _ui = MutableStateFlow(SftpUiState())
    val ui = _ui.asStateFlow()

    fun update(block: (SftpUiState) -> SftpUiState) {
        _ui.value = block(_ui.value)
    }

    fun connect() {
        val s = _ui.value
        if (s.busy) return
        _ui.value = s.copy(busy = true, status = "Connecting…")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsch = JSch()
                val newSession = jsch.getSession(s.user, s.host, s.port.toIntOrNull() ?: 22)
                newSession.setPassword(s.password)
                newSession.setConfig("StrictHostKeyChecking", "no")
                newSession.connect(8000)
                val channel = newSession.openChannel("sftp") as ChannelSftp
                channel.connect(5000)
                session = newSession
                sftp = channel
                _ui.value = _ui.value.copy(connected = true, busy = false, status = "Connected", path = "/")
                list("/")
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, connected = false, status = "Connection failed: ${e.message}")
            }
        }
    }

    fun list(path: String) {
        val channel = sftp ?: return
        _ui.value = _ui.value.copy(busy = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                @Suppress("UNCHECKED_CAST")
                val vector = channel.ls(path) as Vector<ChannelSftp.LsEntry>
                val files = vector.filter { it.filename != "." && it.filename != ".." }
                    .map {
                        val p = if (path.endsWith("/")) path + it.filename else "$path/${it.filename}"
                        SftpFile(it.filename, p, it.attrs.isDir, it.attrs.size)
                    }
                    .sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
                _ui.value = _ui.value.copy(path = path, files = files, busy = false)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, status = "Listing failed: ${e.message}")
            }
        }
    }

    fun up() {
        val current = _ui.value.path.trimEnd('/')
        if (current.isEmpty()) return
        val parent = current.substringBeforeLast('/', "")
        list(parent.ifEmpty { "/" })
    }

    /** Download from SFTP and push straight to the TV's Download folder. */
    fun sendToTv(file: SftpFile) {
        val channel = sftp ?: return
        if (file.isDir) return
        _ui.value = _ui.value.copy(busy = true, status = "Downloading ${file.name}…")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val local = File(TvToolsApp.instance.cacheDir, "sftp_${file.name}")
                channel.get(file.path, local.absolutePath)
                _ui.value = _ui.value.copy(status = "Sending to TV…")
                AppServices.adb.pushFile(local, "/sdcard/Download/${file.name}")
                local.delete()
                _ui.value = _ui.value.copy(busy = false, status = "✅ ${file.name} sent to the TV")
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, status = "Transfer failed: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        runCatching { sftp?.disconnect() }
        runCatching { session?.disconnect() }
    }
}

@Composable
fun SftpScreen(onBack: () -> Unit, vm: SftpViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val ui by vm.ui.collectAsState()
    LabScaffold("SFTP browser", onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!ui.connected) {
                Text(
                    "Browse an SFTP server and send files straight to the TV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ui.host,
                        onValueChange = { v -> vm.update { it.copy(host = v.trim()) } },
                        label = { Text("Host") },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = ui.port,
                        onValueChange = { v -> vm.update { it.copy(port = v.filter(Char::isDigit)) } },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = ui.user,
                    onValueChange = { v -> vm.update { it.copy(user = v) } },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = ui.password,
                    onValueChange = { v -> vm.update { it.copy(password = v) } },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                Button(
                    onClick = vm::connect,
                    enabled = !ui.busy && ui.host.isNotBlank() && ui.user.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (ui.busy) "Connecting…" else "Connect") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = vm::up) { Text("..") }
                    Text(ui.path, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(ui.files, key = { it.path }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (f.isDir) vm.list(f.path) else vm.sendToTv(f)
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (f.isDir) Icons.Default.Folder else Icons.Default.Download,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                if (!f.isDir) Text(formatBytes(f.size), style = MaterialTheme.typography.labelSmall)
                            }
                            if (!f.isDir) Text("Send", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            ui.status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
