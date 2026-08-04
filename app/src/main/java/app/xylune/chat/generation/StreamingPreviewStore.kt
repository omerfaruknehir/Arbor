package app.xylune.chat.generation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class StreamingPreview(
    val content: String,
    val reasoning: String,
)

/**
 * Carries the newest in-process provider text directly to the visible chat.
 * Durable Room writes remain batched for efficiency and recovery, but the UI no
 * longer waits for PagingSource invalidation before it can display a token.
 */
internal object StreamingPreviewStore {
    private val mutablePreviews = MutableStateFlow<Map<String, StreamingPreview>>(emptyMap())
    val previews = mutablePreviews.asStateFlow()

    fun publish(nodeId: String, content: String, reasoning: String) {
        mutablePreviews.update { current ->
            val next = StreamingPreview(content = content, reasoning = reasoning)
            if (current[nodeId] == next) current else current + (nodeId to next)
        }
    }

    fun clear(nodeId: String) {
        mutablePreviews.update { current ->
            if (nodeId !in current) current else current - nodeId
        }
    }
}
