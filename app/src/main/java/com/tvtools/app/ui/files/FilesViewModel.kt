package com.tvtools.app.ui.files

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
    val thumbPath: String? = null,
)

data class FilesUiState(
    val path: String = "/sdcard",
    val entries: List<FileItem> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val uploadProgress: Float = -1f,
)

class FilesViewModel : ViewModel() {

    private val context: Context get() = TvToolsApp.instance
    private val adb = AppServices.adb

    private val _ui = MutableStateFlow(FilesUiState())
    val ui: StateFlow<FilesUiState> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val thumbDir = File(context.cacheDir, "thumbs").apply { mkdirs() }

    init {
        refresh()
    }

    val isAtRoot: Boolean get() = _ui.value.path == "/sdcard" || _ui.value.path == "/sdcard/"

    fun refresh() = list(_ui.value.path)

    fun navigateTo(path: String) = list(path)

    fun up() {
        val current = _ui.value.path.trimEnd('/')
        if (current == "/sdcard") return
        val parent = current.substringBeforeLast('/', "/sdcard").ifBlank { "/sdcard" }
        list(parent)
    }

    private fun list(path: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(path = path, loading = true)
            try {
                val entries = adb.listFiles(path).map { e ->
                    FileItem(
                        name = e.name,
                        path = path.trimEnd('/') + "/" + e.name,
                        isDir = e.isDirectory,
                        size = e.size,
                        mtime = e.mtime,
                    )
                }.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
                _ui.value = _ui.value.copy(entries = entries, loading = false)
                loadThumbnails(entries)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false)
                _messages.emit("Can't open $path: ${e.message}")
            }
        }
    }

    /** Pull small images for thumbnails, in background. */
    private fun loadThumbnails(entries: List<FileItem>) {
        viewModelScope.launch {
            entries.filter { !it.isDir && isImage(it.name) && it.size in 1..10_000_000 }
                .forEach { item ->
                    val key = item.path.hashCode().toString(16)
                    val out = File(thumbDir, "$key.img")
                    if (out.exists()) {
                        updateThumb(item.path, out.absolutePath)
                        return@forEach
                    }
                    try {
                        withContext(Dispatchers.IO) { adb.pullFile(item.path, out) }
                        updateThumb(item.path, out.absolutePath)
                    } catch (_: Exception) {
                    }
                }
        }
    }

    private fun updateThumb(path: String, thumb: String) {
        _ui.value = _ui.value.copy(
            entries = _ui.value.entries.map { if (it.path == path) it.copy(thumbPath = thumb) else it }
        )
    }

    private fun isImage(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    }

    fun upload(uri: Uri) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true, uploadProgress = 0f)
        viewModelScope.launch {
            try {
                val name = queryDisplayName(uri) ?: "upload_${System.currentTimeMillis()}"
                val tmp = File(context.cacheDir, "ul_$name")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                }
                val total = tmp.length().coerceAtLeast(1)
                val remote = _ui.value.path.trimEnd('/') + "/" + name
                adb.pushFile(tmp, remote) { done ->
                    _ui.value = _ui.value.copy(uploadProgress = done.toFloat() / total)
                }
                tmp.delete()
                _messages.emit("✅ Uploaded $name")
                refresh()
            } catch (e: Exception) {
                _messages.emit("Upload failed: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(busy = false, uploadProgress = -1f)
            }
        }
    }

    fun download(item: FileItem) {
        viewModelScope.launch {
            try {
                val dir = File(context.getExternalFilesDir(null), "Downloads").apply { mkdirs() }
                val out = File(dir, item.name)
                _messages.emit("Downloading ${item.name}…")
                withContext(Dispatchers.IO) { adb.pullFile(item.path, out, item.size) }
                _messages.emit("✅ Saved: ${out.absolutePath}")
            } catch (e: Exception) {
                _messages.emit("Download failed: ${e.message}")
            }
        }
    }

    fun delete(item: FileItem) {
        viewModelScope.launch {
            try {
                adb.deleteFile(item.path)
                _messages.emit("Deleted ${item.name}")
                refresh()
            } catch (e: Exception) {
                _messages.emit("Delete failed: ${e.message}")
            }
        }
    }

    fun rename(item: FileItem, newName: String) {
        if (newName.isBlank() || newName.contains('/')) return
        viewModelScope.launch {
            try {
                val newPath = _ui.value.path.trimEnd('/') + "/" + newName
                adb.rename(item.path, newPath)
                refresh()
            } catch (e: Exception) {
                _messages.emit("Rename failed: ${e.message}")
            }
        }
    }

    fun mkdir(name: String) {
        if (name.isBlank() || name.contains('/')) return
        viewModelScope.launch {
            try {
                adb.mkdir(_ui.value.path.trimEnd('/') + "/" + name)
                refresh()
            } catch (e: Exception) {
                _messages.emit("Mkdir failed: ${e.message}")
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()
}
