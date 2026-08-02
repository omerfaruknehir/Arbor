package app.arbor.chat.chat

import app.arbor.chat.data.MemoryEntity
import app.arbor.chat.data.MessageEntity
import app.arbor.chat.data.MessageRole
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

internal data class MemoryWriteResult(
    val memory: MemoryEntity,
    val created: Boolean,
    val mergedMemoryId: String? = null,
)

/**
 * Keeps Arbor memory useful without allowing a large flat memory list to crowd
 * the actual conversation out of the model context.
 */
internal object MemoryManagement {
    const val DEFAULT_CONTEXT_ITEMS = 32
    const val DEFAULT_CONTEXT_CHARACTERS = 12_000
    const val MAX_SEARCH_RESULTS = 200

    fun cleanContent(value: String): String = value
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(2_000)

    fun cleanCategory(value: String): String = value
        .trim()
        .replace(Regex("[^\\p{L}\\p{N} _.-]"), "")
        .replace(Regex("\\s+"), " ")
        .take(40)
        .ifBlank { "general" }

    fun canonicalKey(content: String, category: String): String =
        "${canonicalText(cleanCategory(category))}|${canonicalText(cleanContent(content))}".take(512)

    fun findDuplicate(
        memories: List<MemoryEntity>,
        content: String,
        category: String,
        excludingId: String? = null,
    ): MemoryEntity? {
        val clean = cleanContent(content)
        val cleanCategory = cleanCategory(category)
        val canonical = canonicalText(clean)
        val candidateTokens = tokens(canonical)
        return memories.asSequence()
            .filter { it.id != excludingId }
            .filter { canonicalText(it.category) == canonicalText(cleanCategory) }
            .map { memory -> memory to duplicateScore(canonical, candidateTokens, memory.content) }
            .filter { (_, score) -> score >= DUPLICATE_THRESHOLD }
            .maxWithOrNull(compareBy<Pair<MemoryEntity, Double>> { it.second }.thenBy { it.first.updatedAt })
            ?.first
    }

    fun search(
        memories: List<MemoryEntity>,
        query: String,
        includeDisabled: Boolean = false,
        limit: Int = 100,
    ): List<MemoryEntity> {
        val safeLimit = limit.coerceIn(1, MAX_SEARCH_RESULTS)
        val eligible = memories.filter { includeDisabled || it.enabled }
        val normalizedQuery = canonicalText(query)
        if (normalizedQuery.isBlank()) return eligible.sortedByDescending(MemoryEntity::updatedAt).take(safeLimit)
        val queryTokens = tokens(normalizedQuery)
        return eligible.asSequence()
            .map { memory -> memory to relevanceScore(memory, normalizedQuery, queryTokens, null) }
            .filter { (_, score) -> score > 0.0 }
            .sortedWith(compareByDescending<Pair<MemoryEntity, Double>> { it.second }.thenByDescending { it.first.updatedAt })
            .take(safeLimit)
            .map(Pair<MemoryEntity, Double>::first)
            .toList()
    }

    fun selectForContext(
        memories: List<MemoryEntity>,
        messagesNewestFirst: List<MessageEntity>,
        currentConversationId: String?,
        maxItems: Int = DEFAULT_CONTEXT_ITEMS,
        maxCharacters: Int = DEFAULT_CONTEXT_CHARACTERS,
    ): List<MemoryEntity> {
        val itemLimit = maxItems.coerceIn(1, 100)
        val characterLimit = maxCharacters.coerceIn(1_000, 64_000)
        val enabled = memories.asSequence()
            .filter(MemoryEntity::enabled)
            .sortedByDescending(MemoryEntity::updatedAt)
            .distinctBy { canonicalKey(it.content, it.category) }
            .toList()
        if (enabled.isEmpty()) return emptyList()

        val query = messagesNewestFirst.asSequence()
            .filter { it.role == MessageRole.USER }
            .take(8)
            .map { it.content.take(4_000) }
            .joinToString(" ")
        val normalizedQuery = canonicalText(query)
        val queryTokens = tokens(normalizedQuery)
        val scored = enabled.map { memory ->
            memory to relevanceScore(memory, normalizedQuery, queryTokens, currentConversationId)
        }.sortedWith(compareByDescending<Pair<MemoryEntity, Double>> { it.second }.thenByDescending { it.first.updatedAt })

        val totalCharacters = enabled.sumOf(::estimatedContextCharacters)
        val candidates = if (enabled.size <= itemLimit && totalCharacters <= characterLimit) {
            scored.map(Pair<MemoryEntity, Double>::first)
        } else {
            buildList {
                scored.filter { (_, score) -> score > 0.0 }.forEach { (memory, _) ->
                    if (none { it.id == memory.id }) add(memory)
                }
                enabled.take(RECENT_FALLBACK_ITEMS).forEach { memory ->
                    if (none { it.id == memory.id }) add(memory)
                }
                if (isEmpty()) addAll(enabled)
            }
        }

        val selected = mutableListOf<MemoryEntity>()
        var usedCharacters = 0
        for (memory in candidates) {
            if (selected.size >= itemLimit) break
            val cost = estimatedContextCharacters(memory)
            if (selected.isNotEmpty() && usedCharacters + cost > characterLimit) continue
            selected += memory
            usedCharacters += cost
        }
        return selected.ifEmpty { enabled.take(1) }
    }

    private fun relevanceScore(
        memory: MemoryEntity,
        normalizedQuery: String,
        queryTokens: Set<String>,
        currentConversationId: String?,
    ): Double {
        if (normalizedQuery.isBlank()) {
            return if (memory.sourceConversationId == currentConversationId && currentConversationId != null) 1.0 else 0.0
        }
        val content = canonicalText(memory.content)
        val category = canonicalText(memory.category)
        val memoryTokens = tokens("$category $content")
        val overlap = queryTokens.intersect(memoryTokens)
        var score = overlap.sumOf { token -> min(token.length, 10).toDouble() }
        if (content.isNotBlank() && (normalizedQuery.contains(content) || content.contains(normalizedQuery))) score += 24.0
        if (category.isNotBlank() && queryTokens.contains(category)) score += 8.0
        if (memory.sourceConversationId == currentConversationId && currentConversationId != null) score += 3.0
        return score
    }

    private fun duplicateScore(canonicalCandidate: String, candidateTokens: Set<String>, existingContent: String): Double {
        val canonicalExisting = canonicalText(existingContent)
        if (canonicalExisting == canonicalCandidate) return 1.0
        if (canonicalExisting.isBlank() || canonicalCandidate.isBlank()) return 0.0
        val existingTokens = tokens(canonicalExisting)
        if (candidateTokens.size < 3 || existingTokens.size < 3) return 0.0
        val intersection = candidateTokens.intersect(existingTokens).size.toDouble()
        val union = candidateTokens.union(existingTokens).size.coerceAtLeast(1).toDouble()
        val smaller = min(candidateTokens.size, existingTokens.size).coerceAtLeast(1).toDouble()
        val jaccard = intersection / union
        val overlap = intersection / smaller
        val containment = canonicalCandidate.contains(canonicalExisting) || canonicalExisting.contains(canonicalCandidate)
        return when {
            jaccard >= 0.88 -> jaccard
            containment && overlap >= 0.95 && kotlin.math.abs(candidateTokens.size - existingTokens.size) <= 2 -> overlap
            else -> 0.0
        }
    }

    private fun canonicalText(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun tokens(value: String): Set<String> = canonicalText(value)
        .split(' ')
        .asSequence()
        .filter { it.length >= 2 && it !in STOP_WORDS }
        .toCollection(linkedSetOf())

    private fun estimatedContextCharacters(memory: MemoryEntity): Int =
        memory.content.length + memory.category.length + 48

    private const val DUPLICATE_THRESHOLD = 0.88
    private const val RECENT_FALLBACK_ITEMS = 6
    private val STOP_WORDS = setOf(
        "a", "an", "and", "are", "as", "at", "be", "been", "by", "for", "from", "i", "in", "is", "it",
        "me", "my", "of", "on", "or", "that", "the", "this", "to", "was", "were", "with", "you", "your",
        "ama", "ben", "benim", "bir", "bu", "da", "de", "icin", "ile", "mi", "mu", "sen", "ve", "veya",
    )
}
