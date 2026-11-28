package com.tvtools.app.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbConnection
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Concrete [AbsAdbConnectionManager] backed by a persisted [AdbKeyStore].
 * Provides pairing and creates independently closable sessions using the same ADB identity.
 */
class TvAdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {

    // Lazy: RSA key generation is expensive — do it on first connect (IO thread), not at app start.
    private val keyStore by lazy { AdbKeyStore(context.applicationContext) }

    // libadb destroys its copy of the key when a session or pairing attempt closes.
    override fun getPrivateKey(): PrivateKey = KeyFactory.getInstance("RSA")
        .generatePrivate(PKCS8EncodedKeySpec(keyStore.keyPair.private.encoded))

    override fun getCertificate(): Certificate = keyStore.certificate

    override fun getDeviceName(): String = "TV_Tools_${Build.MODEL.replace(" ", "_")}"

    fun createConnection(host: String, port: Int): AdbConnection = AdbConnection.Builder(host, port)
        .setApi(Build.VERSION.SDK_INT)
        .setPrivateKey(getPrivateKey())
        .setCertificate(getCertificate())
        .setDeviceName(getDeviceName())
        .build()

    companion object {
        @Volatile
        private var instance: TvAdbConnectionManager? = null

        fun getInstance(context: Context): TvAdbConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: TvAdbConnectionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
