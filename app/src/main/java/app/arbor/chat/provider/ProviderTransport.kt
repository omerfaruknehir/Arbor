package app.arbor.chat.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import okhttp3.Call
import okhttp3.Response
import okhttp3.ResponseBody
import kotlin.math.min

internal suspend fun <T> Call.useCancellable(block: suspend (Response) -> T): T {
    val call = this
    val cancellation = currentCoroutineContext().job.invokeOnCompletion { cause ->
        if (cause is CancellationException) call.cancel()
    }
    return try {
        val response = execute()
        try {
            block(response)
        } finally {
            response.close()
        }
    } finally {
        cancellation.dispose()
    }
}

internal fun ResponseBody.readErrorSnippet(limit: Long = 8_192): String {
    val source = source()
    source.request(limit)
    return source.buffer.readUtf8(min(source.buffer.size, limit))
}
