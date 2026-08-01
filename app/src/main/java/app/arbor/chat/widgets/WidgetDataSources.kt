package app.arbor.chat.widgets

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

internal data class WidgetDataRefreshResult(
    val state: Map<String, String>,
    val updatedAtMillis: Long,
)

internal data class WidgetDataPreflightIssue(
    val sourceId: String,
    val message: String,
)

internal data class WidgetDataPreflightResult(
    val state: Map<String, String>,
    val issues: List<WidgetDataPreflightIssue>,
)

private class WidgetHttpFailure(
    val statusCode: Int? = null,
    val isTransient: Boolean = false,
    message: String,
) : IllegalStateException(message)

internal object WidgetDataRuntime {
    private const val MAX_BODY_BYTES = 1_048_576L
    private const val MAX_REDIRECTS = 5
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = InetAddress.getAllByName(hostname).toList().also { addresses ->
                require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "Private and local widget addresses are blocked" }
            }
        })
        .build()

    suspend fun refresh(
        context: Context,
        definition: ArborProgramDefinition,
        grants: WidgetCapabilityGrants,
        currentState: Map<String, String>,
        requestedSources: Set<String> = setOf("*"),
    ): WidgetDataRefreshResult = withContext(Dispatchers.IO) {
        val next = currentState.toMutableMap()
        val selected = definition.dataSources
            .filter { "*" in requestedSources || it.id in requestedSources }
            .sortedBy { sourceOrder(it.type) }
        selected.forEach { source ->
            val values = when (source.type) {
                "location" -> locationValues(context, source, grants)
                "http_json" -> httpValues(source, grants, next, strictBindings = false)
                "folder_text" -> folderValues(context, source, grants)
                else -> emptyMap()
            }
            next.putAll(values)
        }
        WidgetDataRefreshResult(next, System.currentTimeMillis())
    }

    /**
     * Runs public HTTP sources in the internal widget compiler. No cookies, credentials,
     * persisted grants, or private/local addresses are allowed. Deterministic endpoint,
     * redirect, response, and JSON-binding failures are returned to the AI repair loop.
     */
    suspend fun preflightHttpSources(definition: ArborProgramDefinition): WidgetDataPreflightResult = withContext(Dispatchers.IO) {
        val next = definition.state.toMutableMap()
        val issues = mutableListOf<WidgetDataPreflightIssue>()
        val origins = definition.capabilities
            .filter { it.type == "network" }
            .flatMapTo(linkedSetOf()) { it.origins }
        val grants = WidgetCapabilityGrants(networkOrigins = origins)

        // Compile in the same dependency order as the installed runtime. Sources which
        // require an Android grant receive deterministic representative values rather
        // than blank defaults, so location-driven URLs are tested as real URLs instead
        // of producing misleading HTTP 400 responses during compilation.
        definition.dataSources.sortedBy { sourceOrder(it.type) }.forEach { source ->
            when (source.type) {
                "location" -> next.putAll(compilerLocationValues(source))
                "folder_text" -> next.putAll(source.bindings.associate { binding ->
                    binding.state to binding.fallback.ifBlank { compilerFolderValue(binding.path) }
                })
                "http_json" -> {
                    val fallbacksReady = source.bindings.all { it.fallback.isNotBlank() }
                    try {
                        next.putAll(httpValues(source, grants, next, strictBindings = true))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        val transient = when (error) {
                            is WidgetHttpFailure -> error.isTransient
                            is UnknownHostException, is ConnectException, is SocketTimeoutException -> true
                            else -> false
                        }
                        if (transient && fallbacksReady) {
                            next.putAll(source.bindings.associate { it.state to it.fallback })
                        } else {
                            issues += WidgetDataPreflightIssue(
                                sourceId = source.id,
                                message = (error.message ?: error::class.java.simpleName).take(500),
                            )
                        }
                    }
                }
            }
        }
        WidgetDataPreflightResult(next, issues)
    }


    private fun compilerLocationValues(source: ArborWidgetDataSource): Map<String, String> = source.bindings.associate { binding ->
        binding.state to when (binding.path) {
            "latitude" -> "41.0082"
            "longitude" -> "28.9784"
            "accuracy" -> "250"
            "updatedAt" -> "1700000000000"
            else -> binding.fallback
        }
    }

    private fun compilerFolderValue(path: String): String = when (path.ifBlank { "text" }) {
        "text" -> "Preview file content"
        "size" -> "20"
        "lineCount" -> "1"
        else -> "Preview"
    }

    fun writeFolder(
        context: Context,
        source: ArborWidgetDataSource,
        grants: WidgetCapabilityGrants,
        content: String,
    ) {
        require(source.type == "folder_text") { "Folder write target is invalid" }
        require(grants.folderWrite) { "Read/write folder access was not granted to this widget" }
        val tree = Uri.parse(requireNotNull(grants.folderUri) { "Folder access was not granted to this widget" })
        val document = resolveRelativeDocument(context, tree, source.relativePath)
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BODY_BYTES) { "Widget folder write is larger than 1 MB" }
        requireNotNull(context.contentResolver.openOutputStream(document, "wt")) { "Could not write ${source.relativePath}" }.use { output ->
            output.write(bytes)
            output.flush()
        }
    }

    private fun locationValues(
        context: Context,
        source: ArborWidgetDataSource,
        grants: WidgetCapabilityGrants,
    ): Map<String, String> {
        require(grants.location != WidgetLocationGrant.NONE) { "Location was not granted to this widget" }
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        require(coarse || fine) { "Android location permission is no longer available" }
        if (grants.location == WidgetLocationGrant.PRECISE) require(fine) { "Precise location permission is no longer available" }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val candidates = manager.getProviders(true).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        val location = candidates.maxByOrNull { it.time } ?: error("No recent device location is available yet")
        return source.bindings.associate { binding ->
            binding.state to when (binding.path) {
                "latitude" -> location.latitude.toString()
                "longitude" -> location.longitude.toString()
                "accuracy" -> location.accuracy.toString()
                "updatedAt" -> location.time.toString()
                else -> binding.fallback
            }
        }
    }

    private fun httpValues(
        source: ArborWidgetDataSource,
        grants: WidgetCapabilityGrants,
        state: Map<String, String>,
        strictBindings: Boolean,
    ): Map<String, String> {
        val initialUrl = ArborProgramRuntime.render(source.url, state)
        return executeGet(initialUrl, grants).use { response ->
            if (!response.isSuccessful) {
                val excerpt = response.body?.source()?.let { bodySource ->
                    runCatching { bodySource.readUtf8(4_096) }.getOrDefault("")
                }.orEmpty()
                val detail = sanitizeHttpExcerpt(excerpt)
                throw WidgetHttpFailure(
                    statusCode = response.code,
                    isTransient = response.code in setOf(408, 425, 429) || response.code >= 500,
                    message = buildString {
                        append(source.id).append(" returned HTTP ").append(response.code)
                        if (response.message.isNotBlank()) append(' ').append(response.message)
                        if (detail.isNotBlank()) append(" — ").append(detail)
                    },
                )
            }
            val body = requireNotNull(response.body) { "${source.id} returned no content" }
            val declared = body.contentLength()
            require(declared < 0 || declared <= MAX_BODY_BYTES) { "${source.id} is larger than 1 MB" }
            val content = body.source().readUtf8(MAX_BODY_BYTES + 1)
            require(content.toByteArray().size <= MAX_BODY_BYTES) { "${source.id} is larger than 1 MB" }
            val root = runCatching { json.parseToJsonElement(content) }.getOrElse { error ->
                throw WidgetHttpFailure(message = "${source.id} did not return valid JSON: ${error.message ?: "parse failed"}")
            }
            source.bindings.associate { binding ->
                val resolved = valueAt(root, binding.path)
                if (strictBindings && resolved == null) {
                    throw WidgetHttpFailure(message = "${source.id} JSON path '${binding.path}' was not found in the live response")
                }
                binding.state to (resolved ?: binding.fallback)
            }
        }
    }

    private fun executeGet(initialUrl: String, grants: WidgetCapabilityGrants): Response {
        var currentUrl = initialUrl
        val visited = linkedSetOf<String>()
        repeat(MAX_REDIRECTS + 1) { hop ->
            validateGrantedPublicHttpsUrl(currentUrl, grants.networkOrigins)
            if (!visited.add(currentUrl)) throw WidgetHttpFailure(message = "Widget HTTP redirect loop detected")
            val request = Request.Builder()
                .url(currentUrl)
                .header("Accept", "application/json, text/json;q=0.9, */*;q=0.1")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "Arbor-HomeWidget/3 (Android)")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isRedirect) return response
            if (hop >= MAX_REDIRECTS) {
                response.close()
                throw WidgetHttpFailure(statusCode = response.code, message = "Widget HTTP redirect limit exceeded")
            }
            val location = response.header("Location")
            val next = location?.let { response.request.url.resolve(it) }
            val status = response.code
            response.close()
            if (next == null) throw WidgetHttpFailure(statusCode = status, message = "Widget HTTP $status redirect did not include a valid Location")
            currentUrl = next.toString()
            validateGrantedPublicHttpsUrl(currentUrl, grants.networkOrigins)
        }
        throw WidgetHttpFailure(message = "Widget HTTP redirect limit exceeded")
    }

    private fun folderValues(
        context: Context,
        source: ArborWidgetDataSource,
        grants: WidgetCapabilityGrants,
    ): Map<String, String> {
        val tree = Uri.parse(requireNotNull(grants.folderUri) { "Folder access was not granted to this widget" })
        val document = resolveRelativeDocument(context, tree, source.relativePath)
        val bytes = requireNotNull(context.contentResolver.openInputStream(document)) { "Could not open ${source.relativePath}" }.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            while (output.size() <= MAX_BODY_BYTES) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size <= MAX_BODY_BYTES) { "${source.relativePath} is larger than 1 MB" }
        val text = bytes.toString(Charsets.UTF_8)
        return source.bindings.associate { binding ->
            binding.state to when (binding.path.ifBlank { "text" }) {
                "text" -> text
                "size" -> bytes.size.toString()
                "lineCount" -> text.lineSequence().count().toString()
                else -> binding.fallback
            }
        }
    }

    private fun resolveRelativeDocument(context: Context, tree: Uri, relativePath: String): Uri {
        var parentId = DocumentsContract.getTreeDocumentId(tree)
        relativePath.split('/').filter(String::isNotBlank).forEach { segment ->
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            var nextId: String? = null
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == segment) {
                        nextId = cursor.getString(idIndex)
                        break
                    }
                }
            }
            parentId = requireNotNull(nextId) { "${sourceSafe(relativePath)} was not found in the granted folder" }
        }
        return DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
    }

    private fun valueAt(root: JsonElement, path: String): String? {
        var current = root
        path.split('.').forEach { segment ->
            val key = segment.substringBefore('[')
            current = runCatching { current.jsonObject[key] }.getOrNull() ?: return null
            INDEX.findAll(segment).forEach { match ->
                current = runCatching { current.jsonArray[match.groupValues[1].toInt()] }.getOrNull() ?: return null
            }
        }
        return runCatching { current.jsonPrimitive.contentOrNull }.getOrNull() ?: current.toString()
    }

    private fun validateGrantedPublicHttpsUrl(raw: String, grantedOrigins: Set<String>) {
        val uri = URI(raw)
        require(uri.scheme.equals("https", ignoreCase = true)) { "Only HTTPS widget data sources are allowed" }
        require(uri.userInfo == null) { "Credentials cannot be embedded in a widget URL" }
        val host = requireNotNull(uri.host) { "Widget data-source host is missing" }
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        val origin = "https://${host.lowercase()}$port"
        require(origin in grantedOrigins) { "$origin was not declared for this widget; add the final redirect origin or use its final HTTPS URL" }
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "Private and local widget addresses are blocked" }
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val bytes = address.address
        if (bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC) return false
        return true
    }

    private fun sanitizeHttpExcerpt(value: String): String = value
        .replace(Regex("<[^>]{0,200}>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(240)

    private fun sourceOrder(type: String): Int = when (type) {
        "location" -> 0
        "folder_text" -> 1
        else -> 2
    }

    private fun sourceSafe(value: String): String = value.take(120)
    private val INDEX = Regex("\\[(\\d+)]")
}
