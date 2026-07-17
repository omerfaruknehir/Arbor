package app.arbor.chat.widgets

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
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

internal data class WidgetLiveResult(val values: Map<String, String>, val updatedAtMillis: Long)

internal object WidgetLiveDataClient {
    private const val MAX_BODY_BYTES = 1_048_576L
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = InetAddress.getAllByName(hostname).toList().also { addresses ->
                require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "Private and local data-source addresses are blocked" }
            }
        })
        .build()

    suspend fun fetch(source: ArborWidgetDataSource): WidgetLiveResult = withContext(Dispatchers.IO) {
        validatePublicHttpsUrl(source.url)
        val request = Request.Builder()
            .url(source.url)
            .header("Accept", "application/json")
            .header("User-Agent", "Arbor-Widget/1")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Data source returned HTTP ${response.code}" }
            require(!response.isRedirect) { "Data-source redirects are disabled" }
            val body = requireNotNull(response.body) { "Data source returned no content" }
            val declaredSize = body.contentLength()
            require(declaredSize < 0 || declaredSize <= MAX_BODY_BYTES) { "Data source is larger than 1 MB" }
            val content = body.source().readUtf8(MAX_BODY_BYTES + 1)
            require(content.toByteArray().size <= MAX_BODY_BYTES) { "Data source is larger than 1 MB" }
            val root = json.parseToJsonElement(content)
            val values = source.bindings.associate { binding ->
                binding.id to requireNotNull(valueAt(root, binding.path)) { "Missing JSON value at ${binding.path}" }
            }
            WidgetLiveResult(values, System.currentTimeMillis())
        }
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

    private fun validatePublicHttpsUrl(raw: String) {
        val uri = URI(raw)
        require(uri.scheme.equals("https", ignoreCase = true)) { "Only HTTPS live-data sources are allowed" }
        require(uri.userInfo == null) { "Credentials cannot be embedded in a widget URL" }
        val host = requireNotNull(uri.host) { "Data-source host is missing" }
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "Private and local data-source addresses are blocked" }
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val bytes = address.address
        if (bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC) return false
        return true
    }

    private val INDEX = Regex("\\[(\\d+)]")
}
