package com.tvtools.app.ui.music

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import com.tvtools.app.data.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MusicItem(val name: String, val path: String, val size: Long) {
    val title: String get() = name.substringBeforeLast('.', name)
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val folder: String get() = path.substringBeforeLast('/', "").substringAfterLast('/')
}

enum class MusicSort(val label: String) { NAME("Name"), SIZE("Size") }

data class MusicUiState(
    val items: List<MusicItem> = emptyList(),
    val loading: Boolean = true,
    val scanningMore: Boolean = false,
    val busy: Boolean = false,
    val busyLabel: String? = null,
    val progress: Float = -1f,
    val query: String = "",
    val sort: MusicSort = MusicSort.NAME,
    val nowPlaying: NowPlaying? = null,
) {
    val visibleItems: List<MusicItem>
        get() {
            val filtered = if (query.isBlank()) {
                items
            } else {
                items.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.name.contains(query, ignoreCase = true) ||
                        it.folder.contains(query, ignoreCase = true)
                }
            }
            return when (sort) {
                MusicSort.NAME -> filtered.sortedBy { it.title.lowercase() }
                MusicSort.SIZE -> filtered.sortedByDescending { it.size }
            }
        }

    val totalBytes: Long get() = items.sumOf { it.size }
}

/** Lists and manages audio files on the TV (/sdcard/Music, /sdcard/Download). */
class MusicViewModel : ViewModel() {

    private val context: Context get() = TvToolsApp.instance
    private val adb = AppServices.adb

    private val _ui = MutableStateFlow(MusicUiState())
    val ui = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var nowPlayingJob: Job? = null
    private var scanJob: Job? = null

    init { scan() }

    /**
     * Two-phase scan: `/sdcard/Music` is usually small, so show it first instead of
     * blocking the whole screen while the (often huge) Downloads folder is walked.
     */
    fun scan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, scanningMore = true)
            try {
                // MediaStore is indexed and answers in well under a second, unlike
                // walking /sdcard over FUSE (which can take 30-60 s on cheap TVs).
                val indexed = fetchIndexedTracks()
                if (indexed.isNotEmpty()) {
                    _ui.value = _ui.value.copy(
                        items = indexed,
                        loading = false,
                        scanningMore = false,
                    )
                    return@launch
                }
                val music = fetchTracks("/sdcard/Music", timeoutMs = 20_000)
                _ui.value = _ui.value.copy(items = music, loading = false)
                val downloads = fetchTracks("/sdcard/Download", timeoutMs = 20_000)
                val merged = (_ui.value.items + downloads).distinctBy { it.path }
                _ui.value = _ui.value.copy(items = merged, scanningMore = false)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, scanningMore = false)
                _messages.emit("Couldn't scan music: ${e.message}")
            }
        }
    }

    private suspend fun fetchIndexedTracks(): List<MusicItem> {
        val out = try {
            adb.exec(
                "content query --uri content://media/external/audio/media " +
                    "--projection _data:size 2>/dev/null",
                timeoutMs = 15_000,
            )
        } catch (_: Exception) {
            return emptyList()
        }
        val allowed = setOf("mp3", "m4a", "flac", "wav", "ogg", "aac", "opus")
        return out.lineSequence().mapNotNull { line ->
            val path = Regex("_data=([^,]+)").find(line)?.groupValues?.get(1)?.trim()
                ?: return@mapNotNull null
            if (!path.startsWith("/")) return@mapNotNull null
            if (!path.contains("/Music/", true) && !path.contains("/Download/", true)) {
                return@mapNotNull null
            }
            val name = path.substringAfterLast('/')
            if (name.substringAfterLast('.', "").lowercase() !in allowed) return@mapNotNull null
            val size = Regex("\\bsize=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            MusicItem(name, path, size)
        }.distinctBy { it.path }.toList()
    }

    private suspend fun fetchTracks(dir: String, timeoutMs: Long): List<MusicItem> {
        val out = try {
            adb.exec(
                "find $dir -maxdepth 4 -type f " +
                    "\\( -iname '*.mp3' -o -iname '*.m4a' -o -iname '*.flac' -o -iname '*.wav' " +
                    "-o -iname '*.ogg' -o -iname '*.aac' -o -iname '*.opus' \\) 2>/dev/null | head -300",
                timeoutMs = timeoutMs,
            )
        } catch (_: Exception) {
            return emptyList()
        }
        val paths = out.replace("\r\n", "\n").lines().map { it.trim() }.filter { it.isNotBlank() }
        if (paths.isEmpty()) return emptyList()
        val sizes = fetchSizes(paths)
        return paths.map { p -> MusicItem(p.substringAfterLast('/'), p, sizes[p] ?: 0L) }
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

    fun setQuery(query: String) {
        _ui.value = _ui.value.copy(query = query)
    }

    fun setSort(sort: MusicSort) {
        _ui.value = _ui.value.copy(sort = sort)
    }

    // ---------------- now playing on the TV ----------------

    fun startNowPlaying() {
        if (nowPlayingJob?.isActive == true) return
        nowPlayingJob = viewModelScope.launch {
            while (true) {
                if (!adb.recentlyInteracted(1_500)) {
                    _ui.value = _ui.value.copy(
                        nowPlaying = runCatching { adb.nowPlaying() }.getOrNull(),
                    )
                }
                delay(6_000)
            }
        }
    }

    fun stopNowPlaying() {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
    }

    fun playPause() {
        viewModelScope.launch {
            adb.keyEvent(KEY_MEDIA_PLAY_PAUSE)
            refreshNowPlayingSoon()
        }
    }

    fun next() {
        viewModelScope.launch {
            adb.keyEvent(KEY_MEDIA_NEXT)
            refreshNowPlayingSoon()
        }
    }

    fun previous() {
        viewModelScope.launch {
            adb.keyEvent(KEY_MEDIA_PREV)
            refreshNowPlayingSoon()
        }
    }

    fun seek(deltaMs: Long) {
        viewModelScope.launch { adb.seekRelative(deltaMs) }
    }

    private suspend fun refreshNowPlayingSoon() {
        delay(700)
        _ui.value = _ui.value.copy(nowPlaying = runCatching { adb.nowPlaying() }.getOrNull())
    }

    fun playOnTv(item: MusicItem) {
        viewModelScope.launch {
            try {
                adb.openFileOnTv(item.path, mimeOf(item))
                _messages.emit("▶ Playing ${item.title} on the TV")
                delay(1_200)
                _ui.value = _ui.value.copy(nowPlaying = runCatching { adb.nowPlaying() }.getOrNull())
            } catch (e: Exception) {
                _messages.emit("Couldn't play: ${e.message}")
            }
        }
    }

    private fun mimeOf(item: MusicItem): String = when (item.extension) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "aac" -> "audio/aac"
        else -> "audio/*"
    }

    fun download(item: MusicItem) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true, busyLabel = "Downloading ${item.title}…", progress = -1f)
        viewModelScope.launch {
            try {
                val dir = File(context.getExternalFilesDir(null), "Music").apply { mkdirs() }
                val out = File(dir, item.name)
                withContext(Dispatchers.IO) { adb.pullFile(item.path, out, item.size) }
                _messages.emit("Saved to app storage/Music/${item.name}")
            } catch (e: Exception) {
                _messages.emit("Download failed: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(busy = false, busyLabel = null, progress = -1f)
            }
        }
    }

    fun delete(item: MusicItem) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true, busyLabel = "Deleting ${item.title}…", progress = -1f)
        viewModelScope.launch {
            try {
                adb.deleteFile(item.path)
                _messages.emit("Deleted ${item.name}")
                scan()
            } catch (e: Exception) {
                _messages.emit("Delete failed: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(busy = false, busyLabel = null)
            }
        }
    }

    fun upload(uris: List<Uri>) {
        if (uris.isEmpty() || _ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true, busyLabel = "Uploading music…", progress = 0f)
        viewModelScope.launch {
            var ok = 0
            try {
                adb.mkdir("/sdcard/Music")
                uris.forEachIndexed { index, uri ->
                    _ui.value = _ui.value.copy(
                        busyLabel = "Uploading ${index + 1}/${uris.size}…",
                        progress = index.toFloat() / uris.size,
                    )
                    try {
                        val name = queryDisplayName(uri) ?: "music_${System.currentTimeMillis()}"
                        val tmp = File(context.cacheDir, "music_$name")
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tmp.outputStream().use { input.copyTo(it) }
                            }
                        }
                        val total = tmp.length().coerceAtLeast(1)
                        adb.pushFile(tmp, "/sdcard/Music/$name") { done ->
                            _ui.value = _ui.value.copy(
                                progress = (index + done.toFloat() / total) / uris.size,
                            )
                        }
                        tmp.delete()
                        ok++
                    } catch (_: Exception) {
                    }
                }
                if (ok > 0) {
                    _messages.emit("✅ Uploaded $ok/${uris.size} track(s)")
                    scan()
                } else {
                    _messages.emit("Upload failed")
                }
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

    private companion object {
        const val KEY_MEDIA_PLAY_PAUSE = 85
        const val KEY_MEDIA_NEXT = 87
        const val KEY_MEDIA_PREV = 88
    }
}
