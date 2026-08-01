package app.arbor.chat.provider

import app.arbor.chat.data.AttachmentEntity
import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ThinkingEffort

data class InputMessage(
    val role: MessageRole,
    val content: String,
    val reasoning: String = "",
    val toolTraceJson: String = "[]",
    val attachments: List<AttachmentEntity> = emptyList(),
    val nativeToolCalls: List<NativeToolCall> = emptyList(),
    val nativeToolResults: List<NativeToolResult> = emptyList(),
    /** Provider-specific assistant content blocks/parts which must be echoed unchanged during a tool loop. */
    val nativeProviderPayloadJson: String = "",
    val generatedImages: List<GeneratedImageOutput> = emptyList(),
)

data class ChatRequest(
    val provider: ProviderEntity,
    val model: ModelEntity,
    val apiKey: String,
    val messages: List<InputMessage>,
    val maxOutputTokens: Int,
    val thinkingEnabled: Boolean,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    val continuation: Boolean = false,
    val customHeaders: Map<String, String> = emptyMap(),
    val tools: List<NativeToolDefinition> = emptyList(),
    /** Names recognized only by the protocol firewall; these are never serialized as callable tools. */
    val toolProtocolNames: Set<String> = emptySet(),
)


data class GeneratedImageOutput(
    val bytes: ByteArray,
    val mimeType: String = "image/png",
    val displayName: String = "generated-image.png",
    val description: String? = null,
)

data class StreamChunk(
    val text: String = "",
    val reasoning: String = "",
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val finishReason: String? = null,
    val toolCallProgress: List<NativeToolCallProgress> = emptyList(),
    val toolCalls: List<NativeToolCall> = emptyList(),
    val nativeProviderPayloadJson: String = "",
    /**
     * Internal provider signal: discard output emitted by the current HTTP attempt before
     * transparently retrying the same model turn.
     */
    val resetCurrentAttempt: Boolean = false,
    val generatedImages: List<GeneratedImageOutput> = emptyList(),
)

interface ChatProvider {
    suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit)
}

class ProviderHttpException(val status: Int, message: String) : Exception(message)
class ProviderProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
