package app.arbor.chat.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.MessageStatus
import app.arbor.chat.data.AttachmentEntity
import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ReasoningVisibility
import app.arbor.chat.data.SendMode
import app.arbor.chat.data.ThinkingEffort
import app.arbor.chat.agent.ToolTraceEvent
import app.arbor.chat.provider.ThinkingLevelOption
import app.arbor.chat.provider.supportedThinkingLevels
import app.arbor.chat.agent.MessageTimelineEvent
import app.arbor.chat.agent.groupOrderedTimeline
import app.arbor.chat.sandbox.ExecutionResult
import coil.compose.AsyncImage
import app.arbor.chat.sandbox.UbuntuExecutionResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private val ChatMessageJson = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val allProviders by viewModel.providers.collectAsStateWithLifecycle()
    val credentialRevision by viewModel.credentialRevision.collectAsStateWithLifecycle()
    val usableProviders = remember(allProviders, credentialRevision) { viewModel.configuredProviders(allProviders) }
    val recoverable by viewModel.recoverable.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val generating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val revisionHistory by viewModel.revisionHistory.collectAsStateWithLifecycle()
    val contextSummary by viewModel.contextSummary.collectAsStateWithLifecycle()
    val paging = viewModel.messages.collectAsLazyPagingItems()
    val focusedMessageNodeId by viewModel.focusedMessageNodeId.collectAsState()
    var modelMenu by remember { mutableStateOf(false) }
    var chatMenu by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showChatConfiguration by remember { mutableStateOf(false) }
    val messageListState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val scrollScope = rememberCoroutineScope()
    val latestThresholdPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    var followLatest by remember(conversation?.id) { mutableStateOf(true) }
    var searchFocusHandled by remember(conversation?.id, focusedMessageNodeId) { mutableStateOf(false) }
    val isAtLatest by remember(messageListState, latestThresholdPx) {
        derivedStateOf {
            messageListState.layoutInfo.totalItemsCount == 0 ||
                (messageListState.firstVisibleItemIndex == 0 &&
                    messageListState.firstVisibleItemScrollOffset <= latestThresholdPx)
        }
    }
    val composerChromeProgress by remember(messageListState, latestThresholdPx) {
        derivedStateOf {
            when {
                messageListState.firstVisibleItemIndex > 0 -> 1f
                latestThresholdPx <= 0 -> 0f
                else -> (messageListState.firstVisibleItemScrollOffset / latestThresholdPx.toFloat()).coerceIn(0f, 1f)
            }
        }
    }
    val latestMessage = paging.itemSnapshotList.items.firstOrNull()

    LaunchedEffect(messageListState, conversation?.id, latestThresholdPx) {
        snapshotFlow {
            Triple(
                messageListState.isScrollInProgress,
                messageListState.firstVisibleItemIndex,
                messageListState.firstVisibleItemScrollOffset,
            )
        }.collect { (scrolling, index, offset) ->
            val nearLatest = messageListState.layoutInfo.totalItemsCount == 0 ||
                (index == 0 && offset <= latestThresholdPx)
            if (scrolling || nearLatest) followLatest = nearLatest
        }
    }

    LaunchedEffect(
        conversation?.id,
        paging.itemCount,
        latestMessage?.nodeId,
        latestMessage?.updatedAt,
        followLatest,
    ) {
        if (followLatest && paging.itemCount > 0) messageListState.scrollToItem(0)
    }

    LaunchedEffect(focusedMessageNodeId, paging.itemSnapshotList.items.map { it.nodeId }, searchFocusHandled) {
        val target = focusedMessageNodeId ?: return@LaunchedEffect
        if (!searchFocusHandled) {
            val index = paging.itemSnapshotList.items.indexOfFirst { it.nodeId == target }
            if (index >= 0) {
                followLatest = false
                messageListState.scrollToItem(index)
                searchFocusHandled = true
            }
        }
    }

    LaunchedEffect(conversation?.id, conversation?.updatedAt) {
        if (conversation != null) viewModel.markCurrentRead()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .matchParentSize()
                        .blur((8f + 14f * topAppBarState.collapsedFraction.coerceIn(0f, 1f)).dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = .72f + .20f * topAppBarState.collapsedFraction.coerceIn(0f, 1f)),
                                    MaterialTheme.colorScheme.surface.copy(alpha = .56f + .18f * topAppBarState.collapsedFraction.coerceIn(0f, 1f)),
                                    MaterialTheme.colorScheme.surface.copy(alpha = .18f + .18f * topAppBarState.collapsedFraction.coerceIn(0f, 1f)),
                                ),
                            ),
                        ),
                )
                LargeTopAppBar(
                navigationIcon = { if (openDrawer != null) IconButton(onClick = openDrawer) { Icon(Icons.Outlined.Menu, "Conversations") } },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            conversation?.title ?: "Arbor",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AnimatedVisibility(visible = topAppBarState.collapsedFraction < .72f) {
                        Box {
                            Surface(
                                onClick = { modelMenu = true },
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f),
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Psychology, null, Modifier.size(14.dp))
                                    Text(
                                        buildString {
                                            val provider = usableProviders.firstOrNull { it.id == conversation?.selectedProviderId }
                                            if (provider != null && usableProviders.size > 1) append(provider.displayName).append(" · ")
                                            append(models.firstOrNull { it.modelId == conversation?.selectedModelId }?.displayName
                                                ?: conversation?.selectedModelId ?: "Choose model")
                                        },
                                        Modifier.padding(start = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            usableProviders.forEach { provider ->
                                ProviderModelMenuRows(provider, viewModel, conversation?.selectedProviderId, conversation?.selectedModelId) { providerId, modelId ->
                                    viewModel.selectModel(providerId, modelId)
                                    modelMenu = false
                                }
                            }
                            if (usableProviders.isEmpty()) DropdownMenuItem(text = { Text("Open the left menu → Settings to add a provider") }, onClick = { modelMenu = false })
                        }
                        }
                    }
                },
                actions = {
                    if (pending.isNotEmpty()) Badge { Text(pending.size.toString()) }
                    Box {
                        IconButton(onClick = { chatMenu = true }) { Icon(Icons.Outlined.MoreVert, "Chat actions") }
                        DropdownMenu(expanded = chatMenu, onDismissRequest = { chatMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Regenerate chat name") },
                                onClick = { viewModel.regenerateTitle(); chatMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Chat configuration") },
                                leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                                onClick = { showChatConfiguration = true; chatMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Edited message history (${revisionHistory.size})") },
                                leadingIcon = { Icon(Icons.Outlined.History, null) },
                                onClick = { showHistory = true; chatMenu = false },
                            )
                        }
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                )
            }
        },
        bottomBar = {
            Composer(
                viewModel = viewModel,
                provider = allProviders.firstOrNull { it.id == conversation?.selectedProviderId },
                model = models.firstOrNull {
                    it.providerId == conversation?.selectedProviderId && it.modelId == conversation?.selectedModelId
                },
                generating = generating,
                chromeProgress = composerChromeProgress,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (paging.itemCount == 0 && recoverable.isEmpty()) EmptyConversation()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = messageListState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 18.dp),
            ) {
                items(
                    count = paging.itemCount,
                    key = { index -> paging.peek(index)?.nodeId ?: index },
                    contentType = { index -> paging.peek(index)?.role },
                ) { index ->
                    paging[index]?.let { message ->
                        MessageCard(
                            message, viewModel,
                            conversation?.reasoningVisibility ?: ReasoningVisibility.SHOW_WHILE_WORKING,
                            models.firstOrNull { it.modelId == conversation?.selectedModelId },
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = paging.itemCount > 0 && !isAtLatest,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        followLatest = true
                        scrollScope.launch { messageListState.animateScrollToItem(0) }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Go to latest message")
                }
            }
            val interrupted = recoverable.firstOrNull { it.status == MessageStatus.INTERRUPTED || it.status == MessageStatus.ERROR }
            AnimatedVisibility(interrupted != null, modifier = Modifier.align(Alignment.TopCenter)) {
                interrupted?.let { message ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.large,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (message.status == MessageStatus.INTERRUPTED) "Response interrupted" else "Request failed", fontWeight = FontWeight.SemiBold)
                                Text(message.error.orEmpty(), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                            }
                            AssistChip(onClick = { viewModel.resume(message) }, label = { Text("Resume") })
                        }
                    }
                }
            }
        }
    }
    if (showHistory) ModalBottomSheet(onDismissRequest = { showHistory = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Edited message history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Superseded branches remain saved; they are not sent as active context.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(revisionHistory.size, key = { revisionHistory[it].nodeId }) { index ->
                    val message = revisionHistory[index]
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(message.role.name.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(message.content.ifBlank { "(empty response)" }, maxLines = 8, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (revisionHistory.isEmpty()) item { Text("No edited or retried branches yet.", Modifier.padding(12.dp)) }
            }
            Spacer(Modifier.size(20.dp))
        }
    }
    if (showChatConfiguration) {
        conversation?.let { current ->
            ChatConfigurationSheet(current, contextSummary, viewModel) { showChatConfiguration = false }
        } ?: run { showChatConfiguration = false }
    }
}

@Composable
private fun ProviderModelMenuRows(
    provider: ProviderEntity,
    viewModel: ChatViewModel,
    selectedProviderId: String?,
    selectedModelId: String?,
    onSelect: (String, String) -> Unit,
) {
    val models by viewModel.modelsFor(provider.id).collectAsStateWithLifecycle(initialValue = emptyList())
    Text(
        provider.displayName.uppercase(),
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    models.forEach { model ->
        DropdownMenuItem(
            text = {
                Column {
                    Text(if (provider.id == selectedProviderId && model.modelId == selectedModelId) "✓ ${model.displayName}" else model.displayName)
                    Text("${model.contextWindow / 1_000}K context", style = MaterialTheme.typography.labelSmall)
                }
            },
            onClick = { onSelect(provider.id, model.modelId) },
        )
    }
}

@Composable
private fun EmptyConversation() {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Arbor", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text("One native workspace for every model.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Text("Attach files, run local code, branch long chats, or hold Send to queue and steer.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageCard(message: app.arbor.chat.data.MessageEntity, viewModel: ChatViewModel, reasoningVisibility: ReasoningVisibility, activeModel: ModelEntity?) {
    val attachments by viewModel.run { containerAttachments(message.nodeId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val user = message.role == MessageRole.USER
    var editing by remember(message.nodeId) { mutableStateOf(false) }
    var editedText by remember(message.nodeId) { mutableStateOf(message.content) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = if (user) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium,
            color = if (user) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            modifier = Modifier.fillMaxWidth(if (user) .88f else 1f),
        ) {
            Column(Modifier.padding(if (user) 14.dp else 4.dp)) {
                val timeline = remember(message.timelineJson) {
                    runCatching { ChatMessageJson.decodeFromString<List<MessageTimelineEvent>>(message.timelineJson) }.getOrDefault(emptyList())
                }
                if (attachments.isNotEmpty() && (user || timeline.isEmpty())) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        attachments.forEach { attachment ->
                            val fallback = user && when {
                                attachment.mimeType.startsWith("image/") && attachment.mimeType != "image/svg+xml" -> activeModel?.supportsVision == false
                                attachment.mimeType == "application/pdf" -> activeModel?.supportsFiles == false
                                else -> false
                            }
                            AttachmentCard(
                                attachment = attachment,
                                modelUsesFallback = fallback,
                                allowOcr = user,
                                onEnableOcr = if (user) ({ viewModel.enableOcr(attachment) }) else null,
                            )
                        }
                    }
                }
                if (timeline.isNotEmpty()) {
                    OrderedMessageTimeline(message.nodeId, timeline, attachments, message.status == MessageStatus.STREAMING, reasoningVisibility, viewModel)
                } else {
                    LegacyWorkingBlock(message.reasoning, message.toolTraceJson, message.status == MessageStatus.STREAMING, reasoningVisibility)
                    if (message.content.isNotBlank()) RichMessage(
                        operationScope = message.nodeId,
                        text = message.content,
                        streaming = message.status == MessageStatus.STREAMING,
                        onRunPython = viewModel::executePython,
                        onRunUbuntu = viewModel::executeUbuntu,
                        onReviewPythonPackages = viewModel::reviewPythonPackages,
                        onInstallPackages = viewModel::installPythonPackagesAndContinue,
                        onReviewUbuntuPackages = viewModel::reviewUbuntuPackages,
                        onInstallUbuntuPackages = viewModel::installUbuntuPackagesAndContinue,
                        onWidgetSubmit = viewModel::submitWidgetResponse,
                        onReviewWidgetSecurity = viewModel::reviewWidgetSecurity,
                    )
                }
                if (message.status == MessageStatus.STREAMING && message.content.isBlank()) {
                    StreamingTokenPulse(visible = true, label = "Working")
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    val tokens = message.inputTokens + message.outputTokens
                    val cost = message.costMicros / 1_000_000.0
                    Text(
                        buildString {
                            if (!message.modelId.isNullOrBlank()) append(message.modelId)
                            if (tokens > 0) append(" • $tokens tok")
                            when {
                                message.costKnown -> append(" • $").append("%.5f".format(cost))
                                cost > 0 -> append(" • $").append("%.5f".format(cost)).append(" partial")
                                tokens > 0 -> append(" • cost unavailable")
                            }
                            if (message.status !in setOf(MessageStatus.COMPLETE, MessageStatus.STREAMING)) append(" • ${message.status.name.lowercase()}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (user) {
                        IconButton(onClick = { editedText = message.content; editing = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Edit, "Edit message", Modifier.size(18.dp))
                        }
                    } else if (message.role == MessageRole.ASSISTANT && message.status != MessageStatus.STREAMING) {
                        IconButton(onClick = { viewModel.retryMessage(message) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Refresh, "Retry response", Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    if (editing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text("Edit message") },
        text = {
            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                minLines = 3,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = { AssistChip(onClick = { editing = false }, label = { Text("Cancel") }) },
        confirmButton = {
            Button(onClick = { viewModel.editMessage(message, editedText); editing = false }, enabled = editedText.isNotBlank()) {
                Text("Save & regenerate")
            }
        },
    )
}

@Composable
private fun OrderedMessageTimeline(
    messageKey: String,
    events: List<MessageTimelineEvent>,
    attachments: List<AttachmentEntity>,
    streaming: Boolean,
    visibility: ReasoningVisibility,
    viewModel: ChatViewModel,
) {
    val orderedEvents = remember(events, attachments) {
        val explicitAttachmentIds = events.filter { it.kind == "file" }.map { it.output }.toSet()
        val synthetic = attachments.filterNot { it.id in explicitAttachmentIds }.map { attachment ->
            MessageTimelineEvent(
                id = "file-${attachment.id}", kind = "file", label = "Sent file",
                status = "complete", input = attachment.displayName, output = attachment.id,
                startedAt = attachment.createdAt, finishedAt = attachment.createdAt,
            )
        }
        (events + synthetic).sortedBy(MessageTimelineEvent::startedAt)
    }
    val segments = remember(orderedEvents) { groupOrderedTimeline(orderedEvents) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEachIndexed { index, segment ->
            if (segment.working) {
                TimelineWorkingBlock(segment.events, streaming, streaming && index == segments.lastIndex, visibility, viewModel)
            } else {
                segment.events.forEach { event ->
                    if (event.kind == "file") {
                        attachments.firstOrNull { it.id == event.output }?.let { attachment ->
                            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("FILE • ${event.label.ifBlank { "Sent file" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                AttachmentCard(attachment, allowOcr = false)
                            }
                        }
                    } else if (event.content.isNotBlank()) RichMessage(
                        operationScope = "$messageKey:${event.id}",
                        text = event.content,
                        streaming = streaming && index == segments.lastIndex && event == segment.events.lastOrNull(),
                        onRunPython = viewModel::executePython,
                        onRunUbuntu = viewModel::executeUbuntu,
                        onReviewPythonPackages = viewModel::reviewPythonPackages,
                        onInstallPackages = viewModel::installPythonPackagesAndContinue,
                        onReviewUbuntuPackages = viewModel::reviewUbuntuPackages,
                        onInstallUbuntuPackages = viewModel::installUbuntuPackagesAndContinue,
                        onWidgetSubmit = viewModel::submitWidgetResponse,
                        onReviewWidgetSecurity = viewModel::reviewWidgetSecurity,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineWorkingBlock(events: List<MessageTimelineEvent>, streaming: Boolean, active: Boolean, visibility: ReasoningVisibility, viewModel: ChatViewModel) {
    val initiallyExpanded = when (visibility) {
        ReasoningVisibility.ALWAYS -> true
        ReasoningVisibility.SHOW_WHILE_WORKING -> streaming
        ReasoningVisibility.COLLAPSED -> false
    }
    if (events.isEmpty()) return
    var expanded by remember(events.first().id, visibility, streaming) { mutableStateOf(initiallyExpanded) }
    Surface(
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (events.size == 1 && events.first().kind == "reasoning") "Working" else "Working • ${events.size} steps",
                    Modifier.padding(start = 8.dp).weight(1f),
                    fontWeight = FontWeight.Medium,
                )
                Text(if (expanded) "Collapse" else "Expand", style = MaterialTheme.typography.labelMedium)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    events.forEachIndexed { index, event ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val duration = event.finishedAt?.let { (it - event.startedAt).coerceAtLeast(0) }
                            Text(
                                buildString {
                                    append(index + 1).append(". ")
                                    append(event.label.ifBlank { if (event.kind == "reasoning") "Reasoning" else event.kind.replaceFirstChar(Char::uppercase) })
                                    if (duration != null) append(" • ").append(duration).append(" ms")
                                    if (event.status == "error") append(" • error")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (event.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            if (event.content.isNotBlank()) Text(event.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (event.kind in setOf("python", "ubuntu", "search", "fetch")) ToolStepDetails(event.kind, event.input, event.output, event.status, viewModel)
                            else {
                                if (event.input.isNotBlank()) Text(event.input, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (event.output.isNotBlank()) GenericToolOutputCard(event.output, failed = event.status == "error")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyWorkingBlock(text: String, toolTraceJson: String, streaming: Boolean, visibility: ReasoningVisibility) {
    val traces = remember(toolTraceJson) {
        runCatching { ChatMessageJson.decodeFromString<List<ToolTraceEvent>>(toolTraceJson) }.getOrDefault(emptyList())
    }
    val hasContent = text.isNotBlank() || traces.isNotEmpty()
    val initiallyExpanded = when (visibility) {
        ReasoningVisibility.ALWAYS -> true
        ReasoningVisibility.SHOW_WHILE_WORKING -> streaming
        ReasoningVisibility.COLLAPSED -> false
    }
    if (!hasContent) return
    var expanded by remember(streaming, visibility) { mutableStateOf(initiallyExpanded) }
    Surface(
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (streaming) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Working", Modifier.padding(start = 8.dp).weight(1f), fontWeight = FontWeight.Medium)
                Text(if (expanded) "Collapse" else "Expand", style = MaterialTheme.typography.labelMedium)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    traces.forEach { event ->
                        Column {
                            Text("${event.label} • ${event.status}", style = MaterialTheme.typography.labelMedium, color = if (event.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            if (event.input.isNotBlank()) CodeSourcePanel(if (event.type.contains("python", true)) "python" else if (event.type.contains("ubuntu", true) || event.type.contains("shell", true)) "bash" else "input", event.input.take(4_000))
                            if (event.output.isNotBlank()) GenericToolOutputCard(event.output.take(12_000), failed = event.status == "error")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolStepDetails(kind: String, input: String, output: String, status: String, viewModel: ChatViewModel) {
    val language = if (kind == "python") "python" else if (kind == "ubuntu") "bash" else "text"
    if (input.isNotBlank()) CodeSourcePanel(language, input, when (kind) { "python" -> "PYTHON CODE"; "ubuntu" -> "SHELL COMMAND"; "search" -> "SEARCH QUERY"; "fetch" -> "URL"; else -> "INPUT" })
    if (output.isNotBlank()) {
        val json = ChatMessageJson
        when (kind) {
            "python" -> runCatching { json.decodeFromString<ExecutionResult>(output) }.getOrNull()?.let { PythonExecutionCard(it, "Python tool result") }
                ?: GenericToolOutputCard(output, failed = status == "error")
            "ubuntu" -> runCatching { json.decodeFromString<UbuntuExecutionResult>(output) }.getOrNull()?.let { UbuntuExecutionCard(it, "Ubuntu tool result") }
                ?: GenericToolOutputCard(output, failed = status == "error")
            else -> GenericToolOutputCard(output, failed = status == "error")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    viewModel: ChatViewModel,
    provider: ProviderEntity?,
    model: ModelEntity?,
    generating: Boolean,
    chromeProgress: Float,
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsState()
    val staged by viewModel.stagedAttachments.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val context = LocalContext.current
    var sendMenu by remember { mutableStateOf(false) }
    var plusMenu by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val haptics = LocalHapticFeedback.current
    val hasPayload = draft.isNotBlank() || staged.isNotEmpty()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(viewModel::import)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
        uris.forEach(viewModel::import)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        val file = pendingCameraFile
        pendingCameraUri = null
        pendingCameraFile = null
        if (saved && uri != null) viewModel.import(uri) else file?.delete()
    }

    fun takePhoto() {
        val file = File(context.cacheDir, "camera/${UUID.randomUUID()}.jpg").also { it.parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        pendingCameraFile = file
        pendingCameraUri = uri
        camera.launch(uri)
    }

    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .blur((8f + 14f * chromeProgress.coerceIn(0f, 1f)).dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = .20f + .16f * chromeProgress.coerceIn(0f, 1f)),
                            MaterialTheme.colorScheme.surface.copy(alpha = .58f + .22f * chromeProgress.coerceIn(0f, 1f)),
                        ),
                    ),
                ),
        )
        Surface(
            shadowElevation = 8.dp,
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .66f + .16f * chromeProgress.coerceIn(0f, 1f)),
        ) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (pending.isNotEmpty()) Text(
                "${pending.size} message${if (pending.size == 1) "" else "s"} queued",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            if (staged.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    items(staged.size, key = { staged[it].id }) { index ->
                        StagedAttachmentPreview(
                            attachment = staged[index],
                            modelSupportsVision = model?.supportsVision != false,
                            onRemove = { viewModel.removeStaged(staged[index].id) },
                        )
                    }
                }
            }
            conversation?.let { current ->
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 48.dp),
                ) {
                    item {
                        ThinkingComposerChip(
                            enabled = current.thinkingEnabled,
                            effort = current.thinkingEffort,
                            provider = provider,
                            model = model,
                            onSelection = { enabled, effort ->
                                viewModel.updateConversation {
                                    it.copy(
                                        thinkingEnabled = enabled,
                                        thinkingEffort = effort ?: it.thinkingEffort,
                                    )
                                }
                            },
                        )
                    }
                    item {
                        SearchComposerChip(
                            webEnabled = current.webSearchEnabled,
                            deepResearchEnabled = current.deepResearchEnabled,
                            onSelection = { webEnabled, deepResearchEnabled ->
                                viewModel.updateConversation {
                                    it.copy(
                                        webSearchEnabled = webEnabled,
                                        deepResearchEnabled = deepResearchEnabled,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { plusMenu = true }, enabled = !importing) {
                    Icon(Icons.Outlined.Add, "Add files, images, camera, or tools")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { viewModel.draft.value = it },
                    placeholder = {
                        Text(
                            if (generating) "Steer or queue…"
                            else if (conversation?.deepResearchEnabled == true) "Research request…"
                            else "Message Arbor…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp, max = 170.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    maxLines = 7,
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp).combinedClickable(
                        onClick = { if (generating && draft.isBlank() && staged.isEmpty()) viewModel.stop() else viewModel.send() },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sendMenu = true
                        },
                    ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (generating && draft.isBlank() && staged.isEmpty()) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                            if (generating) "Stop or send" else "Send",
                        )
                    }
                }
            }

            }
        }
    }

    if (plusMenu) {
        ModalBottomSheet(onDismissRequest = { plusMenu = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text("Add and use tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                ComposerActionRow(Icons.Outlined.AttachFile, "Files", "Documents, archives, code, audio, and other supported files") {
                    plusMenu = false
                    filePicker.launch(arrayOf("*/*"))
                }
                ComposerActionRow(Icons.Outlined.Image, "Photos", "Choose one or more images") {
                    plusMenu = false
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ComposerActionRow(Icons.Outlined.CameraAlt, "Camera", "Take a photo and attach it") {
                    plusMenu = false
                    takePhoto()
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                conversation?.let { current ->
                    ComposerToggleRow(Icons.Outlined.Search, "Web search", "Search and fetch public web sources", current.webSearchEnabled) { enabled ->
                        viewModel.updateConversation { it.copy(webSearchEnabled = enabled, deepResearchEnabled = it.deepResearchEnabled && enabled) }
                    }
                    ComposerToggleRow(Icons.Outlined.TravelExplore, "Deep Research", "Plan, search repeatedly, verify sources, and write a cited report", current.deepResearchEnabled) { enabled ->
                        viewModel.updateConversation { it.copy(deepResearchEnabled = enabled, webSearchEnabled = it.webSearchEnabled || enabled) }
                    }
                    ComposerToggleRow(Icons.Outlined.Code, "Local Code Execution", "Run Python locally in this chat's persistent workspace", current.agentPythonEnabled) { enabled ->
                        viewModel.updateConversation { it.copy(agentPythonEnabled = enabled) }
                    }
                    ComposerToggleRow(Icons.Outlined.Terminal, "Linux", "Use the selected Linux tooling workspace", current.agentUbuntuEnabled) { enabled ->
                        viewModel.updateConversation { it.copy(agentUbuntuEnabled = enabled) }
                    }
                }
            }
        }
    }

    if (sendMenu) {
        ModalBottomSheet(onDismissRequest = { sendMenu = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    if (generating) "Response actions" else "Send options",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                if (generating) ListItem(
                    headlineContent = { Text("Stop current response") },
                    supportingContent = { Text("Keep the partial answer and Working trace") },
                    leadingContent = { Icon(Icons.Filled.Stop, null) },
                    modifier = Modifier.combinedClickable(onClick = { viewModel.stop(); sendMenu = false }, onLongClick = { viewModel.stop(); sendMenu = false }),
                )
                ListItem(
                    headlineContent = { Text(if (generating) "Steer current response" else "Send now") },
                    supportingContent = { Text(if (!hasPayload) "Type a message or attach a file first" else if (generating) "Stop it, preserve its state, insert this message, then continue" else "Start a response immediately") },
                    leadingContent = { Icon(if (generating) Icons.AutoMirrored.Outlined.AltRoute else Icons.AutoMirrored.Filled.Send, null) },
                    modifier = Modifier.combinedClickable(
                        onClick = { if (hasPayload) { viewModel.send(if (generating) SendMode.STEER else SendMode.SEND_NOW); sendMenu = false } },
                        onLongClick = { if (hasPayload) { viewModel.send(if (generating) SendMode.STEER else SendMode.SEND_NOW); sendMenu = false } },
                    ),
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        headlineColor = if (hasPayload) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
                    ),
                )
                if (generating) ListItem(
                    headlineContent = { Text("Queue after response") },
                    supportingContent = { Text(if (hasPayload) "Send automatically when the current response finishes" else "Type a message or attach a file first") },
                    leadingContent = { Icon(Icons.Outlined.Schedule, null) },
                    modifier = Modifier.combinedClickable(onClick = { if (hasPayload) { viewModel.send(SendMode.QUEUE); sendMenu = false } }, onLongClick = { if (hasPayload) { viewModel.send(SendMode.QUEUE); sendMenu = false } }),
                )
                if (generating) ListItem(
                    headlineContent = { Text("Start a separate turn") },
                    supportingContent = { Text(if (hasPayload) "Let both responses run concurrently" else "Type a message or attach a file first") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                    modifier = Modifier.combinedClickable(onClick = { if (hasPayload) { viewModel.send(SendMode.SEND_NOW); sendMenu = false } }, onLongClick = { if (hasPayload) { viewModel.send(SendMode.SEND_NOW); sendMenu = false } }),
                )
            }
        }
    }
}

@Composable
private fun StagedAttachmentPreview(
    attachment: AttachmentEntity,
    modelSupportsVision: Boolean,
    onRemove: () -> Unit,
) {
    val isImage = attachment.mimeType.startsWith("image/") && attachment.mimeType != "image/svg+xml"
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .92f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .width(if (isImage) 86.dp else 176.dp)
            .height(82.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (isImage) {
                AsyncImage(
                    model = File(attachment.thumbnailPath ?: attachment.localPath),
                    contentDescription = attachment.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = .62f)),
                                startY = 20f,
                            ),
                        ),
                )
                Text(
                    attachment.displayName,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 28.dp, bottom = 7.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(
                    Modifier.fillMaxSize().padding(start = 10.dp, end = 30.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        stagedFileIcon(attachment),
                        null,
                        Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.padding(start = 9.dp)) {
                        Text(attachment.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        Text(
                            Formatter.formatShortFileSize(LocalContext.current, attachment.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).size(30.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.scrim.copy(alpha = if (isImage) .58f else .12f)) {
                    Icon(
                        Icons.Outlined.Close,
                        "Remove ${attachment.displayName}",
                        Modifier.padding(5.dp).size(15.dp),
                        tint = if (isImage) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (attachment.ocrJson != null || (isImage && !modelSupportsVision)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .94f),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                ) {
                    Text(
                        if (attachment.ocrJson != null) "OCR" else "OCR on send",
                        Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

private fun stagedFileIcon(attachment: AttachmentEntity) = when {
    attachment.mimeType == "application/pdf" -> Icons.Outlined.PictureAsPdf
    attachment.mimeType.startsWith("text/") || attachment.extractedText != null -> Icons.Outlined.Description
    attachment.mimeType.startsWith("audio/") -> Icons.Outlined.AudioFile
    attachment.mimeType.contains("zip") || attachment.mimeType.contains("archive") || attachment.mimeType.contains("compressed") -> Icons.Outlined.Archive
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

@Composable
private fun ThinkingComposerChip(
    enabled: Boolean,
    effort: ThinkingEffort,
    provider: ProviderEntity?,
    model: ModelEntity?,
    onSelection: (Boolean, ThinkingEffort?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val options = remember(provider?.id, provider?.kind, model?.modelId, model?.supportsThinking) {
        supportedThinkingLevels(provider, model)
    }
    val selectedIndex = remember(options, enabled, effort) {
        options.indexOfFirst { option ->
            if (!enabled) !option.enabled else option.enabled && option.effort == effort
        }.takeIf { it >= 0 } ?: options.indexOfFirst { it.enabled }.coerceAtLeast(0)
    }
    var sliderValue by remember(options, selectedIndex, menu) { mutableFloatStateOf(selectedIndex.toFloat()) }
    val selected = options.getOrNull(selectedIndex)

    Box {
        Surface(
            onClick = { if (options.isNotEmpty()) menu = true },
            enabled = options.isNotEmpty(),
            color = if (enabled && options.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (enabled && options.isNotEmpty()) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape,
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.Psychology, null, Modifier.size(17.dp))
                Text(
                    if (options.isEmpty()) "Thinking unavailable"
                    else "Think · ${selected?.label ?: if (enabled) effort.composerName else "Off"}",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Outlined.ExpandLess, "Choose thinking level", Modifier.size(19.dp))
            }
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            modifier = Modifier.width(304.dp),
        ) {
            if (options.isNotEmpty()) {
                val preview = options[sliderValue.roundToInt().coerceIn(options.indices)]
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Think", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(preview.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            val option = options[sliderValue.roundToInt().coerceIn(options.indices)]
                            onSelection(option.enabled, option.effort)
                        },
                        valueRange = 0f..options.lastIndex.toFloat().coerceAtLeast(1f),
                        steps = (options.size - 2).coerceAtLeast(0),
                        enabled = options.size > 1,
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(options.first().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(options.last().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        preview.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private val ThinkingEffort.effortDescription: String
    get() = when (this) {
        ThinkingEffort.MINIMAL -> "Fastest, light reasoning"
        ThinkingEffort.LOW -> "Short reasoning"
        ThinkingEffort.MEDIUM -> "Balanced"
        ThinkingEffort.HIGH -> "More thorough reasoning"
        ThinkingEffort.XHIGH -> "Extended reasoning"
        ThinkingEffort.MAX -> "Maximum supported reasoning"
    }

private val ThinkingEffort.displayName: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase)

private val ThinkingEffort.composerName: String
    get() = when (this) {
        ThinkingEffort.MINIMAL -> "Min"
        ThinkingEffort.LOW -> "Low"
        ThinkingEffort.MEDIUM -> "Med"
        ThinkingEffort.HIGH -> "High"
        ThinkingEffort.XHIGH -> "XHigh"
        ThinkingEffort.MAX -> "Max"
    }

@Composable
private fun SearchComposerChip(
    webEnabled: Boolean,
    deepResearchEnabled: Boolean,
    onSelection: (Boolean, Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val label = when {
        deepResearchEnabled -> "Research"
        webEnabled -> "Search"
        else -> "Search off"
    }
    val icon = when {
        deepResearchEnabled -> Icons.Outlined.TravelExplore
        else -> Icons.Outlined.Search
    }
    Box {
        Surface(
            onClick = { menu = true },
            color = if (webEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (webEnabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape,
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, Modifier.size(17.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandLess, "Choose search mode", Modifier.size(19.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Search off") },
                onClick = { onSelection(false, false); menu = false },
                leadingIcon = { Icon(Icons.Outlined.Close, null) },
            )
            DropdownMenuItem(
                text = { Text("Web search") },
                onClick = { onSelection(true, false); menu = false },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
            )
            DropdownMenuItem(
                text = { Text("Deep Research") },
                onClick = { onSelection(true, true); menu = false },
                leadingIcon = { Icon(Icons.Outlined.TravelExplore, null) },
            )
        }
    }
}

@Composable
private fun ComposerActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onClick),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComposerToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.combinedClickable(onClick = { onCheckedChange(!checked) }, onLongClick = { onCheckedChange(!checked) }),
    )
}


private fun ChatViewModel.containerAttachments(nodeId: String) = observeAttachments(nodeId)
