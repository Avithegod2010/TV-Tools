package com.tvtools.app.data

/** One audio file on the TV, enriched with MediaStore metadata when available. */
data class MusicItem(
    val name: String,
    val path: String,
    val size: Long,
    val title: String = name.substringBeforeLast('.', name),
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val folder: String get() = path.substringBeforeLast('/', "").substringAfterLast('/')
}
