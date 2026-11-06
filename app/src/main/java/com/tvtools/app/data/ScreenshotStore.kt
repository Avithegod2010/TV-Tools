package com.tvtools.app.data

import android.content.Context
import java.io.File

/**
 * Local screenshot gallery with per-TV folders and a configurable retention limit.
 *
 * Screenshots never leave the phone; the limit only controls how many of the
 * newest captures are kept in app storage. `limit <= 0` means keep everything.
 */
object ScreenshotStore {

    const val UNSORTED = "Unsorted"

    fun galleryDir(context: Context): File =
        File(context.filesDir, "screenshots").apply { mkdirs() }

    /** Safe folder name for a TV model, e.g. "JHSD200". */
    fun safeDeviceName(name: String?): String {
        val clean = name.orEmpty()
            .replace(Regex("[^A-Za-z0-9 _-]"), "_")
            .trim()
            .trim('.')
        return clean.ifBlank { "TV" }.take(40)
    }

    fun deviceDir(context: Context, deviceName: String?): File =
        File(galleryDir(context), safeDeviceName(deviceName)).apply { mkdirs() }

    /** Copy [source] into the gallery under a timestamped name. */
    fun save(context: Context, source: File, deviceName: String? = null): File? = runCatching {
        val dir = if (deviceName.isNullOrBlank()) galleryDir(context) else deviceDir(context, deviceName)
        var dest = File(dir, "shot_${System.currentTimeMillis()}.png")
        var suffix = 1
        while (dest.exists()) {
            dest = File(dir, "shot_${System.currentTimeMillis()}_${suffix++}.png")
        }
        source.copyTo(dest, overwrite = false)
        dest
    }.getOrNull()

    /** Every capture, newest first, including legacy files stored at the gallery root. */
    fun allFiles(context: Context): List<File> {
        val root = galleryDir(context)
        val result = ArrayList<File>()
        root.listFiles()?.forEach { entry ->
            when {
                entry.isFile -> result.add(entry)
                entry.isDirectory -> entry.listFiles()?.forEach { child ->
                    if (child.isFile) result.add(child)
                }
            }
        }
        return result.sortedByDescending { it.lastModified() }
    }

    /** Folder name -> files (newest first). Legacy captures are grouped as [UNSORTED]. */
    fun groups(context: Context): Map<String, List<File>> {
        val result = LinkedHashMap<String, MutableList<File>>()
        allFiles(context).forEach { file ->
            val parent = file.parentFile
            val key = if (parent == null || parent == galleryDir(context)) UNSORTED else parent.name
            result.getOrPut(key) { mutableListOf() }.add(file)
        }
        return result.mapValues { it.value.sortedByDescending { f -> f.lastModified() } }
    }

    /** Remove every capture and per-TV folder (used by Settings). */
    fun clear(context: Context) {
        galleryDir(context).listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Delete the oldest captures beyond [limit] across every TV. */
    fun prune(context: Context, limit: Int): Int = pruneFiles(allFiles(context), limit)

    fun pruneFiles(files: List<File>, limit: Int): Int {
        var removed = 0
        excess(files, limit).forEach { file ->
            if (runCatching { file.delete() }.getOrDefault(false)) removed++
        }
        return removed
    }

    /** Files older than the newest [limit]; empty when retention is unlimited. */
    fun excess(files: List<File>, limit: Int): List<File> {
        if (limit <= 0) return emptyList()
        return files.sortedByDescending { it.lastModified() }.drop(limit)
    }
}
