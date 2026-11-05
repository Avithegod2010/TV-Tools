package com.tvtools.app.data

/** One recorded key press plus the pause that followed it. */
data class MacroStep(
    val keyCode: Int,
    val delayMs: Long,
)

/** A named sequence of remote key presses. */
data class Macro(
    val name: String,
    val steps: List<MacroStep>,
) {
    val label: String
        get() = "$name · ${steps.size} key" + if (steps.size == 1) "" else "s"
}

/** Keeps stored macro names free of the `|` and `,` separators. */
internal fun sanitizeMacroName(raw: String): String =
    raw.trim()
        .replace("\n", " ")
        .replace('|', '-')
        .replace(',', ' ')
        .take(40)

/** Encodes a macro as `name|code:delay,code:delay`, or null when it has no steps. */
internal fun encodeMacroLine(macro: Macro): String? {
    val name = sanitizeMacroName(macro.name)
    if (name.isBlank() || macro.steps.isEmpty()) return null
    val steps = macro.steps.joinToString(",") { "${it.keyCode}:${it.delayMs}" }
    return "$name|$steps"
}

/** Decodes one stored macro line, skipping malformed entries. */
internal fun decodeMacroLine(line: String): Macro? {
    if (!line.contains('|')) return null
    val name = line.substringBefore('|').trim()
    if (name.isBlank()) return null
    val steps = line.substringAfter('|')
        .split(',')
        .mapNotNull { part ->
            val code = part.substringBefore(':').trim().toIntOrNull() ?: return@mapNotNull null
            val delay = part.substringAfter(':', "0").trim().toLongOrNull() ?: 0L
            MacroStep(code, delay.coerceIn(0, 10_000))
        }
    return if (steps.isEmpty()) null else Macro(name, steps)
}
