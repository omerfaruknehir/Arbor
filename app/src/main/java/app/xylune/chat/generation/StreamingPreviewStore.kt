package app.xylune.chat.generation

import app.xylune.chat.provider.GeneratedImageOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class StreamingPreview(
    val conversationId: String,
    val content: String,
    val reasoning: String,
    val generatedImagePreview: GeneratedImageOutput? = null,
    val generatedImagePreviewIndex: Int? = null,
    val generatedImagePreviewCount: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Carries the newest in-process provider output directly to the visible chat.
 * Durable Room writes remain batched for efficiency and recovery. Image previews
 * are intentionally transient: only the provider's completed image is persisted.
 */
internal object StreamingPreviewStore {
    private val mutablePreviews = MutableStateFlow<Map<String, StreamingPreview>>(emptyMap())
    val previews = mutablePreviews.asStateFlow()

    fun publish(
        nodeId: String,
        conversationId: String,
        content: String,
        reasoning: String,
        generatedImagePreview: GeneratedImageOutput? = null,
        generatedImagePreviewIndex: Int? = null,
        generatedImagePreviewCount: Int? = null,
    ) {
        mutablePreviews.update { current ->
            val previous = current[nodeId]
            val next = StreamingPreview(
                conversationId = conversationId,
                content = content,
                reasoning = reasoning,
                generatedImagePreview = generatedImagePreview,
                generatedImagePreviewIndex = generatedImagePreviewIndex,
                generatedImagePreviewCount = generatedImagePreviewCount,
            )
            if (
                previous != null &&
                previous.conversationId == next.conversationId &&
                previous.content == next.content &&
                previous.reasoning == next.reasoning &&
                previous.generatedImagePreview === next.generatedImagePreview &&
                previous.generatedImagePreviewIndex == next.generatedImagePreviewIndex &&
                previous.generatedImagePreviewCount == next.generatedImagePreviewCount
            ) current else current + (nodeId to next)
        }
    }

    fun clear(nodeId: String) {
        mutablePreviews.update { current ->
            if (nodeId !in current) current else current - nodeId
        }
    }
}
