package com.tvtools.app.ui.home

/**
 * Splits manual input into host + port. Accepts:
 *  - `192.168.1.5` (uses [defaultPort])
 *  - `192.168.1.5:5555`
 *  - `fe80::1` / `2001:db8::1` (uses [defaultPort])
 *  - `[fe80::1]:5555` (bracketed IPv6 with a port)
 *  - `adb://192.168.1.5:5555/`
 */
internal fun parseHostPort(raw: String, defaultPort: Int): Pair<String, Int> {
    val text = raw.trim()
        .removePrefix("adb://")
        .removePrefix("tcp://")
        .removeSuffix("/")
    if (text.startsWith("[")) {
        val close = text.indexOf(']')
        if (close > 0) {
            val host = text.substring(1, close)
            val port = text.substring(close + 1)
                .removePrefix(":")
                .toIntOrNull()
                ?: defaultPort
            return host to port
        }
        return text.trim('[', ']') to defaultPort
    }
    // Bare IPv6 (more than one colon) has no port part.
    if (text.count { it == ':' } > 1) return text to defaultPort
    val colon = text.lastIndexOf(':')
    if (colon > 0) {
        val maybePort = text.substring(colon + 1).toIntOrNull()
        if (maybePort != null) return text.substring(0, colon) to maybePort
    }
    return text to defaultPort
}

/** Shows IPv6 literals in brackets so host:port stays unambiguous. */
internal fun formatEndpoint(host: String, port: Int): String =
    if (host.contains(':')) "[$host]:$port" else "$host:$port"
