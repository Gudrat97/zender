package zender.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Ключ API хранится зашифрованным (Android Keystore), а не открытым текстом. */
class ApiKeyStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "zender_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun get(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun set(apiKey: String) {
        prefs.edit().putString(KEY, apiKey.trim()).apply()
    }

    companion object {
        private const val KEY = "anthropic_api_key"
    }
}
