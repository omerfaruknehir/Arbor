package app.xylune.chat.widgets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Accepts a small set of intuitive generated-style aliases, then emits Xylune's
 * strict canonical schema before validation or persistence.
 */
internal object WidgetSourceNormalizer {
    private val json = Json

    fun normalize(source: String): String = runCatching {
        val root = json.parseToJsonElement(source) as? JsonObject ?: return source
        JsonObject(root.mapValues { (key, value) ->
            if (key == "ui") normalizeNode(value) else value
        }).toString()
    }.getOrElse { source }

    private fun normalizeNode(value: JsonElement): JsonElement {
        val node = value as? JsonObject ?: return value
        return JsonObject(node.mapNotNull { (key, child) ->
            when (key) {
                "style" -> key to normalizeStyle(child)
                "children" -> key to normalizeChildren(child)
                else -> key to child
            }
        }.toMap())
    }

    private fun normalizeChildren(value: JsonElement): JsonElement {
        val array = value as? kotlinx.serialization.json.JsonArray ?: return value
        return kotlinx.serialization.json.JsonArray(array.map(::normalizeNode))
    }

    private fun normalizeStyle(value: JsonElement): JsonElement {
        val style = value as? JsonObject ?: return value
        val normalized = style.toMutableMap()

        if ("fontSize" !in normalized) {
            normalized.remove("size")?.let { normalized["fontSize"] = it }
        } else {
            normalized.remove("size")
        }

        if ("emphasis" !in normalized) {
            normalized.remove("fontWeight")?.let { weight ->
                normalizeWeight(weight)?.let { normalized["emphasis"] = JsonPrimitive(it) }
            }
        } else {
            normalized.remove("fontWeight")
        }
        return JsonObject(normalized)
    }

    private fun normalizeWeight(value: JsonElement): String? {
        val raw = runCatching { value.jsonPrimitive.contentOrNull.orEmpty().trim().lowercase() }.getOrDefault("")
        return when (raw) {
            "normal", "regular", "400" -> "normal"
            "medium", "500", "600" -> "medium"
            "bold", "strong", "700", "800", "900" -> "strong"
            else -> null
        }
    }
}
