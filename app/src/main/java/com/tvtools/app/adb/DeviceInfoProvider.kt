package com.tvtools.app.adb

import com.tvtools.app.data.DeviceStats
import kotlinx.coroutines.delay

/** Collects and parses device statistics from shell commands. */
class DeviceInfoProvider(private val adb: AdbClient) {

    private var lastCpuLine: String? = null

    suspend fun getStats(): DeviceStats {
        val props = adb.exec(
            "getprop ro.product.model; getprop ro.product.brand; " +
                "getprop ro.build.version.release; getprop ro.product.cpu.abi"
        ).replace("\r\n", "\n").split("\n")

        val meminfo = adb.exec("cat /proc/meminfo")
        val (ramTotal, ramAvail) = parseMeminfo(meminfo)

        val (storageTotal, storageUsed) = parseDf(adb.exec("df -k /sdcard 2>/dev/null || df -k /data"))

        val cpu = readCpuUsage()
        val (rx, tx) = parseNetDev(adb.exec("cat /proc/net/dev"))
        val uptime = adb.exec("cat /proc/uptime").trim().substringBefore(' ').toDoubleOrNull()?.toLong() ?: 0L

        return DeviceStats(
            model = props.getOrNull(0)?.trim().orEmpty().ifBlank { "Android TV" },
            brand = props.getOrNull(1)?.trim().orEmpty(),
            androidVersion = props.getOrNull(2)?.trim().orEmpty(),
            abi = props.getOrNull(3)?.trim().orEmpty(),
            storageTotal = storageTotal,
            storageUsed = storageUsed,
            ramTotal = ramTotal,
            ramAvailable = ramAvail,
            cpuUsagePercent = cpu,
            netRxBytes = rx,
            netTxBytes = tx,
            uptimeSeconds = uptime,
        )
    }

    private fun parseMeminfo(text: String): Pair<Long, Long> {
        var total = 0L
        var avail = 0L
        text.lineSequence().forEach { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val kb = parts[1].toLongOrNull()?.times(1024) ?: return@forEach
                when {
                    line.startsWith("MemTotal") -> total = kb
                    line.startsWith("MemAvailable") -> avail = kb
                }
            }
        }
        return total to avail
    }

    private fun parseDf(text: String): Pair<Long, Long> {
        val line = text.replace("\r\n", "\n").lines().lastOrNull { it.isNotBlank() } ?: return 0L to 0L
        val p = line.split(Regex("\\s+"))
        // Filesystem 1K-blocks Used Available Use% Mounted
        val total = (p.getOrNull(1)?.toLongOrNull() ?: 0L) * 1024
        val used = (p.getOrNull(2)?.toLongOrNull() ?: 0L) * 1024
        return total to used
    }

    private suspend fun readCpuUsage(): Int {
        val line = adb.exec("cat /proc/stat").replace("\r\n", "\n")
            .lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return 0
        val prev = lastCpuLine
        lastCpuLine = line
        if (prev == null) {
            delay(400)
            val line2 = adb.exec("cat /proc/stat").replace("\r\n", "\n")
                .lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return 0
            return cpuDelta(prev = line, curr = line2).also { lastCpuLine = line2 }
        }
        return cpuDelta(prev, line)
    }

    private fun cpuDelta(prev: String, curr: String): Int {
        fun parts(l: String) = l.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        val a = parts(prev); val b = parts(curr)
        if (a.size < 4 || b.size < 4) return 0
        val idleA = a.getOrElse(3) { 0 } + a.getOrElse(4) { 0 }
        val idleB = b.getOrElse(3) { 0 } + b.getOrElse(4) { 0 }
        val totalA = a.sum(); val totalB = b.sum()
        val dTotal = totalB - totalA
        val dIdle = idleB - idleA
        if (dTotal <= 0) return 0
        return ((dTotal - dIdle).coerceAtLeast(0) * 100 / dTotal).toInt().coerceIn(0, 100)
    }

    private fun parseNetDev(text: String): Pair<Long, Long> {
        var rx = 0L
        var tx = 0L
        text.replace("\r\n", "\n").lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx < 0) return@forEach
            val iface = line.substring(0, idx).trim()
            if (iface == "lo") return@forEach
            val f = line.substring(idx + 1).trim().split(Regex("\\s+"))
            rx += f.getOrNull(0)?.toLongOrNull() ?: 0
            tx += f.getOrNull(8)?.toLongOrNull() ?: 0
        }
        return rx to tx
    }
}
