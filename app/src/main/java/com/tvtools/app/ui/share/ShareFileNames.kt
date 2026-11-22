package com.tvtools.app.ui.share

/**
 * Treats a share sender's `DISPLAY_NAME` as hostile input.
 *
 * The name arrives from another app's ContentProvider and is used for a cache file and for a
 * remote path on the TV, so it must be a plain file name: no separators, no traversal, no
 * hidden-file prefix, no control characters. Anything that fails is replaced by a generated
 * name rather than "cleaned", so the sender cannot steer the result either.
 */
object ShareFileNames {

    const val REMOTE_DIR = "/sdcard/Download"
    private const val MAX_NAME_LENGTH = 160
    private val CONTROL_CHARS = Regex("[\\u0000-\\u001F\\u007F]")

    fun generatedName(nowMillis: Long): String = "shared_$nowMillis"

    fun sanitize(displayName: String?, fallback: String): String {
        val candidate = displayName?.trim().orEmpty()
        return if (isSafe(candidate)) candidate else fallback
    }

    fun isSafe(name: String): Boolean =
        name.isNotEmpty() &&
            name.length <= MAX_NAME_LENGTH &&
            !name.startsWith(".") &&
            !name.contains('/') &&
            !name.contains('\\') &&
            !name.contains("..") &&
            !CONTROL_CHARS.containsMatchIn(name)

    /** Cache-side name; the prefix keeps a share from colliding with other cache files. */
    fun cacheName(name: String): String {
        require(isSafe(name)) { "refusing unsafe share name" }
        return "share_$name"
    }

    /** Remote path is always a direct child of `/sdcard/Download`. */
    fun remotePath(name: String): String {
        require(isSafe(name)) { "refusing unsafe share name" }
        return "$REMOTE_DIR/$name"
    }
}
