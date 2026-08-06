package app.xylune.chat.security

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


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
        "xylune_secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @SuppressLint("UseKtx")
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

    fun searchApiKey(engineId: String): String =
        preferences.getString("search_key_${engineId.lowercase().filter(Char::isLetterOrDigit)}", "").orEmpty()

    fun setSearchApiKey(engineId: String, value: String) {
        val key = "search_key_${engineId.lowercase().filter(Char::isLetterOrDigit)}"
        preferences.edit {
            if (value.isBlank()) remove(key) else putString(key, value.trim())
        }
    }

    fun cloudRecord(key: String): String? =
        preferences.getString("cloud_${key.filter { it.isLetterOrDigit() || it == '_' || it == '-' }}", null)

    fun setCloudRecord(key: String, value: String?) {
        val safeKey = "cloud_${key.filter { it.isLetterOrDigit() || it == '_' || it == '-' }}"
        preferences.edit(commit = true) {
            if (value.isNullOrBlank()) remove(safeKey) else putString(safeKey, value)
        }
    }

    /** All OAuth sessions are encrypted together and keyed by Xylune provider id. */
    fun openAiOAuthAccounts(): Map<String, OpenAiOAuthSecrets> {
        val current = preferences.getString(OPENAI_OAUTH_SESSIONS, null)?.let(::parseAccountMap).orEmpty()
        if (current.isNotEmpty()) return current
        val legacy = preferences.getString(OPENAI_OAUTH_SESSION_LEGACY, null)?.let(::parseSecrets) ?: return emptyMap()
        val migrated = mapOf(DEFAULT_OPENAI_OAUTH_PROVIDER_ID to legacy)
        writeOpenAiOAuthAccounts(migrated)
        return migrated
    }

    fun openAiOAuthSecrets(providerId: String = DEFAULT_OPENAI_OAUTH_PROVIDER_ID): OpenAiOAuthSecrets? =
        openAiOAuthAccounts()[providerId]

    fun setOpenAiOAuthSecrets(providerId: String, value: OpenAiOAuthSecrets?) {
        val accounts = openAiOAuthAccounts().toMutableMap()
        if (value == null) accounts.remove(providerId) else accounts[providerId] = value
        writeOpenAiOAuthAccounts(accounts)
    }

    /** Compatibility overload for the original single-account provider. */
    fun setOpenAiOAuthSecrets(value: OpenAiOAuthSecrets?) =
        setOpenAiOAuthSecrets(DEFAULT_OPENAI_OAUTH_PROVIDER_ID, value)

    private fun writeOpenAiOAuthAccounts(accounts: Map<String, OpenAiOAuthSecrets>) {
        val raw = buildJsonObject {
            accounts.toSortedMap().forEach { (providerId, secrets) ->
                put(providerId, secrets.toJson())
            }
        }.toString()
        preferences.edit(commit = true) {
            if (accounts.isEmpty()) remove(OPENAI_OAUTH_SESSIONS) else putString(OPENAI_OAUTH_SESSIONS, raw)
            remove(OPENAI_OAUTH_SESSION_LEGACY)
        }
    }

    private fun parseAccountMap(raw: String): Map<String, OpenAiOAuthSecrets> = runCatching {
        Json.parseToJsonElement(raw).jsonObject.mapNotNull { (providerId, element) ->
            parseSecrets(element.toString())?.let { providerId to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun parseSecrets(raw: String): OpenAiOAuthSecrets? = runCatching {
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

    private fun OpenAiOAuthSecrets.toJson(): JsonObject = buildJsonObject {
        put("accessToken", JsonPrimitive(accessToken))
        put("accountId", JsonPrimitive(accountId))
        refreshToken?.let { put("refreshToken", JsonPrimitive(it)) }
        idToken?.let { put("idToken", JsonPrimitive(it)) }
        put("expiresAtEpochMs", JsonPrimitive(expiresAtEpochMs))
        put("isFedRamp", JsonPrimitive(isFedRamp))
    }

    private companion object {
        const val DEFAULT_OPENAI_OAUTH_PROVIDER_ID = "openai-oauth"
        const val OPENAI_OAUTH_SESSION_LEGACY = "openai_oauth_session_v1"
        const val OPENAI_OAUTH_SESSIONS = "openai_oauth_sessions_v2"
    }
}
