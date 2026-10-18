package me.zhanghai.android.files.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Cryptographic operations with keys that never leave Android Keystore.
 */
object KeyStoreHelper {
    private const val KEY_STORE_TYPE = "AndroidKeyStore"

    private const val HMAC_ALGORITHM = "HmacSHA256"

    @Throws(GeneralSecurityException::class)
    fun hmac(keyAlias: String, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(getOrCreateHmacKey(keyAlias))
        return mac.doFinal(data)
    }

    @Synchronized
    private fun getOrCreateHmacKey(keyAlias: String): SecretKey {
        getKey(keyAlias)?.let { return it }
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEY_STORE_TYPE)
        keyGenerator.init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN).build())
        return keyGenerator.generateKey()
    }

    private fun getKey(keyAlias: String): SecretKey? {
        val keyStore = KeyStore.getInstance(KEY_STORE_TYPE).apply { load(null) }
        return keyStore.getKey(keyAlias, null) as SecretKey?
    }
}
