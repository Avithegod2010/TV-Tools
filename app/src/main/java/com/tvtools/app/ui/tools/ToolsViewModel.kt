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
                    runCatching {
                        val gallery = File(context.filesDir, "screenshots").apply { mkdirs() }
                        galleryFile(File(gallery, "shot_${System.currentTimeMillis()}.png"))
                    }
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
                _messages.emit("Couldn't list phone apps: ${e.message}")
            } finally {
                _phoneAppsLoading.value = false
            }
        }
    }

    fun installPhoneApp(item: PhoneAppItem) {
        if (_ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = "Preparing ${item.label}…", progress = -1f)
        viewModelScope.launch {
            val copied = ArrayList<File>()
            try {
                withContext(Dispatchers.IO) {
                    item.apkPaths.forEachIndexed { i, path ->
                        val src = File(path)
                        if (!src.exists() || !src.canRead()) throw java.io.IOException("Phone blocks reading this app's APK")
                        val dst = File(context.cacheDir, "phone_${item.packageName}_$i.apk")
                        src.inputStream().use { input -> dst.outputStream().use { input.copyTo(it) } }
                        copied.add(dst)
                    }
                }
                _ui.value = _ui.value.copy(busyLabel = "Installing ${item.label} on TV…", progress = 0f)
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
                    if (result.contains("Success", true)) "✅ ${item.label} installed on the TV"
                    else result.trim().ifBlank { "Install failed" }
                )
            } catch (e: Exception) {
                _messages.emit("Install failed: ${e.message}")
            } finally {
                copied.forEach { it.delete() }
                _ui.value = _ui.value.copy(busyLabel = null, progress = -1f)
            }
        }
    }

    // ---------------- multi-file / folder upload ----------------

    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty() || _ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = "Uploading 0/${uris.size}…", progress = 0f)
        viewModelScope.launch {
            var ok = 0
            try {
                uris.forEachIndexed { index, uri ->
                    _ui.value = _ui.value.copy(busyLabel = "Uploading ${index + 1}/${uris.size}…")
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
                _messages.emit("✅ Uploaded $ok/${uris.size} file(s) to the TV")
            } finally {
                _ui.value = _ui.value.copy(busyLabel = null, progress = -1f)
            }
        }
    }

    fun uploadFolder(treeUri: Uri) {
        if (_ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = "Scanning folder…", progress = -1f)
        viewModelScope.launch {
            try {
                val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw java.io.IOException("Cannot open folder")
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
                    _ui.value = _ui.value.copy(busyLabel = "Uploading ${index + 1}/${files.size}: ${doc.name}")
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
                _messages.emit("✅ Uploaded $ok file(s) from folder")
            } catch (e: Exception) {
                _messages.emit("Folder upload failed: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(busyLabel = null)
            }
        }
    }

    // ---------------- screen recording ----------------

    fun screenRecord(seconds: Int) {
        if (_ui.value.busyLabel != null) return
        _ui.value = _ui.value.copy(busyLabel = "Recording ${seconds}s…", progress = -1f)
        viewModelScope.launch {
            val remote = "/data/local/tmp/tvtools_rec.mp4"
            try {
                withContext(Dispatchers.IO) {
                    adb.exec("screenrecord --time-limit $seconds $remote", timeoutMs = seconds * 1000L + 25_000)
                }
                _ui.value = _ui.value.copy(busyLabel = "Saving recording…")
                val local = File(context.cacheDir, "tv_record_${System.currentTimeMillis()}.mp4")
                withContext(Dispatchers.IO) { adb.pullFile(remote, local) }
                try { adb.exec("rm -f $remote", timeoutMs = 15_000) } catch (_: Exception) {}
                saveVideoToGallery(local)
                _messages.emit("✅ Recording saved to Movies/TV Tools")
            } catch (e: Exception) {
                _messages.emit("Screen record failed: ${e.message}")
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
            ?: throw java.io.IOException("MediaStore insert failed")
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    private fun galleryFile(dest: File) {
        screenshotFile.copyTo(dest, overwrite = true)
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
