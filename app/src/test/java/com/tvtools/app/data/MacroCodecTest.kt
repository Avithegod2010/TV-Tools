package com.tvtools.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacroCodecTest {

    @Test
    fun `round trips name and steps`() {
        val macro = Macro(
            "Morning news",
            listOf(MacroStep(3, 0), MacroStep(20, 250), MacroStep(23, 1_500)),
        )

        val line = encodeMacroLine(macro)
        requireNotNull(line)
        assertEquals(macro, decodeMacroLine(line))
    }

    @Test
    fun `name separators are sanitised`() {
        val line = encodeMacroLine(Macro("A|B,C", listOf(MacroStep(4, 0))))
        requireNotNull(line)
        assertEquals("A-B C|4:0", line)
    }

    @Test
    fun `names are trimmed and capped`() {
        val long = "  " + "x".repeat(80) + "  "
        val line = encodeMacroLine(Macro(long, listOf(MacroStep(3, 0))))
        requireNotNull(line)
        assertEquals(40, line.substringBefore('|').length)
    }

    @Test
    fun `macros without steps are not stored`() {
        assertNull(encodeMacroLine(Macro("Empty", emptyList())))
        assertNull(encodeMacroLine(Macro("   ", listOf(MacroStep(3, 0)))))
    }

    @Test
    fun `invalid lines are ignored`() {
        assertNull(decodeMacroLine("no separator"))
        assertNull(decodeMacroLine("name|"))
        assertNull(decodeMacroLine("name|not-a-code"))
        assertNull(decodeMacroLine("|3:0"))
    }

    @Test
    fun `malformed steps are skipped and delays clamped`() {
        val macro = decodeMacroLine("Mixed|3:0,bad,4:60000,5:abc")
        requireNotNull(macro)
        assertEquals(listOf(MacroStep(3, 0), MacroStep(4, 10_000), MacroStep(5, 0)), macro.steps)
    }

    @Test
    fun `negative delays become zero`() {
        val macro = decodeMacroLine("Negative|3:-900")
        requireNotNull(macro)
        assertEquals(0L, macro.steps.single().delayMs)
    }
}
