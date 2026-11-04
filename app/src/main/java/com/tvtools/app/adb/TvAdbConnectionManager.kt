package com.tvtools.app.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/**
 * Concrete [AbsAdbConnectionManager] backed by a persisted [AdbKeyStore].
 * One instance per process (the underlying connection is single-socket).
 */
class TvAdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {

    // Lazy: RSA key generation is expensive — do it on first connect (IO thread), not at app start.
    private val keyStore by lazy { AdbKeyStore(context.applicationContext) }

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(15, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = keyStore.keyPair.private

    override fun getCertificate(): Certificate = keyStore.certificate

    override fun getDeviceName(): String = "TV_Tools_${Build.MODEL.replace(" ", "_")}"

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
