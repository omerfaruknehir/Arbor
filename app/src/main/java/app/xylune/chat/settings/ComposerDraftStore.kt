package app.xylune.chat.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Durable per-conversation composer text.
 *
 * File attachments are already copied into Xylune's private storage and represented
 * by staged AttachmentEntity rows keyed to the same conversation. Keeping text
 * here means switching chats, process death, and launcher-icon restarts preserve
 * the complete draft without duplicating attachment bytes.
 */
class ComposerDraftStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "xylune_composer_drafts",
        Context.MODE_PRIVATE,
    )

    fun read(conversationId: String): String =
        preferences.getString(key(conversationId), "").orEmpty()

    fun write(conversationId: String, text: String) {
        preferences.edit(commit = true) {
            if (text.isEmpty()) remove(key(conversationId))
            else putString(key(conversationId), text)
        }
    }

    fun remove(conversationId: String) {
        preferences.edit(commit = true) { remove(key(conversationId)) }
    }

    private fun key(conversationId: String) = "draft.$conversationId"
}
