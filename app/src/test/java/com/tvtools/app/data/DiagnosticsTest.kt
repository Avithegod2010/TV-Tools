package com.tvtools.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {

    private val environment = """
        TV Tools 1.6 (7)
        Android 14 (SDK 34)
        Device: Google Pixel
    """.trimIndent()

    @Test
    fun `report includes the exception, thread and environment`() {
        val error = IllegalStateException("ADB socket closed")
        val report = Diagnostics.formatCrash(
            thread = Thread.currentThread(),
            error = error,
            environment = environment,
            timestamp = 0L,
        )

        assertTrue(report.startsWith("TV Tools crash report"))
        assertTrue(report.contains("When: 1970-01-01"))
        assertTrue(report.contains("TV Tools 1.6 (7)"))
        assertTrue(report.contains("Thread: ${Thread.currentThread().name}"))
        assertTrue(report.contains("java.lang.IllegalStateException: ADB socket closed"))
        assertTrue(report.contains("DiagnosticsTest.report includes"))
    }

    @Test
    fun `report is truncated to a bounded size`() {
        val hugeMessage = "x".repeat(200_000)
        val report = Diagnostics.formatCrash(
            thread = Thread.currentThread(),
            error = RuntimeException(hugeMessage),
            environment = environment,
        )

        assertTrue("Report should be bounded, was ${report.length}", report.length <= 64_020)
        assertTrue(report.endsWith("… truncated"))
    }

    @Test
    fun `long messages are cut before the truncation marker`() {
        val report = Diagnostics.formatCrash(
            thread = Thread.currentThread(),
            error = RuntimeException("y".repeat(1_000)),
            environment = environment,
        )
        assertTrue(report.contains("y".repeat(100)))
        assertEquals(false, report.contains("… truncated"))
    }

    @Test
    fun `causes are preserved in the stack trace`() {
        val root = IllegalArgumentException("root cause")
        val wrapped = RuntimeException("outer", root)
        val report = Diagnostics.formatCrash(Thread.currentThread(), wrapped, environment)

        assertTrue(report.contains("Caused by: java.lang.IllegalArgumentException: root cause"))
    }
}
