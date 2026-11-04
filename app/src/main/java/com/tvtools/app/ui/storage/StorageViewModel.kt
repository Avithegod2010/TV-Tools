package com.tvtools.app.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StorageItem(val path: String, val size: Long, val isDir: Boolean)

data class StorageUiState(
    val total: Long = 0,
    val used: Long = 0,
    val folders: List<StorageItem> = emptyList(),
    val largeFiles: List<StorageItem> = emptyList(),
    val loading: Boolean = true,
    val busyLabel: String? = null,
)

class StorageViewModel : ViewModel() {

    private val adb = AppServices.adb

    private val _ui = MutableStateFlow(StorageUiState())
    val ui = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init { scan() }

    fun scan() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            try {
                // Capacity
                val df = adb.exec("df -k /sdcard 2>/dev/null || df -k /data", timeoutMs = 20_000)
                val line = df.replace("\r\n", "\n").lines().lastOrNull { it.isNotBlank() }
                val p = line?.split(Regex("\\s+")) ?: emptyList()
                val total = (p.getOrNull(1)?.toLongOrNull() ?: 0) * 1024
                val used = (p.getOrNull(2)?.toLongOrNull() ?: 0) * 1024

                // Top-level folder sizes
                val du = adb.exec(
                    "du -sk /sdcard/* 2>/dev/null | sort -rn | head -30",
                    timeoutMs = 120_000,
                )
                val folders = du.replace("\r\n", "\n").lines().mapNotNull { l ->
                    val parts = l.trim().split(Regex("\\s+"), limit = 2)
                    if (parts.size < 2) return@mapNotNull null
                    val kb = parts[0].toLongOrNull() ?: return@mapNotNull null
                    val path = parts[1]
                    StorageItem(path, kb * 1024, path.substringAfterLast('/').contains('.').not())
                }.filter { it.size > 0 }

                // Large files
                val find = adb.exec(
                    "find /sdcard -type f -size +50M 2>/dev/null | head -40",
                    timeoutMs = 120_000,
                )
                val paths = find.replace("\r\n", "\n").lines().map { it.trim() }.filter { it.isNotBlank() }
                val large = if (paths.isEmpty()) emptyList() else {
                    val quoted = paths.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
                    val stat = adb.exec("stat -c '%s %n' $quoted 2>/dev/null", timeoutMs = 60_000)
                    stat.replace("\r\n", "\n").lines().mapNotNull { l ->
                        val size = l.substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
                        StorageItem(l.substringAfter(' '), size, false)
                    }.sortedByDescending { it.size }
                }

                _ui.value = _ui.value.copy(
                    total = total,
                    used = used,
                    folders = folders,
                    largeFiles = large,
                    loading = false,
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false)
                _messages.emit("Scan failed: ${e.message}")
            }
        }
    }

    fun delete(item: StorageItem) {
        viewModelScope.launch {
            try {
                adb.deleteFile(item.path)
                _messages.emit("Deleted ${item.path.substringAfterLast('/')}")
                scan()
            } catch (e: Exception) {
                _messages.emit("Delete failed: ${e.message}")
            }
        }
    }

    fun trimCache() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busyLabel = "Trimming caches…")
            try {
                adb.exec("pm trim-caches 32G 2>&1", timeoutMs = 120_000)
                _messages.emit("✅ Caches trimmed")
                scan()
            } catch (e: Exception) {
                _messages.emit("Cache trim failed: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(busyLabel = null)
            }
        }
    }
}
