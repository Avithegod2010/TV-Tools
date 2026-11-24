package com.tvtools.app.adb

/**
 * Seals the ADB private key before it touches disk. The production implementation is
 * [KeystoreKeyProtector] (an AndroidKeyStore AES/GCM key); tests use a stand-in.
 */
interface AdbKeyProtector {
    fun seal(plaintext: ByteArray): ByteArray
    fun unseal(sealed: ByteArray): ByteArray
}
