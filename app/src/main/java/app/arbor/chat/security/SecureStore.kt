package app.arbor.chat.security

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive



data class OpenAiOAuthSecrets(
    val accessToken: String,
    val accountId: String,
    val refreshToken: String?,
    val idToken: String?,
    val expiresAtEpochMs: Long,
    val isFedRamp: Boolean,
)

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

    @SuppressLint("UseKtx") // A checked synchronous commit is intentional before opening SQLCipher.
    fun databasePassphrase(): ByteArray {
        val existing = preferences.getString("database_passphrase", null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val fresh = ByteArray(32).also(SecureRandom()::nextBytes)
        check(preferences.edit().putString("database_passphrase", Base64.encodeToString(fresh, Base64.NO_WRAP)).commit())
        return fresh
    }

    fun apiKey(providerId: String): String = preferences.getString("key_$providerId", "").orEmpty()

    fun setApiKey(providerId: String, value: String) {
        preferences.edit { putString("key_$providerId", value.trim()) }
    }

    fun openAiOAuthSecrets(): OpenAiOAuthSecrets? {
        val raw = preferences.getString(OPENAI_OAUTH_SESSION, null) ?: return null
        return runCatching {
            val value = Json.parseToJsonElement(raw).jsonObject
            OpenAiOAuthSecrets(
                accessToken = value.getValue("accessToken").jsonPrimitive.content,
                accountId = value.getValue("accountId").jsonPrimitive.content,
                refreshToken = value["refreshToken"]?.jsonPrimitive?.contentOrNull,
                idToken = value["idToken"]?.jsonPrimitive?.contentOrNull,
                expiresAtEpochMs = value.getValue("expiresAtEpochMs").jsonPrimitive.content.toLong(),
                isFedRamp = value["isFedRamp"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true,
            )
        }.getOrNull()
    }

    fun setOpenAiOAuthSecrets(value: OpenAiOAuthSecrets?) {
        preferences.edit(commit = true) {
            if (value == null) {
                remove(OPENAI_OAUTH_SESSION)
            } else {
                putString(OPENAI_OAUTH_SESSION, buildJsonObject {
                    put("accessToken", JsonPrimitive(value.accessToken))
                    put("accountId", JsonPrimitive(value.accountId))
                    value.refreshToken?.let { put("refreshToken", JsonPrimitive(it)) }
                    value.idToken?.let { put("idToken", JsonPrimitive(it)) }
                    put("expiresAtEpochMs", JsonPrimitive(value.expiresAtEpochMs))
                    put("isFedRamp", JsonPrimitive(value.isFedRamp))
                }.toString())
            }
        }
    }

    private companion object {
        const val OPENAI_OAUTH_SESSION = "openai_oauth_session_v1"
    }
}
