package si.semafor.app.mojelektro

import android.content.Context
import org.json.JSONObject

/**
 * Encrypted on-device storage of the Moj Elektro token and EIMM.
 *
 * - Token and EIMM are stored together as ciphertext ([MojElektroCrypto], Keystore-backed AES-256-GCM, random IV per
 *   write) in the private SharedPreferences file "mojelektro". Backup and device transfer are switched off for the
 *   whole app (allowBackup=false and data_extraction_rules.xml), so the ciphertext is never copied off the device.
 * - If the data cannot be decrypted (for example the Keystore was reset), it is deleted and the user connects again.
 *
 * Blocking (Keystore + disk): call from a background thread.
 */
internal class MojElektroCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Result of reading the stored connection. */
    sealed interface Loaded {
        data object None : Loaded
        data class Present(val credentials: MojElektroCredentials) : Loaded

        /** Stored data existed but could not be decrypted; it has been deleted. */
        data object Unreadable : Loaded
    }

    /** True if a connection is stored (no decryption, cheap). */
    fun hasCredentials(): Boolean = prefs.contains(KEY_DATA)

    /** Encrypts and stores [credentials], replacing any earlier ones. Returns false if storing failed. */
    fun save(credentials: MojElektroCredentials): Boolean = try {
        val plain = JSONObject()
            .put("token", credentials.token)
            .put("eimm", credentials.eimm)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val value = MojElektroCrypto.encrypt(plain)
        plain.fill(0)
        prefs.edit().putString(KEY_DATA, value).commit()
    } catch (e: Exception) {
        false
    }

    fun load(): Loaded {
        val value = prefs.getString(KEY_DATA, null) ?: return Loaded.None
        return try {
            val plain = MojElektroCrypto.decrypt(value)
            val o = JSONObject(String(plain, Charsets.UTF_8))
            plain.fill(0)
            Loaded.Present(MojElektroCredentials(o.getString("token"), o.getString("eimm")))
        } catch (e: Exception) {
            clear()
            Loaded.Unreadable
        }
    }

    /** Deletes the stored token and EIMM, and the Keystore key (which also makes any cached readings unreadable). */
    fun clear() {
        prefs.edit().remove(KEY_DATA).commit()
        MojElektroCrypto.deleteKey()
    }

    private companion object {
        const val PREFS = "mojelektro"
        const val KEY_DATA = "credentials"
    }
}
