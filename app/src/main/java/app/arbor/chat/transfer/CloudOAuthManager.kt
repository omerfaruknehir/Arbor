package app.arbor.chat.transfer

import android.content.Context
import android.net.Uri
import android.util.Base64
import app.arbor.chat.BuildConfig
import app.arbor.chat.security.SecureStore
import app.arbor.chat.security.currentAppInstallIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class CloudOAuthManager(
    context: Context,
    private val secureStore: SecureStore,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val sessions = CloudOAuthProvider.entries.associateWith(::loadSession).toMutableMap()
    private val _states = MutableStateFlow(
        CloudOAuthProvider.entries.associateWith { provider -> initialState(provider) },
    )
    val states: StateFlow<Map<CloudOAuthProvider, CloudOAuthState>> = _states.asStateFlow()

    fun isBuildConfigured(provider: CloudOAuthProvider): Boolean = clientId(provider).isNotBlank()

    fun configurationReason(provider: CloudOAuthProvider): String? = when {
        isBuildConfigured(provider) -> null
        provider == CloudOAuthProvider.ONEDRIVE ->
            "This build is missing ARBOR_MICROSOFT_CLIENT_ID. Add the public Entra application client ID as a GitHub Actions repository variable."
        else ->
            "This build is missing ARBOR_DROPBOX_APP_KEY. Add the public Dropbox app key as a GitHub Actions repository variable."
    }

    fun redirectUri(provider: CloudOAuthProvider): String = when (provider) {
        CloudOAuthProvider.ONEDRIVE -> microsoftRedirectUri()
        CloudOAuthProvider.DROPBOX -> "db-${BuildConfig.DROPBOX_APP_KEY}://2/token"
    }

    fun beginAuthorization(provider: CloudOAuthProvider): Uri {
        val clientId = clientId(provider)
        require(clientId.isNotBlank()) { configurationReason(provider) ?: "Cloud OAuth is not configured" }
        val verifier = randomBase64(64)
        val pending = PendingCloudOAuth(
            provider = provider,
            state = randomBase64(32),
            codeVerifier = verifier,
            redirectUri = redirectUri(provider),
            createdAtEpochMs = System.currentTimeMillis(),
        )
        secureStore.setCloudRecord(pendingKey(provider), json.encodeToString(pending))
        updateState(provider, CloudOAuthState.Authorizing(pending.createdAtEpochMs))
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        return when (provider) {
            CloudOAuthProvider.ONEDRIVE -> Uri.parse(MICROSOFT_AUTHORIZE).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", pending.redirectUri)
                .appendQueryParameter("response_mode", "query")
                .appendQueryParameter("scope", MICROSOFT_SCOPE)
                .appendQueryParameter("state", pending.state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("prompt", "select_account")
                .build()
            CloudOAuthProvider.DROPBOX -> Uri.parse(DROPBOX_AUTHORIZE).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", pending.redirectUri)
                .appendQueryParameter("state", pending.state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("token_access_type", "offline")
                .appendQueryParameter("scope", DROPBOX_SCOPE)
                .build()
        }
    }

    fun canHandleRedirect(uri: Uri): Boolean = when {
        uri.scheme.equals("msauth", ignoreCase = true) && uri.host == BuildConfig.APPLICATION_ID -> true
        BuildConfig.DROPBOX_APP_KEY.isNotBlank() &&
            uri.scheme.equals("db-${BuildConfig.DROPBOX_APP_KEY}", ignoreCase = true) &&
            uri.host == "2" -> true
        else -> false
    }

    suspend fun completeRedirect(uri: Uri): CloudOAuthSession = mutex.withLock {
        val provider = when {
            uri.scheme.equals("msauth", ignoreCase = true) -> CloudOAuthProvider.ONEDRIVE
            else -> CloudOAuthProvider.DROPBOX
        }
        try {
            val pending = loadPending(provider)
                ?: error("The ${provider.displayName} sign-in session expired. Start connection again.")
            require(System.currentTimeMillis() - pending.createdAtEpochMs <= PENDING_MAX_AGE_MS) {
                "The ${provider.displayName} sign-in session expired. Start connection again."
            }
            require(uri.getQueryParameter("state") == pending.state) {
                "The ${provider.displayName} sign-in response did not match this request."
            }
            uri.getQueryParameter("error")?.let { code ->
                val detail = uri.getQueryParameter("error_description").orEmpty()
                error(detail.ifBlank { "$provider authorization failed: $code" })
            }
            val code = uri.getQueryParameter("code")
                ?.takeIf(String::isNotBlank)
                ?: error("${provider.displayName} returned no authorization code")
            val session = withContext(Dispatchers.IO) {
                exchangeAuthorizationCode(provider, pending, code)
            }
            saveSession(session)
            secureStore.setCloudRecord(pendingKey(provider), null)
            sessions[provider] = session
            updateState(provider, CloudOAuthState.Connected(session.accountLabel))
            session
        } catch (error: Throwable) {
            updateState(provider, CloudOAuthState.Error(error.message ?: "${provider.displayName} sign-in failed"))
            throw error
        }
    }

    suspend fun accessToken(provider: CloudOAuthProvider): String = mutex.withLock {
        val session = sessions[provider] ?: loadSession(provider)
            ?: error("Connect ${provider.displayName} first")
        if (session.expiresAtEpochMs - System.currentTimeMillis() > TOKEN_REFRESH_MARGIN_MS) {
            sessions[provider] = session
            return session.accessToken
        }
        val refresh = session.refreshToken
            ?: error("${provider.displayName} authorization expired. Connect the account again.")
        val refreshed = withContext(Dispatchers.IO) { refreshSession(provider, session, refresh) }
        saveSession(refreshed)
        sessions[provider] = refreshed
        updateState(provider, CloudOAuthState.Connected(refreshed.accountLabel))
        refreshed.accessToken
    }

    fun disconnect(provider: CloudOAuthProvider) {
        sessions.remove(provider)
        secureStore.setCloudRecord(sessionKey(provider), null)
        secureStore.setCloudRecord(pendingKey(provider), null)
        updateState(provider, initialState(provider))
    }

    private fun exchangeAuthorizationCode(
        provider: CloudOAuthProvider,
        pending: PendingCloudOAuth,
        code: String,
    ): CloudOAuthSession {
        val body = FormBody.Builder()
            .add("client_id", clientId(provider))
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", pending.redirectUri)
            .add("code_verifier", pending.codeVerifier)
            .build()
        val root = tokenRequest(tokenEndpoint(provider), body)
        val accessToken = root["access_token"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: error("${provider.displayName} returned no access token")
        val refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull
        val expiresIn = root["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3_600L
        val idToken = root["id_token"]?.jsonPrimitive?.contentOrNull
        val label = when (provider) {
            CloudOAuthProvider.ONEDRIVE -> idToken?.let(::jwtAccountLabel)
            CloudOAuthProvider.DROPBOX -> fetchDropboxAccountLabel(accessToken)
        }
        return CloudOAuthSession(
            provider = provider,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochMs = System.currentTimeMillis() + expiresIn.coerceAtLeast(60L) * 1_000L,
            accountLabel = label,
            scope = root["scope"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun refreshSession(
        provider: CloudOAuthProvider,
        previous: CloudOAuthSession,
        refreshToken: String,
    ): CloudOAuthSession {
        val body = FormBody.Builder()
            .add("client_id", clientId(provider))
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .apply {
                if (provider == CloudOAuthProvider.ONEDRIVE) add("scope", MICROSOFT_SCOPE)
            }
            .build()
        val root = tokenRequest(tokenEndpoint(provider), body)
        val accessToken = root["access_token"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: error("${provider.displayName} returned no refreshed access token")
        val expiresIn = root["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3_600L
        return previous.copy(
            accessToken = accessToken,
            refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull ?: refreshToken,
            expiresAtEpochMs = System.currentTimeMillis() + expiresIn.coerceAtLeast(60L) * 1_000L,
            scope = root["scope"]?.jsonPrimitive?.contentOrNull ?: previous.scope,
        )
    }

    private fun tokenRequest(url: String, body: FormBody) = client.newCall(
        Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(body)
            .build(),
    ).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        if (!response.isSuccessful) {
            val detail = parsed?.get("error_description")?.jsonPrimitive?.contentOrNull
                ?: parsed?.get("error_summary")?.jsonPrimitive?.contentOrNull
                ?: parsed?.get("error")?.jsonPrimitive?.contentOrNull
            throw IOException(detail ?: "OAuth token exchange failed with HTTP ${response.code}")
        }
        parsed ?: error("OAuth provider returned invalid JSON")
    }

    private fun fetchDropboxAccountLabel(accessToken: String): String? = runCatching {
        client.newCall(
            Request.Builder()
                .url("https://api.dropboxapi.com/2/users/get_current_account")
                .header("Authorization", "Bearer $accessToken")
                .post(ByteArray(0).let { okhttp3.RequestBody.create(null, it) })
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            root["email"]?.jsonPrimitive?.contentOrNull
                ?: root["name"]?.jsonObject?.get("display_name")?.jsonPrimitive?.contentOrNull
        }
    }.getOrNull()

    private fun jwtAccountLabel(token: String): String? = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return@runCatching null
        val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        val root = json.parseToJsonElement(decoded).jsonObject
        root["preferred_username"]?.jsonPrimitive?.contentOrNull
            ?: root["email"]?.jsonPrimitive?.contentOrNull
            ?: root["name"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun microsoftRedirectUri(): String {
        val fingerprint = appContext.currentAppInstallIdentity().signingSha1
        val raw = fingerprint.split(':').mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        require(raw.isNotEmpty()) { "Could not read Arbor's signing certificate" }
        return "msauth://${BuildConfig.APPLICATION_ID}/${base64Url(raw)}"
    }

    private fun initialState(provider: CloudOAuthProvider): CloudOAuthState {
        val reason = configurationReason(provider)
        if (reason != null) return CloudOAuthState.Unavailable(reason)
        val session = sessions[provider] ?: loadSession(provider)
        return if (session == null) CloudOAuthState.Disconnected
        else CloudOAuthState.Connected(session.accountLabel)
    }

    private fun updateState(provider: CloudOAuthProvider, state: CloudOAuthState) {
        _states.value = _states.value.toMutableMap().apply { put(provider, state) }
    }

    private fun loadSession(provider: CloudOAuthProvider): CloudOAuthSession? =
        secureStore.cloudRecord(sessionKey(provider))?.let { raw ->
            runCatching { json.decodeFromString<CloudOAuthSession>(raw) }
                .getOrNull()
                ?.takeIf { it.provider == provider }
        }

    private fun saveSession(session: CloudOAuthSession) {
        secureStore.setCloudRecord(sessionKey(session.provider), json.encodeToString(session))
    }

    private fun loadPending(provider: CloudOAuthProvider): PendingCloudOAuth? =
        secureStore.cloudRecord(pendingKey(provider))?.let { raw ->
            runCatching { json.decodeFromString<PendingCloudOAuth>(raw) }
                .getOrNull()
                ?.takeIf { it.provider == provider }
        }

    private fun clientId(provider: CloudOAuthProvider): String = when (provider) {
        CloudOAuthProvider.ONEDRIVE -> BuildConfig.MICROSOFT_CLIENT_ID
        CloudOAuthProvider.DROPBOX -> BuildConfig.DROPBOX_APP_KEY
    }

    private fun tokenEndpoint(provider: CloudOAuthProvider): String = when (provider) {
        CloudOAuthProvider.ONEDRIVE -> MICROSOFT_TOKEN
        CloudOAuthProvider.DROPBOX -> DROPBOX_TOKEN
    }

    private fun randomBase64(size: Int): String = ByteArray(size).also(random::nextBytes).let(::base64Url)

    private fun base64Url(value: ByteArray): String =
        Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun sessionKey(provider: CloudOAuthProvider) = "oauth_session_${provider.name.lowercase()}_v1"
    private fun pendingKey(provider: CloudOAuthProvider) = "oauth_pending_${provider.name.lowercase()}_v1"

    companion object {
        private const val MICROSOFT_AUTHORIZE = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        private const val MICROSOFT_TOKEN = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        private const val MICROSOFT_SCOPE = "openid profile email offline_access Files.ReadWrite.AppFolder"
        private const val DROPBOX_AUTHORIZE = "https://www.dropbox.com/oauth2/authorize"
        private const val DROPBOX_TOKEN = "https://api.dropboxapi.com/oauth2/token"
        private const val DROPBOX_SCOPE = "account_info.read files.metadata.read files.content.read files.content.write"
        private const val TOKEN_REFRESH_MARGIN_MS = 2L * 60L * 1_000L
        private const val PENDING_MAX_AGE_MS = 15L * 60L * 1_000L
    }
}
