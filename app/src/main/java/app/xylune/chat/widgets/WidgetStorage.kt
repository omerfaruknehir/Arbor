package app.xylune.chat.widgets

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class WidgetLocationGrant { NONE, APPROXIMATE, PRECISE }

data class WidgetCapabilityGrants(
    val networkOrigins: Set<String> = emptySet(),
    val location: WidgetLocationGrant = WidgetLocationGrant.NONE,
    val folderUri: String? = null,
    val folderWrite: Boolean = false,
    val backgroundRefresh: Boolean = false,
)

internal data class PendingWidgetProgram(
    val source: String,
    val grants: WidgetCapabilityGrants,
)

/**
 * Storage for the new xylune-widget/1 runtime. It intentionally uses a new file
 * and schema: old generated widgets and mini-app storage are not imported.
 */
internal class WidgetStorage(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun savePending(token: String, source: String, grants: WidgetCapabilityGrants) {
        cleanupExpiredPending()
        preferences.edit {
            putString("pending_${token}_source", source)
            putString("pending_${token}_grants", encodeGrants(grants))
            putLong("pending_${token}_time", System.currentTimeMillis())
        }
    }

    fun takePending(token: String): PendingWidgetProgram? {
        val createdAt = preferences.getLong("pending_${token}_time", 0L)
        val source = preferences.getString("pending_${token}_source", null)
        val grants = preferences.getString("pending_${token}_grants", null)?.let(::decodeGrants)
        preferences.edit {
            remove("pending_${token}_source")
            remove("pending_${token}_grants")
            remove("pending_${token}_time")
        }
        return if (source != null && grants != null && createdAt > 0 && System.currentTimeMillis() - createdAt <= PENDING_TTL_MS) {
            PendingWidgetProgram(source, grants)
        } else null
    }

    fun save(id: Int, source: String, grants: WidgetCapabilityGrants) {
        preferences.edit {
            putString("widget_${id}_source", source)
            putString("widget_${id}_grants", encodeGrants(grants))
            putInt("widget_${id}_schema", CURRENT_SCHEMA)
        }
    }

    fun source(id: Int): String? = preferences.getString("widget_${id}_source", null)
        ?.takeIf { preferences.getInt("widget_${id}_schema", 0) == CURRENT_SCHEMA }

    fun grants(id: Int): WidgetCapabilityGrants? = preferences.getString("widget_${id}_grants", null)
        ?.takeIf { preferences.getInt("widget_${id}_schema", 0) == CURRENT_SCHEMA }
        ?.let(::decodeGrants)

    fun state(id: Int): Map<String, String> {
        val prefix = "state_${id}_"
        return preferences.all.mapNotNull { (key, value) ->
            if (key.startsWith(prefix) && value is String) key.removePrefix(prefix) to value else null
        }.toMap()
    }

    fun setState(id: Int, values: Map<String, String>) {
        val prefix = "state_${id}_"
        val existing = preferences.all.keys.filter { it.startsWith(prefix) }
        preferences.edit {
            existing.forEach(::remove)
            values.forEach { (key, value) -> putString("$prefix$key", value.take(1_000)) }
        }
    }

    fun setValue(id: Int, key: String, value: String) {
        preferences.edit { putString("state_${id}_$key", value.take(1_000)) }
    }

    fun initializeState(id: Int, defaults: Map<String, String>) {
        val current = state(id)
        if (current.isEmpty()) setState(id, defaults)
    }

    fun error(id: Int): String? = preferences.getString("widget_${id}_error", null)
    fun setError(id: Int, value: String?) { preferences.edit { putString("widget_${id}_error", value?.take(240)) } }
    fun updatedAt(id: Int): Long = preferences.getLong("widget_${id}_updated", 0L)
    fun setUpdatedAt(id: Int, value: Long) { preferences.edit { putLong("widget_${id}_updated", value) } }

    fun delete(id: Int) {
        val prefixes = listOf("state_${id}_", "widget_${id}_")
        val keys = preferences.all.keys.filter { key -> prefixes.any(key::startsWith) }
        preferences.edit { keys.forEach(::remove) }
    }

    private fun cleanupExpiredPending() {
        val now = System.currentTimeMillis()
        val expired = preferences.all.keys.filter { it.startsWith("pending_") && it.endsWith("_time") }.mapNotNull { key ->
            val token = key.removePrefix("pending_").removeSuffix("_time")
            token.takeIf { now - preferences.getLong(key, 0L) > PENDING_TTL_MS }
        }
        if (expired.isNotEmpty()) preferences.edit {
            expired.forEach { token ->
                remove("pending_${token}_source")
                remove("pending_${token}_grants")
                remove("pending_${token}_time")
            }
        }
    }

    private fun encodeGrants(value: WidgetCapabilityGrants): String = buildJsonObject {
        put("networkOrigins", buildJsonArray { value.networkOrigins.sorted().forEach { add(JsonPrimitive(it)) } })
        put("location", value.location.name)
        value.folderUri?.let { put("folderUri", it) }
        put("folderWrite", value.folderWrite)
        put("backgroundRefresh", value.backgroundRefresh)
    }.toString()

    private fun decodeGrants(raw: String): WidgetCapabilityGrants? = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        WidgetCapabilityGrants(
            networkOrigins = (root["networkOrigins"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet().orEmpty(),
            location = root["location"]?.jsonPrimitive?.contentOrNull?.let { runCatching { WidgetLocationGrant.valueOf(it) }.getOrNull() } ?: WidgetLocationGrant.NONE,
            folderUri = root["folderUri"]?.jsonPrimitive?.contentOrNull,
            folderWrite = root["folderWrite"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
            backgroundRefresh = root["backgroundRefresh"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
        )
    }.getOrNull()

    private companion object {
        const val PREFERENCES = "xylune_program_widgets_v2"
        const val CURRENT_SCHEMA = 2
        const val PENDING_TTL_MS = 10 * 60 * 1_000L
    }
}
