package com.tvtools.app.adb

import java.io.File

/**
 * On-disk layout of the ADB identity private key.
 *
 * New writes are `MAGIC || seal(pkcs8)` where `seal` is an AndroidKeyStore-backed AES/GCM
 * wrapper, so the plaintext PKCS#8 never reaches storage. A key written by an older build is
 * plain PKCS#8; [readOrMigrate] reads it, rewrites the file sealed, and returns the same key
 * bytes — already-authorised TVs keep working because the key material is unchanged.
 */
object AdbKeyFile {

    private val MAGIC = byteArrayOf(0x54, 0x56, 0x54, 0x4B, 0x01) // "TVTK", format 1
    private const val MAX_PLAINTEXT_BYTES = 16 * 1024
    private const val MAX_SEALED_BYTES = 64 * 1024

    fun isSealed(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    /** Returns the plaintext PKCS#8 bytes, sealing a legacy plaintext file before returning. */
    fun readOrMigrate(file: File, protector: AdbKeyProtector): ByteArray {
        val bytes = file.readBytes()
        require(bytes.isNotEmpty()) { "empty key file" }
        require(bytes.size <= MAX_SEALED_BYTES) { "oversized key file" }
        if (isSealed(bytes)) {
            return protector.unseal(bytes.copyOfRange(MAGIC.size, bytes.size))
        }
        require(bytes.size <= MAX_PLAINTEXT_BYTES) { "oversized plaintext key file" }
        writeAtomically(file, frame(protector.seal(bytes)))
        return bytes
    }

    fun write(file: File, plaintext: ByteArray, protector: AdbKeyProtector) {
        require(plaintext.isNotEmpty()) { "empty key" }
        writeAtomically(file, frame(protector.seal(plaintext)))
    }

    private fun frame(sealed: ByteArray): ByteArray = MAGIC + sealed

    /**
     * Replace-in-place so a crash cannot leave a truncated key behind: the sealed blob goes to a
     * sibling temp file first, then replaces the target. If the filesystem refuses the rename the
     * sealed bytes are written directly, which still removes the plaintext.
     */
    private fun writeAtomically(file: File, bytes: ByteArray) {
        val dir = file.parentFile ?: error("key file has no parent directory")
        if (!dir.exists()) dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                file.writeBytes(bytes)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
