package app.xylune.chat.generation

import android.content.Context
import androidx.work.Data
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkManager
import app.xylune.chat.chat.ChatRepository
import app.xylune.chat.data.MessageStatus
import app.xylune.chat.data.SendMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class GenerationScheduler(
    private val context: Context,
    private val repository: ChatRepository,
) {
    private val resumeMutex = Mutex()
    suspend fun submit(conversationId: String, text: String, attachmentIds: List<String>, mode: SendMode) {
        val effectiveMode = if (mode == SendMode.QUEUE && repository.activeStream(conversationId) == null) SendMode.SEND_NOW else mode
        if (mode == SendMode.STEER) {
            val active = repository.activeStreams(conversationId)
            if (active.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    WorkManager.getInstance(context).cancelAllWorkByTag(conversationTag(conversationId)).result.get()
                }
                // Steering is an intentional hand-off, not an error or a paused
                // response. Preserve each partial answer, but do not offer Resume
                // for work which the new user message has explicitly replaced.
                active.forEach { message ->
                    repository.finish(
                        message.nodeId,
                        MessageStatus.COMPLETE,
                        null,
                        message.inputTokens,
                        message.outputTokens,
                        message.cachedInputTokens,
                        message.costMicros,
                        message.costKnown,
                    )
                }
            }
        }
        val assistantId = repository.submit(conversationId, text, attachmentIds, effectiveMode) ?: return
        start(conversationId, assistantId, continuation = false)
    }

    suspend fun resume(conversationId: String, assistantId: String) = resumeMutex.withLock {
        val manager = WorkManager.getInstance(context)
        // REPLACE cancels an older instance. Its cancellation callback used to
        // overwrite the new STREAMING state, so Continue appeared to do nothing.
        // Finish that cancellation first, then publish the new run state.
        withContext(Dispatchers.IO) {
            manager.cancelUniqueWork(workName(assistantId)).result.get()
        }
        if (repository.message(assistantId) == null) return@withLock
        repository.markStreaming(assistantId)
        start(conversationId, assistantId, continuation = true)
    }

    fun start(conversationId: String, assistantId: String, continuation: Boolean) {
        val input = Data.Builder()
            .putString(GenerationWorker.KEY_CONVERSATION_ID, conversationId)
            .putString(GenerationWorker.KEY_ASSISTANT_ID, assistantId)
            .putBoolean(GenerationWorker.KEY_CONTINUATION, continuation)
            .build()
        val request = OneTimeWorkRequestBuilder<GenerationWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag("generation")
            .addTag(conversationTag(conversationId))
            .addTag("assistant_$assistantId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(assistantId), ExistingWorkPolicy.REPLACE, request)
    }

    fun stop(assistantId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(assistantId))
    }

    fun stopConversation(conversationId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(conversationTag(conversationId))
    }

    private fun workName(assistantId: String) = "generation_$assistantId"
    private fun conversationTag(conversationId: String) = "conversation_$conversationId"
}
