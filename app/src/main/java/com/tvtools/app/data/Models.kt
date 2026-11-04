package com.tvtools.app.data

enum class DeviceProto(val label: String) {
    TLS("Wireless debugging"),
    TCP("ADB over network")
}

/** A TV/box discovered on the local network.
 *  [pairing] = true means the port is a *pairing* port (Android 11+), not a connect port. */
data class TvDevice(
    val name: String,
    val host: String,
    val port: Int,
    val proto: DeviceProto,
    val pairing: Boolean = false,
)

/** An app installed on the TV. */
data class TvApp(
    val packageName: String,
    val apkPath: String,
    val sizeBytes: Long,
    val isSystemApp: Boolean,
    val label: String? = null,
    val versionName: String? = null,
    val enabled: Boolean = true,
    val iconPath: String? = null,
)

/** Aggregated device statistics for the Info tab. */
data class DeviceStats(
    val model: String = "—",
    val brand: String = "",
    val androidVersion: String = "—",
    val buildId: String = "",
    val abi: String = "",
    val storageTotal: Long = 0,
    val storageUsed: Long = 0,
    val ramTotal: Long = 0,
    val ramAvailable: Long = 0,
    val cpuUsagePercent: Int = 0,
    val netRxBytes: Long = 0,
    val netTxBytes: Long = 0,
    val uptimeSeconds: Long = 0,
)
