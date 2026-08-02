from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str, label: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{label}: start marker missing")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{label}: end marker missing")
    return text[:start] + replacement + text[end:]


# Version and release notes.
build = Path("app/build.gradle.kts")
text = build.read_text()
text = replace_once(
    text,
    '        versionCode = 164\n        versionName = "0.22.0"',
    '        versionCode = 165\n        versionName = "0.22.1"',
    "version",
)
build.write_text(text)

notes = Path("docs/releases/RELEASE_NOTES_0.22.1.md")
notes.write_text("""# Arbor 0.22.1

Arbor 0.22.1 improves memory management and repairs navigation state on phones.

## Memory management

- Gives memory text the full card width and moves controls into a separate metadata row.
- Adds enabled/disabled filtering, category filtering, sorting, selection, and bulk enable/disable/delete.
- Adds confirmation before destructive single-item, selected-item, or disabled-item cleanup.
- Shows whether a memory was saved manually or from a chat, plus its update time.
- Prevents a small memory library from being injected wholesale into unrelated chats.
- Keeps a small set of profile/preference memories available as baseline context and prioritizes relevant or same-chat memories.
- Merges exact duplicates even when their categories differ, while fuzzy duplicate matching remains category-scoped.

## Navigation and scrolling

- New Settings destinations start at the top; Back restores the prior page position.
- Settings and chat titles are derived from the actual restored scroll position, preventing expanded titles over scrolled content.
- Opening Settings from the drawer starts at the Settings root instead of reviving a stale nested page.
- Chat switching restores each chat's own saved position without inheriting another chat's app-bar state.

## Predictive Back

- An open drawer now owns the complete predictive-back gesture.
- Back progress directly closes the drawer, cancellation restores it, and page navigation cannot steal the gesture halfway through.
""")


# Memory selection and duplicate semantics.
path = Path("app/src/main/java/app/arbor/chat/chat/MemoryManagement.kt")
text = path.read_text()
old = '''        val clean = cleanContent(content)
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
'''
new = '''        val clean = cleanContent(content)
        val cleanCategory = cleanCategory(category)
        val canonical = canonicalText(clean)
        val eligible = memories.filter { it.id != excludingId }
        eligible.firstOrNull { canonicalText(it.content) == canonical }?.let { return it }
        val candidateTokens = tokens(canonical)
        return eligible.asSequence()
            .filter { canonicalText(it.category) == canonicalText(cleanCategory) }
            .map { memory -> memory to duplicateScore(canonical, candidateTokens, memory.content) }
            .filter { (_, score) -> score >= DUPLICATE_THRESHOLD }
            .maxWithOrNull(compareBy<Pair<MemoryEntity, Double>> { it.second }.thenBy { it.first.updatedAt })
            ?.first
'''
text = replace_once(text, old, new, "duplicate matching")
old = '''        val totalCharacters = enabled.sumOf(::estimatedContextCharacters)
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
'''
new = '''        val candidates = buildList {
            scored.filter { (_, score) -> score > 0.0 }.forEach { (memory, _) ->
                if (none { it.id == memory.id }) add(memory)
            }
            if (currentConversationId != null) {
                enabled.asSequence()
                    .filter { it.sourceConversationId == currentConversationId }
                    .take(SAME_CHAT_FALLBACK_ITEMS)
                    .forEach { memory -> if (none { it.id == memory.id }) add(memory) }
            }
            enabled.asSequence()
                .filter(::isBaselineMemory)
                .take(BASELINE_CONTEXT_ITEMS)
                .forEach { memory -> if (none { it.id == memory.id }) add(memory) }
            if (asksForMemoryOverview(normalizedQuery, queryTokens)) {
                enabled.take(RECENT_OVERVIEW_ITEMS).forEach { memory ->
                    if (none { it.id == memory.id }) add(memory)
                }
            }
        }

        val selected = mutableListOf<MemoryEntity>()
'''
text = replace_once(text, old, new, "context candidates")
text = replace_once(
    text,
    '        return selected.ifEmpty { enabled.take(1) }\n',
    '        return selected\n',
    "empty context fallback",
)
old = '''    private fun estimatedContextCharacters(memory: MemoryEntity): Int =
        memory.content.length + memory.category.length + 48

    private const val DUPLICATE_THRESHOLD = 0.88
    private const val RECENT_FALLBACK_ITEMS = 6
'''
new = '''    private fun estimatedContextCharacters(memory: MemoryEntity): Int =
        memory.content.length + memory.category.length + 48

    private fun isBaselineMemory(memory: MemoryEntity): Boolean =
        canonicalText(memory.category) in BASELINE_CATEGORIES

    private fun asksForMemoryOverview(normalizedQuery: String, queryTokens: Set<String>): Boolean =
        MEMORY_OVERVIEW_PHRASES.any(normalizedQuery::contains) ||
            queryTokens.any { it in MEMORY_OVERVIEW_TOKENS }

    private const val DUPLICATE_THRESHOLD = 0.88
    private const val SAME_CHAT_FALLBACK_ITEMS = 6
    private const val BASELINE_CONTEXT_ITEMS = 4
    private const val RECENT_OVERVIEW_ITEMS = 12
    private val BASELINE_CATEGORIES = setOf(
        "identity", "language", "languages", "personal", "preference", "preferences", "profile",
    )
    private val MEMORY_OVERVIEW_TOKENS = setOf("memory", "memories", "remember", "remembered", "hatirla", "hafiza")
    private val MEMORY_OVERVIEW_PHRASES = setOf("know about me", "what do you know", "benim hakkimda")
'''
text = replace_once(text, old, new, "memory constants")
path.write_text(text)


# DAO and repository bulk operations.
path = Path("app/src/main/java/app/arbor/chat/data/Daos.kt")
text = path.read_text()
text = replace_once(
    text,
    '''    @Query("UPDATE memories SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Query("UPDATE memories SET enabled = :enabled, updatedAt = :now")
''',
    '''    @Query("UPDATE memories SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long): Int

    @Query("UPDATE memories SET enabled = :enabled, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setEnabled(ids: List<String>, enabled: Boolean, now: Long): Int

    @Query("DELETE FROM memories WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>): Int

    @Query("UPDATE memories SET enabled = :enabled, updatedAt = :now")
''',
    "memory bulk DAO",
)
path.write_text(text)

path = Path("app/src/main/java/app/arbor/chat/chat/ChatRepository.kt")
text = path.read_text()
text = replace_once(
    text,
    '''    suspend fun setMemoryEnabled(id: String, enabled: Boolean) =
        database.memoryDao().setEnabled(id, enabled, System.currentTimeMillis())
    suspend fun setAllMemoriesEnabled(enabled: Boolean): Int =
''',
    '''    suspend fun setMemoryEnabled(id: String, enabled: Boolean) =
        database.memoryDao().setEnabled(id, enabled, System.currentTimeMillis())
    suspend fun setMemoriesEnabled(ids: Collection<String>, enabled: Boolean): Int {
        val distinctIds = ids.filter(String::isNotBlank).distinct()
        return if (distinctIds.isEmpty()) 0
        else database.memoryDao().setEnabled(distinctIds, enabled, System.currentTimeMillis())
    }
    suspend fun deleteMemories(ids: Collection<String>): Int {
        val distinctIds = ids.filter(String::isNotBlank).distinct()
        return if (distinctIds.isEmpty()) 0 else database.memoryDao().delete(distinctIds)
    }
    suspend fun setAllMemoriesEnabled(enabled: Boolean): Int =
''',
    "repository bulk memory",
)
path.write_text(text)


# View-model page entry state and bulk memory actions.
path = Path("app/src/main/java/app/arbor/chat/ui/ChatViewModel.kt")
text = path.read_text()
text = replace_once(
    text,
    'import kotlinx.coroutines.flow.stateIn\n',
    'import kotlinx.coroutines.flow.stateIn\nimport kotlinx.coroutines.flow.update\n',
    "flow update import",
)
text = replace_once(
    text,
    '''    val settingsRoute = savedStateHandle.getMutableStateFlow("settings_route", restoredUiState.settingsRoute)
    val searchQuery = savedStateHandle.getMutableStateFlow("search_query", restoredUiState.searchQuery)
''',
    '''    val settingsRoute = savedStateHandle.getMutableStateFlow("settings_route", restoredUiState.settingsRoute)
    val settingsPageRevisions = MutableStateFlow<Map<SettingsRoute, Long>>(emptyMap())
    val searchQuery = savedStateHandle.getMutableStateFlow("search_query", restoredUiState.searchQuery)
''',
    "settings revisions state",
)
text = replace_once(
    text,
    '''    fun settingsScrollOffset(route: SettingsRoute): Int =
        synchronized(latestSettingsScrollOffsets) {
            latestSettingsScrollOffsets[route]
        } ?: container.persistentUiState.settingsScroll(route)

    fun saveSettingsScrollOffset(route: SettingsRoute, offset: Int) {
''',
    '''    fun settingsScrollOffset(route: SettingsRoute): Int =
        synchronized(latestSettingsScrollOffsets) {
            latestSettingsScrollOffsets[route]
        } ?: container.persistentUiState.settingsScroll(route)

    fun openSettingsRoute(route: SettingsRoute) {
        settingsPageRevisions.update { revisions ->
            revisions + (route to ((revisions[route] ?: 0L) + 1L))
        }
        synchronized(latestSettingsScrollOffsets) {
            latestSettingsScrollOffsets[route] = 0
        }
        container.persistentUiState.saveSettingsScroll(route, 0)
        settingsRoute.value = route
    }

    fun openSettingsHome() {
        openSettingsRoute(SettingsRoute.HOME)
        screen.value = Screen.SETTINGS
    }

    fun saveSettingsScrollOffset(route: SettingsRoute, offset: Int) {
''',
    "settings navigation methods",
)
text = replace_once(
    text,
    '''    fun openProviderSetup() {
        providerSetupRequested.value = true
        screen.value = Screen.SETTINGS
    }
''',
    '''    fun openProviderSetup() {
        openSettingsRoute(SettingsRoute.PROVIDERS)
        providerSetupRequested.value = true
        screen.value = Screen.SETTINGS
    }
''',
    "provider settings entry",
)
text = replace_once(
    text,
    '''    fun setAllMemoriesEnabled(enabled: Boolean) = launchAction {
        container.repository.setAllMemoriesEnabled(enabled)
    }
    fun deleteDisabledMemories() = launchAction {
''',
    '''    fun setAllMemoriesEnabled(enabled: Boolean) = launchAction {
        container.repository.setAllMemoriesEnabled(enabled)
    }
    fun setMemoriesEnabled(ids: Set<String>, enabled: Boolean) = launchAction {
        container.repository.setMemoriesEnabled(ids, enabled)
    }
    fun deleteMemories(ids: Set<String>) = launchAction {
        container.repository.deleteMemories(ids)
    }
    fun deleteDisabledMemories() = launchAction {
''',
    "view model bulk memory",
)
path.write_text(text)


# Settings UI, scroll restoration, and title synchronization.
path = Path("app/src/main/java/app/arbor/chat/ui/SettingsScreen.kt")
text = path.read_text()
text = replace_once(text, 'import androidx.compose.material3.TopAppBarDefaults\n', 'import androidx.compose.material3.TopAppBarDefaults\nimport androidx.compose.material3.TopAppBarState\n', "top app bar import")
text = replace_once(text, 'import androidx.compose.runtime.getValue\n', 'import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.key\n', "key import")
text = replace_once(
    text,
    '''private val LocalSettingsScaffoldPadding = compositionLocalOf { PaddingValues() }
private val LocalSettingsRoute = compositionLocalOf { SettingsRoute.HOME }
private val LocalSettingsViewModel = compositionLocalOf<ChatViewModel?> { null }
''',
    '''private val LocalSettingsScaffoldPadding = compositionLocalOf { PaddingValues() }
private val LocalSettingsRoute = compositionLocalOf { SettingsRoute.HOME }
private val LocalSettingsViewModel = compositionLocalOf<ChatViewModel?> { null }
private val LocalSettingsTopAppBarState = compositionLocalOf<TopAppBarState?> { null }
private val LocalSettingsPageRevision = compositionLocalOf { 0L }
''',
    "settings locals",
)
text = replace_once(
    text,
    '    val route by viewModel.settingsRoute.collectAsState()\n    val haptics = rememberArborHaptics()\n',
    '    val route by viewModel.settingsRoute.collectAsState()\n    val pageRevisions by viewModel.settingsPageRevisions.collectAsState()\n    val haptics = rememberArborHaptics()\n',
    "settings revision collection",
)
text = replace_once(text, '            viewModel.settingsRoute.value = SettingsRoute.PROVIDERS\n', '            viewModel.openSettingsRoute(SettingsRoute.PROVIDERS)\n', "provider route open")
text = replace_once(
    text,
    '''                    LocalSettingsScaffoldPadding provides padding,
                    LocalSettingsRoute provides currentRoute,
                    LocalSettingsViewModel provides viewModel,
''',
    '''                    LocalSettingsScaffoldPadding provides padding,
                    LocalSettingsRoute provides currentRoute,
                    LocalSettingsViewModel provides viewModel,
                    LocalSettingsTopAppBarState provides scrollBehavior.state,
                    LocalSettingsPageRevision provides (pageRevisions[currentRoute] ?: 0L),
''',
    "settings local providers",
)
text = replace_once(text, '                            onOpen = { viewModel.settingsRoute.value = it },\n', '                            onOpen = viewModel::openSettingsRoute,\n', "settings destination open")
text = replace_once(text, '                            onOpenDeveloper = { viewModel.settingsRoute.value = SettingsRoute.DEVELOPER },\n                            onOpenLicenses = { viewModel.settingsRoute.value = SettingsRoute.LICENSES },\n', '                            onOpenDeveloper = { viewModel.openSettingsRoute(SettingsRoute.DEVELOPER) },\n                            onOpenLicenses = { viewModel.openSettingsRoute(SettingsRoute.LICENSES) },\n', "about nested routes")
old_settings_page = '''@Composable
internal fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    val scaffoldPadding = LocalSettingsScaffoldPadding.current
    val route = LocalSettingsRoute.current
    val viewModel = LocalSettingsViewModel.current
    val scrollState = rememberScrollState(initial = viewModel?.settingsScrollOffset(route) ?: 0)
    LaunchedEffect(route, scrollState, viewModel) {
        val target = viewModel ?: return@LaunchedEffect
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collect { target.saveSettingsScrollOffset(route, it) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = scaffoldPadding.calculateTopPadding() + 20.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}
'''
new_settings_page = '''internal fun settingsTopBarHeightOffset(scrollOffset: Int, heightOffsetLimit: Float): Float =
    if (heightOffsetLimit >= 0f) 0f
    else (-scrollOffset.coerceAtLeast(0).toFloat()).coerceIn(heightOffsetLimit, 0f)

@Composable
internal fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    val scaffoldPadding = LocalSettingsScaffoldPadding.current
    val route = LocalSettingsRoute.current
    val viewModel = LocalSettingsViewModel.current
    val topAppBarState = LocalSettingsTopAppBarState.current
    val revision = LocalSettingsPageRevision.current
    key(revision) {
        val scrollState = rememberScrollState(initial = viewModel?.settingsScrollOffset(route) ?: 0)
        LaunchedEffect(route, scrollState, viewModel) {
            val target = viewModel ?: return@LaunchedEffect
            snapshotFlow { scrollState.value }
                .distinctUntilChanged()
                .collect { target.saveSettingsScrollOffset(route, it) }
        }
        LaunchedEffect(route, scrollState, topAppBarState) {
            val state = topAppBarState ?: return@LaunchedEffect
            snapshotFlow { scrollState.value to state.heightOffsetLimit }
                .distinctUntilChanged()
                .collect { (offset, limit) ->
                    state.heightOffset = settingsTopBarHeightOffset(offset, limit)
                    state.contentOffset = -offset.coerceAtLeast(0).toFloat()
                }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = scaffoldPadding.calculateTopPadding() + 20.dp,
                    bottom = scaffoldPadding.calculateBottomPadding() + 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}
'''
text = replace_once(text, old_settings_page, new_settings_page, "settings page")
memory_start = '@Composable\nprivate fun MemorySettingsPage('
memory_end = '\n@Composable\nprivate fun AppearanceSettingsPage('
new_memory = '''private enum class MemoryStatusFilter(val label: String) {
    ALL("All"), ENABLED("Enabled"), DISABLED("Disabled")
}

private enum class MemorySortOrder(val label: String) {
    UPDATED("Recently updated"), CREATED("Recently created"), CATEGORY("Category")
}

@Composable
private fun MemorySettingsPage(
    automation: AutomationSettingsEntity,
    memories: List<MemoryEntity>,
    viewModel: ChatViewModel,
) = SettingsPage {
    var draft by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("general") }
    var memorySearch by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    var editCategory by rememberSaveable { mutableStateOf("general") }
    var statusFilter by remember { mutableStateOf(MemoryStatusFilter.ALL) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf(MemorySortOrder.UPDATED) }
    var categoryMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>?>(null) }
    var deleteDisabledPending by remember { mutableStateOf(false) }
    val categories = remember(memories) { memories.map(MemoryEntity::category).distinct().sortedBy(String::lowercase) }
    val visibleMemories = remember(memories, memorySearch, statusFilter, categoryFilter, sortOrder) {
        val query = memorySearch.trim()
        memories.asSequence()
            .filter { memory -> query.isBlank() || memory.content.contains(query, true) || memory.category.contains(query, true) }
            .filter { memory ->
                when (statusFilter) {
                    MemoryStatusFilter.ALL -> true
                    MemoryStatusFilter.ENABLED -> memory.enabled
                    MemoryStatusFilter.DISABLED -> !memory.enabled
                }
            }
            .filter { memory -> categoryFilter == null || memory.category == categoryFilter }
            .let { sequence ->
                when (sortOrder) {
                    MemorySortOrder.UPDATED -> sequence.sortedByDescending(MemoryEntity::updatedAt)
                    MemorySortOrder.CREATED -> sequence.sortedByDescending(MemoryEntity::createdAt)
                    MemorySortOrder.CATEGORY -> sequence.sortedWith(
                        compareBy<MemoryEntity> { it.category.lowercase() }.thenBy { it.content.lowercase() },
                    )
                }
            }
            .toList()
    }
    LaunchedEffect(memories) {
        val existing = memories.mapTo(mutableSetOf(), MemoryEntity::id)
        selectedIds = selectedIds.intersect(existing)
        if (categoryFilter != null && categoryFilter !in categories) categoryFilter = null
    }

    SectionTitle(
        "Memory",
        "Arbor stores memories in its encrypted local database and selects only relevant items under a strict context budget. Disabled memories remain stored but are not supplied to models.",
    )
    ListItem(
        headlineContent = { Text("Use memory") },
        supportingContent = { Text("Expose selected enabled memories to chats and allow memory tools") },
        trailingContent = {
            Switch(
                checked = automation.memoryEnabled,
                onCheckedChange = { enabled -> viewModel.updateAutomationSettings { it.copy(memoryEnabled = enabled) } },
            )
        },
    )
    ListItem(
        headlineContent = { Text("Automatic memory") },
        supportingContent = { Text("Allow models to save stable, non-sensitive details; duplicate items are merged") },
        trailingContent = {
            Switch(
                checked = automation.memoryAutoSave,
                enabled = automation.memoryEnabled,
                onCheckedChange = { enabled -> viewModel.updateAutomationSettings { it.copy(memoryAutoSave = enabled) } },
            )
        },
    )
    HorizontalDivider()
    SectionTitle("Add memory", "Manual memories are available immediately and use the same deduplication rules.")
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text("Memory") },
        minLines = 2,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = category,
        onValueChange = { category = it },
        label = { Text("Category") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        enabled = draft.isNotBlank(),
        onClick = {
            viewModel.addMemory(draft, category)
            draft = ""
        },
    ) { Text("Save memory") }

    HorizontalDivider()
    SectionTitle(
        "Saved memories",
        if (memories.isEmpty()) "Nothing is stored yet."
        else "${memories.size} saved item${if (memories.size == 1) "" else "s"}; ${memories.count { it.enabled }} enabled.",
    )
    OutlinedTextField(
        value = memorySearch,
        onValueChange = { memorySearch = it },
        label = { Text("Search memories") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MemoryStatusFilter.entries.forEach { option ->
            FilterChip(
                selected = statusFilter == option,
                onClick = { statusFilter = option },
                label = { Text(option.label) },
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(categoryFilter ?: "All categories", maxLines = 1)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
            }
            ArborDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                DropdownMenuItem(text = { Text("All categories") }, onClick = { categoryFilter = null; categoryMenu = false })
                categories.forEach { value ->
                    DropdownMenuItem(text = { Text(value) }, onClick = { categoryFilter = value; categoryMenu = false })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { sortMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(sortOrder.label, maxLines = 1)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
            }
            ArborDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                MemorySortOrder.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { sortOrder = option; sortMenu = false })
                }
            }
        }
    }

    if (selectedIds.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${selectedIds.size} selected", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { viewModel.setMemoriesEnabled(selectedIds, true); selectedIds = emptySet() }) { Text("Enable") }
                    TextButton(onClick = { viewModel.setMemoriesEnabled(selectedIds, false); selectedIds = emptySet() }) { Text("Disable") }
                    TextButton(onClick = { pendingDeleteIds = selectedIds }) { Text("Delete") }
                    TextButton(onClick = { selectedIds = emptySet() }) { Text("Clear") }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = visibleMemories.isNotEmpty(),
                onClick = { selectedIds = visibleMemories.mapTo(linkedSetOf(), MemoryEntity::id) },
            ) { Text("Select shown") }
            TextButton(
                enabled = memories.any { !it.enabled },
                onClick = { deleteDisabledPending = true },
            ) { Text("Delete disabled") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(enabled = memories.any { !it.enabled }, onClick = { viewModel.setAllMemoriesEnabled(true) }) { Text("Enable all") }
            TextButton(enabled = memories.any { it.enabled }, onClick = { viewModel.setAllMemoriesEnabled(false) }) { Text("Disable all") }
        }
    }

    if (visibleMemories.isEmpty()) {
        Text(
            if (memories.isEmpty()) "No memories saved yet." else "No memories match the current filters.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    visibleMemories.forEach { memory ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (editingId == memory.id) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        label = { Text("Memory") },
                        minLines = 2,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editCategory,
                        onValueChange = { editCategory = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = editText.isNotBlank(),
                            onClick = {
                                viewModel.updateMemory(memory.id, editText, editCategory)
                                editingId = null
                            },
                        ) { Text("Save") }
                        TextButton(onClick = { editingId = null }) { Text("Cancel") }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(memory.content, style = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = memory.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + memory.id else selectedIds - memory.id
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                memory.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(
                                "${if (memory.sourceConversationId == null) "Manual" else "From chat"} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(memory.updatedAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        IconButton(onClick = {
                            editingId = memory.id
                            editText = memory.content
                            editCategory = memory.category
                        }) { Icon(Icons.Outlined.Edit, "Edit memory") }
                        Switch(
                            checked = memory.enabled,
                            onCheckedChange = { viewModel.setMemoryEnabled(memory.id, it) },
                        )
                        IconButton(onClick = { pendingDeleteIds = setOf(memory.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, "Delete memory")
                        }
                    }
                }
            }
        }
    }

    pendingDeleteIds?.let { ids ->
        ArborAlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            title = { Text(if (ids.size == 1) "Delete memory?" else "Delete ${ids.size} memories?") },
            text = { Text("This permanently removes the selected memory data from Arbor.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteMemories(ids)
                    selectedIds = selectedIds - ids
                    pendingDeleteIds = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteIds = null }) { Text("Cancel") } },
        )
    }
    if (deleteDisabledPending) {
        val count = memories.count { !it.enabled }
        ArborAlertDialog(
            onDismissRequest = { deleteDisabledPending = false },
            title = { Text("Delete $count disabled memor${if (count == 1) "y" else "ies"}?") },
            text = { Text("Disabled memories are currently excluded from chats. This cleanup permanently removes them.") },
            confirmButton = {
                Button(onClick = { viewModel.deleteDisabledMemories(); deleteDisabledPending = false }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteDisabledPending = false }) { Text("Cancel") } },
        )
    }
    Spacer(Modifier.padding(bottom = 24.dp))
}
'''
text = replace_between(text, memory_start, memory_end, new_memory, "memory settings page")
path.write_text(text)


# Chat app-bar state follows actual list position.
path = Path("app/src/main/java/app/arbor/chat/ui/ChatScreen.kt")
text = path.read_text()
text = replace_once(
    text,
    '''internal fun calculateTopChromeProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    startPx: Int,
    endPx: Int,
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    if (endPx <= startPx) return if (firstVisibleItemScrollOffset > startPx) 1f else 0f
    return ((firstVisibleItemScrollOffset - startPx).toFloat() / (endPx - startPx).toFloat()).coerceIn(0f, 1f)
}
''',
    '''internal fun calculateTopChromeProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    startPx: Int,
    endPx: Int,
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    if (endPx <= startPx) return if (firstVisibleItemScrollOffset > startPx) 1f else 0f
    return ((firstVisibleItemScrollOffset - startPx).toFloat() / (endPx - startPx).toFloat()).coerceIn(0f, 1f)
}

internal fun chatTopBarHeightOffsetForScroll(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    startPx: Int,
    endPx: Int,
    heightOffsetLimit: Float,
): Float = heightOffsetLimit * calculateTopChromeProgress(
    firstVisibleItemIndex,
    firstVisibleItemScrollOffset,
    startPx,
    endPx,
)
''',
    "chat top bar helper",
)
old = '''        topAppBarState.contentOffset = 0f
        topAppBarState.heightOffset = 0f

        val collapsedOffset = snapshotFlow { topAppBarState.heightOffsetLimit }
            .first { it < 0f }
        topAppBarState.heightOffset = savedScroll?.topBarHeightOffset
            ?.coerceIn(collapsedOffset, 0f)
            ?: collapsedOffset
    }
'''
new = '''        topAppBarState.contentOffset = 0f
        topAppBarState.heightOffset = 0f
    }
'''
text = replace_once(text, old, new, "chat stale app bar restore")
anchor = '''    LaunchedEffect(messageListState, conversation?.id, topAppBarState, initialPositioned) {
        if (!initialPositioned) return@LaunchedEffect
        val conversationId = conversation?.id ?: return@LaunchedEffect
'''
sync_effect = '''    LaunchedEffect(messageListState, conversation?.id, topAppBarState, initialPositioned, chromeStartPx, chromeEndPx) {
        if (!initialPositioned) return@LaunchedEffect
        snapshotFlow {
            Triple(
                messageListState.firstVisibleItemIndex,
                messageListState.firstVisibleItemScrollOffset,
                topAppBarState.heightOffsetLimit,
            )
        }
            .distinctUntilChanged()
            .collect { (index, offset, limit) ->
                if (limit < 0f) {
                    topAppBarState.heightOffset = chatTopBarHeightOffsetForScroll(
                        firstVisibleItemIndex = index,
                        firstVisibleItemScrollOffset = offset,
                        startPx = chromeStartPx,
                        endPx = chromeEndPx,
                        heightOffsetLimit = limit,
                    ).coerceIn(limit, 0f)
                    topAppBarState.contentOffset = -(index * chromeEndPx + offset).toFloat()
                }
            }
    }

'''
if anchor not in text:
    raise SystemExit("chat persistence anchor missing")
text = text.replace(anchor, sync_effect + anchor, 1)
path.write_text(text)


# Drawer predictive-back physics and ownership.
path = Path("app/src/main/java/app/arbor/chat/ui/DrawerPhysics.kt")
text = path.read_text()
text = replace_once(
    text,
    '''    fun dragOffset(startOffsetPx: Float, accumulatedDragPx: Float, drawerWidthPx: Float): Float =
        (startOffsetPx + accumulatedDragPx).coerceIn(0f, drawerWidthPx.coerceAtLeast(0f))

    fun settleTarget(
''',
    '''    fun dragOffset(startOffsetPx: Float, accumulatedDragPx: Float, drawerWidthPx: Float): Float =
        (startOffsetPx + accumulatedDragPx).coerceIn(0f, drawerWidthPx.coerceAtLeast(0f))

    fun predictiveBackOffset(startOffsetPx: Float, progress: Float): Float =
        startOffsetPx.coerceAtLeast(0f) * (1f - progress.coerceIn(0f, 1f))

    fun settleTarget(
''',
    "drawer predictive physics",
)
path.write_text(text)

path = Path("app/src/main/java/app/arbor/chat/ui/InteractiveNavigationDrawer.kt")
text = path.read_text()
text = replace_once(
    text,
    '''    private var visibleState by mutableStateOf(false)

    /**
''',
    '''    private var visibleState by mutableStateOf(false)
    private var predictiveBackActive by mutableStateOf(false)
    private var predictiveBackStartOffsetPx = 0f

    /**
''',
    "drawer predictive state",
)
text = replace_once(
    text,
    '''    val isVisible: Boolean get() = visibleState
    val isClosed: Boolean get() = !visibleState && !animationRunning
''',
    '''    val isVisible: Boolean get() = visibleState
    val claimsBack: Boolean get() = visibleState || predictiveBackActive
    val isClosed: Boolean get() = !visibleState && !animationRunning && !predictiveBackActive
''',
    "drawer claims back",
)
text = replace_once(text, '        val nextVisible = next > 0.5f\n', '        val nextVisible = next > 0.01f\n', "drawer visible threshold")
text = replace_once(
    text,
    '''    fun open() = animateTo(DrawerAnchor.OPEN)
    fun close() = animateTo(DrawerAnchor.CLOSED)

    private fun animateTo(
''',
    '''    fun open() = animateTo(DrawerAnchor.OPEN)
    fun close() = animateTo(DrawerAnchor.CLOSED)

    fun beginPredictiveBack() {
        stop()
        predictiveBackStartOffsetPx = offsetPx
        predictiveBackActive = true
    }

    fun updatePredictiveBack(progress: Float) {
        if (!predictiveBackActive) return
        updateOffset(DrawerPhysics.predictiveBackOffset(predictiveBackStartOffsetPx, progress))
    }

    fun commitPredictiveBack() {
        updateOffset(0f)
        predictiveBackActive = false
        predictiveBackStartOffsetPx = 0f
    }

    fun cancelPredictiveBack() {
        val restoreOffset = predictiveBackStartOffsetPx.coerceIn(0f, widthPx)
        predictiveBackActive = false
        predictiveBackStartOffsetPx = 0f
        animateToOffset(restoreOffset)
    }

    private fun animateTo(
''',
    "drawer predictive methods",
)
old = '''        animationJob = scope.launch {
            try {
                Animatable(offsetPx).apply { updateBounds(0f, widthPx) }.animateTo(
                    targetValue = if (anchor == DrawerAnchor.OPEN) widthPx else 0f,
                    animationSpec = spring(
                        dampingRatio = 0.84f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialVelocity = initialVelocityPxPerSecond,
                ) { updateOffset(value) }
            } finally {
                animationRunning = false
            }
        }
    }
}
'''
new = '''        animateToOffset(
            targetOffsetPx = if (anchor == DrawerAnchor.OPEN) widthPx else 0f,
            initialVelocityPxPerSecond = initialVelocityPxPerSecond,
        )
    }

    private fun animateToOffset(
        targetOffsetPx: Float,
        initialVelocityPxPerSecond: Float = 0f,
    ) {
        stop()
        animationRunning = true
        animationJob = scope.launch {
            try {
                Animatable(offsetPx).apply { updateBounds(0f, widthPx) }.animateTo(
                    targetValue = targetOffsetPx.coerceIn(0f, widthPx),
                    animationSpec = spring(
                        dampingRatio = 0.84f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialVelocity = initialVelocityPxPerSecond,
                ) { updateOffset(value) }
            } finally {
                animationRunning = false
            }
        }
    }
}
'''
text = replace_once(text, old, new, "drawer animation refactor")
path.write_text(text)

path = Path("app/src/main/java/app/arbor/chat/ui/ArborApp.kt")
text = path.read_text()
text = replace_once(text, 'import androidx.activity.compose.BackHandler\n', 'import androidx.activity.compose.PredictiveBackHandler\n', "predictive back import")
text = replace_once(text, 'import kotlinx.coroutines.delay\n', 'import kotlinx.coroutines.CancellationException\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.flow.collect\n', "drawer coroutine imports")
text = replace_once(
    text,
    '''    val drawerVisible = drawerState.isVisible
    BackHandler(enabled = drawerVisible) { drawerState.close() }
''',
    '''    val drawerVisible = drawerState.isVisible
    val drawerClaimsBack = drawerState.claimsBack
    PredictiveBackHandler(enabled = drawerClaimsBack) { events ->
        drawerState.beginPredictiveBack()
        try {
            events.collect { event -> drawerState.updatePredictiveBack(event.progress) }
            drawerState.commitPredictiveBack()
        } catch (cancelled: CancellationException) {
            drawerState.cancelPredictiveBack()
            throw cancelled
        }
    }
''',
    "drawer predictive handler",
)
text = replace_once(text, '                backEnabled = pageBackEnabled(drawerVisible),\n', '                backEnabled = pageBackEnabled(drawerClaimsBack),\n', "page back drawer ownership")
text = text.replace('                    onScreen = { viewModel.screen.value = it },\n', '                    onScreen = { destination ->\n                        if (destination == Screen.SETTINGS) viewModel.openSettingsHome()\n                        else viewModel.screen.value = destination\n                    },\n', 1)
text = text.replace('                            onScreen = { viewModel.screen.value = it; drawerState.close() },\n', '                            onScreen = { destination ->\n                                if (destination == Screen.SETTINGS) viewModel.openSettingsHome()\n                                else viewModel.screen.value = destination\n                                drawerState.close()\n                            },\n', 1)
path.write_text(text)


# Tests.
path = Path("app/src/test/java/app/arbor/chat/chat/MemoryManagementTest.kt")
text = path.read_text()
insert = '''
    @Test fun exactDuplicateCanMoveAcrossCategoriesWithoutCreatingAnotherItem() {
        val existing = memory("one", "User prefers compact answers", category = "general")
        val duplicate = MemoryManagement.findDuplicate(
            memories = listOf(existing),
            content = "User prefers compact answers.",
            category = "preferences",
        )
        assertEquals("one", duplicate?.id)
    }

    @Test fun unrelatedSmallLibraryIsNotInjectedWholesale() {
        val selected = MemoryManagement.selectForContext(
            memories = listOf(memory("unrelated", "The user's bicycle is red", category = "general")),
            messagesNewestFirst = listOf(userMessage("Explain Kotlin coroutine cancellation")),
            currentConversationId = null,
        )
        assertTrue(selected.isEmpty())
    }

    @Test fun baselineProfileMemoryRemainsAvailableWithoutKeywordOverlap() {
        val selected = MemoryManagement.selectForContext(
            memories = listOf(memory("language", "Prefers replies in Turkish", category = "language")),
            messagesNewestFirst = listOf(userMessage("Explain coroutine cancellation")),
            currentConversationId = null,
        )
        assertEquals(listOf("language"), selected.map { it.id })
    }
'''
marker = '    private fun memory(\n'
if marker not in text:
    raise SystemExit("memory test insertion marker missing")
text = text.replace(marker, insert + '\n' + marker, 1)
path.write_text(text)

nav_test = Path("app/src/test/java/app/arbor/chat/ui/MemoryNavigationBehaviorTest.kt")
nav_test.write_text('''package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryNavigationBehaviorTest {
    @Test fun settingsTitleMatchesRestoredScroll() {
        assertEquals(0f, settingsTopBarHeightOffset(0, -120f))
        assertEquals(-40f, settingsTopBarHeightOffset(40, -120f))
        assertEquals(-120f, settingsTopBarHeightOffset(500, -120f))
    }

    @Test fun chatTitleCollapsesFromActualListPosition() {
        assertEquals(0f, chatTopBarHeightOffsetForScroll(0, 20, 56, 176, -100f))
        assertEquals(-100f, chatTopBarHeightOffsetForScroll(1, 0, 56, 176, -100f))
    }

    @Test fun predictiveBackTracksDrawerAndPageWaits() {
        assertEquals(300f, DrawerPhysics.predictiveBackOffset(300f, 0f))
        assertEquals(150f, DrawerPhysics.predictiveBackOffset(300f, .5f))
        assertEquals(0f, DrawerPhysics.predictiveBackOffset(300f, 1f))
        assertFalse(pageBackEnabled(true))
        assertTrue(pageBackEnabled(false))
    }
}
''')
