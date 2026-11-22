package com.tvtools.app.adb

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AdbKeyFileTest {

    private val dir: File = Files.createTempDirectory("adbkey-test").toFile()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** JVM stand-in for the AndroidKeyStore-backed protector. */
    private class FakeProtector(private val key: SecretKeySpec) : AdbKeyProtector {
        var sealCalls = 0
            private set

        override fun seal(plaintext: ByteArray): ByteArray {
            sealCalls++
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return cipher.iv + cipher.doFinal(plaintext)
        }

        override fun unseal(sealed: ByteArray): ByteArray {
            val iv = sealed.copyOfRange(0, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return cipher.doFinal(sealed, 12, sealed.size - 12)
        }
    }

    private fun protector() = FakeProtector(SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES"))

    @Test
    fun `plaintext key on disk is migrated and the plaintext is gone`() {
        val legacy = ByteArray(96) { (it * 7).toByte() }
        val file = File(dir, "adb_key.pk8")
        file.writeBytes(legacy)

        val p = protector()
        val readBack = AdbKeyFile.readOrMigrate(file, p)

        // Same key material, so TVs that already authorised this app keep working.
        assertArrayEquals(legacy, readBack)
        val onDisk = file.readBytes()
        assertFalse("plaintext must not remain on disk", legacy.contentEquals(onDisk))
        assertTrue(AdbKeyFile.isSealed(onDisk))
        assertEquals("legacy file is sealed exactly once", 1, p.sealCalls)
        assertEquals(
            "no temp or plaintext sibling is left behind",
            listOf("adb_key.pk8"),
            dir.listFiles()!!.map { it.name }.sorted(),
        )
    }

    @Test
    fun `already sealed key is read without re-sealing`() {
        val material = ByteArray(48) { (it + 3).toByte() }
        val file = File(dir, "adb_key.pk8")
        val p = protector()
        AdbKeyFile.write(file, material, p)
        assertEquals(1, p.sealCalls)

        val readBack = AdbKeyFile.readOrMigrate(file, p)

        assertArrayEquals(material, readBack)
        assertEquals("sealed file must not be rewritten", 1, p.sealCalls)
    }

    @Test
    fun `written file is not the plaintext bytes`() {
        val material = ByteArray(32) { it.toByte() }
        val file = File(dir, "adb_key.pk8")

        AdbKeyFile.write(file, material, protector())

        assertTrue(AdbKeyFile.isSealed(file.readBytes()))
        assertFalse(material.contentEquals(file.readBytes()))
    }

    @Test
    fun `tampered sealed key is rejected`() {
        val file = File(dir, "adb_key.pk8")
        AdbKeyFile.write(file, ByteArray(40) { it.toByte() }, protector())
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
        file.writeBytes(bytes)

        var threw = false
        try {
            AdbKeyFile.readOrMigrate(file, protector())
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("GCM tamper must fail closed", threw)
    }

    @Test
    fun `empty legacy file is rejected instead of being sealed`() {
        val file = File(dir, "adb_key.pk8")
        file.writeBytes(ByteArray(0))

        var threw = false
        try {
            AdbKeyFile.readOrMigrate(file, protector())
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
        assertFalse(AdbKeyFile.isSealed(file.readBytes()))
    }
}
