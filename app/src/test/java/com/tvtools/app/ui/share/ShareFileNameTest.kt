package com.tvtools.app.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareFileNameTest {

    private val fallback = ShareFileNames.generatedName(1_700_000_000_000)

    @Test
    fun `hostile sender names are replaced by the generated name`() {
        val hostile = listOf(
            "../../../data/local/tmp/evil.apk",
            "..\\..\\evil.apk",
            "sub/dir/evil.apk",
            "sub\\evil.apk",
            "..",
            "..png",
            ".hidden.apk",
            "evil\nname.apk",
            "evil\rname.apk",
            "evil\u0000name.apk",
            "evil\u007Fname.apk",
            "a..b",
            "   ",
            "",
        )
        hostile.forEach { name ->
            assertEquals(name, fallback, ShareFileNames.sanitize(name, fallback))
        }
    }

    @Test
    fun `null display name falls back`() {
        assertEquals(fallback, ShareFileNames.sanitize(null, fallback))
    }

    @Test
    fun `ordinary display names are preserved`() {
        listOf(
            "movie.mp4",
            "My File (1).mp4",
            "photo-2026_11.jpg",
            "TV Tools.apk",
            "song name.mp3",
        ).forEach { name ->
            assertEquals(name, name, ShareFileNames.sanitize(name, fallback))
        }
    }

    @Test
    fun `overlong names are replaced`() {
        val long = "a".repeat(161)
        assertEquals(fallback, ShareFileNames.sanitize(long, fallback))
        assertTrue(ShareFileNames.isSafe("a".repeat(160)))
    }

    @Test
    fun `remote path is always a direct child of the download folder`() {
        assertEquals(
            "/sdcard/Download/movie.mp4",
            ShareFileNames.remotePath("movie.mp4"),
        )
        listOf("../evil.apk", "a/b.apk", "/absolute.apk", ".hidden", "a\u0000b").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                ShareFileNames.remotePath(name)
            }
        }
    }

    @Test
    fun `cache name keeps the sanitised name but is namespaced`() {
        assertEquals("share_movie.mp4", ShareFileNames.cacheName("movie.mp4"))
        assertThrows(IllegalArgumentException::class.java) {
            ShareFileNames.cacheName("../evil.apk")
        }
    }

    @Test
    fun `generated name is itself safe`() {
        assertTrue(ShareFileNames.isSafe(fallback))
        assertFalse(ShareFileNames.isSafe("has/slash"))
    }
}
