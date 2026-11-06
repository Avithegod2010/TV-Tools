package com.tvtools.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ScreenshotStoreTest {

    private fun gallery(count: Int): Pair<File, List<File>> {
        val dir = Files.createTempDirectory("tvtools-shots").toFile()
        val files = (1..count).map { i ->
            File(dir, "shot_$i.png").apply {
                writeText("png-$i")
                setLastModified(i * 10_000L)
            }
        }
        return dir to files
    }

    @Test
    fun `unlimited retention keeps every screenshot`() {
        val (_, files) = gallery(4)
        assertTrue(ScreenshotStore.excess(files, 0).isEmpty())
        assertEquals(0, ScreenshotStore.pruneFiles(files, 0))
        assertEquals(4, files.count { it.exists() })
    }

    @Test
    fun `limit smaller than count drops the oldest files`() {
        val (dir, files) = gallery(5)
        val excess = ScreenshotStore.excess(files, 2)
        assertEquals(setOf("shot_1.png", "shot_2.png", "shot_3.png"), excess.map { it.name }.toSet())

        assertEquals(3, ScreenshotStore.pruneFiles(files, 2))
        assertEquals(
            setOf("shot_4.png", "shot_5.png"),
            dir.listFiles().orEmpty().map { it.name }.toSet(),
        )
    }

    @Test
    fun `limit larger than count removes nothing`() {
        val (_, files) = gallery(3)
        assertTrue(ScreenshotStore.excess(files, 10).isEmpty())
        assertEquals(0, ScreenshotStore.pruneFiles(files, 10))
        assertEquals(3, files.count { it.exists() })
    }
}
