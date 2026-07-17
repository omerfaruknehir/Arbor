package app.arbor.chat.widgets

import android.content.Context
import androidx.core.content.edit

internal class WidgetStorage(context: Context) {
    private val preferences = context.getSharedPreferences("arbor_generated_widgets", Context.MODE_PRIVATE)

    fun savePending(token: String, source: String) {
        cleanupExpiredPending()
        preferences.edit { putString("pending_$token", source); putLong("pending_time_$token", System.currentTimeMillis()) }
    }

    fun takePending(token: String): String? {
        val createdAt = preferences.getLong("pending_time_$token", 0L)
        val source = preferences.getString("pending_$token", null)?.takeIf {
            createdAt > 0 && System.currentTimeMillis() - createdAt <= PENDING_TTL_MS
        }
        preferences.edit { remove("pending_$token"); remove("pending_time_$token") }
        return source
    }

    fun save(id: Int, source: String) { preferences.edit { putString("widget_$id", source); putInt("widget_schema_$id", CURRENT_SCHEMA) } }
    fun source(id: Int): String? = preferences.getString("widget_$id", null)?.takeIf { preferences.getInt("widget_schema_$id", 0) == CURRENT_SCHEMA }
    fun state(id: Int, name: String): String? = preferences.getString("state_${id}_$name", null)
    fun setState(id: Int, name: String, value: String) { preferences.edit { putString("state_${id}_$name", value) } }

    fun clearState(id: Int) {
        val keys = preferences.all.keys.filter { it.startsWith("state_${id}_") }
        preferences.edit { keys.forEach(::remove) }
    }

    fun delete(id: Int) {
        clearState(id)
        preferences.edit { remove("widget_$id") }
    }

    private fun cleanupExpiredPending() {
        val now = System.currentTimeMillis()
        val expiredTokens = preferences.all.keys.filter { it.startsWith("pending_time_") }.mapNotNull { key ->
            val token = key.removePrefix("pending_time_")
            token.takeIf { now - preferences.getLong(key, 0L) > PENDING_TTL_MS }
        }
        if (expiredTokens.isNotEmpty()) preferences.edit {
            expiredTokens.forEach { token -> remove("pending_$token"); remove("pending_time_$token") }
        }
    }

    companion object {
        private const val CURRENT_SCHEMA = 1
        private const val PENDING_TTL_MS = 10 * 60 * 1_000L
    }
}
