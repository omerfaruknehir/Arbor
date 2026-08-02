from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"Expected fixup anchor not found in {path}: {old[:200]!r}")
    file.write_text(text.replace(old, new, 1))


def replace_all(path: str, old: str, new: str, minimum: int = 1) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count < minimum:
        raise SystemExit(f"Expected at least {minimum} fixup anchors in {path}, found {count}: {old!r}")
    file.write_text(text.replace(old, new))


# Python's first-stage string literals emitted an invalid Kotlin \s escape.
replace_all(
    "app/src/main/java/app/arbor/chat/chat/ChatRepository.kt",
    r'Regex("\s+")',
    r'Regex("\\s+")',
)
replace_all(
    "app/src/main/java/app/arbor/chat/transfer/AppSettingsArchiveStore.kt",
    r'Regex("\s+")',
    r'Regex("\\s+")',
)

# The memory list is serialized as native tool output.
replace_once(
    "app/src/main/java/app/arbor/chat/data/Entities.kt",
    ''')
data class MemoryEntity(
''',
    ''')
@Serializable
data class MemoryEntity(
''',
)

# Avoid heterogeneous Map<String, Any> serialization and return stable schemas.
replace_once(
    "app/src/main/java/app/arbor/chat/agent/AgentTools.kt",
    '''            AgentToolOutcome(json.encodeToString(mapOf(
                "saved" to true,
                "id" to memory.id,
                "category" to memory.category,
                "content" to memory.content,
            )))
''',
    '''            AgentToolOutcome(json.encodeToString(MemorySaveToolResult(
                saved = true,
                id = memory.id,
                category = memory.category,
                content = memory.content,
            )))
''',
)
replace_once(
    "app/src/main/java/app/arbor/chat/agent/AgentTools.kt",
    '''            AgentToolOutcome(json.encodeToString(mapOf("forgotten" to true, "id" to id)))
''',
    '''            AgentToolOutcome(json.encodeToString(MemoryForgetToolResult(forgotten = true, id = id)))
''',
)
replace_once(
    "app/src/main/java/app/arbor/chat/agent/AgentTools.kt",
    '''@Serializable
private data class UbuntuToolResult(
''',
    '''@Serializable
private data class MemorySaveToolResult(
    val saved: Boolean,
    val id: String,
    val category: String,
    val content: String,
)

@Serializable
private data class MemoryForgetToolResult(
    val forgotten: Boolean,
    val id: String,
)

@Serializable
private data class UbuntuToolResult(
''',
)

# Distinguish a disabled memory system from an enabled-but-empty one.
replace_once(
    "app/src/main/java/app/arbor/chat/chat/ContextAssembler.kt",
    '''        memories: List<MemoryEntity> = emptyList(),
        memoryAutoSave: Boolean = false,
''',
    '''        memories: List<MemoryEntity> = emptyList(),
        memoryEnabled: Boolean = false,
        memoryAutoSave: Boolean = false,
''',
)
replace_once(
    "app/src/main/java/app/arbor/chat/chat/ContextAssembler.kt",
    '''        val memoryLayer = if (memories.isEmpty()) {
            "Arbor memory is enabled but currently empty."
        } else buildString {
''',
    '''        val memoryLayer = when {
            !memoryEnabled -> "Arbor memory is disabled."
            memories.isEmpty() -> "Arbor memory is enabled but currently empty."
            else -> buildString {
''',
)
replace_once(
    "app/src/main/java/app/arbor/chat/chat/ContextAssembler.kt",
    '''            memories = activeMemories,
            memoryAutoSave = automationSettings.memoryAutoSave,
''',
    '''            memories = activeMemories,
            memoryEnabled = automationSettings.memoryEnabled,
            memoryAutoSave = automationSettings.memoryAutoSave,
''',
)
