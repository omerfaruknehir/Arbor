package app.xylune.chat.provider

import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity

/** Request-shape details that vary between model families hosted by Model Studio. */
internal object AlibabaRequestCapabilities {
    fun usesMaxCompletionTokens(provider: ProviderEntity, model: ModelEntity): Boolean {
        if (!ModelRequestPolicy.isAlibabaModelStudio(provider)) return false
        val id = model.modelId.substringAfterLast('/').lowercase()
        return when {
            id.startsWith("glm-5.2") -> true
            id.startsWith("qwen3.7-max") -> true
            id.startsWith("qwen3.7-plus") -> true
            id.startsWith("qwen3.7-flash") -> true
            id.startsWith("qwen3.6-plus") -> true
            id.startsWith("qwen3.6-flash") -> true
            id.startsWith("qwen3.5-plus") -> true
            id.startsWith("qwen3.5-flash") -> true
            else -> false
        }
    }

    fun usesEnableThinking(provider: ProviderEntity, model: ModelEntity): Boolean {
        if (!ModelRequestPolicy.isAlibabaModelStudio(provider)) return false
        val id = model.modelId.substringAfterLast('/').lowercase()
        return !id.startsWith("minimax-")
    }
}
