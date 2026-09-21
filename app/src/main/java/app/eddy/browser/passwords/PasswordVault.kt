package app.eddy.browser.passwords

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts saved passwords with an AES-256-GCM key that never leaves the Android Keystore.
 * The key is created on first use and is not tied to biometrics, so autofill works without a prompt;
 * the password manager screen enforces the screen lock separately.
 */
object PasswordVault {
    private const val ALIAS = "eddy_login_key"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Returns "iv:ciphertext", both Base64. */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + ":" + b64(data)
    }

    /** Null when the blob is corrupt or the key was lost (for example after a lock-screen reset). */
    fun decrypt(blob: String): String? = runCatching {
        val (iv, data) = blob.split(":", limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
