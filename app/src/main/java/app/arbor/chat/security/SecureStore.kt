package app.arbor.chat.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

class SecureStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        "arbor_secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun databasePassphrase(): ByteArray {
        val existing = preferences.getString("database_passphrase", null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val fresh = ByteArray(32).also(SecureRandom()::nextBytes)
        check(preferences.edit().putString("database_passphrase", Base64.encodeToString(fresh, Base64.NO_WRAP)).commit())
        return fresh
    }

    fun apiKey(providerId: String): String = preferences.getString("key_$providerId", "").orEmpty()

    fun setApiKey(providerId: String, value: String) {
        preferences.edit().putString("key_$providerId", value.trim()).apply()
    }
}
