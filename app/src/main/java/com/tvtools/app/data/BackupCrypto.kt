package com.tvtools.app.data

import java.io.File
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted export of the whole backups folder.
 * File layout: MAGIC(4) + salt(16) + iv(12) + AES-GCM ciphertext (zip).
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('T'.code.toByte(), 'V'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte())
    private const val ITERATIONS = 120_000

    fun export(backupsRoot: File, output: File, passphrase: CharArray) {
        val zipTmp = File.createTempFile("tvtools_export", ".zip")
        try {
            ZipOutputStream(zipTmp.outputStream().buffered()).use { zos ->
                backupsRoot.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = f.relativeTo(backupsRoot).invariantSeparatorsPath
                    zos.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val key = derive(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(zipTmp.readBytes())
            output.outputStream().use { out ->
                out.write(MAGIC)
                out.write(salt)
                out.write(iv)
                out.write(encrypted)
            }
        } finally {
            zipTmp.delete()
        }
    }

    /** Restores into [backupsRoot]; returns number of files written. */
    fun import(input: File, backupsRoot: File, passphrase: CharArray): Int {
        val data = input.readBytes()
        require(data.size > 4 + 16 + 12 && data.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not a TV Tools backup" }
        val salt = data.copyOfRange(4, 20)
        val iv = data.copyOfRange(20, 32)
        val ciphertext = data.copyOfRange(32, data.size)
        val key = derive(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val zipBytes = cipher.doFinal(ciphertext)
        var count = 0
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(backupsRoot, entry.name)
                out.parentFile?.mkdirs()
                out.outputStream().use { zis.copyTo(it) }
                count++
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    private fun derive(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        return SecretKeySpec(key, "AES")
    }
}
