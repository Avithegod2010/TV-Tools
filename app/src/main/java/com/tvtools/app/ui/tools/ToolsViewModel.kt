package com.tvtools.app.ui.tools

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.R
import com.tvtools.app.TvToolsApp
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.data.ScreenshotStore
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
import java.io.IOException

data class ToolsUiState(
    val screenshot: ImageBitmap? = null,
    val screenshotLoading: Boolean = false,
    val busyLabel: String? = null,
    val progress: Float = -1f, // -1 = indeterminate/none
)

data class PhoneAppItem(
    val label: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable?,
    val apkPaths: List<String>,
)

class ToolsViewModel : ViewModel() {

    private val context: Context get() = TvToolsApp.instance
    private val adb = AppServices.adb

    private val _ui = MutableStateFlow(ToolsUiState())
    val ui: StateFlow<ToolsUiState> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _phoneApps = MutableStateFlow<List<PhoneAppItem>>(emptyList())
    val phoneApps: StateFlow<List<PhoneAppItem>> = _phoneApps.asStateFlow()

    private val _phoneAppsLoading = MutableStateFlow(false)
    val phoneAppsLoading: StateFlow<Boolean> = _phoneAppsLoading.asStateFlow()

    /** IDs of the tools pinned to the quick grid. */
    private val _favorites = MutableStateFlow(ToolCatalog.defaultIds)
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    init {
        viewModelScope.launch {
            _favorites.value = AppServices.prefs.loadToolFavorites()
                ?.let(ToolCatalog::normalized)
                ?: ToolCatalog.defaultIds
        }
    }

    fun setFavorites(ids: List<String>) {
        val clean = ToolCatalog.normalized(ids)
        _favorites.value = clean
        viewModelScope.launch { AppServices.prefs.saveToolFavorites(clean) }
    }

    fun toggleFavorite(id: String) {
        val current = _favorites.value
        setFavorites(if (id in current) current - id else current + id)
    }

    fun resetFavorites() = setFavorites(ToolCatalog.defaultIds)

    fun selectAllFavorites() = setFavorites(ToolCatalog.all.map { it.id })

    private var deviceName: String = "TV"

    /** Set by the Tools tab so captures are filed per TV. */
    fun setDevice(name: String) {
        if (name.isNotBlank()) deviceName = name
    }

    val screenshotFile: File
        get() = File(context.cacheDir, "tv_screenshot.png")

    private suspend fun captureWithReconnect() {
        if (!adb.isConnected && adb.reconnect() != AdbClient.ConnectResult.Success) {
            throw java.io.IOException("The TV connection is offline")
        }
        try {
            withContext(Dispatchers.IO) { adb.screencap(screenshotFile) }
        } catch (e: Exception) {
            // A silent drop can surface as "connect() must be called first"; retry once.
            if (adb.reconnect() != AdbClient.ConnectResult.Success) throw e
            withContext(Dispatchers.IO) { adb.screencap(screenshotFile) }
        }
    }

    fun onApkDocumentSelected(uri: Uri) {
        viewModelScope.launch {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                try {
                    // The URI is a picker hint. Installing uses only the current read grant.
                    AppServices.prefs.saveLastApkDocument(uri)
                } catch (_: IOException) {
                    // A preference write failure should not prevent installing the selected APK.
                }
            }
            installApk(uri)
        }
    }

    fun takeScreenshot() {
        if (_ui.value.screenshotLoading) return
        _ui.value = _ui.value.copy(screenshotLoading = true)
        viewModelScope.launch {
            try {
                val preferencesHost = adb.connectedDevice?.preferencesHost
                captureWithReconnect()
                val bmp = BitmapFactory.decodeFile(screenshotFile.absolutePath)
                if (bmp != null) {
                    // Save into the local gallery and honour the retention setting.
                    ScreenshotStore.save(context, screenshotFile, deviceName)
                    ScreenshotStore.prune(
                        context,
                        AppServices.settings.state.value.screenshotLimit,
                    )
                    // Home shows the last explicit capture instead of polling the TV.
                    (preferencesHost ?: adb.connectedDevice?.preferencesHost)?.let { host ->
                        runCatching {
                            val dir = File(context.filesDir, "tvthumbs").apply { mkdirs() }
                            screenshotFile.copyTo(File(dir, "${host.hashCode()}.png"), overwrite = true)
                        }
                    }
                    _ui.value = _ui.value.copy(screenshot = bmp.asImageBitmap(), screenshotLoading = false)
                } else {
                    _ui.value = _ui.value.copy(screenshotLoading = false)
                    _messages.emit(context.getString(R.string.couldnt_decode_the_screenshot))
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(screenshotLoading = false)
                val detail = e.message.orEmpty()
                _messages.emit(
                    if (detail.contains("connect()", true) || detail.contains("offline", true)) {
                        context.getString(R.string.screenshot_failed_the_tv_connection_dropped_go)
                    } else {
                        context.getString(R.string.screenshot_failed, detail)
                    }
                )
            }
        }
    }

    // ---------------- install phone apps to TV ----------------

    fun loadPhoneApps() {
        if (_phoneAppsLoading.value) return
        _phoneAppsLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                @Suppress("DEPRECATION")
                val resolved = pm.queryIntentActivities(intent, 0)
                val seen = HashSet<String>()
                val list = resolved.mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (!seen.add(pkg)) return@mapNotNull null
                    val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
                    val paths = ArrayList<String>()
                    ai.sourceDir?.let { paths.add(it) }
                    ai.splitSourceDirs?.forEach { paths.add(it) }
                    PhoneAppItem(
                        label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg),
                        packageName = pkg,
                        icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull(),
                        apkPaths = paths,
                    )
                }.sortedBy { it.label.lowercase() }
                _phoneApps.value = list
            } catch (e: Exception) {
                _messages.emit(context.getString(R.string.phone_apps_list_failed, e.message.orEmpty()))
            } finally {
                _phoneAppsLoading.value = false
            }
        }
    }

    fun installPhoneApp(item: PhoneAppItem) {
        if (_ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.preparing_app, item.label), progress = -1f)
        viewModelScope.launch {
            val copied = ArrayList<File>()
            try {
                withContext(Dispatchers.IO) {
                    item.apkPaths.forEachIndexed { i, path ->
                        val src = File(path)
                        if (!src.exists() || !src.canRead()) throw java.io.IOException(context.getString(R.string.phone_blocks_reading_this_apps_apk))
                        val dst = File(context.cacheDir, "phone_${item.packageName}_$i.apk")
                        src.inputStream().use { input -> dst.outputStream().use { input.copyTo(it) } }
                        copied.add(dst)
                    }
                }
                _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.installing_app, item.label), progress = 0f)
                val total = copied.sumOf { it.length() }.coerceAtLeast(1)
                val result = if (copied.size > 1) {
                    adb.installSplitApk(copied) { done ->
                        _ui.value = _ui.value.copy(progress = done.toFloat() / total)
                    }
                } else {
                    adb.installApk(copied.first()) { done ->
                        _ui.value = _ui.value.copy(progress = done.toFloat() / total)
                    }
                }
                _messages.emit(
                    if (result.contains("Success", true)) context.getString(R.string.installed_named_app, item.label)
                    else result.trim().ifBlank { context.getString(R.string.install_failed) }
                )
            } catch (e: Exception) {
                _messages.emit(context.getString(R.string.install_error, e.message.orEmpty()))
            } finally {
                copied.forEach { it.delete() }
                _ui.value = _ui.value.copy(busyLabel = null, progress = -1f)
            }
        }
    }

    // ---------------- multi-file / folder upload ----------------

    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty() || _ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.upload_start, uris.size), progress = 0f)
        viewModelScope.launch {
            var ok = 0
            try {
                uris.forEachIndexed { index, uri ->
                    _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.upload_progress, index + 1, uris.size))
                    try {
                        val name = queryDisplayName(uri) ?: "file_${System.currentTimeMillis()}"
                        val tmp = withContext(Dispatchers.IO) {
                            val f = File(context.cacheDir, "up_$name")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                f.outputStream().use { input.copyTo(it) }
                            }
                            f
                        }
                        adb.pushFile(tmp, "/sdcard/Download/$name")
                        tmp.delete()
                        ok++
                    } catch (_: Exception) {
                    }
                }
                _messages.emit(context.getString(R.string.files_uploaded_progress, ok, uris.size))
            } finally {
                _ui.value = _ui.value.copy(busyLabel = null, progress = -1f)
            }
        }
    }

    fun uploadFolder(treeUri: Uri) {
        if (_ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.scanning_folder), progress = -1f)
        viewModelScope.launch {
            try {
                val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw java.io.IOException(context.getString(R.string.cannot_open_folder))
                val files = ArrayList<Pair<androidx.documentfile.provider.DocumentFile, String>>()
                fun walk(dir: androidx.documentfile.provider.DocumentFile, rel: String) {
                    if (files.size > 300) return
                    dir.listFiles().forEach { f ->
                        val name = f.name ?: return@forEach
                        if (f.isDirectory) walk(f, "$rel/$name")
                        else files.add(f to "$rel/$name")
                    }
                }
                withContext(Dispatchers.IO) { walk(root, root.name ?: "folder") }

                var ok = 0
                files.forEachIndexed { index, (doc, relPath) ->
                    _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.upload_named_progress, index + 1, files.size, doc.name.orEmpty()))
                    try {
                        val target = "/sdcard/Download/" + relPath.trimStart('/')
                        val dir = target.substringBeforeLast('/')
                        adb.mkdir(dir)
                        val tmp = withContext(Dispatchers.IO) {
                            val f = File(context.cacheDir, "folder_${index}_${doc.name}")
                            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                                f.outputStream().use { input.copyTo(it) }
                            }
                            f
                        }
                        adb.pushFile(tmp, target)
                        tmp.delete()
                        ok++
                    } catch (_: Exception) {
                    }
                }
                _messages.emit(context.getString(R.string.uploaded_from_folder, ok))
            } catch (e: Exception) {
                _messages.emit(context.getString(R.string.folder_upload_failed, e.message.orEmpty()))
            } finally {
                _ui.value = _ui.value.copy(busyLabel = null)
            }
        }
    }

    // ---------------- screen recording ----------------

    fun screenRecord(seconds: Int) {
        if (_ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.recording_seconds, seconds), progress = -1f)
        viewModelScope.launch {
            val remote = "/data/local/tmp/tvtools_rec.mp4"
            try {
                withContext(Dispatchers.IO) {
                    adb.exec("screenrecord --time-limit $seconds $remote", timeoutMs = seconds * 1000L + 25_000)
                }
                _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.saving_recording))
                val local = File(context.cacheDir, "tv_record_${System.currentTimeMillis()}.mp4")
                withContext(Dispatchers.IO) { adb.pullFile(remote, local) }
                try { adb.exec("rm -f $remote", timeoutMs = 15_000) } catch (_: Exception) {}
                saveVideoToGallery(local)
                _messages.emit(context.getString(R.string.recording_saved_to_movies_tv_tools))
            } catch (e: Exception) {
                _messages.emit(context.getString(R.string.screen_record_failed, e.message.orEmpty()))
            } finally {
                _ui.value = _ui.value.copy(busyLabel = null)
            }
        }
    }

    private fun saveVideoToGallery(file: File) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                android.provider.MediaStore.Video.Media.RELATIVE_PATH,
                "${android.os.Environment.DIRECTORY_MOVIES}/TV Tools"
            )
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException(context.getString(R.string.mediastore_insert_failed))
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
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
                ) ?: throw java.io.IOException(context.getString(R.string.mediastore_insert_failed))
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { out ->
                        screenshotFile.inputStream().use { it.copyTo(out) }
                    } ?: throw java.io.IOException(context.getString(R.string.cannot_open_destination))
                }
                _messages.emit(context.getString(R.string.saved_to_pictures_tv_tools))
            } catch (e: Exception) {
                _messages.emit(context.getString(R.string.save_failed, e.message.orEmpty()))
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
        _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.installing), progress = 0f)
        viewModelScope.launch {
            var apksToDelete = emptyList<java.io.File>()
            try {
                val picked = withContext(Dispatchers.IO) { copyUriToCache(uri, ".apk") }
                if (picked == null) {
                    _messages.emit(context.getString(R.string.couldnt_read_the_selected_file))
                    return@launch
                }
                val name = picked.name.lowercase()
                if (name.endsWith(".apks") || name.endsWith(".xapk") || name.endsWith(".zip")) {
                    _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.extracting), progress = -1f)
                    val dir = java.io.File(context.cacheDir, "split_${System.currentTimeMillis()}")
                    val apks = withContext(Dispatchers.IO) { unzipApks(picked, dir) }
                    picked.delete()
                    apksToDelete = apks
                    if (apks.isEmpty()) {
                        _messages.emit(context.getString(R.string.no_apk_files_found_in_the_archive))
                        return@launch
                    }
                    _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.installing_split_apks, apks.size), progress = 0f)
                    val total = apks.sumOf { it.length() }.coerceAtLeast(1)
                    val result = adb.installSplitApk(apks) { done ->
                        _ui.value = _ui.value.copy(progress = done.toFloat() / total)
                    }
                    if (result.contains("Success", ignoreCase = true)) {
                        _messages.emit(context.getString(R.string.installed_split_apks, apks.size))
                    } else {
                        _messages.emit(context.getString(R.string.install_result, result.trim().ifBlank { context.getString(R.string.unknown_result) }))
                    }
                } else {
                    val total = picked.length().coerceAtLeast(1)
                    val result = adb.installApk(picked) { done ->
                        _ui.value = _ui.value.copy(progress = done.toFloat() / total)
                    }
                    picked.delete()
                    if (result.contains("Success", ignoreCase = true)) {
                        _messages.emit(context.getString(R.string.installed_successfully))
                    } else {
                        _messages.emit(context.getString(R.string.install_result, result.trim().ifBlank { context.getString(R.string.unknown_result) }))
                    }
                }
            } catch (e: Exception) {
                _messages.emit(context.getString(R.string.install_error, e.message.orEmpty()))
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
        _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.uploading), progress = 0f)
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { copyUriToCache(uri, "") }
                if (file == null) {
                    _messages.emit(context.getString(R.string.couldnt_read_the_selected_file))
                    return@launch
                }
                val total = file.length().coerceAtLeast(1)
                adb.pushFile(file, "/sdcard/Download/${file.name}") { done ->
                    _ui.value = _ui.value.copy(progress = done.toFloat() / total)
                }
                file.delete()
                _messages.emit(context.getString(R.string.uploaded_to_tvs_download_folder))
            } catch (e: Exception) {
                _messages.emit(context.getString(R.string.upload_failed_detail, e.message.orEmpty()))
            } finally {
                _ui.value = _ui.value.copy(busyLabel = null, progress = -1f)
            }
        }
    }

    fun clearCache() {
        if (_ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = context.getString(R.string.clearing_caches), progress = -1f)
        viewModelScope.launch {
            try {
                // pm trim-caches exists on Android 8..15+; it asks the system to evict app caches.
                val out = adb.exec("pm trim-caches 32G 2>&1", timeoutMs = 120_000)
                if (out.contains("Unknown command", true) || out.trimStart().startsWith("Error")) {
                    _messages.emit(context.getString(R.string.cache_trim_unsupported, out.trim().take(120)))
                } else {
                    _messages.emit(context.getString(R.string.caches_trimmed))
                }
            } catch (e: Exception) {
                _messages.emit(context.getString(R.string.cache_clear_failed, e.message.orEmpty()))
            } finally {
                _ui.value = _ui.value.copy(busyLabel = null)
            }
        }
    }
}
