package me.zhanghai.android.files.provider.sftp.client

import android.content.Context
import androidx.core.content.edit
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.util.toBase64
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey

/**
 * Trusts the host key of a host on first use, and rejects any other host key for it afterwards.
 *
 * Unlike [net.schmizz.sshj.transport.verification.OpenSSHKnownHosts], a host key of a different
 * type is also rejected instead of being treated as unknown.
 */
object TrustOnFirstUseHostKeyVerifier : HostKeyVerifier {
    private const val SHARED_PREFERENCES_NAME = "sftp_host_keys"

    private val sharedPreferences by lazy {
        application.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val keyType = KeyType.fromKey(key)
        if (keyType == KeyType.UNKNOWN) {
            return false
        }
        val hostKey =
            "$keyType ${Buffer.PlainBuffer().putPublicKey(key).compactData.toBase64().value}"
        val preferenceKey = getPreferenceKey(hostname, port)
        val trustedHostKey = sharedPreferences.getString(preferenceKey, null)
        if (trustedHostKey == null) {
            sharedPreferences.edit { putString(preferenceKey, hostKey) }
            return true
        }
        return hostKey == trustedHostKey
    }

    @Synchronized
    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
        val trustedHostKey = sharedPreferences.getString(getPreferenceKey(hostname, port), null)
            ?: return emptyList()
        val keyType = trustedHostKey.substringBefore(' ')
        return if (keyType == KeyType.RSA.toString()) {
            // RSA host keys are also used with SHA-2 signatures.
            listOf(keyType, "rsa-sha2-256", "rsa-sha2-512")
        } else {
            listOf(keyType)
        }
    }

    @Synchronized
    fun removeHostKey(hostname: String, port: Int) {
        sharedPreferences.edit { remove(getPreferenceKey(hostname, port)) }
    }

    private fun getPreferenceKey(hostname: String, port: Int): String =
        "${hostname.lowercase()}:$port"
}
