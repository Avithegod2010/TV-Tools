package com.tvtools.app.ui.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.TvToolsApp
import com.tvtools.app.data.BackupManager
import com.tvtools.app.data.TvApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

enum class AppSort { NAME, SIZE }

data class AppsUiState(
    val apps: List<TvApp> = emptyList(),
    val loading: Boolean = true,
    val search: String = "",
    val sort: AppSort = AppSort.NAME,
    val onlyRunning: Boolean = false,
    val runningPkgs: Set<String> = emptySet(),
    val busyPkg: String? = null,
    val busyLabel: String? = null,
    val selection: Set<String> = emptySet(),
) {
    val visible: List<TvApp>
        get() {
            var list = apps
            if (onlyRunning) list = list.filter { it.packageName in runningPkgs }
            if (search.isNotBlank()) {
                val q = search.trim().lowercase()
                list = list.filter {
                    (it.label ?: "").lowercase().contains(q) || it.packageName.lowercase().contains(q)
                }
            }
            return when (sort) {
                AppSort.NAME -> list.sortedBy { (it.label ?: it.packageName).lowercase() }
                AppSort.SIZE -> list.sortedByDescending { it.sizeBytes }
            }
        }
}

class AppsViewModel : ViewModel() {

    private val context: Context get() = TvToolsApp.instance
    private val adb = AppServices.adb

    private val _ui = MutableStateFlow(AppsUiState())
    val ui: StateFlow<AppsUiState> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val iconDir = File(context.cacheDir, "app_icons").apply { mkdirs() }
    private val apkTmpDir = File(context.cacheDir, "apk_tmp").apply { mkdirs() }

    /** In-memory decoded icons: pkg -> (iconFile, mtime) */
    private val iconIndex = HashMap<String, Pair<File, Long>>()
    private val versionLookups = HashSet<String>()

    /** Auto icon loading budget: only small, user-installed APKs (TV Wi-Fi is slow). */
    private val autoIconSizeLimit = 40L * 1024 * 1024

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            try {
                // NOTE: APK paths on modern Android contain '=' chars (/data/app/~~xxx==/...),
                // so parse in Kotlin and split at the LAST '=' — never in shell.
                val listing = adb.exec("pm list packages -f", timeoutMs = 60_000)
                val entries = listing.replace("\r\n", "\n").lineSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("package:") && it.contains('=') }
                    .mapNotNull { line ->
                        val rest = line.removePrefix("package:")
                        val path = rest.substringBeforeLast('=', "").trim()
                        val pkg = rest.substringAfterLast('=').trim()
                        if (pkg.isBlank() || path.isBlank()) return@mapNotNull null
                        pkg to path
                    }
                    .distinctBy { it.first }
                    .toList()

                // Batch-stat all APKs in a single shell roundtrip.
                val sizes = fetchSizes(entries.map { it.second })

                val apps = entries.map { (pkg, path) ->
                    val system = path.startsWith("/system") || path.startsWith("/vendor") ||
                        path.startsWith("/product") || path.startsWith("/apex") ||
                        path.startsWith("/system_ext") || path.startsWith("/odm")
                    TvApp(
                        packageName = pkg,
                        apkPath = path,
                        sizeBytes = sizes[path] ?: 0L,
                        isSystemApp = system,
                    )
                }

                // Attach any previously cached icons so they show instantly
                val withCachedIcons = apps.map { app ->
                    val f = File(iconDir, "${app.packageName}.png")
                    if (f.exists()) app.copy(iconPath = f.absolutePath) else app
                }
                _ui.value = _ui.value.copy(apps = withCachedIcons, loading = false)
                loadRunningPkgs()
                autoLoadIcons(withCachedIcons)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false)
                _messages.emit("Failed to load apps: ${e.message}")
            }
        }
    }

    /**
     * `stat` APK paths in small chunks → map path -> bytes.
     * Chunking matters: some TV adbd builds choke on long shell service strings.
     */
    private suspend fun fetchSizes(paths: List<String>): Map<String, Long> {
        if (paths.isEmpty()) return emptyMap()
        val result = HashMap<String, Long>()
        paths.chunked(20).forEach { chunk ->
            val quoted = chunk.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
            try {
                val out = adb.exec("stat -c '%s %n' $quoted 2>/dev/null", timeoutMs = 30_000)
                out.replace("\r\n", "\n").lineSequence().forEach { line ->
                    val size = line.substringBefore(' ').toLongOrNull() ?: return@forEach
                    val path = line.substringAfter(' ')
                    result[path] = size
                }
            } catch (_: Exception) {
                // try the remaining chunks anyway
            }
        }
        // Fallback: single-file stat for anything a chunk missed (some adbd builds choke on batched args).
        paths.filter { it !in result }.take(60).forEach { path ->
            try {
                val quoted = "'" + path.replace("'", "'\\''") + "'"
                val single = adb.exec("stat -c %s $quoted 2>/dev/null", timeoutMs = 10_000).trim()
                    .toLongOrNull()
                if (single != null) result[path] = single
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun loadRunningPkgs() {
        viewModelScope.launch {
            try {
                val ps = adb.exec("ps -A -o NAME 2>/dev/null || ps -A")
                val running = ps.replace("\r\n", "\n").lineSequence()
                    .map { it.trim().substringAfterLast(' ').substringBefore(':') }
                    .filter { it.contains('.') }
                    .toSet()
                _ui.value = _ui.value.copy(runningPkgs = running)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Background icon loader. Only small user apps are pulled automatically —
     * on a typical TV's slow Wi-Fi, pulling a 100 MB system APK per icon is not viable.
     * Big/system apps get their icons only on explicit request ("Load icon" menu action).
     */
    private fun autoLoadIcons(apps: List<TvApp>) {
        viewModelScope.launch {
            val semaphore = Semaphore(1)
            apps.filter {
                !it.isSystemApp && it.iconPath == null && it.sizeBytes in 1..autoIconSizeLimit
            }.forEach { app ->
                launch {
                    semaphore.withPermit { enrichOne(app, force = false) }
                }
            }
        }
    }

    /** Force icon extraction for one app (any size), used by the "Load icon" action. */
    fun loadIconNow(app: TvApp) {
        if (_ui.value.busyPkg != null) return
        _ui.value = _ui.value.copy(busyPkg = app.packageName)
        viewModelScope.launch {
            try {
                enrichOne(app, force = true)
            } finally {
                _ui.value = _ui.value.copy(busyPkg = null)
            }
        }
    }

    private suspend fun enrichOne(app: TvApp, force: Boolean) {
        if (!force && (app.isSystemApp || app.sizeBytes > autoIconSizeLimit)) return
        if (iconIndex.containsKey(app.packageName)) return

        val tmp = File(apkTmpDir, "${app.packageName}.apk")
        try {
            withContext(Dispatchers.IO) {
                adb.pullFile(app.apkPath, tmp)
            }
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = pm.getPackageArchiveInfo(tmp.absolutePath, 0) ?: return
            val ai = info.applicationInfo ?: return
            ai.sourceDir = tmp.absolutePath
            ai.publicSourceDir = tmp.absolutePath

            val label = runCatching { ai.loadLabel(pm)?.toString() }.getOrNull()
            val version = info.versionName
            val iconFile = File(iconDir, "${app.packageName}.png")
            runCatching {
                val drawable: Drawable = ai.loadIcon(pm)
                val bmp = drawable.toBitmap(96, 96, Bitmap.Config.ARGB_8888)
                iconFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            iconIndex[app.packageName] = iconFile to tmp.lastModified()

            updateApp(app.packageName) {
                it.copy(
                    label = label ?: it.label,
                    versionName = version ?: it.versionName,
                    iconPath = if (iconFile.exists()) iconFile.absolutePath else it.iconPath,
                )
            }
        } catch (_: Exception) {
            // ignore per-app failures
        } finally {
            tmp.delete()
        }
    }

    /**
     * Lazily fetch the version name via dumpsys (fast, ~0.15 s — no APK transfer).
     * Called when a row becomes visible.
     */
    fun ensureDetails(app: TvApp) {
        if (app.versionName != null) return
        synchronized(versionLookups) {
            if (!versionLookups.add(app.packageName)) return
        }
        viewModelScope.launch {
            try {
                val out = adb.exec(
                    "dumpsys package ${app.packageName} 2>/dev/null | grep -m1 versionName",
                    timeoutMs = 15_000,
                )
                val v = out.substringAfter("versionName=", "").trim()
                updateApp(app.packageName) { it.copy(versionName = v) }
            } catch (_: Exception) {
                // mark as checked so we don't retry constantly
                updateApp(app.packageName) { it.copy(versionName = "") }
            }
        }
    }

    private fun updateApp(pkg: String, transform: (TvApp) -> TvApp) {
        val apps = _ui.value.apps.map { if (it.packageName == pkg) transform(it) else it }
        _ui.value = _ui.value.copy(apps = apps)
    }

    // ---------------- filters ----------------

    fun setSearch(q: String) { _ui.value = _ui.value.copy(search = q) }

    // ---------------- selection & backup ----------------

    fun toggleSelection(pkg: String) {
        val sel = _ui.value.selection
        _ui.value = _ui.value.copy(selection = if (pkg in sel) sel - pkg else sel + pkg)
    }

    fun clearSelection() {
        _ui.value = _ui.value.copy(selection = emptySet())
    }

    fun backupSelected() {
        val apps = _ui.value.apps.filter { it.packageName in _ui.value.selection }
        if (apps.isEmpty() || _ui.value.busyLabel != null) return
        viewModelScope.launch {
            var ok = 0
            apps.forEachIndexed { index, app ->
                _ui.value = _ui.value.copy(busyLabel = "Backing up ${index + 1}/${apps.size}: ${app.label ?: app.packageName}")
                try {
                    BackupManager(context).create(AppServices.adb, app)
                    ok++
                } catch (e: Exception) {
                    _messages.emit("Backup failed for ${app.packageName}: ${e.message}")
                }
            }
            _ui.value = _ui.value.copy(busyLabel = null)
            clearSelection()
            _messages.emit("✅ Backed up $ok app(s) — see App backups")
        }
    }

    fun selectAll() {
        _ui.value = _ui.value.copy(selection = _ui.value.visible.map { it.packageName }.toSet())
    }
    fun toggleSort() {
        _ui.value = _ui.value.copy(sort = if (_ui.value.sort == AppSort.NAME) AppSort.SIZE else AppSort.NAME)
    }
    fun toggleRunning() { _ui.value = _ui.value.copy(onlyRunning = !_ui.value.onlyRunning) }

    // ---------------- actions ----------------

    private fun withBusy(pkg: String, block: suspend () -> String) {
        if (_ui.value.busyPkg != null) return
        _ui.value = _ui.value.copy(busyPkg = pkg)
        viewModelScope.launch {
            try {
                val out = block()
                if (out.isNotBlank()) _messages.emit(out.trim())
            } catch (e: Exception) {
                _messages.emit("Error: ${e.message}")
            } finally {
                _ui.value = _ui.value.copy(busyPkg = null)
            }
        }
    }

    fun launch(app: TvApp) = withBusy(app.packageName) {
        adb.launchApp(app.packageName)
        "Launching ${app.label ?: app.packageName} on the TV…"
    }

    fun uninstall(app: TvApp) = withBusy(app.packageName) {
        val out = adb.uninstallApp(app.packageName)
        if (out.contains("Success", true)) {
            _ui.value = _ui.value.copy(apps = _ui.value.apps.filterNot { it.packageName == app.packageName })
            "✅ ${app.label ?: app.packageName} uninstalled"
        } else out.trim().ifBlank { "Uninstall failed" }
    }

    fun forceStop(app: TvApp) = withBusy(app.packageName) {
        adb.forceStopApp(app.packageName)
        loadRunningPkgs()
        "Force-stopped ${app.label ?: app.packageName}"
    }

    fun clearData(app: TvApp) = withBusy(app.packageName) {
        val out = adb.clearAppData(app.packageName)
        if (out.contains("Success", true)) "✅ Data cleared" else out.trim().ifBlank { "Clear data failed" }
    }

    fun toggleEnabled(app: TvApp) = withBusy(app.packageName) {
        if (app.enabled) {
            adb.disableApp(app.packageName)
            updateApp(app.packageName) { it.copy(enabled = false) }
            "Disabled ${app.label ?: app.packageName}"
        } else {
            adb.enableApp(app.packageName)
            updateApp(app.packageName) { it.copy(enabled = true) }
            "Enabled ${app.label ?: app.packageName}"
        }
    }

    fun openOnTv(app: TvApp) = withBusy(app.packageName) {
        adb.openAppSettingsOnTv(app.packageName)
        "Opened app info on the TV"
    }

    /** Pull the APK to the phone's app-specific Download dir and share its path. */
    fun downloadApk(app: TvApp) = withBusy(app.packageName) {
        val dir = File(context.getExternalFilesDir(null), "apks").apply { mkdirs() }
        val safeName = (app.label ?: app.packageName).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val out = File(dir, "$safeName.apk")
        withContext(Dispatchers.IO) { adb.pullFile(app.apkPath, out) }
        "✅ Saved: ${out.absolutePath}"
    }

    fun apkFileFor(app: TvApp): File {
        val dir = File(context.getExternalFilesDir(null), "apks").apply { mkdirs() }
        val safeName = (app.label ?: app.packageName).replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$safeName.apk")
    }
}
