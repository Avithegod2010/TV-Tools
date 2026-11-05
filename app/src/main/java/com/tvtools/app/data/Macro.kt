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
