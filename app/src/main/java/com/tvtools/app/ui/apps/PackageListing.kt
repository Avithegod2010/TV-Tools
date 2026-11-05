package com.tvtools.app.ui.apps

/**
 * Parses `pm list packages -f` output.
 *
 * APK paths on modern Android contain `=` characters
 * (`/data/app/~~xxxxxxxx==/pkg/base.apk`), so the split must happen at the
 * **last** `=` — splitting in shell or at the first `=` truncates paths.
 */
internal fun parsePackageListing(output: String): List<Pair<String, String>> =
    output.replace("\r\n", "\n").lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") && it.contains('=') }
        .mapNotNull { line ->
            val rest = line.removePrefix("package:")
            val path = rest.substringBeforeLast('=', "").trim()
            val pkg = rest.substringAfterLast('=').trim()
            if (pkg.isBlank() || path.isBlank()) null else pkg to path
        }
        .distinctBy { it.first }
        .toList()

/** System partitions that mark an app as pre-installed. */
internal fun isSystemApkPath(path: String): Boolean =
    path.startsWith("/system") || path.startsWith("/vendor") ||
        path.startsWith("/product") || path.startsWith("/apex") ||
        path.startsWith("/system_ext") || path.startsWith("/odm")
