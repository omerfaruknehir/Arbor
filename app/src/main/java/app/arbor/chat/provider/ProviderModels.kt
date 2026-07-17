package app.arbor.chat.provider

import app.arbor.chat.data.AttachmentEntity
import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.ProviderEntity

data class InputMessage(
    val role: MessageRole,
    val content: String,
    val reasoning: String = "",
    val toolTraceJson: String = "[]",
    val attachments: List<AttachmentEntity> = emptyList(),
)

data class ChatRequest(
    val provider: ProviderEntity,
    val model: ModelEntity,
    val apiKey: String,
    val messages: List<InputMessage>,
    val maxOutputTokens: Int,
    val thinkingEnabled: Boolean,
    val continuation: Boolean = false,
    val customHeaders: Map<String, String> = emptyMap(),
)

data class StreamChunk(
    val text: String = "",
    val reasoning: String = "",
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val finishReason: String? = null,
)

interface ChatProvider {
    suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit)
}

class ProviderHttpException(val status: Int, message: String) : Exception(message)
class ProviderProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
