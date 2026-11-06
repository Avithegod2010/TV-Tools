package com.tvtools.app.ui.music

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import com.tvtools.app.data.MusicItem
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
    val queue: List<MusicItem> = emptyList(),
    val queueIndex: Int = -1,
    val current: MusicItem? = null,
) {
    val visibleItems: List<MusicItem>
        get() {
            val filtered = if (query.isBlank()) {
                items
            } else {
                items.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.name.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true) ||
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

/** Lists and manages audio files on the TV and keeps the shared queue in sync. */
class MusicViewModel : ViewModel() {

    private val context: Context get() = TvToolsApp.instance
    private val adb = AppServices.adb
    private val playback = AppServices.playback

    private val _ui = MutableStateFlow(MusicUiState())
    val ui = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var scanJob: Job? = null

    init {
        observePlayback()
        scan()
    }

    private fun observePlayback() {
        viewModelScope.launch {
            playback.state.collect { state ->
                _ui.value = _ui.value.copy(
                    nowPlaying = state.nowPlaying,
                    queue = state.queue,
                    queueIndex = state.index,
                    current = state.current,
                )
            }
        }
    }

    fun refreshPlayback() = playback.refreshOnce()

    // ---------------- scanning ----------------

    /**
     * MediaStore is indexed and answers in well under a second, unlike walking
     * /sdcard over FUSE (which can take 30-60 s on cheap TVs). The filesystem
     * scan is only a fallback for TVs whose media database is empty.
     */
    fun scan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, scanningMore = true)
            try {
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
                    "--projection _data:size:title:artist:album:duration 2>/dev/null",
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
            fun field(key: String): String =
                Regex("\\b$key=([^,]*)").find(line)?.groupValues?.get(1)?.trim().orEmpty()
            val size = field("size").toLongOrNull() ?: 0L
            val duration = field("duration").toLongOrNull() ?: 0L
            MusicItem(
                name = name,
                path = path,
                size = size,
                title = field("title").ifBlank { name.substringBeforeLast('.', name) },
                artist = field("artist"),
                album = field("album"),
                durationMs = duration,
            )
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

    // ---------------- playback / queue ----------------

    fun playOnTv(item: MusicItem) {
        playback.play(item, _ui.value.visibleItems)
        viewModelScope.launch { _messages.emit("▶ ${item.title}") }
    }

    fun playPause() = playback.toggle()

    fun next() = playback.next()

    fun previous() = playback.previous()

    fun seek(deltaMs: Long) = playback.seek(deltaMs)

    fun addToQueue(item: MusicItem) {
        playback.enqueue(item)
        viewModelScope.launch { _messages.emit("Added ${item.title} to the queue") }
    }

    fun playNext(item: MusicItem) {
        playback.playNext(item)
        viewModelScope.launch { _messages.emit("Playing ${item.title} next") }
    }

    fun playQueueAt(index: Int) = playback.playAt(index)

    fun removeFromQueue(index: Int) = playback.removeFromQueue(index)

    fun clearQueue() = playback.clearQueue()

    fun stopPlayback() = playback.stop()

    // ---------------- downloads / uploads ----------------

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
                val queueIndex = _ui.value.queue.indexOfFirst { it.path == item.path }
                if (queueIndex >= 0) playback.removeFromQueue(queueIndex)
                _ui.value = _ui.value.copy(items = _ui.value.items.filterNot { it.path == item.path })
                _messages.emit("Deleted ${item.name}")
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
            val added = ArrayList<MusicItem>()
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
                        val remote = "/sdcard/Music/$name"
                        adb.pushFile(tmp, remote) { done ->
                            _ui.value = _ui.value.copy(
                                progress = (index + done.toFloat() / total) / uris.size,
                            )
                        }
                        tmp.delete()
                        added += MusicItem(name, remote, total)
                        ok++
                    } catch (_: Exception) {
                    }
                }
                if (ok > 0) {
                    // Show the new files immediately even if MediaStore hasn't indexed them yet.
                    _ui.value = _ui.value.copy(
                        items = (_ui.value.items + added).distinctBy { it.path },
                    )
                    _messages.emit("✅ Uploaded $ok/${uris.size} track(s)")
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
}
