package com.tvtools.app.data

/** Snapshot of the TV's active media session, parsed from `dumpsys media_session`. */
data class NowPlaying(
    val packageName: String?,
    val title: String,
    val artist: String,
    val state: Int,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val updatedAtMs: Long,
) {
    val isPlaying: Boolean get() = state == 3

    /** Estimated current position, compensating for time since the dump. */
    fun currentPositionMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (!isPlaying || speed <= 0f) return positionMs
        val extra = ((nowMs - updatedAtMs).coerceAtLeast(0) * speed).toLong()
        return (positionMs + extra).coerceAtMost(if (durationMs > 0) durationMs else Long.MAX_VALUE)
    }
}

/**
 * Best-effort parser for `dumpsys media_session` output. TV vendors print
 * slightly different layouts, so this scans line by line and keeps the session
 * that is actually playing (or the first one with metadata).
 */
fun parseMediaSession(dump: String): NowPlaying? {
    val sessions = ArrayList<NowPlaying>()
    var packageName: String? = null
    var title = ""
    var artist = ""
    var state = 0
    var position = 0L
    var duration = 0L
    var speed = 1f
    var hasData = false

    fun flush() {
        if (!hasData) return
        sessions.add(
            NowPlaying(
                packageName = packageName,
                title = title.ifBlank { packageName ?: "Unknown media" },
                artist = artist,
                state = state,
                positionMs = position,
                durationMs = duration,
                speed = speed,
                updatedAtMs = System.currentTimeMillis(),
            )
        )
        packageName = null
        title = ""
        artist = ""
        state = 0
        position = 0L
        duration = 0L
        speed = 1f
        hasData = false
    }

    for (rawLine in dump.replace("\r\n", "\n").lineSequence()) {
        val line = rawLine.trim()
        if (line.startsWith("MediaSessionRecord") || line.startsWith("Session ")) {
            flush()
            line.substringAfter("package=", "").substringBefore(',').substringBefore(' ').takeIf { it.isNotBlank() }
                ?.let { packageName = it }
            continue
        }
        if (line.startsWith("package=")) {
            packageName = line.removePrefix("package=").substringBefore(',').substringBefore(' ').trim()
            hasData = true
        }
        if (line.contains("PlaybackState")) {
            Regex("state=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { state = it }
            Regex("position=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()?.let { position = it }
            Regex("speed=([\\d.]+)").find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let { speed = it }
            hasData = true
        }
        if (line.contains("description=")) {
            Regex("description=([^,}]+)").find(line)?.groupValues?.get(1)?.trim()?.let { title = it }
            hasData = true
        }
        if (line.contains("artist=")) {
            Regex("artist=([^,}]+)").find(line)?.groupValues?.get(1)?.trim()?.let { artist = it }
        }
        if (line.contains("duration=")) {
            Regex("duration=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()?.let { duration = it }
        }
        if (line.contains("metadata:") && line.contains("size=")) {
            Regex("description=([^,}]+)").find(line)?.groupValues?.get(1)?.trim()?.let { title = it }
            Regex("artist=([^,}]+)").find(line)?.groupValues?.get(1)?.trim()?.let { artist = it }
            hasData = true
        }
    }
    flush()

    return sessions.firstOrNull { it.isPlaying && it.title.isNotBlank() }
        ?: sessions.firstOrNull { it.durationMs > 0 }
        ?: sessions.firstOrNull()
}
