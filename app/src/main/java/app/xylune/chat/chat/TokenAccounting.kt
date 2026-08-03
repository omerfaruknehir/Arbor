package app.xylune.chat.chat

import app.xylune.chat.data.MessageEntity
import app.xylune.chat.data.ModelEntity
import kotlin.math.ceil
import kotlin.math.roundToLong

object TokenEstimator {
    // Used only for the live preflight gauge. Provider-reported usage replaces it after a request.
    fun estimate(text: String): Int {
        if (text.isBlank()) return 0
        val ascii = text.count { it.code < 128 }
        val nonAscii = text.length - ascii
        return ceil(ascii / 3.8 + nonAscii / 1.7).toInt().coerceAtLeast(1)
    }

    fun estimate(message: MessageEntity): Int = estimate(message.content) + estimate(message.reasoning) + estimate(message.toolTraceJson)
}

object CostCalculator {
    fun micros(model: ModelEntity, input: Long, cached: Long, output: Long): Long? {
        if (!model.pricingConfigured) return null
        val cachedSafe = cached.coerceIn(0, input)
        val miss = input - cachedSafe
        val dollars = cachedSafe * model.inputCacheHitUsdPerMillion / 1_000_000.0 +
            miss * model.inputCacheMissUsdPerMillion / 1_000_000.0 +
            output * model.outputUsdPerMillion / 1_000_000.0
        return (dollars * 1_000_000.0).roundToLong()
    }
}
