package app.xylune.chat.provider

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import app.xylune.chat.security.OpenAiOAuthSecrets
import app.xylune.chat.security.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedInputStream
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    val supportsImageGeneration: Boolean? = null,
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


data class OpenAiOAuthUsageWindow(
    val usedPercent: Double,
    val windowDurationSeconds: Long?,
    val resetsAtEpochSeconds: Long?,
)

data class OpenAiOAuthAdditionalLimit(
    val id: String?,
    val name: String,
    val primary: OpenAiOAuthUsageWindow?,
    val secondary: OpenAiOAuthUsageWindow?,
)

data class OpenAiOAuthUsageSnapshot(
    val planType: String?,
    val allowed: Boolean?,
    val limitReached: Boolean?,
    val rateLimitReachedType: String?,
    val primary: OpenAiOAuthUsageWindow?,
    val secondary: OpenAiOAuthUsageWindow?,
    val additionalLimits: List<OpenAiOAuthAdditionalLimit>,
    val creditsBalance: String?,
    val creditsUnlimited: Boolean?,
    val hasCredits: Boolean?,
    val fetchedAtEpochMs: Long,
)

sealed interface OpenAiOAuthUsageState {
    data object SignedOut : OpenAiOAuthUsageState
    data class Loading(val previous: OpenAiOAuthUsageSnapshot? = null) : OpenAiOAuthUsageState
    data class Loaded(val snapshot: OpenAiOAuthUsageSnapshot) : OpenAiOAuthUsageState
    data class Error(val message: String, val previous: OpenAiOAuthUsageSnapshot? = null) : OpenAiOAuthUsageState
}

internal object OpenAiOAuthUsageParser {
    fun parse(root: JsonObject, fetchedAtEpochMs: Long = System.currentTimeMillis()): OpenAiOAuthUsageSnapshot {
        val rateLimit = root.objectValue("rate_limit", "rateLimit", "rateLimits") ?: root
        val nowEpochSeconds = fetchedAtEpochMs / 1_000L
        val primary = parseWindow(rateLimit.objectValue("primary_window", "primaryWindow", "primary"), nowEpochSeconds)
        val secondary = parseWindow(rateLimit.objectValue("secondary_window", "secondaryWindow", "secondary"), nowEpochSeconds)
        val credits = root.objectValue("credits") ?: rateLimit.objectValue("credits")
        val additional = buildList {
            (root["additional_rate_limits"] as? JsonArray)?.forEach additionalArray@{ element ->
                val item = element as? JsonObject ?: return@additionalArray
                val nested = item.objectValue("rate_limit", "rateLimit") ?: item
                val id = item.stringValue("limit_id", "limitId", "id")
                val name = item.stringValue("limit_name", "limitName", "name")
                    ?: id?.let(::humanizeIdentifier)
                    ?: "Additional limit"
                add(
                    OpenAiOAuthAdditionalLimit(
                        id = id,
                        name = name,
                        primary = parseWindow(nested.objectValue("primary_window", "primaryWindow", "primary"), nowEpochSeconds),
                        secondary = parseWindow(nested.objectValue("secondary_window", "secondaryWindow", "secondary"), nowEpochSeconds),
                    ),
                )
            }
            root.objectValue("rateLimitsByLimitId")?.forEach additionalMap@{ (id, element) ->
                if (id == "codex") return@additionalMap
                val item = element as? JsonObject ?: return@additionalMap
                add(
                    OpenAiOAuthAdditionalLimit(
                        id = id,
                        name = item.stringValue("limitName", "limit_name") ?: humanizeIdentifier(id),
                        primary = parseWindow(item.objectValue("primary", "primary_window"), nowEpochSeconds),
                        secondary = parseWindow(item.objectValue("secondary", "secondary_window"), nowEpochSeconds),
                    ),
                )
            }
            root.objectValue("code_review_rate_limit", "codeReviewRateLimit")?.let { item ->
                add(
                    OpenAiOAuthAdditionalLimit(
                        id = "code-review",
                        name = "Code review",
                        primary = parseWindow(item.objectValue("primary_window", "primaryWindow", "primary") ?: item, nowEpochSeconds),
                        secondary = parseWindow(item.objectValue("secondary_window", "secondaryWindow", "secondary"), nowEpochSeconds),
                    ),
                )
            }
        }.filter { it.primary != null || it.secondary != null }.distinctBy { it.id ?: it.name }

        val snapshot = OpenAiOAuthUsageSnapshot(
            planType = root.stringValue("plan_type", "planType") ?: rateLimit.stringValue("plan_type", "planType"),
            allowed = rateLimit.booleanValue("allowed"),
            limitReached = rateLimit.booleanValue("limit_reached", "limitReached"),
            rateLimitReachedType = rateLimit.stringValue("rate_limit_reached_type", "rateLimitReachedType"),
            primary = primary,
            secondary = secondary,
            additionalLimits = additional,
            creditsBalance = credits?.stringValue("balance"),
            creditsUnlimited = credits?.booleanValue("unlimited"),
            hasCredits = credits?.booleanValue("has_credits", "hasCredits")
                ?: credits?.stringValue("balance")?.toDoubleOrNull()?.let { it > 0.0 },
            fetchedAtEpochMs = fetchedAtEpochMs,
        )
        if (
            snapshot.primary == null &&
            snapshot.secondary == null &&
            snapshot.additionalLimits.isEmpty() &&
            snapshot.creditsBalance == null &&
            snapshot.planType == null
        ) {
            throw ProviderProtocolException("ChatGPT returned usage data in an unsupported format")
        }
        return snapshot
    }

    private fun parseWindow(value: JsonObject?, nowEpochSeconds: Long): OpenAiOAuthUsageWindow? {
        value ?: return null
        val used = value.numberValue("used_percent", "usedPercent") ?: return null
        val seconds = value.longValue("limit_window_seconds", "window_duration_seconds", "windowDurationSeconds")
            ?: value.longValue("window_duration_mins", "window_minutes", "windowDurationMins")?.times(60L)
        val absoluteRaw = value.longValue("reset_at", "resets_at", "resetsAt", "resetAt")
        val resetAfter = value.longValue(
            "reset_after_seconds", "resetAfterSeconds", "resets_in_seconds", "resetsInSeconds", "reset_after", "resetAfter",
        )
        val resetAt = resetAfter?.takeIf { it >= 0L }?.let(nowEpochSeconds::plus) ?: when {
            absoluteRaw == null -> null
            // Defensive normalization: private endpoints have returned both epoch seconds and milliseconds.
            absoluteRaw >= 10_000_000_000L -> absoluteRaw / 1_000L
            // A small reset value is a duration, not a Unix timestamp.
            absoluteRaw in 0L..9_999_999L -> nowEpochSeconds + absoluteRaw
            else -> absoluteRaw
        }
        return OpenAiOAuthUsageWindow(
            usedPercent = used.coerceIn(0.0, 100.0),
            windowDurationSeconds = seconds,
            resetsAtEpochSeconds = resetAt,
        )
    }

    private fun JsonObject.objectValue(vararg keys: String): JsonObject? =
        keys.firstNotNullOfOrNull { key -> this[key] as? JsonObject }

    private fun JsonObject.stringValue(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }

    private fun JsonObject.booleanValue(vararg keys: String): Boolean? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.booleanOrNull }

    private fun JsonObject.numberValue(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() }

    private fun JsonObject.longValue(vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() }

    private fun humanizeIdentifier(id: String): String = id.substringAfterLast('/').replace('-', ' ').replace('_', ' ')
        .split(' ').filter(String::isNotBlank).joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}

/** Native Android PKCE + loopback OAuth flow compatible with Codex/OpenAI OAuth. */
class OpenAiOAuthManager(
    context: Context,
    private val secureStore: SecureStore,
    providedClient: OkHttpClient? = null,
) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val client: OkHttpClient = providedClient ?: OkHttpClient.Builder()
        .dns(AndroidNetworkDns(connectivityManager))
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val loginMutex = Mutex()
    private val refreshMutex = Mutex()
    private val modelMutex = Mutex()
    private val usageMutex = Mutex()
    private data class ModelCache(val models: List<OpenAiOAuthModelInfo>, val validUntil: Long)
    private data class UsageCache(val snapshot: OpenAiOAuthUsageSnapshot, val validUntil: Long)
    private val sessions = ConcurrentHashMap<String, OpenAiOAuthSession>().apply {
        putAll(secureStore.openAiOAuthAccounts().mapValues { it.value.toSession() })
    }
    private val modelCaches = ConcurrentHashMap<String, ModelCache>()
    private val usageCaches = ConcurrentHashMap<String, UsageCache>()
    private val random = SecureRandom()
    @Volatile private var activeLoginServers: List<ServerSocket> = emptyList()
    @Volatile private var activeReturnLatch: CountDownLatch? = null
    @Volatile private var activeLoginProviderId: String? = null
    @Volatile private var loginCancelled = false
    private val callbackReceived = AtomicBoolean(false)

    private val _accountStates = MutableStateFlow<Map<String, OpenAiOAuthState>>(
        sessions.mapValues { signedInState(it.value) },
    )
    val accountStates: StateFlow<Map<String, OpenAiOAuthState>> = _accountStates.asStateFlow()

    private val _usageStates = MutableStateFlow<Map<String, OpenAiOAuthUsageState>>(
        sessions.keys.associateWith { OpenAiOAuthUsageState.Loading() },
    )
    val usageStates: StateFlow<Map<String, OpenAiOAuthUsageState>> = _usageStates.asStateFlow()

    // Compatibility state for the original built-in provider id.
    private val _usageState = MutableStateFlow<OpenAiOAuthUsageState>(
        if (sessions[PROVIDER_ID] == null) OpenAiOAuthUsageState.SignedOut else OpenAiOAuthUsageState.Loading(),
    )
    val usageState: StateFlow<OpenAiOAuthUsageState> = _usageState.asStateFlow()
    private val _state = MutableStateFlow<OpenAiOAuthState>(
        sessions[PROVIDER_ID]?.let(::signedInState) ?: OpenAiOAuthState.SignedOut,
    )
    val state: StateFlow<OpenAiOAuthState> = _state.asStateFlow()

    fun signedInAccountId(providerId: String = PROVIDER_ID): String? =
        sessions[providerId]?.accountId ?: secureStore.openAiOAuthSecrets(providerId)?.accountId

    suspend fun modelInfo(providerId: String, modelId: String): OpenAiOAuthModelInfo? =
        modelCatalog(providerId).firstOrNull { it.id == modelId }

    suspend fun modelInfo(modelId: String): OpenAiOAuthModelInfo? = modelInfo(PROVIDER_ID, modelId)

    suspend fun modelCatalog(
        providerId: String = PROVIDER_ID,
        forceRefresh: Boolean = false,
    ): List<OpenAiOAuthModelInfo> = modelMutex.withLock {
        val now = System.currentTimeMillis()
        val cached = modelCaches[providerId]
        if (!forceRefresh && cached != null && cached.models.isNotEmpty() && now < cached.validUntil) return cached.models
        var auth = validSession(providerId)
        val models = try {
            withContext(Dispatchers.IO) { fetchModelCatalog(auth) }
        } catch (error: ProviderHttpException) {
            if (error.status != 401) throw error
            auth = validSession(providerId, forceRefresh = true)
            withContext(Dispatchers.IO) { fetchModelCatalog(auth) }
        }
        require(models.isNotEmpty()) { "The ChatGPT account returned no usable models" }
        modelCaches[providerId] = ModelCache(models, now + MODEL_CACHE_MS)
        models
    }

    suspend fun usage(
        providerId: String = PROVIDER_ID,
        forceRefresh: Boolean = false,
    ): OpenAiOAuthUsageSnapshot = usageMutex.withLock {
        val now = System.currentTimeMillis()
        val cached = usageCaches[providerId]
        val previous = cached?.snapshot
        if (!forceRefresh && cached != null && now < cached.validUntil) {
            updateUsageState(providerId, OpenAiOAuthUsageState.Loaded(cached.snapshot))
            return cached.snapshot
        }
        if (sessions[providerId] == null && secureStore.openAiOAuthSecrets(providerId) == null) {
            updateUsageState(providerId, OpenAiOAuthUsageState.SignedOut)
            throw IllegalStateException("Sign in with ChatGPT for this provider in Settings")
        }

        updateUsageState(providerId, OpenAiOAuthUsageState.Loading(previous))
        try {
            var auth = validSession(providerId)
            val snapshot = try {
                withContext(Dispatchers.IO) { fetchUsage(auth) }
            } catch (error: ProviderHttpException) {
                if (error.status != 401) throw error
                auth = validSession(providerId, forceRefresh = true)
                withContext(Dispatchers.IO) { fetchUsage(auth) }
            }
            usageCaches[providerId] = UsageCache(snapshot, System.currentTimeMillis() + USAGE_CACHE_MS)
            updateUsageState(providerId, OpenAiOAuthUsageState.Loaded(snapshot))
            snapshot
        } catch (error: Throwable) {
            val message = error.message?.take(500) ?: "ChatGPT usage could not be loaded"
            updateUsageState(providerId, OpenAiOAuthUsageState.Error(message, previous))
            throw error
        }
    }

    suspend fun signIn(providerId: String = PROVIDER_ID): OpenAiOAuthSession? = loginMutex.withLock {
        activeLoginProviderId = providerId
        loginCancelled = false
        updateAccountState(providerId, OpenAiOAuthState.SigningIn)
        try {
            val result = withContext(Dispatchers.IO) { performBrowserLogin() }
            if (loginCancelled) {
                updateAccountState(providerId, OpenAiOAuthState.SignedOut)
                return@withLock null
            }
            persist(providerId, result)
            updateAccountState(providerId, signedInState(result))
            usageCaches.remove(providerId)
            updateUsageState(providerId, OpenAiOAuthUsageState.Loading())
            result
        } catch (error: Throwable) {
            if (loginCancelled) {
                updateAccountState(providerId, OpenAiOAuthState.SignedOut)
                null
            } else {
                val message = error.message?.take(500) ?: "ChatGPT sign-in failed"
                updateAccountState(providerId, OpenAiOAuthState.Error(message))
                throw error
            }
        } finally {
            activeLoginProviderId = null
        }
    }

    fun cancelSignIn(providerId: String? = activeLoginProviderId) {
        loginCancelled = true
        activeReturnLatch?.countDown()
        activeLoginServers.forEach { server -> runCatching { server.close() } }
        providerId?.let { id ->
            if (_accountStates.value[id] is OpenAiOAuthState.SigningIn) {
                updateAccountState(id, if (sessions[id] == null) OpenAiOAuthState.SignedOut else signedInState(sessions.getValue(id)))
            }
        }
    }

    /** Called when Xylune becomes foreground after the browser OAuth callback. */
    fun onBrowserReturned() {
        if (callbackReceived.get()) activeReturnLatch?.countDown()
    }

    fun signOut(providerId: String = PROVIDER_ID) {
        if (activeLoginProviderId == providerId) cancelSignIn(providerId)
        sessions.remove(providerId)
        secureStore.setOpenAiOAuthSecrets(providerId, null)
        modelCaches.remove(providerId)
        usageCaches.remove(providerId)
        updateUsageState(providerId, OpenAiOAuthUsageState.SignedOut)
        updateAccountState(providerId, OpenAiOAuthState.SignedOut)
    }

    suspend fun validSession(
        providerId: String = PROVIDER_ID,
        forceRefresh: Boolean = false,
    ): OpenAiOAuthSession = refreshMutex.withLock {
        val current = sessions[providerId]
            ?: secureStore.openAiOAuthSecrets(providerId)?.toSession()?.also { sessions[providerId] = it }
            ?: throw IllegalStateException("Sign in with ChatGPT for this provider in Settings")
        if (!forceRefresh && current.expiresAtEpochMs - REFRESH_EARLY_MS > System.currentTimeMillis()) return current
        val refreshToken = current.refreshToken
            ?: throw IllegalStateException("This ChatGPT session expired. Sign in again in Settings")
        return try {
            val refreshed = withContext(Dispatchers.IO) { refresh(refreshToken, current) }
            persist(providerId, refreshed)
            updateAccountState(providerId, signedInState(refreshed))
            refreshed
        } catch (error: Throwable) {
            updateAccountState(providerId, OpenAiOAuthState.Error(error.message?.take(500) ?: "ChatGPT session refresh failed"))
            throw error
        }
    }

    private fun persist(providerId: String, value: OpenAiOAuthSession) {
        sessions[providerId] = value
        secureStore.setOpenAiOAuthSecrets(providerId, value.toSecrets())
    }

    private fun updateAccountState(providerId: String, value: OpenAiOAuthState) {
        _accountStates.update { it + (providerId to value) }
        if (providerId == PROVIDER_ID) _state.value = value
    }

    private fun updateUsageState(providerId: String, value: OpenAiOAuthUsageState) {
        _usageStates.update { it + (providerId to value) }
        if (providerId == PROVIDER_ID) _usageState.value = value
    }

    private fun performBrowserLogin(): OpenAiOAuthSession {
        val state = randomBase64Url(24)
        val verifier = randomBase64Url(48)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val returnLatch = CountDownLatch(1)
        activeReturnLatch = returnLatch
        callbackReceived.set(false)
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
                // A fresh authentication prompt is required for multi-account providers; otherwise
                // the browser may silently reuse the previous ChatGPT session.
                .appendQueryParameter("prompt", "login")
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
                sendBrowserResponse(callback.socket, false, "The sign-in state did not match. Return to Xylune and try again.")
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

            // Token exchange must run after Xylune is foreground again. On some Android builds,
            // background-app DNS briefly returns EAI_NODATA while the browser owns the foreground.
            callbackReceived.set(true)
            sendBrowserResponse(callback.socket, true, "Authorization received. Finishing sign-in in Xylune…")
            callback.socket.close()

            val returnedToApp = returnLatch.await(APP_RETURN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            check(!loginCancelled) { "ChatGPT sign-in was cancelled" }
            check(returnedToApp) { "Return to Xylune to finish ChatGPT sign-in" }
            awaitUsableNetwork(NETWORK_READY_TIMEOUT_MS)
            return exchangeCode(code, verifier)
        } finally {
            callbackReceived.set(false)
            activeReturnLatch = null
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
            executeWithNetworkRetry(request).use { response ->
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
            .header("User-Agent", "Xylune/0.19.4 openai-oauth-android")
            .apply { if (auth.isFedRamp) header("X-OpenAI-Fedramp", "true") }
            .build()
        executeWithNetworkRetry(request).use { response ->
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
                    supportsImageGeneration = model["supports_image_generation"]?.jsonPrimitive?.booleanOrNull
                        ?: model["image_generation"]?.jsonPrimitive?.booleanOrNull
                        ?: (model["supported_tools"] as? JsonArray)?.let { tools ->
                            tools.any { it.jsonPrimitive.contentOrNull in setOf("image_generation", "image-gen") }
                                .takeIf { supported -> supported }
                        }
                        ?: true.takeIf { id.startsWith("gpt-image-") },
                    useResponsesLite = model["use_responses_lite"]?.jsonPrimitive?.booleanOrNull == true,
                    defaultReasoningLevel = model["default_reasoning_level"]?.jsonPrimitive?.contentOrNull,
                )
            }.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
        }
    }

    private fun fetchUsage(auth: OpenAiOAuthSession): OpenAiOAuthUsageSnapshot {
        var lastNotFound: ProviderHttpException? = null
        for (url in USAGE_URLS) {
            val request = Request.Builder().url(url).get()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer ${auth.accessToken}")
                .header("chatgpt-account-id", auth.accountId)
                .header("OpenAI-Beta", "codex-1")
                .header("originator", "Xylune")
                .header("User-Agent", "Xylune/0.19.4 openai-oauth-android")
                .apply { if (auth.isFedRamp) header("X-OpenAI-Fedramp", "true") }
                .build()
            executeWithNetworkRetry(request).use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code == 404 || response.code == 405) {
                    lastNotFound = ProviderHttpException(response.code, "ChatGPT usage endpoint was unavailable")
                    return@use
                }
                if (!response.isSuccessful) throw ProviderHttpException(
                    response.code,
                    "ChatGPT usage lookup failed (${response.code}): ${text.take(1_000)}",
                )
                val root = runCatching { ProviderJson.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw ProviderProtocolException("ChatGPT returned invalid usage data", it) }
                return OpenAiOAuthUsageParser.parse(root)
            }
        }
        throw lastNotFound ?: ProviderProtocolException("ChatGPT usage data is unavailable")
    }

    private fun humanizeModel(id: String): String = id.substringAfterLast('/').replace('-', ' ').replace('_', ' ')
        .split(' ').filter(String::isNotBlank).joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private fun executeTokenRequest(request: Request, previous: OpenAiOAuthSession?): OpenAiOAuthSession {
        executeWithNetworkRetry(request).use { response ->
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

    private fun executeWithNetworkRetry(request: Request): Response {
        var lastFailure: IOException? = null
        repeat(NETWORK_ATTEMPTS) { attempt ->
            try {
                return client.newCall(request).execute()
            } catch (error: IOException) {
                if (!error.isDnsFailure() || attempt == NETWORK_ATTEMPTS - 1) {
                    throw friendlyNetworkError(request, error)
                }
                lastFailure = error
                awaitUsableNetwork(NETWORK_READY_TIMEOUT_MS)
                Thread.sleep(NETWORK_RETRY_DELAYS_MS[attempt])
            }
        }
        throw friendlyNetworkError(request, lastFailure ?: UnknownHostException(request.url.host))
    }

    private fun IOException.isDnsFailure(): Boolean = generateSequence<Throwable>(this) { it.cause }
        .any { it is UnknownHostException }

    private fun friendlyNetworkError(request: Request, cause: IOException): IOException = IOException(
        "Xylune could not reach ${request.url.host}. Check the connection, VPN, Private DNS, or per-app firewall, then try again.",
        cause,
    )

    private fun awaitUsableNetwork(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        do {
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
            if (network != null && capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                return true
            }
            Thread.sleep(NETWORK_POLL_MS)
        } while (System.currentTimeMillis() < deadline && !loginCancelled)
        return false
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
            """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Return to Xylune</title></head><body style="font-family:sans-serif;max-width:34rem;margin:4rem auto;padding:1rem"><h1>Authorization received</h1><p>$safeMessage</p><p><a href="xylune://oauth-complete">Return to Xylune</a></p><script>setTimeout(function(){location.href='xylune://oauth-complete'},150)</script></body></html>"""
        } else {
            """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Xylune sign-in failed</title></head><body style="font-family:sans-serif;max-width:34rem;margin:4rem auto;padding:1rem"><h1>Sign-in failed</h1><p>$safeMessage</p><p>Return to Xylune and try again.</p></body></html>"""
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

    private class AndroidNetworkDns(
        private val connectivityManager: ConnectivityManager,
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (hostname.isBlank()) throw UnknownHostException("hostname is empty")
            var activeNetworkFailure: UnknownHostException? = null
            connectivityManager.activeNetwork?.let { network ->
                try {
                    val addresses = network.getAllByName(hostname).toList()
                    if (addresses.isNotEmpty()) return addresses
                } catch (error: UnknownHostException) {
                    activeNetworkFailure = error
                }
            }
            try {
                return Dns.SYSTEM.lookup(hostname)
            } catch (error: UnknownHostException) {
                throw activeNetworkFailure ?: error
            }
        }
    }

    companion object {
        const val PROVIDER_ID = "openai-oauth"
        const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        const val REDIRECT_URI = "http://localhost:1455/auth/callback"
        const val CODEX_BASE_URL = "https://chatgpt.com/backend-api/codex"
        private val USAGE_URLS = listOf(
            "https://chatgpt.com/backend-api/wham/usage",
            "https://chatgpt.com/backend-api/codex/usage",
        )
        const val TOKEN_URL = "https://auth.openai.com/oauth/token"
        const val AUTHORIZATION_URL = "https://auth.openai.com/oauth/authorize"
        const val SCOPE = "openid profile email offline_access"
        private const val CALLBACK_PORT = 1455
        private const val CALLBACK_POLL_MS = 250L
        private val LOOPBACK_HOSTS = arrayOf("::1", "127.0.0.1")
        private const val LOGIN_TIMEOUT_MS = 5 * 60 * 1_000L
        private const val APP_RETURN_TIMEOUT_MS = 30_000L
        private const val NETWORK_READY_TIMEOUT_MS = 8_000L
        private const val NETWORK_POLL_MS = 250L
        private const val NETWORK_ATTEMPTS = 3
        private val NETWORK_RETRY_DELAYS_MS = longArrayOf(400L, 1_200L)
        private const val REFRESH_EARLY_MS = 60_000L
        private const val DEFAULT_TOKEN_LIFETIME_MS = 60 * 60 * 1_000L
        private const val MODEL_CACHE_MS = 5 * 60 * 1_000L
        private const val USAGE_CACHE_MS = 60 * 1_000L
        private const val DEFAULT_CODEX_CLIENT_VERSION = "0.144.1"
        private const val CODEX_NPM_LATEST_URL = "https://registry.npmjs.org/@openai/codex/latest"
        private const val MAX_REQUEST_LINE = 16_384
    }
}
