package app.arbor.chat.provider

import app.arbor.chat.chat.TokenEstimator
import app.arbor.chat.data.ProviderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** Accuracy tier used for optional preflight counting. */
enum class TokenCountSource {
    PROVIDER_EXACT,
    LOCAL_FAMILY_ESTIMATE,
    GENERIC_ESTIMATE,
}

data class TokenCountResult(
    val tokens: Long,
    val source: TokenCountSource,
    val detail: String,
)

/**
 * Optional preflight counter. Provider endpoints are preferred when they exist.
 * A failed count never blocks generation: Arbor falls back locally and provider-reported
 * usage remains authoritative after the request.
 */
class HybridTokenCounter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun count(request: ChatRequest): TokenCountResult = withContext(Dispatchers.IO) {
        when (request.provider.kind) {
            ProviderKind.ANTHROPIC -> runCatching { countAnthropic(request) }.getOrElse { localCount(request, it.message) }
            ProviderKind.GEMINI -> runCatching { countGemini(request) }.getOrElse { localCount(request, it.message) }
            ProviderKind.OPENAI_COMPATIBLE -> localCount(request, null)
        }
    }

    private suspend fun countAnthropic(request: ChatRequest): TokenCountResult {
        val generated = AnthropicProvider().buildRequestBody(request)
        val body = JsonObject(generated.filterKeys { key ->
            key !in setOf("max_tokens", "stream", "thinking", "output_config")
        })
        val http = Request.Builder()
            .url(request.provider.baseUrl.trimEnd('/') + "/messages/count_tokens")
            .header("x-api-key", request.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .also { builder -> request.customHeaders.forEach(builder::header) }
            .build()
        return client.newCall(http).useCancellable { response ->
            if (!response.isSuccessful) throw ProviderHttpException(response.code, response.body?.readErrorSnippet().orEmpty())
            val root = ProviderJson.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val count = root.long("input_tokens") ?: throw ProviderProtocolException("Anthropic token count omitted input_tokens")
            TokenCountResult(count, TokenCountSource.PROVIDER_EXACT, "Anthropic count_tokens")
        }
    }

    private suspend fun countGemini(request: ChatRequest): TokenCountResult {
        val generated = GeminiProvider().buildRequestBody(request)
        val countable = JsonObject(generated.filterKeys { it != "generationConfig" })
        val body = buildJsonObject { put("generateContentRequest", countable) }
        val http = Request.Builder()
            .url(request.provider.baseUrl.trimEnd('/') + "/models/${request.model.modelId}:countTokens")
            .header("Content-Type", "application/json")
            .also { builder -> if (request.apiKey.isNotBlank()) builder.header("x-goog-api-key", request.apiKey) }
            .post(body.toString().toRequestBody(JSON))
            .also { builder -> request.customHeaders.forEach(builder::header) }
            .build()
        return client.newCall(http).useCancellable { response ->
            if (!response.isSuccessful) throw ProviderHttpException(response.code, response.body?.readErrorSnippet().orEmpty())
            val root = ProviderJson.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val count = root.long("totalTokens") ?: throw ProviderProtocolException("Gemini token count omitted totalTokens")
            TokenCountResult(count, TokenCountSource.PROVIDER_EXACT, "Gemini models.countTokens")
        }
    }

    private fun localCount(request: ChatRequest, providerFailure: String?): TokenCountResult {
        val model = request.model.modelId.lowercase()
        val supportedFamily = when {
            model.contains("gpt") || model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4") -> "OpenAI o200k-like"
            model.contains("deepseek") -> "DeepSeek family"
            model.contains("qwen") -> "Qwen family"
            model.contains("llama") || model.contains("mistral") || model.contains("grok") -> "SentencePiece-like"
            else -> null
        }
        if (supportedFamily == null) {
            val generic = request.messages.sumOf { message ->
                TokenEstimator.estimate(message.content + message.reasoning + message.toolTraceJson).toLong() +
                    attachmentEstimate(message)
            } + toolEstimate(request)
            return TokenCountResult(generic.coerceAtLeast(1), TokenCountSource.GENERIC_ESTIMATE,
                providerFailure?.let { "Provider count unavailable; generic fallback" } ?: "Generic fallback")
        }
        val count = request.messages.sumOf { message ->
            familyTextCount(message.content + message.reasoning + message.toolTraceJson, model) +
                attachmentEstimate(message) + 4L
        } + toolEstimate(request) + 3L
        val detail = buildString {
            append(supportedFamily)
            if (!providerFailure.isNullOrBlank()) append("; provider counter unavailable")
        }
        return TokenCountResult(count.coerceAtLeast(1), TokenCountSource.LOCAL_FAMILY_ESTIMATE, detail)
    }

    private fun familyTextCount(text: String, model: String): Long {
        if (text.isBlank()) return 0
        val ascii = text.count { it.code < 128 }
        val nonAscii = text.length - ascii
        val codePunctuation = text.count { it in "{}[]()<>;:=_\\/|`" }
        val asciiDivisor = when {
            model.contains("deepseek") || model.contains("qwen") -> 3.35
            model.contains("llama") || model.contains("mistral") || model.contains("grok") -> 3.45
            else -> 3.75
        }
        val nonAsciiDivisor = when {
            model.contains("qwen") || model.contains("deepseek") -> 1.25
            else -> 1.55
        }
        return ceil(ascii / asciiDivisor + nonAscii / nonAsciiDivisor + codePunctuation / 18.0).toLong()
    }

    private fun attachmentEstimate(message: InputMessage): Long = message.attachments.sumOf { attachment ->
        when {
            attachment.extractedText != null -> familyTextCount(attachment.extractedText.take(1_000_000), "generic") + 32
            attachment.ocrJson != null -> familyTextCount(attachment.ocrJson.take(128_000), "generic") + 64
            attachment.mimeType.startsWith("image/") -> 1_536
            else -> 512
        }
    }

    private fun toolEstimate(request: ChatRequest): Long = request.tools.sumOf { tool ->
        TokenEstimator.estimate(tool.name + tool.description + tool.parametersJson).toLong() + 16
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
