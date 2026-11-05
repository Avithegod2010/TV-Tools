package com.tvtools.app.data

import android.content.Context
import com.tvtools.app.adb.AdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppBackup(
    val packageName: String,
    val label: String,
    val version: String,
    val date: Long,
    val apkFiles: List<File>,
    val hasData: Boolean = false,
) {
    val totalBytes: Long get() = apkFiles.sumOf { it.length() }
}

/** Stores app backups (base APK + splits + metadata) in app-specific external storage. */
class BackupManager(context: Context) {

    private val root = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }

    fun list(): List<AppBackup> {
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir -> readMeta(dir) }
            ?.sortedByDescending { it.date }
            ?: emptyList()
    }

    private fun readMeta(dir: File): AppBackup? {
        val meta = File(dir, "meta.txt")
        if (!meta.exists()) return null
        val map = meta.readLines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()
        val files = dir.listFiles { f -> f.extension == "apk" }?.sortedBy { it.name } ?: return null
        if (files.isEmpty()) return null
        return AppBackup(
            packageName = map["pkg"] ?: dir.name,
            label = map["label"] ?: dir.name,
            version = map["version"] ?: "",
            date = map["date"]?.toLongOrNull() ?: dir.lastModified(),
            apkFiles = files,
            hasData = File(dir, "data.tar").exists(),
        )
    }

    /** Backs up one app: all APK paths (base + splits) + metadata. */
    suspend fun create(
        adb: AdbClient,
        app: TvApp,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): AppBackup = withContext(Dispatchers.IO) {
        val paths = adb.getPackagePaths(app.packageName)
        require(paths.isNotEmpty()) { "No APK paths for ${app.packageName}" }

        val dir = File(root, app.packageName)
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()

        val files = ArrayList<File>()
        paths.forEachIndexed { index, remote ->
            val name = if (index == 0) "base.apk" else "split_$index.apk"
            val out = File(dir, name)
            adb.pullFile(remote, out, onProgress = { done ->
                onProgress?.invoke(done, 0)
            })
            files.add(out)
        }

        File(dir, "meta.txt").writeText(
            "pkg=${app.packageName}\n" +
                "label=${app.label ?: app.packageName}\n" +
                "version=${app.versionName ?: ""}\n" +
                "date=${System.currentTimeMillis()}\n"
        )

        AppBackup(
            packageName = app.packageName,
            label = app.label ?: app.packageName,
            version = app.versionName ?: "",
            date = System.currentTimeMillis(),
            apkFiles = files,
        )
    }

    /**
     * Best-effort data backup: tars the app's *external* data
     * (/sdcard/Android/data/<pkg>) which is readable by the shell user.
     * Internal /data/data is not accessible without root.
     */
    suspend fun createDataBackup(adb: AdbClient, packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val remote = "/data/local/tmp/tvtools_data.tar"
            adb.exec(
                "tar -cf $remote -C /sdcard/Android/data '$packageName' 2>/dev/null; stat -c %s $remote 2>/dev/null",
                timeoutMs = 120_000,
            ).trim().toLongOrNull()?.let { size ->
                if (size <= 0) return@withContext false
                val dir = File(root, packageName).apply { mkdirs() }
                adb.pullFile(remote, File(dir, "data.tar"))
                adb.exec("rm -f $remote", timeoutMs = 15_000)
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun restoreDataBackup(adb: AdbClient, backup: AppBackup): Boolean = withContext(Dispatchers.IO) {
        val tar = File(File(root, backup.packageName), "data.tar")
        if (!tar.exists()) return@withContext false
        try {
            adb.pushFile(tar, "/data/local/tmp/tvtools_data.tar")
            adb.exec(
                "mkdir -p /sdcard/Android/data && tar -xf /data/local/tmp/tvtools_data.tar -C /sdcard/Android/data; rm -f /data/local/tmp/tvtools_data.tar",
                timeoutMs = 120_000,
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun delete(backup: AppBackup) {
        File(root, backup.packageName).deleteRecursively()
    }

    fun rootDir(): File = root
}
