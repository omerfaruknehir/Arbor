package app.xylune.chat.chat

import app.xylune.chat.data.MessageEntity
import app.xylune.chat.data.MessageRole

object ChatTitleGenerator {
    private val generic = setOf(
        "yes", "no", "ok", "okay", "sure", "continue", "thanks", "thank you", "do it", "go ahead",
    )

    fun generate(messagesNewestFirst: List<MessageEntity>): String {
        val userTexts = messagesNewestFirst
            .asSequence()
            .filter { it.role == MessageRole.USER }
            .map { clean(it.content) }
            .filter(String::isNotBlank)
            .take(8)
            .toList()
        if (userTexts.isEmpty()) return ""

        val useful = userTexts.firstOrNull { candidate ->
            candidate.lowercase() !in generic && candidate.length >= 10
        } ?: userTexts.first()
        val newerContext = userTexts.take(3).filterNot { it == useful || it.lowercase() in generic }
        val combined = (listOf(useful) + newerContext)
            .flatMap { it.split(Regex("\\s+")) }
            .distinctBy(String::lowercase)
            .take(9)
            .joinToString(" ")
        return combined.take(64).trim(' ', '-', ':', '.', ',', '!', '?')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun clean(raw: String): String = raw
        .replace(Regex("```[\\s\\S]*?```"), " code ")
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("[*_#>`~\\[\\]{}()]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
