package com.tvtools.app.data

import android.content.Context
import java.io.File

/**
 * Local screenshot gallery with a configurable retention limit.
 *
 * Screenshots never leave the phone; the limit only controls how many of the
 * newest captures are kept in app storage. `limit <= 0` means keep everything.
 */
object ScreenshotStore {

    fun galleryDir(context: Context): File =
        File(context.filesDir, "screenshots").apply { mkdirs() }

    /** Copy [source] into the gallery under a timestamped name. */
    fun save(context: Context, source: File): File? = runCatching {
        val dest = File(galleryDir(context), "shot_${System.currentTimeMillis()}.png")
        source.copyTo(dest, overwrite = true)
        dest
    }.getOrNull()

    /** Delete the oldest captures beyond [limit]. Returns how many were removed. */
    fun prune(context: Context, limit: Int): Int =
        pruneFiles(galleryDir(context).listFiles()?.toList().orEmpty(), limit)

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
