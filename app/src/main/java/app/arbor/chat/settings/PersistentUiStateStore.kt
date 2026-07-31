package app.arbor.chat.settings

import android.content.Context
import androidx.core.content.edit
import app.arbor.chat.ui.Screen
import app.arbor.chat.ui.SettingsRoute

/** Small synchronous state journal used before an intentional app restart. */
class PersistentUiStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "arbor_ui_session",
        Context.MODE_PRIVATE,
    )

    data class RestoredState(
        val selectedConversationId: String?,
        val newDraftConversationId: String?,
        val screen: Screen,
        val settingsRoute: SettingsRoute,
        val selectedProjectId: String?,
        val showArchived: Boolean,
        val searchQuery: String,
        val focusedMessageNodeId: String?,
        val setupActive: Boolean,
        val setupStepIndex: Int,
        val setupPageOffsetFraction: Float,
        val setupTemporarilyAway: Boolean,
        val setupDismissed: Boolean,
    )

    data class ChatScrollSnapshot(
        val anchorNodeId: String?,
        val firstVisibleItemIndex: Int,
        val firstVisibleItemOffset: Int,
        val atLatest: Boolean,
        val topBarHeightOffset: Float,
    )

    fun restore(): RestoredState = RestoredState(
        selectedConversationId = preferences.getString(KEY_SELECTED_CONVERSATION, null),
        newDraftConversationId = preferences.getString(KEY_NEW_DRAFT_CONVERSATION, null),
        screen = enumValue(KEY_SCREEN, Screen.CHAT),
        settingsRoute = enumValue(KEY_SETTINGS_ROUTE, SettingsRoute.HOME),
        selectedProjectId = preferences.getString(KEY_SELECTED_PROJECT, null),
        showArchived = preferences.getBoolean(KEY_SHOW_ARCHIVED, false),
        searchQuery = preferences.getString(KEY_SEARCH_QUERY, "").orEmpty(),
        focusedMessageNodeId = preferences.getString(KEY_FOCUSED_MESSAGE, null),
        setupActive = preferences.getBoolean(KEY_SETUP_ACTIVE, false),
        setupStepIndex = preferences.getInt(KEY_SETUP_STEP, 0).coerceAtLeast(0),
        setupPageOffsetFraction = preferences.getFloat(KEY_SETUP_PAGE_OFFSET, 0f)
            .coerceIn(MIN_PAGE_OFFSET, MAX_PAGE_OFFSET),
        setupTemporarilyAway = preferences.getBoolean(KEY_SETUP_TEMPORARILY_AWAY, false),
        setupDismissed = preferences.getBoolean(KEY_SETUP_DISMISSED, false),
    )

    fun saveSession(
        selectedConversationId: String?,
        newDraftConversationId: String?,
        screen: Screen,
        settingsRoute: SettingsRoute,
        selectedProjectId: String?,
        showArchived: Boolean,
        searchQuery: String,
        focusedMessageNodeId: String?,
        setupActive: Boolean,
        setupStepIndex: Int,
        setupPageOffsetFraction: Float,
        setupTemporarilyAway: Boolean,
        setupDismissed: Boolean,
    ) {
        preferences.edit(commit = true) {
            putNullableString(KEY_SELECTED_CONVERSATION, selectedConversationId)
            putNullableString(KEY_NEW_DRAFT_CONVERSATION, newDraftConversationId)
            putString(KEY_SCREEN, screen.name)
            putString(KEY_SETTINGS_ROUTE, settingsRoute.name)
            putNullableString(KEY_SELECTED_PROJECT, selectedProjectId)
            putBoolean(KEY_SHOW_ARCHIVED, showArchived)
            putString(KEY_SEARCH_QUERY, searchQuery)
            putNullableString(KEY_FOCUSED_MESSAGE, focusedMessageNodeId)
            putBoolean(KEY_SETUP_ACTIVE, setupActive)
            putInt(KEY_SETUP_STEP, setupStepIndex.coerceAtLeast(0))
            putFloat(
                KEY_SETUP_PAGE_OFFSET,
                setupPageOffsetFraction.coerceIn(MIN_PAGE_OFFSET, MAX_PAGE_OFFSET),
            )
            putBoolean(KEY_SETUP_TEMPORARILY_AWAY, setupTemporarilyAway)
            putBoolean(KEY_SETUP_DISMISSED, setupDismissed)
        }
    }

    fun saveChatScroll(
        conversationId: String,
        snapshot: ChatScrollSnapshot,
        immediate: Boolean = false,
    ) {
        val encoded = listOf(
            snapshot.anchorNodeId.orEmpty(),
            snapshot.firstVisibleItemIndex.toString(),
            snapshot.firstVisibleItemOffset.toString(),
            snapshot.atLatest.toString(),
            snapshot.topBarHeightOffset.toString(),
        ).joinToString(SEPARATOR)
        preferences.edit(commit = immediate) { putString(scrollKey(conversationId), encoded) }
    }

    fun chatScroll(conversationId: String): ChatScrollSnapshot? {
        val parts = preferences.getString(scrollKey(conversationId), null)
            ?.split(SEPARATOR)
            ?: return null
        if (parts.size != 5) return null
        return ChatScrollSnapshot(
            anchorNodeId = parts[0].ifBlank { null },
            firstVisibleItemIndex = parts[1].toIntOrNull()?.coerceAtLeast(0) ?: return null,
            firstVisibleItemOffset = parts[2].toIntOrNull()?.coerceAtLeast(0) ?: return null,
            atLatest = parts[3].toBooleanStrictOrNull() ?: return null,
            topBarHeightOffset = parts[4].toFloatOrNull() ?: 0f,
        )
    }


    fun saveSettingsScroll(route: SettingsRoute, offset: Int, immediate: Boolean = false) {
        preferences.edit(commit = immediate) {
            putInt(settingsScrollKey(route), offset.coerceAtLeast(0))
        }
    }

    fun settingsScroll(route: SettingsRoute): Int =
        preferences.getInt(settingsScrollKey(route), 0).coerceAtLeast(0)


    fun saveSetupScroll(stepIndex: Int, offset: Int, immediate: Boolean = false) {
        preferences.edit(commit = immediate) {
            putInt(setupScrollKey(stepIndex), offset.coerceAtLeast(0))
        }
    }

    fun setupScroll(stepIndex: Int): Int =
        preferences.getInt(setupScrollKey(stepIndex), 0).coerceAtLeast(0)

    fun clearChatScroll(conversationId: String) {
        preferences.edit { remove(scrollKey(conversationId)) }
    }

    private inline fun <reified T : Enum<T>> enumValue(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(preferences.getString(key, null) ?: fallback.name) }
            .getOrDefault(fallback)

    private fun android.content.SharedPreferences.Editor.putNullableString(key: String, value: String?) {
        if (value == null) remove(key) else putString(key, value)
    }

    private fun scrollKey(conversationId: String) = "chat_scroll.$conversationId"

    private fun settingsScrollKey(route: SettingsRoute) = "settings_scroll.${route.name}"

    private fun setupScrollKey(stepIndex: Int) = "setup_scroll.${stepIndex.coerceAtLeast(0)}"

    private companion object {
        const val SEPARATOR = "\u001f"
        const val KEY_SELECTED_CONVERSATION = "selected_conversation"
        const val KEY_NEW_DRAFT_CONVERSATION = "new_draft_conversation"
        const val KEY_SCREEN = "screen"
        const val KEY_SETTINGS_ROUTE = "settings_route"
        const val KEY_SELECTED_PROJECT = "selected_project"
        const val KEY_SHOW_ARCHIVED = "show_archived"
        const val KEY_SEARCH_QUERY = "search_query"
        const val KEY_FOCUSED_MESSAGE = "focused_message"
        const val KEY_SETUP_ACTIVE = "setup_active"
        const val KEY_SETUP_STEP = "setup_step"
        const val KEY_SETUP_PAGE_OFFSET = "setup_page_offset"
        const val KEY_SETUP_TEMPORARILY_AWAY = "setup_temporarily_away"
        const val MIN_PAGE_OFFSET = -0.499f
        const val MAX_PAGE_OFFSET = 0.499f
        const val KEY_SETUP_DISMISSED = "setup_dismissed"
    }
}
