package me.zhanghai.android.files.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cryptographic operations with keys that never leave Android Keystore.
 */
object KeyStoreHelper {
    private const val KEY_STORE_TYPE = "AndroidKeyStore"

    private const val AES_KEY_SIZE = 256
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_GCM_IV_SIZE = 12
    private const val AES_GCM_TAG_SIZE = 128

    private const val HMAC_ALGORITHM = "HmacSHA256"

    /**
     * Encrypts with AES-GCM and returns the random IV followed by the ciphertext.
     */
    @Throws(GeneralSecurityException::class)
    fun encrypt(keyAlias: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        // Android Keystore always generates a random IV for encryption.
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey(keyAlias))
        return cipher.iv + cipher.doFinal(plaintext)
    }

    @Throws(GeneralSecurityException::class)
    fun decrypt(keyAlias: String, data: ByteArray): ByteArray {
        val key = getKey(keyAlias) ?: throw GeneralSecurityException("No key for $keyAlias")
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE, key, GCMParameterSpec(AES_GCM_TAG_SIZE, data, 0, AES_GCM_IV_SIZE)
        )
        return cipher.doFinal(data, AES_GCM_IV_SIZE, data.size - AES_GCM_IV_SIZE)
    }

    @Throws(GeneralSecurityException::class)
    fun hmac(keyAlias: String, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(getOrCreateHmacKey(keyAlias))
        return mac.doFinal(data)
    }

    @Synchronized
    private fun getOrCreateAesKey(keyAlias: String): SecretKey {
        getKey(keyAlias)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE_TYPE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE)
                .build()
        )
        return keyGenerator.generateKey()
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
