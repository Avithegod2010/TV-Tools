package com.tvtools.app.adb

import android.content.Context
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random

/**
 * Generates and persists the RSA key pair + self-signed certificate used to
 * authenticate this app to ADB daemons (the "ADB public key").
 */
class AdbKeyStore(context: Context) {

    private val keyDir = File(context.filesDir, "adbkeys")
    private val privFile = File(keyDir, "adb_key.pk8")
    private val certFile = File(keyDir, "adb_cert.der")

    val keyPair: KeyPair
    val certificate: Certificate

    init {
        val loaded: Pair<KeyPair, Certificate>? = if (privFile.exists() && certFile.exists()) {
            try {
                val privBytes = privFile.readBytes()
                val privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certFile.inputStream())
                KeyPair(cert.publicKey, privateKey) to cert
            } catch (e: Exception) {
                // Corrupted store: regenerate
                privFile.delete(); certFile.delete()
                null
            }
        } else null

        val result = loaded ?: generate()
        keyPair = result.first
        certificate = result.second
    }

    private fun generate(): Pair<KeyPair, Certificate> {
        keyDir.mkdirs()
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
        val kp = keyPairGenerator.generateKeyPair()

        val algorithmName = "SHA512withRSA"
        val expiryDate = System.currentTimeMillis() + 30L * 365 * 24 * 60 * 60 * 1000

        val extensions = CertificateExtensions()
        extensions.set(
            "SubjectKeyIdentifier",
            SubjectKeyIdentifierExtension(KeyIdentifier(kp.public).identifier)
        )
        val x500Name = X500Name("CN=TV Tools")
        val notBefore = Date()
        val notAfter = Date(expiryDate)
        extensions.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))
        val validity = CertificateValidity(notBefore, notAfter)

        val info = X509CertInfo()
        info.set("version", CertificateVersion(2))
        info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
        info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
        info.set("subject", CertificateSubjectName(x500Name))
        info.set("key", CertificateX509Key(kp.public))
        info.set("validity", validity)
        info.set("issuer", CertificateIssuerName(x500Name))
        info.set("extensions", extensions)

        val cert = X509CertImpl(info)
        cert.sign(kp.private, algorithmName)

        privFile.writeBytes(kp.private.encoded)
        certFile.writeBytes(cert.encoded)

        return KeyPair(kp.public, kp.private) to cert
    }
}
