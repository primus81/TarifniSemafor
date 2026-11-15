package si.semafor.app.mojelektro

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption with a key that is created in, and never leaves, the Android Keystore. Used for everything
 * the Moj Elektro connection keeps on the device: the token and EIMM ([MojElektroCredentialStore]) and the cached
 * meter readings ([UsageCache]).
 *
 * Why not EncryptedSharedPreferences: androidx.security:security-crypto is deprecated by Google and no longer
 * maintained. The same protection (Keystore-backed AES-GCM) is done here directly, without an extra library.
 *
 * Blocking (Keystore): call from a background thread.
 */
internal object MojElektroCrypto {
    private const val KEY_ALIAS = "si.semafor.app.mojelektro"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val SEPARATOR = ":"

    /** Encrypts [plain] with a fresh random IV. Result: "base64(iv):base64(ciphertext)". */
    fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain)
        return b64(cipher.iv) + SEPARATOR + b64(encrypted)
    }

    /** Decrypts a value of [encrypt]. Throws if the data or the key is not usable. */
    fun decrypt(value: String): ByteArray {
        val parts = value.split(SEPARATOR)
        require(parts.size == 2) { "Corrupt data" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: error("Key missing"), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    /** Deletes the key: everything encrypted with it becomes unreadable. */
    @Synchronized
    fun deleteKey() {
        try {
            keyStore().deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            // No key (or no Keystore): nothing to delete.
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey

    // Synchronized: two first-time writers on different threads must not each generate a key (the second would
    // replace the first and make what was just encrypted with it unreadable).
    @Synchronized
    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        ANDROID_KEYSTORE
    ).apply {
        init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
    }.generateKey()

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
