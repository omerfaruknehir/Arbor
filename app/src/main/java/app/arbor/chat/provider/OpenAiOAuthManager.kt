package app.arbor.chat.provider

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import app.arbor.chat.security.OpenAiOAuthSecrets
import app.arbor.chat.security.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedInputStream
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

sealed interface OpenAiOAuthState {
    data object SignedOut : OpenAiOAuthState
    data object SigningIn : OpenAiOAuthState
    data class SignedIn(val accountId: String, val email: String?) : OpenAiOAuthState
    data class Error(val message: String) : OpenAiOAuthState
}

data class OpenAiOAuthModelInfo(
    val id: String,
    val displayName: String,
    val contextWindow: Int?,
    val maxOutputTokens: Int?,
    val supportsThinking: Boolean,
    val useResponsesLite: Boolean,
    val defaultReasoningLevel: String?,
)

data class OpenAiOAuthSession(
    val accessToken: String,
    val accountId: String,
    val refreshToken: String?,
    val idToken: String?,
    val expiresAtEpochMs: Long,
    val isFedRamp: Boolean,
)

/** Native Android PKCE + loopback OAuth flow compatible with Codex/OpenAI OAuth. */
class OpenAiOAuthManager(
    context: Context,
    private val secureStore: SecureStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    private val appContext = context.applicationContext
    private val loginMutex = Mutex()
    private val refreshMutex = Mutex()
    private val modelMutex = Mutex()
    private var cachedModels: List<OpenAiOAuthModelInfo> = emptyList()
    private var cachedModelsUntil = 0L
    private val random = SecureRandom()
    @Volatile private var activeLoginServers: List<ServerSocket> = emptyList()
    @Volatile private var loginCancelled = false
    private var session: OpenAiOAuthSession? = secureStore.openAiOAuthSecrets()?.toSession()

    private val _state = MutableStateFlow<OpenAiOAuthState>(
        session?.let(::signedInState) ?: OpenAiOAuthState.SignedOut,
    )
    val state: StateFlow<OpenAiOAuthState> = _state.asStateFlow()

    fun signedInAccountId(): String? = session?.accountId

    suspend fun modelInfo(modelId: String): OpenAiOAuthModelInfo? = modelCatalog().firstOrNull { it.id == modelId }

    suspend fun modelCatalog(forceRefresh: Boolean = false): List<OpenAiOAuthModelInfo> = modelMutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedModels.isNotEmpty() && now < cachedModelsUntil) return cachedModels
        var auth = validSession()
        val models = try {
            withContext(Dispatchers.IO) { fetchModelCatalog(auth) }
        } catch (error: ProviderHttpException) {
            if (error.status != 401) throw error
            auth = validSession(forceRefresh = true)
            withContext(Dispatchers.IO) { fetchModelCatalog(auth) }
        }
        require(models.isNotEmpty()) { "The ChatGPT account returned no usable models" }
        cachedModels = models
        cachedModelsUntil = now + MODEL_CACHE_MS
        models
    }

    suspend fun signIn(): OpenAiOAuthSession? = loginMutex.withLock {
        loginCancelled = false
        _state.value = OpenAiOAuthState.SigningIn
        try {
            val result = withContext(Dispatchers.IO) { performBrowserLogin() }
            if (loginCancelled) {
                _state.value = OpenAiOAuthState.SignedOut
                return@withLock null
            }
            persist(result)
            _state.value = signedInState(result)
            result
        } catch (error: Throwable) {
            if (loginCancelled) {
                _state.value = OpenAiOAuthState.SignedOut
                null
            } else {
                val message = error.message?.take(500) ?: "ChatGPT sign-in failed"
                _state.value = OpenAiOAuthState.Error(message)
                throw error
            }
        }
    }

    fun cancelSignIn() {
        loginCancelled = true
        activeLoginServers.forEach { server -> runCatching { server.close() } }
        if (_state.value is OpenAiOAuthState.SigningIn) _state.value = OpenAiOAuthState.SignedOut
    }

    fun signOut() {
        cancelSignIn()
        session = null
        secureStore.setOpenAiOAuthSecrets(null)
        _state.value = OpenAiOAuthState.SignedOut
    }

    suspend fun validSession(forceRefresh: Boolean = false): OpenAiOAuthSession = refreshMutex.withLock {
        val current = session ?: secureStore.openAiOAuthSecrets()?.toSession()?.also { session = it }
            ?: throw IllegalStateException("Sign in with ChatGPT in Settings")
        if (!forceRefresh && current.expiresAtEpochMs - REFRESH_EARLY_MS > System.currentTimeMillis()) return current
        val refreshToken = current.refreshToken
            ?: throw IllegalStateException("The ChatGPT session expired. Sign in again in Settings")
        return try {
            val refreshed = withContext(Dispatchers.IO) { refresh(refreshToken, current) }
            persist(refreshed)
            _state.value = signedInState(refreshed)
            refreshed
        } catch (error: Throwable) {
            _state.value = OpenAiOAuthState.Error(error.message?.take(500) ?: "ChatGPT session refresh failed")
            throw error
        }
    }

    private fun persist(value: OpenAiOAuthSession) {
        session = value
        secureStore.setOpenAiOAuthSecrets(value.toSecrets())
    }

    private fun performBrowserLogin(): OpenAiOAuthSession {
        val state = randomBase64Url(24)
        val verifier = randomBase64Url(48)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val servers = bindLoopbackServers()
        activeLoginServers = servers

        try {
            check(!loginCancelled) { "ChatGPT sign-in was cancelled" }
            val authorizationUrl = AUTHORIZATION_URL.toUri().buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", SCOPE)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("id_token_add_organizations", "true")
                .appendQueryParameter("codex_cli_simplified_flow", "true")
                .build()

            val browser = Intent(Intent.ACTION_VIEW, authorizationUrl)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                appContext.startActivity(browser)
            } catch (error: ActivityNotFoundException) {
                throw IllegalStateException("No browser is available for ChatGPT sign-in", error)
            }

            val callback = awaitCallback(servers)
            if (callback.state != state) {
                sendBrowserResponse(callback.socket, false, "The sign-in state did not match. Return to Arbor and try again.")
                callback.socket.close()
                throw IllegalStateException("ChatGPT sign-in was rejected because its state did not match")
            }
            callback.error?.let { oauthError ->
                sendBrowserResponse(callback.socket, false, oauthError)
                callback.socket.close()
                throw IllegalStateException(oauthError)
            }
            val code = callback.code ?: run {
                sendBrowserResponse(callback.socket, false, "OpenAI did not return an authorization code.")
                callback.socket.close()
                throw IllegalStateException("OpenAI did not return an authorization code")
            }

            return try {
                exchangeCode(code, verifier).also {
                    sendBrowserResponse(callback.socket, true, "Signed in. Returning to Arbor…")
                }
            } catch (error: Throwable) {
                sendBrowserResponse(callback.socket, false, error.message ?: "Token exchange failed")
                throw error
            } finally {
                callback.socket.close()
            }
        } finally {
            servers.forEach { server -> runCatching { server.close() } }
            if (activeLoginServers === servers) activeLoginServers = emptyList()
        }
    }

    private fun bindLoopbackServers(): List<ServerSocket> {
        val servers = mutableListOf<ServerSocket>()
        for (host in LOOPBACK_HOSTS) {
            val server = ServerSocket()
            try {
                server.reuseAddress = true
                server.bind(InetSocketAddress(InetAddress.getByName(host), CALLBACK_PORT), 8)
                servers += server
            } catch (error: BindException) {
                runCatching { server.close() }
                servers.forEach { bound -> runCatching { bound.close() } }
                throw IllegalStateException(
                    "ChatGPT sign-in needs localhost port $CALLBACK_PORT, but another app is already using it",
                    error,
                )
            } catch (_: SocketException) {
                // Some Android kernels disable IPv6. The other loopback family is enough.
                runCatching { server.close() }
            }
        }
        check(servers.isNotEmpty()) { "No loopback address is available for ChatGPT sign-in" }
        return servers
    }

    private fun awaitCallback(servers: List<ServerSocket>): BrowserCallback {
        val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS
        while (true) {
            check(!loginCancelled) { "ChatGPT sign-in was cancelled" }
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) throw IllegalStateException("ChatGPT sign-in timed out")
            for (server in servers) {
                server.soTimeout = remaining.coerceAtMost(CALLBACK_POLL_MS).toInt()
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (error: SocketException) {
                    if (loginCancelled) throw IllegalStateException("ChatGPT sign-in was cancelled", error)
                    throw error
                }
                socket.soTimeout = 10_000
                val target = readRequestTarget(socket)
                if (target == null || !target.substringBefore('?').equals("/auth/callback", ignoreCase = true)) {
                    sendRawResponse(socket, 404, "Not found")
                    socket.close()
                    continue
                }
                val uri = "http://localhost$target".toUri()
                return BrowserCallback(
                    socket = socket,
                    code = uri.getQueryParameter("code"),
                    state = uri.getQueryParameter("state"),
                    error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error"),
                )
            }
        }
    }

    private fun readRequestTarget(socket: Socket): String? {
        val input = BufferedInputStream(socket.getInputStream())
        val line = StringBuilder()
        while (line.length < MAX_REQUEST_LINE) {
            val byte = input.read()
            if (byte == -1 || byte == '\n'.code) break
            if (byte != '\r'.code) line.append(byte.toChar())
        }
        val parts = line.toString().split(' ')
        return parts.getOrNull(1)
    }

    private fun exchangeCode(code: String, verifier: String): OpenAiOAuthSession {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", CLIENT_ID)
            .add("code_verifier", verifier)
            .build()
        val request = Request.Builder().url(TOKEN_URL).post(body).header("Accept", "application/json").build()
        return executeTokenRequest(request, previous = null)
    }

    private fun refresh(refreshToken: String, previous: OpenAiOAuthSession): OpenAiOAuthSession {
        val body = buildString {
            append('{')
            append("\"grant_type\":\"refresh_token\",")
            append("\"refresh_token\":").append(jsonString(refreshToken)).append(',')
            append("\"client_id\":").append(jsonString(CLIENT_ID))
            append('}')
        }.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(TOKEN_URL).post(body).header("Accept", "application/json").build()
        return executeTokenRequest(request, previous)
    }

    private fun fetchModelCatalog(auth: OpenAiOAuthSession): List<OpenAiOAuthModelInfo> {
        val clientVersion = runCatching {
            val request = Request.Builder().url(CODEX_NPM_LATEST_URL).get().header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = ProviderJson.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                root["version"]?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()?.takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+")) } ?: DEFAULT_CODEX_CLIENT_VERSION

        val url = "$CODEX_BASE_URL/models?client_version=${Uri.encode(clientVersion)}"
        val request = Request.Builder().url(url).get()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${auth.accessToken}")
            .header("chatgpt-account-id", auth.accountId)
            .header("User-Agent", "Arbor/0.19.0 openai-oauth-android")
            .apply { if (auth.isFedRamp) header("X-OpenAI-Fedramp", "true") }
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ProviderHttpException(
                response.code,
                "ChatGPT model discovery failed (${response.code}): ${text.take(1_000)}",
            )
            val root = runCatching { ProviderJson.parseToJsonElement(text).jsonObject }
                .getOrElse { throw ProviderProtocolException("ChatGPT returned an invalid model catalog", it) }
            return root["models"]?.jsonArray.orEmpty().mapNotNull { element ->
                val model = element as? JsonObject ?: return@mapNotNull null
                val id = model["slug"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (id.isBlank()) return@mapNotNull null
                val supported = model["supported_in_api"]?.jsonPrimitive?.booleanOrNull
                val visibility = model["visibility"]?.jsonPrimitive?.contentOrNull
                if (supported == false || (visibility != null && visibility != "list")) return@mapNotNull null
                OpenAiOAuthModelInfo(
                    id = id,
                    displayName = model["display_name"]?.jsonPrimitive?.contentOrNull ?: humanizeModel(id),
                    contextWindow = model["context_window"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                    maxOutputTokens = model["max_output_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                    supportsThinking = model["default_reasoning_level"]?.jsonPrimitive?.contentOrNull != null || id.startsWith("gpt-5"),
                    useResponsesLite = model["use_responses_lite"]?.jsonPrimitive?.booleanOrNull == true,
                    defaultReasoningLevel = model["default_reasoning_level"]?.jsonPrimitive?.contentOrNull,
                )
            }.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
        }
    }

    private fun humanizeModel(id: String): String = id.substringAfterLast('/').replace('-', ' ').replace('_', ' ')
        .split(' ').filter(String::isNotBlank).joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private fun executeTokenRequest(request: Request, previous: OpenAiOAuthSession?): OpenAiOAuthSession {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    val root = ProviderJson.parseToJsonElement(text).jsonObject
                    root["error_description"]?.jsonPrimitive?.contentOrNull
                        ?: root["message"]?.jsonPrimitive?.contentOrNull
                        ?: root["detail"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                throw ProviderHttpException(response.code, "ChatGPT token request failed (${response.code})${detail?.let { ": ${it.take(300)}" }.orEmpty()}")
            }
            val root = runCatching { ProviderJson.parseToJsonElement(text).jsonObject }
                .getOrElse { throw ProviderProtocolException("OpenAI returned an invalid OAuth token response", it) }
            val accessToken = root["access_token"]?.jsonPrimitive?.contentOrNull
                ?: throw ProviderProtocolException("OpenAI did not return an access token")
            val idToken = root["id_token"]?.jsonPrimitive?.contentOrNull ?: previous?.idToken
            val accountId = deriveAccountId(idToken) ?: deriveAccountId(accessToken) ?: previous?.accountId
                ?: throw ProviderProtocolException("The ChatGPT account ID was missing from the OAuth session")
            val expiresInSeconds = root["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val jwtExpiry = jwtClaims(accessToken)?.get("exp")?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.times(1_000L)
            val expiresAt = expiresInSeconds?.let { System.currentTimeMillis() + it * 1_000L }
                ?: jwtExpiry
                ?: (System.currentTimeMillis() + DEFAULT_TOKEN_LIFETIME_MS)
            val isFedRamp = deriveFedRamp(idToken) || deriveFedRamp(accessToken) || previous?.isFedRamp == true
            return OpenAiOAuthSession(
                accessToken = accessToken,
                accountId = accountId,
                refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull ?: previous?.refreshToken,
                idToken = idToken,
                expiresAtEpochMs = expiresAt,
                isFedRamp = isFedRamp,
            )
        }
    }

    private fun signedInState(value: OpenAiOAuthSession): OpenAiOAuthState.SignedIn {
        val claims = jwtClaims(value.idToken) ?: jwtClaims(value.accessToken)
        val email = claims?.get("email")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        return OpenAiOAuthState.SignedIn(value.accountId, email)
    }

    private fun deriveAccountId(token: String?): String? {
        val claims = jwtClaims(token) ?: return null
        val auth = claims["https://api.openai.com/auth"] as? JsonObject
        auth?.get("chatgpt_account_id")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { return it }
        claims["chatgpt_account_id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { return it }
        return (claims["organizations"]?.jsonArray?.firstOrNull() as? JsonObject)
            ?.get("id")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    }

    private fun deriveFedRamp(token: String?): Boolean {
        val claims = jwtClaims(token) ?: return false
        val auth = claims["https://api.openai.com/auth"] as? JsonObject
        return auth?.get("chatgpt_account_is_fedramp")?.jsonPrimitive?.booleanOrNull == true
    }

    private fun jwtClaims(token: String?): JsonObject? {
        val payload = token?.split('.')?.getOrNull(1) ?: return null
        return runCatching {
            val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
            val decoded = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
            ProviderJson.parseToJsonElement(decoded.toString(Charsets.UTF_8)).jsonObject
        }.getOrNull()
    }

    private fun randomBase64Url(byteCount: Int): String = ByteArray(byteCount).also(random::nextBytes).let(::base64Url)

    private fun base64Url(bytes: ByteArray): String = Base64.encodeToString(
        bytes,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    private fun sendBrowserResponse(socket: Socket, success: Boolean, message: String) {
        val safeMessage = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").take(500)
        val html = if (success) {
            """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Arbor sign-in complete</title></head><body style="font-family:sans-serif;max-width:34rem;margin:4rem auto;padding:1rem"><h1>Signed in</h1><p>$safeMessage</p><p><a href="arbor://oauth-complete">Return to Arbor</a></p><script>setTimeout(function(){location.href='arbor://oauth-complete'},250)</script></body></html>"""
        } else {
            """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Arbor sign-in failed</title></head><body style="font-family:sans-serif;max-width:34rem;margin:4rem auto;padding:1rem"><h1>Sign-in failed</h1><p>$safeMessage</p><p>Return to Arbor and try again.</p></body></html>"""
        }
        sendRawResponse(socket, 200, html, "text/html; charset=utf-8")
    }

    private fun sendRawResponse(socket: Socket, status: Int, body: String, contentType: String = "text/plain; charset=utf-8") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().buffered().use { output ->
            output.write("HTTP/1.1 $status ${if (status == 200) "OK" else "Not Found"}\r\n".toByteArray())
            output.write("Content-Type: $contentType\r\n".toByteArray())
            output.write("Cache-Control: no-store\r\n".toByteArray())
            output.write("Connection: close\r\n".toByteArray())
            output.write("Content-Length: ${bytes.size}\r\n\r\n".toByteArray())
            output.write(bytes)
            output.flush()
        }
    }

    private data class BrowserCallback(
        val socket: Socket,
        val code: String?,
        val state: String?,
        val error: String?,
    )

    private fun OpenAiOAuthSecrets.toSession() = OpenAiOAuthSession(
        accessToken, accountId, refreshToken, idToken, expiresAtEpochMs, isFedRamp,
    )

    private fun OpenAiOAuthSession.toSecrets() = OpenAiOAuthSecrets(
        accessToken, accountId, refreshToken, idToken, expiresAtEpochMs, isFedRamp,
    )

    companion object {
        const val PROVIDER_ID = "openai-oauth"
        const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        const val REDIRECT_URI = "http://localhost:1455/auth/callback"
        const val CODEX_BASE_URL = "https://chatgpt.com/backend-api/codex"
        const val TOKEN_URL = "https://auth.openai.com/oauth/token"
        const val AUTHORIZATION_URL = "https://auth.openai.com/oauth/authorize"
        const val SCOPE = "openid profile email offline_access"
        private const val CALLBACK_PORT = 1455
        private const val CALLBACK_POLL_MS = 250L
        private val LOOPBACK_HOSTS = arrayOf("::1", "127.0.0.1")
        private const val LOGIN_TIMEOUT_MS = 5 * 60 * 1_000L
        private const val REFRESH_EARLY_MS = 60_000L
        private const val DEFAULT_TOKEN_LIFETIME_MS = 60 * 60 * 1_000L
        private const val MODEL_CACHE_MS = 5 * 60 * 1_000L
        private const val DEFAULT_CODEX_CLIENT_VERSION = "0.144.1"
        private const val CODEX_NPM_LATEST_URL = "https://registry.npmjs.org/@openai/codex/latest"
        private const val MAX_REQUEST_LINE = 16_384
    }
}
