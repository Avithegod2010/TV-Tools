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

sealed interface FilePreview {
    data class Image(val file: File) : FilePreview
    data class Text(val content: String) : FilePreview
    data class Message(val text: String) : FilePreview
}

data class FilesUiState(
    val path: String = "/sdcard",
    val entries: List<FileItem> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val uploadProgress: Float = -1f,
    val selection: Set<String> = emptySet(),
    val downloadLabel: String? = null,
    val downloadProgress: Float = -1f,
    val queueRemaining: Int = 0,
    val grid: Boolean = false,
)

class FilesViewModel : ViewModel() {

    private val context: Context get() = TvToolsApp.instance
    private val adb = AppServices.adb

    private val _ui = MutableStateFlow(FilesUiState())
    val ui: StateFlow<FilesUiState> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _preview = MutableStateFlow<FilePreview?>(null)
    val preview: StateFlow<FilePreview?> = _preview.asStateFlow()

    private val thumbDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
    private val downloadQueue = ArrayDeque<FileItem>()
    private var downloading = false

    init { refresh() }

    val isAtRoot: Boolean get() = _ui.value.path.trimEnd('/') == "/sdcard"

    fun refresh() = list(_ui.value.path)

    fun navigateTo(path: String) = list(path).also { clearSelection() }

    fun up() {
        val current = _ui.value.path.trimEnd('/')
        if (current == "/sdcard") return
        val parent = current.substringBeforeLast('/', "/sdcard").ifBlank { "/sdcard" }
        list(parent)
        clearSelection()
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

    private fun loadThumbnails(entries: List<FileItem>) {
        viewModelScope.launch {
            entries.filter { !it.isDir && isImage(it.name) && it.size in 1..10_000_000 }
                .forEach { item ->
                    if (item.thumbPath != null) return@forEach
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

    // ---------------- selection ----------------

    fun toggleSelection(path: String) {
        val sel = _ui.value.selection
        _ui.value = _ui.value.copy(selection = if (path in sel) sel - path else sel + path)
    }

    fun clearSelection() {
        _ui.value = _ui.value.copy(selection = emptySet())
    }

    fun toggleGrid() {
        _ui.value = _ui.value.copy(grid = !_ui.value.grid)
    }

    fun selectAll() {
        _ui.value = _ui.value.copy(selection = _ui.value.entries.map { it.path }.toSet())
    }

    private fun selectedItems(): List<FileItem> =
        _ui.value.entries.filter { it.path in _ui.value.selection }

    // ---------------- clipboard: copy / move ----------------

    fun copySelected(destination: String) = transfer(destination, move = false)
    fun moveSelected(destination: String) = transfer(destination, move = true)

    private fun transfer(destination: String, move: Boolean) {
        val items = selectedItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            try {
                var ok = 0
                items.forEach { item ->
                    val dest = destination.trimEnd('/') + "/" + item.name
                    val cmd = if (move) "mv" else "cp -r"
                    adb.exec("$cmd '${item.path.replace("'", "'\\''")}' '${dest.replace("'", "'\\''")}'", timeoutMs = 120_000)
                    ok++
                }
                _messages.emit("${if (move) "Moved" else "Copied"} $ok item(s)")
                clearSelection()
                refresh()
            } catch (e: Exception) {
                _messages.emit("Transfer failed: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }

    // ---------------- download queue ----------------

    fun downloadSelected() {
        val items = selectedItems().filter { !it.isDir }
        if (items.isEmpty()) return
        items.forEach { enqueueDownload(it) }
        clearSelection()
    }

    fun enqueueDownload(item: FileItem) {
        if (item.isDir) {
            _messages.tryEmit("Folders can't be downloaded directly — copy them instead.")
            return
        }
        downloadQueue.addLast(item)
        _ui.value = _ui.value.copy(queueRemaining = downloadQueue.size)
        if (!downloading) processQueue()
    }

    private fun processQueue() {
        if (downloading) return
        downloading = true
        viewModelScope.launch {
            val dir = File(context.getExternalFilesDir(null), "Downloads").apply { mkdirs() }
            while (downloadQueue.isNotEmpty()) {
                val item = downloadQueue.removeFirst()
                _ui.value = _ui.value.copy(
                    queueRemaining = downloadQueue.size,
                    downloadLabel = item.name,
                    downloadProgress = 0f,
                )
                try {
                    val out = File(dir, item.name)
                    if (out.exists()) out.delete()
                    val total = item.size.coerceAtLeast(1)
                    adb.pullFile(item.path, out, item.size) { done ->
                        _ui.value = _ui.value.copy(downloadProgress = done.toFloat() / total)
                    }
                    _messages.emit("Saved ${item.name} to Downloads")
                } catch (e: Exception) {
                    _messages.emit("Download failed: ${e.message}")
                }
            }
            _ui.value = _ui.value.copy(downloadLabel = null, downloadProgress = -1f, queueRemaining = 0)
            downloading = false
        }
    }

    // ---------------- single-item actions ----------------

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

    fun deleteSelected() {
        val items = selectedItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            try {
                items.forEach { adb.deleteFile(it.path) }
                _messages.emit("Deleted ${items.size} item(s)")
                clearSelection()
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

    // ---------------- upload ----------------

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
                _messages.emit("Uploaded $name")
                refresh()
            } catch (e: Exception) {
                _messages.emit("Upload failed: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(busy = false, uploadProgress = -1f)
            }
        }
    }

    // ---------------- preview ----------------

    fun preview(item: FileItem) {
        if (item.isDir) return
        viewModelScope.launch {
            val ext = item.name.substringAfterLast('.', "").lowercase()
            when {
                isImage(item.name) && item.size <= 25_000_000 -> {
                    try {
                        val out = File(context.cacheDir, "preview_${item.name.hashCode()}.img")
                        adb.pullFile(item.path, out, item.size)
                        _preview.value = FilePreview.Image(out)
                    } catch (e: Exception) {
                        _preview.value = FilePreview.Message("Couldn't load image: ${e.message}")
                    }
                }
                ext in setOf("txt", "log", "json", "xml", "md", "ini", "conf", "sh", "properties") -> {
                    try {
                        val text = adb.exec("head -c 24000 '${item.path.replace("'", "'\\''")}'", timeoutMs = 30_000)
                        _preview.value = FilePreview.Text(text)
                    } catch (e: Exception) {
                        _preview.value = FilePreview.Message("Couldn't read file: ${e.message}")
                    }
                }
                else -> {
                    _preview.value = FilePreview.Message(
                        "${item.name}\n${formatBytesLocal(item.size)}\n\nNo inline preview for this type — use Download."
                    )
                }
            }
        }
    }

    fun dismissPreview() {
        _preview.value = null
    }

    private fun formatBytesLocal(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = -1
        do { v /= 1024.0; i++ } while (v >= 1024 && i < units.size - 1)
        return String.format(java.util.Locale.US, "%.1f %s", v, units[i])
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()
}
