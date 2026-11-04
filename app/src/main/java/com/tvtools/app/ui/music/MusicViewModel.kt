package com.tvtools.app.ui.music

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MusicItem(val name: String, val path: String, val size: Long)

data class MusicUiState(
    val items: List<MusicItem> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val busyLabel: String? = null,
    val progress: Float = -1f,
)

/** Lists and manages audio files on the TV (/sdcard/Music, /sdcard/Download). */
class MusicViewModel : ViewModel() {

    private val context: Context get() = TvToolsApp.instance
    private val adb = AppServices.adb

    private val _ui = MutableStateFlow(MusicUiState())
    val ui = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init { scan() }

    fun scan() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            try {
                val out = adb.exec(
                    "find /sdcard/Music /sdcard/Download -type f " +
                        "\\( -iname '*.mp3' -o -iname '*.m4a' -o -iname '*.flac' -o -iname '*.wav' " +
                        "-o -iname '*.ogg' -o -iname '*.aac' -o -iname '*.opus' \\) 2>/dev/null | head -300",
                    timeoutMs = 60_000,
                )
                val paths = out.replace("\r\n", "\n").lines().map { it.trim() }.filter { it.isNotBlank() }
                val sizes = fetchSizes(paths)
                val items = paths.map { p ->
                    MusicItem(p.substringAfterLast('/'), p, sizes[p] ?: 0L)
                }.sortedBy { it.name.lowercase() }
                _ui.value = _ui.value.copy(items = items, loading = false)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false)
                _messages.emit("Couldn't scan music: ${e.message}")
            }
        }
    }

    private suspend fun fetchSizes(paths: List<String>): Map<String, Long> {
        val result = HashMap<String, Long>()
        paths.chunked(20).forEach { chunk ->
            val quoted = chunk.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
            try {
                val out = adb.exec("stat -c '%s %n' $quoted 2>/dev/null", timeoutMs = 30_000)
                out.replace("\r\n", "\n").lines().forEach { line ->
                    val size = line.substringBefore(' ').toLongOrNull() ?: return@forEach
                    result[line.substringAfter(' ')] = size
                }
            } catch (_: Exception) {
            }
        }
        // Fallback for picky adbd builds
        paths.filter { it !in result }.take(30).forEach { p ->
            try {
                val size = adb.exec("stat -c %s '${p.replace("'", "'\\''")}' 2>/dev/null", timeoutMs = 10_000)
                    .trim().toLongOrNull()
                if (size != null) result[p] = size
            } catch (_: Exception) {
            }
        }
        return result
    }

    fun playOnTv(item: MusicItem) {
        viewModelScope.launch {
            try {
                val mime = when (item.name.substringAfterLast('.', "").lowercase()) {
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "flac" -> "audio/flac"
                    "wav" -> "audio/wav"
                    "ogg" -> "audio/ogg"
                    "opus" -> "audio/opus"
                    else -> "audio/*"
                }
                adb.openFileOnTv(item.path, mime)
                _messages.emit("▶ Playing ${item.name} on the TV")
            } catch (e: Exception) {
                _messages.emit("Couldn't play: ${e.message}")
            }
        }
    }

    fun download(item: MusicItem) {
        viewModelScope.launch {
            try {
                val dir = File(context.getExternalFilesDir(null), "Music").apply { mkdirs() }
                val out = File(dir, item.name)
                withContext(Dispatchers.IO) { adb.pullFile(item.path, out, item.size) }
                _messages.emit("Saved to app storage/Music/${item.name}")
            } catch (e: Exception) {
                _messages.emit("Download failed: ${e.message}")
            }
        }
    }

    fun delete(item: MusicItem) {
        viewModelScope.launch {
            try {
                adb.deleteFile(item.path)
                _messages.emit("Deleted ${item.name}")
                scan()
            } catch (e: Exception) {
                _messages.emit("Delete failed: ${e.message}")
            }
        }
    }

    fun upload(uri: Uri) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true, busyLabel = "Uploading music…", progress = 0f)
        viewModelScope.launch {
            try {
                val name = queryDisplayName(uri) ?: "music_${System.currentTimeMillis()}"
                val tmp = File(context.cacheDir, "music_$name")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                }
                adb.mkdir("/sdcard/Music")
                val total = tmp.length().coerceAtLeast(1)
                adb.pushFile(tmp, "/sdcard/Music/$name") { done ->
                    _ui.value = _ui.value.copy(progress = done.toFloat() / total)
                }
                tmp.delete()
                _messages.emit("✅ Uploaded $name")
                scan()
            } catch (e: Exception) {
                _messages.emit("Upload failed: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(busy = false, busyLabel = null, progress = -1f)
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
