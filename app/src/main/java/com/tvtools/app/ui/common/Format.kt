package com.tvtools.app.ui.common

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = -1
    do {
        value /= 1024.0
        i++
    } while (value >= 1024 && i < units.size - 1)
    return if (value >= 100) String.format(Locale.US, "%.0f %s", value, units[i])
    else String.format(Locale.US, "%.1f %s", value, units[i])
}

fun formatBytesShort(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = -1
    do {
        value /= 1024.0
        i++
    } while (value >= 1024 && i < units.size - 1)
    return String.format(Locale.US, "%.2f %s", value, units[i])
}

fun formatUptime(totalSeconds: Long): String {
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "${days}d ${hours}h ${minutes}m ${seconds}s"
}

fun formatDate(epochSeconds: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return sdf.format(java.util.Date(epochSeconds * 1000))
}
