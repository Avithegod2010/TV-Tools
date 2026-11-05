package com.tvtools.app.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageListingTest {

    @Test
    fun `keeps paths containing double equals`() {
        val output = """
            package:/data/app/~~AbC==/com.spotify.tv.android-XyZ==/base.apk=com.spotify.tv.android
            package:/system/priv-app/TvSettings/TvSettings.apk=com.android.tv.settings
        """.trimIndent()

        val entries = parsePackageListing(output)

        assertEquals(2, entries.size)
        assertEquals(
            "/data/app/~~AbC==/com.spotify.tv.android-XyZ==/base.apk",
            entries[0].second,
        )
        assertEquals("com.spotify.tv.android", entries[0].first)
        assertEquals("/system/priv-app/TvSettings/TvSettings.apk", entries[1].second)
    }

    @Test
    fun `duplicate packages are collapsed`() {
        val output = """
            package:/data/app/~~x==/com.foo/base.apk=com.foo
            package:/data/app/~~x==/com.foo/base.apk=com.foo
        """.trimIndent()

        val entries = parsePackageListing(output)
        assertEquals(1, entries.size)
        assertEquals("com.foo" to "/data/app/~~x==/com.foo/base.apk", entries.single())
    }

    @Test
    fun `malformed lines are skipped`() {
        val output = """
            package:/data/app/base.apk
            not a package line
            package:=com.missing.path
            package:/data/app/base.apk=com.good
            package:/data/app/other.apk=
        """.trimIndent()

        val entries = parsePackageListing(output)
        assertEquals(listOf("com.good" to "/data/app/base.apk"), entries)
    }

    @Test
    fun `windows line endings and padding are handled`() {
        val output = "  package:/data/app/a.apk=com.a\r\npackage:/data/app/b.apk=com.b\r\n"
        assertEquals(
            listOf("com.a" to "/data/app/a.apk", "com.b" to "/data/app/b.apk"),
            parsePackageListing(output),
        )
    }

    @Test
    fun `empty output yields no entries`() {
        assertTrue(parsePackageListing("").isEmpty())
    }

    @Test
    fun `system partitions are recognised`() {
        listOf(
            "/system/app/Foo/base.apk",
            "/system_ext/app/Foo/base.apk",
            "/vendor/app/Foo/base.apk",
            "/product/app/Foo/base.apk",
            "/apex/com.android.foo/base.apk",
            "/odm/app/Foo/base.apk",
        ).forEach { assertTrue(it, isSystemApkPath(it)) }

        assertFalse(isSystemApkPath("/data/app/~~x==/com.foo/base.apk"))
        assertFalse(isSystemApkPath("/sdcard/Download/foo.apk"))
    }
}
