package com.tvtools.app.ui.tools

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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

data class ToolsUiState(
    val screenshot: ImageBitmap? = null,
    val screenshotLoading: Boolean = false,
    val busyLabel: String? = null,
    val progress: Float = -1f, // -1 = indeterminate/none
)

class ToolsViewModel : ViewModel() {

    private val context: Context get() = TvToolsApp.instance
    private val adb = AppServices.adb

    private val _ui = MutableStateFlow(ToolsUiState())
    val ui: StateFlow<ToolsUiState> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val screenshotFile: File
        get() = File(context.cacheDir, "tv_screenshot.png")

    fun takeScreenshot() {
        if (_ui.value.screenshotLoading) return
        _ui.value = _ui.value.copy(screenshotLoading = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { adb.screencap(screenshotFile) }
                val bmp = BitmapFactory.decodeFile(screenshotFile.absolutePath)
                if (bmp != null) {
                    _ui.value = _ui.value.copy(screenshot = bmp.asImageBitmap(), screenshotLoading = false)
                } else {
                    _ui.value = _ui.value.copy(screenshotLoading = false)
                    _messages.emit("Couldn't decode the screenshot")
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(screenshotLoading = false)
                _messages.emit("Screenshot failed: ${e.message}")
            }
        }
    }

    fun saveScreenshotToDownloads() {
        viewModelScope.launch {
            try {
                val name = "tv_screenshot_${System.currentTimeMillis()}.png"
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        "${android.os.Environment.DIRECTORY_PICTURES}/TV Tools")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: throw java.io.IOException("MediaStore insert failed")
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { out ->
                        screenshotFile.inputStream().use { it.copyTo(out) }
                    } ?: throw java.io.IOException("Cannot open destination")
                }
                _messages.emit("✅ Saved to Pictures/TV Tools")
            } catch (e: Exception) {
                _messages.emit("Save failed: ${e.message}")
            }
        }
    }

    private fun copyUriToCache(uri: Uri, hint: String): File? {
        return try {
            val name = queryDisplayName(uri) ?: "file_${System.currentTimeMillis()}$hint"
            val out = File(context.cacheDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: return null
            out
        } catch (e: Exception) {
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    fun installApk(uri: Uri) {
        if (_ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = "Installing…", progress = 0f)
        viewModelScope.launch {
            var apksToDelete = emptyList<java.io.File>()
            try {
                val picked = withContext(Dispatchers.IO) { copyUriToCache(uri, ".apk") }
                if (picked == null) {
                    _messages.emit("Couldn't read the selected file")
                    return@launch
                }
                val name = picked.name.lowercase()
                if (name.endsWith(".apks") || name.endsWith(".xapk") || name.endsWith(".zip")) {
                    _ui.value = _ui.value.copy(busyLabel = "Extracting…", progress = -1f)
                    val dir = java.io.File(context.cacheDir, "split_${System.currentTimeMillis()}")
                    val apks = withContext(Dispatchers.IO) { unzipApks(picked, dir) }
                    picked.delete()
                    apksToDelete = apks
                    if (apks.isEmpty()) {
                        _messages.emit("No APK files found in the archive")
                        return@launch
                    }
                    _ui.value = _ui.value.copy(busyLabel = "Installing ${apks.size} APKs…", progress = 0f)
                    val total = apks.sumOf { it.length() }.coerceAtLeast(1)
                    val result = adb.installSplitApk(apks) { done ->
                        _ui.value = _ui.value.copy(progress = done.toFloat() / total)
                    }
                    if (result.contains("Success", ignoreCase = true)) {
                        _messages.emit("✅ Installed successfully (${apks.size} APKs)")
                    } else {
                        _messages.emit("Install result: ${result.trim().ifBlank { "unknown" }}")
                    }
                } else {
                    val total = picked.length().coerceAtLeast(1)
                    val result = adb.installApk(picked) { done ->
                        _ui.value = _ui.value.copy(progress = done.toFloat() / total)
                    }
                    picked.delete()
                    if (result.contains("Success", ignoreCase = true)) {
                        _messages.emit("✅ Installed successfully")
                    } else {
                        _messages.emit("Install result: ${result.trim().ifBlank { "unknown" }}")
                    }
                }
            } catch (e: Exception) {
                _messages.emit("Install failed: ${e.message}")
            } finally {
                apksToDelete.forEach { it.parentFile?.deleteRecursively() }
                _ui.value = _ui.value.copy(busyLabel = null, progress = -1f)
            }
        }
    }

    private fun unzipApks(archive: java.io.File, dir: java.io.File): List<java.io.File> {
        dir.mkdirs()
        try {
            java.util.zip.ZipInputStream(archive.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                var count = 0
                while (entry != null && count < 50) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        val out = java.io.File(dir, "split_${count++}.apk")
                        out.outputStream().use { zis.copyTo(it) }
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
        }
        return dir.listFiles { f -> f.extension.equals("apk", true) }?.toList() ?: emptyList()
    }

    fun uploadToDownloads(uri: Uri) {
        if (_ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = "Uploading…", progress = 0f)
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { copyUriToCache(uri, "") }
                if (file == null) {
                    _messages.emit("Couldn't read the selected file")
                    return@launch
                }
                val total = file.length().coerceAtLeast(1)
                adb.pushFile(file, "/sdcard/Download/${file.name}") { done ->
                    _ui.value = _ui.value.copy(progress = done.toFloat() / total)
                }
                file.delete()
                _messages.emit("✅ Uploaded to TV's Download folder")
            } catch (e: Exception) {
                _messages.emit("Upload failed: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(busyLabel = null, progress = -1f)
            }
        }
    }

    fun clearCache() {
        if (_ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = "Clearing caches…", progress = -1f)
        viewModelScope.launch {
            try {
                // pm trim-caches exists on Android 8..15+; it asks the system to evict app caches.
                val out = adb.exec("pm trim-caches 32G 2>&1", timeoutMs = 120_000)
                if (out.contains("Unknown command", true) || out.trimStart().startsWith("Error")) {
                    _messages.emit("Cache trimming is not supported on this TV (${out.trim().take(120)})")
                } else {
                    _messages.emit("✅ Caches trimmed")
                }
            } catch (e: Exception) {
                _messages.emit("Clear cache failed: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(busyLabel = null)
            }
        }
    }
}
