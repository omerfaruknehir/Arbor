package app.xylune.chat.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.lazy.LazyListLayoutInfo
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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import app.xylune.chat.R
import app.xylune.chat.data.MessageEntity
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.MessageStatus
import app.xylune.chat.data.AttachmentEntity
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ReasoningVisibility
import app.xylune.chat.data.SendMode
import app.xylune.chat.data.ThinkingEffort
import app.xylune.chat.agent.ToolTraceEvent
import app.xylune.chat.agent.WebFetchResponse
import app.xylune.chat.agent.WebSearchResponse
import app.xylune.chat.agent.WebSearchResult
import app.xylune.chat.provider.ThinkingLevelOption
import app.xylune.chat.provider.defaultThinkingEffort
import app.xylune.chat.provider.effectiveThinkingEnabled
import app.xylune.chat.provider.supportedThinkingLevels
import app.xylune.chat.agent.MessageTimelineEvent
import app.xylune.chat.generation.StreamingPreviewStore
import app.xylune.chat.agent.materializeTimelineContent
import app.xylune.chat.agent.groupOrderedTimeline
import app.xylune.chat.sandbox.ExecutionResult
import app.xylune.chat.sandbox.ExecutionProgress
import coil.compose.AsyncImage
import app.xylune.chat.sandbox.UbuntuExecutionResult
import app.xylune.chat.sandbox.AppliedPatchResult
import app.xylune.chat.sandbox.ScriptRunResult
import app.xylune.chat.sandbox.WorkspaceReadResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import java.io.File
import java.util.UUID

private val ChatMessageJson = Json { ignoreUnknownKeys = true }
internal fun calculateTopChromeProgress(
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

internal fun calculateAutoFollowStepPx(
    distancePx: Float,
    frameSeconds: Float,
    maxSpeedPxPerSecond: Float,
): Float {
    if (distancePx <= 0f || frameSeconds <= 0f || maxSpeedPxPerSecond <= 0f) return 0f

    // Distance-sensitive response without the previous near-teleport rate.
    // A separate per-frame cap below also protects against a delayed/janky frame.
    val distanceBoost = 1f - exp(-(distancePx / 180f).coerceAtLeast(0f))
    val responseRatePerSecond = 10f + (28f * distanceBoost)
    val response = 1f - exp(-responseRatePerSecond * frameSeconds)
    val easedStep = (distancePx * response).coerceAtLeast(min(1f, distancePx))
    return min(distancePx, min(easedStep, maxSpeedPxPerSecond * frameSeconds))
}

internal fun calculateAutoFollowSeekSpeedPxPerSecond(
    hiddenItemCount: Int,
    elapsedSeconds: Float,
    minSpeedPxPerSecond: Float,
    maxSpeedPxPerSecond: Float,
): Float {
    if (
        hiddenItemCount <= 0 || elapsedSeconds < 0f || minSpeedPxPerSecond <= 0f ||
        maxSpeedPxPerSecond < minSpeedPxPerSecond
    ) return 0f

    val itemFactor = 1f - exp(-0.65f * hiddenItemCount.toFloat())
    val timeFactor = 1f - exp(-5f * elapsedSeconds)
    val combined = 1f - ((1f - itemFactor) * (1f - timeFactor))
    val shaped = combined * combined * (3f - (2f * combined))
    return minSpeedPxPerSecond + ((maxSpeedPxPerSecond - minSpeedPxPerSecond) * shaped)
}

private data class PersistedChatScrollSample(
    val anchorNodeId: String?,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemOffset: Int,
    val atLatest: Boolean,
    val topBarHeightOffset: Float,
)

internal data class MessageBranchKey(
    val conversationId: String,
    val parentNodeId: String?,
    val role: MessageRole,
)

internal fun buildRevisionBranchGroups(
    revisionHistory: List<MessageEntity>,
): Map<MessageBranchKey, List<MessageEntity>> = revisionHistory
    .asSequence()
    .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
    .groupBy { MessageBranchKey(it.conversationId, it.parentNodeId, it.role) }
    .mapValues { (_, messages) ->
        messages.distinctBy(MessageEntity::nodeId)
            .sortedWith(compareBy<MessageEntity> { it.createdAt }.thenBy { it.rowId })
    }

internal fun inlineBranchOptions(
    activeMessage: MessageEntity,
    revisionGroups: Map<MessageBranchKey, List<MessageEntity>>,
): List<MessageEntity> {
    if (activeMessage.role != MessageRole.USER && activeMessage.role != MessageRole.ASSISTANT) return emptyList()
    val branchKey = MessageBranchKey(activeMessage.conversationId, activeMessage.parentNodeId, activeMessage.role)
    val revisions = revisionGroups[branchKey].orEmpty()
    if (revisions.isEmpty()) return emptyList()
    return (revisions + activeMessage)
        .distinctBy(MessageEntity::nodeId)
        .sortedWith(compareBy<MessageEntity> { it.createdAt }.thenBy { it.rowId })
        .takeIf { it.size > 1 }
        .orEmpty()
}

internal fun chronologicalSourceIndex(uiIndex: Int, itemCount: Int): Int =
    (itemCount - 1 - uiIndex).coerceIn(0, (itemCount - 1).coerceAtLeast(0))

internal fun chronologicalUiIndex(sourceIndex: Int, itemCount: Int): Int =
    (itemCount - 1 - sourceIndex).coerceIn(0, (itemCount - 1).coerceAtLeast(0))

private enum class ChatFollowMode { FOLLOWING, DETACHED }

private data class StreamingScrollAnchor(
    val messageNodeId: String,
    val itemIndex: Int,
    val scrollOffsetPx: Int,
)

private class StreamingScrollAnchorTracker {
    var anchor: StreamingScrollAnchor? = null
    var missingAnchorFrames: Int = 0
}

internal fun shouldRestoreStreamingAnchor(
    previousItemIndex: Int,
    currentItemIndex: Int,
    userDragging: Boolean,
): Boolean = !userDragging && currentItemIndex < previousItemIndex - 1

internal fun calculateViewportCorrectionDeltaPx(
    currentScreenOffsetPx: Int,
    anchoredScreenOffsetPx: Int,
): Float = (currentScreenOffsetPx - anchoredScreenOffsetPx).toFloat()

internal fun calculateCardViewportCorrectionPx(
    currentPositionPx: Float,
    targetPositionPx: Float,
): Float = currentPositionPx - targetPositionPx

internal fun calculateCenteredCardCorrectionPx(
    cardTopPx: Float,
    cardBottomPx: Float,
    viewportTopPx: Float,
    viewportBottomPx: Float,
): Float = ((cardTopPx + cardBottomPx) / 2f) - ((viewportTopPx + viewportBottomPx) / 2f)

internal fun shouldCenterCollapsedCard(expandedHeightPx: Float, viewportHeightPx: Float): Boolean =
    viewportHeightPx > 0f && expandedHeightPx >= viewportHeightPx * 0.55f

internal enum class WorkingCardMutation {
    AUTO_EXPAND,
    AUTO_COLLAPSE,
    MANUAL_EXPAND,
    MANUAL_COLLAPSE,
}

internal enum class WorkingCardViewportAnchor {
    NONE,
    TOP,
    BOTTOM,
    LATEST,
}

internal fun workingBlockDefaultExpanded(
    visibility: ReasoningVisibility,
    active: Boolean,
): Boolean = when (visibility) {
    ReasoningVisibility.ALWAYS -> true
    ReasoningVisibility.SHOW_WHILE_WORKING -> active
    ReasoningVisibility.COLLAPSED -> false
}

internal fun workEventTitle(event: MessageTimelineEvent): String =
    event.label.takeIf(String::isNotBlank) ?: when (event.kind) {
        "reasoning" -> "Reasoning"
        "search" -> "Web search"
        "native_search" -> "Provider native search"
        "fetch" -> "Reading source"
        "script", "python" -> "Code execution"
        "ubuntu" -> "Linux command"
        "file_send" -> "Preparing file"
        else -> event.kind.replace('_', ' ').replaceFirstChar(Char::uppercase)
    }

internal data class TimelineSourceLink(
    val title: String,
    val url: String,
)

private val TimelineLegacySourceLink = Regex(
    """\[\[source\|([^|\]\n]{1,240})\|(https?://[^\]\s]+)]]""",
    RegexOption.IGNORE_CASE,
)
private val TimelineCompactSourceLink = Regex(
    """\[\[([^|\]\n]{1,240})\|(https?://[^\]\s]+)]]""",
    RegexOption.IGNORE_CASE,
)
private val TimelineMarkdownSourceLink = Regex(
    """\[([^\]\n]{1,240})]\((https?://[^)\s]+)\)""",
    RegexOption.IGNORE_CASE,
)
private val TimelineRawUrl = Regex("""https?://[^\s<>()\[\]]+""", RegexOption.IGNORE_CASE)

internal fun extractTimelineSourceLinks(text: String): List<TimelineSourceLink> {
    data class LocatedLink(val offset: Int, val link: TimelineSourceLink)

    val located = mutableListOf<LocatedLink>()
    fun collect(regex: Regex) {
        regex.findAll(text).forEach { match ->
            val title = match.groupValues[1]
                .replace("\\[", "[")
                .replace("\\]", "]")
                .replace('|', '·')
                .trim()
            val url = match.groupValues[2].trim().trimEnd('.', ',', ';')
            if (url.startsWith("http://") || url.startsWith("https://")) {
                located += LocatedLink(
                    offset = match.range.first,
                    link = TimelineSourceLink(title.ifBlank { url }, url),
                )
            }
        }
    }
    collect(TimelineLegacySourceLink)
    collect(TimelineCompactSourceLink)
    collect(TimelineMarkdownSourceLink)

    val alreadyLocated = located.mapTo(linkedSetOf()) { it.link.url }
    TimelineRawUrl.findAll(text).forEach { match ->
        val url = match.value.trimEnd('.', ',', ';')
        if (alreadyLocated.add(url)) {
            val host = runCatching { url.toUri().host }.getOrNull().orEmpty().removePrefix("www.")
            located += LocatedLink(match.range.first, TimelineSourceLink(host.ifBlank { url }, url))
        }
    }

    val seen = linkedSetOf<String>()
    return located.sortedBy(LocatedLink::offset).mapNotNull { candidate ->
        candidate.link.takeIf { seen.add(it.url) }
    }
}

internal fun recoveryNoticeKey(message: MessageEntity): String =
    "${message.nodeId}:${message.updatedAt}:${message.status}:${message.error.orEmpty()}"

internal fun recoveryErrorSummary(message: MessageEntity): String = message.error
    ?.lineSequence()
    ?.map(String::trim)
    ?.filter(String::isNotBlank)
    ?.joinToString(" ")
    ?.take(360)
    ?.takeIf(String::isNotBlank)
    ?: if (message.status == MessageStatus.ERROR) {
        "The provider stream failed without returning additional diagnostic text."
    } else {
        "The response stopped before it completed."
    }

internal fun isActionableRecoveryMessage(message: MessageEntity): Boolean =
    message.status == MessageStatus.ERROR ||
        (message.status == MessageStatus.INTERRUPTED &&
            message.error !in setOf("Steered by user", "Replaced by an edited message"))

internal fun isRecoveryNoticeCandidate(
    message: MessageEntity,
    activeLeafNodeId: String?,
    dismissedNoticeKey: String?,
): Boolean = isActionableRecoveryMessage(message) &&
    message.nodeId == activeLeafNodeId &&
    recoveryNoticeKey(message) != dismissedNoticeKey

internal fun shouldRenderAssistantRecoveryState(message: MessageEntity): Boolean =
    message.role == MessageRole.ASSISTANT && isActionableRecoveryMessage(message)

internal fun withDismissedRecoveryNotice(
    current: Map<String, String>,
    conversationId: String?,
    message: MessageEntity,
): Map<String, String> = conversationId?.let { id ->
    current + (id to recoveryNoticeKey(message))
} ?: current

internal fun workEventStateLabel(event: MessageTimelineEvent): String = when (event.status) {
    "preparing" -> "Preparing"
    "prepared" -> "Ready"
    "running" -> "Running"
    "error" -> "Failed"
    "complete" -> event.finishedAt?.let { finished ->
        "${(finished - event.startedAt).coerceAtLeast(0)} ms"
    } ?: "Done"
    else -> event.status.replace('_', ' ').replaceFirstChar(Char::uppercase)
}

internal fun workingBlockHeadline(events: List<MessageTimelineEvent>, active: Boolean): String {
    val latest = events.lastOrNull()
    return when {
        latest == null -> if (active) "Working" else "Activity"
        active -> workEventTitle(latest)
        events.any { it.status == "error" } -> "Finished with an error"
        events.size == 1 && latest.kind == "reasoning" -> "Reasoning"
        else -> "Work complete"
    }
}

internal fun workingBlockSummary(events: List<MessageTimelineEvent>, active: Boolean): String {
    val latest = events.lastOrNull()
    if (active && latest != null) {
        return when {
            latest.status in setOf("preparing", "prepared", "running", "error") -> workEventStateLabel(latest)
            latest.finishedAt == null && latest.kind == "reasoning" -> "Thinking"
            latest.finishedAt == null -> "Working"
            else -> workEventStateLabel(latest)
        }
    }
    val errors = events.count { it.status == "error" }
    return buildString {
        append(events.size).append(if (events.size == 1) " step" else " steps")
        if (errors > 0) append(" • ").append(errors).append(if (errors == 1) " error" else " errors")
    }
}

internal fun chooseWorkingCardViewportAnchor(
    manual: Boolean,
    followingLatest: Boolean,
    cardTopPx: Float?,
    cardBottomPx: Float?,
    viewportTopPx: Float?,
    viewportBottomPx: Float?,
): WorkingCardViewportAnchor {
    if (manual) return WorkingCardViewportAnchor.TOP
    if (followingLatest) return WorkingCardViewportAnchor.LATEST

    val cardTop = cardTopPx ?: return WorkingCardViewportAnchor.NONE
    val cardBottom = cardBottomPx ?: return WorkingCardViewportAnchor.NONE
    val viewportTop = viewportTopPx ?: return WorkingCardViewportAnchor.NONE
    val viewportBottom = viewportBottomPx ?: return WorkingCardViewportAnchor.NONE

    return when {
        cardBottom <= viewportTop -> WorkingCardViewportAnchor.BOTTOM
        cardTop >= viewportBottom -> WorkingCardViewportAnchor.NONE
        cardTop <= viewportTop -> WorkingCardViewportAnchor.BOTTOM
        else -> WorkingCardViewportAnchor.TOP
    }
}

internal data class WorkingCardViewportController(
    val viewportBounds: Rect?,
    val listScrolling: Boolean,
    val applyMutation: (WorkingCardMutation, () -> Rect?, () -> Unit) -> Unit,
) {
    fun isVisible(bounds: Rect?): Boolean {
        val viewport = viewportBounds ?: return true
        val card = bounds ?: return true
        return card.bottom > viewport.top && card.top < viewport.bottom
    }
}

internal fun calculateVisibleChatViewportEndPx(viewportEndPx: Int, obscuredBottomPx: Int): Int =
    (viewportEndPx - obscuredBottomPx.coerceAtLeast(0)).coerceAtLeast(0)

private const val ChatFollowMaxSpeedPxPerSecond = 8_000f
private const val ChatFollowSeekMinSpeedPxPerSecond = 1_800f
private const val ChatFollowSeekMaxSpeedPxPerSecond = 12_000f
private const val ChatFollowMaxFrameStepPx = 128f
private const val ChatFollowSeekMaxFrameStepPx = 176f
private const val STREAM_HAPTIC_CHARACTER_INTERVAL = 32

private suspend fun snapChatToBottom(
    state: androidx.compose.foundation.lazy.LazyListState,
    lastIndex: Int,
    obscuredBottomPx: Int,
) {
    if (lastIndex < 0) return
    state.scrollToItem(lastIndex)
    withFrameNanos { }
    val layout = state.layoutInfo
    val last = layout.visibleItemsInfo.firstOrNull { it.index == lastIndex } ?: return
    val visibleEnd = calculateVisibleChatViewportEndPx(layout.viewportEndOffset, obscuredBottomPx)
    val overflow = last.offset + last.size - visibleEnd
    if (overflow > 0) state.scrollBy(overflow.toFloat())
}

internal fun calculateComposerChromeProgressFromBottom(
    layoutInfo: LazyListLayoutInfo,
    startPx: Int,
    endPx: Int,
): Float {
    val total = layoutInfo.totalItemsCount
    if (total == 0) return 0f
    val last = layoutInfo.visibleItemsInfo.lastOrNull()
    if (last == null || last.index != total - 1) return 1f
    val distance = (last.offset + last.size - layoutInfo.viewportEndOffset).coerceAtLeast(0)
    if (endPx <= startPx) return if (distance > startPx) 1f else 0f
    return ((distance - startPx).toFloat() / (endPx - startPx).toFloat()).coerceIn(0f, 1f)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    SideEffect { XyluneRenderProfiler.recordChatRecomposition() }
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsStateWithLifecycle()
    val chromeEdgeSoftness by viewModel.chromeEdgeSoftness.collectAsStateWithLifecycle()
    val chromeOverlayOpacity by viewModel.chromeOverlayOpacity.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val allProviders by viewModel.providers.collectAsStateWithLifecycle()
    val favoriteModels by viewModel.favoriteModels.collectAsStateWithLifecycle()
    val recentModels by viewModel.recentModels.collectAsStateWithLifecycle()
    val credentialRevision by viewModel.credentialRevision.collectAsStateWithLifecycle()
    val usableProviders = remember(allProviders, credentialRevision) { viewModel.configuredProviders(allProviders) }
    val linuxStatus by viewModel.ubuntuStatus.collectAsStateWithLifecycle()
    val recoverable by viewModel.recoverable.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val generating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingPreviews by StreamingPreviewStore.previews.collectAsStateWithLifecycle()
    val revisionHistory by viewModel.revisionHistory.collectAsStateWithLifecycle()
    val contextSummary by viewModel.contextSummary.collectAsStateWithLifecycle()
    val revisionBranchGroups = remember(revisionHistory) { buildRevisionBranchGroups(revisionHistory) }
    val paging = viewModel.messages.collectAsLazyPagingItems()
    val focusedMessageNodeId by viewModel.focusedMessageNodeId.collectAsState()
    var showModelPicker by remember { mutableStateOf(false) }
    var chatMenu by remember { mutableStateOf(false) }
    var showChatConfiguration by remember { mutableStateOf(false) }
    var dismissedRecoveryNoticeKeys by rememberSaveable {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    val dismissedRecoveryNoticeKey = conversation?.id?.let { dismissedRecoveryNoticeKeys[it] }
    var recoveryDetailsMessage by remember(conversation?.id) { mutableStateOf<MessageEntity?>(null) }
    val messageListState = rememberLazyListState()
    val savedScroll = remember(conversation?.id) {
        conversation?.id?.let(viewModel::chatScrollSnapshot)
    }
    val userDraggingMessageList by messageListState.interactionSource.collectIsDraggedAsState()
    val listScope = rememberCoroutineScope()
    val blurState = rememberXyluneBackdropBlurState()
    val streamHaptics = rememberXyluneHaptics()
    val density = LocalDensity.current
    val topAppBarState = rememberTopAppBarState()
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val chromeStartPx = with(density) { 56.dp.roundToPx() }
    val chromeEndPx = with(density) { 176.dp.roundToPx() }
    var followMode by remember(conversation?.id) { mutableStateOf(ChatFollowMode.FOLLOWING) }
    var manualFollowHold by remember(conversation?.id) { mutableStateOf(false) }
    var initialPositioned by remember(conversation?.id) { mutableStateOf(false) }
    var messageViewportBounds by remember(conversation?.id) { mutableStateOf<Rect?>(null) }
    var messageBottomInsetPx by remember(conversation?.id) { mutableIntStateOf(0) }
    var workingCardMutationCount by remember(conversation?.id) { mutableIntStateOf(0) }
    val messageViewportBoundsState = rememberUpdatedState(messageViewportBounds)
    val messageBottomInsetState = rememberUpdatedState(messageBottomInsetPx)
    val followModeState = rememberUpdatedState(followMode)
    val manualFollowHoldState = rememberUpdatedState(manualFollowHold)
    val userDraggingMessageListState = rememberUpdatedState(userDraggingMessageList)
    var searchFocusHandled by remember(conversation?.id, focusedMessageNodeId) { mutableStateOf(false) }
    val streamingAnchorTracker = remember(conversation?.id) { StreamingScrollAnchorTracker() }
    val stableMessageKeysByUiIndex = remember(conversation?.id) { mutableMapOf<Int, String>() }
    var pagingNodeIds by remember(conversation?.id) { mutableStateOf<List<String>>(emptyList()) }
    val generatingState = rememberUpdatedState(generating)
    val selectedActiveModel = remember(models, conversation?.selectedModelId) {
        models.firstOrNull { it.modelId == conversation?.selectedModelId }
    }

    LaunchedEffect(paging, conversation?.id) {
        snapshotFlow { paging.itemSnapshotList.items.map(MessageEntity::nodeId) }
            .distinctUntilChanged()
            .collect { nodeIds ->
                pagingNodeIds = nodeIds
                nodeIds.forEachIndexed { sourceIndex, nodeId ->
                    stableMessageKeysByUiIndex[
                        chronologicalUiIndex(sourceIndex, nodeIds.size)
                    ] = nodeId
                }
                if (!generatingState.value && nodeIds.isNotEmpty()) {
                    stableMessageKeysByUiIndex.keys.removeAll { it !in nodeIds.indices }
                }
            }
    }

    LaunchedEffect(paging, conversation?.id) {
        var activeNodeId: String? = null
        var lastLength = 0
        var pendingCharacters = 0
        snapshotFlow {
            paging.itemSnapshotList.items
                .lastOrNull { it.role == MessageRole.ASSISTANT && it.status == MessageStatus.STREAMING }
                ?.let { it.nodeId to (it.content.length + it.reasoning.length) }
        }
            .distinctUntilChanged()
            .collect { sample ->
                if (sample == null) {
                    activeNodeId = null
                    lastLength = 0
                    pendingCharacters = 0
                    return@collect
                }
                val (nodeId, length) = sample
                if (nodeId != activeNodeId) {
                    activeNodeId = nodeId
                    lastLength = length
                    pendingCharacters = 0
                    return@collect
                }
                val delta = (length - lastLength).coerceAtLeast(0)
                lastLength = length
                pendingCharacters += delta
                if (pendingCharacters >= STREAM_HAPTIC_CHARACTER_INTERVAL) {
                    pendingCharacters %= STREAM_HAPTIC_CHARACTER_INTERVAL
                    streamHaptics.streamTick()
                }
            }
    }

    var previousGeneratingForHaptics by remember(conversation?.id) { mutableStateOf(false) }
    LaunchedEffect(generating, conversation?.id) {
        if (previousGeneratingForHaptics && !generating) streamHaptics.streamComplete()
        previousGeneratingForHaptics = generating
    }

    val applyWorkingCardMutation = remember(messageListState, listScope, conversation?.id) {
        { mutation: WorkingCardMutation, boundsProvider: () -> Rect?, mutate: () -> Unit ->
            val manual = mutation == WorkingCardMutation.MANUAL_EXPAND ||
                mutation == WorkingCardMutation.MANUAL_COLLAPSE
            if (manual) {
                manualFollowHold = true
                followMode = ChatFollowMode.DETACHED
            }

            listScope.launch {
                val before = boundsProvider()
                val viewport = messageViewportBoundsState.value
                val anchor = chooseWorkingCardViewportAnchor(
                    manual = manual,
                    followingLatest = !manual &&
                        followModeState.value == ChatFollowMode.FOLLOWING &&
                        !manualFollowHoldState.value,
                    cardTopPx = before?.top,
                    cardBottomPx = before?.bottom,
                    viewportTopPx = viewport?.top,
                    viewportBottomPx = viewport?.bottom,
                )
                val anchoredTop = before?.top
                val anchoredBottom = before?.bottom

                workingCardMutationCount += 1
                try {
                    mutate()

                    suspend fun correctViewportAnchor() {
                        if (userDraggingMessageListState.value) return
                        val correction = when (anchor) {
                            WorkingCardViewportAnchor.NONE -> 0f
                            WorkingCardViewportAnchor.TOP -> {
                                val current = boundsProvider()
                                if (current != null && anchoredTop != null) {
                                    calculateCardViewportCorrectionPx(current.top, anchoredTop)
                                } else 0f
                            }
                            WorkingCardViewportAnchor.BOTTOM -> {
                                val current = boundsProvider()
                                if (current != null && anchoredBottom != null) {
                                    calculateCardViewportCorrectionPx(current.bottom, anchoredBottom)
                                } else 0f
                            }
                            WorkingCardViewportAnchor.LATEST -> {
                                val layout = messageListState.layoutInfo
                                val lastIndex = layout.totalItemsCount - 1
                                val last = layout.visibleItemsInfo.firstOrNull { it.index == lastIndex }
                                if (last != null) {
                                    val visibleEnd = calculateVisibleChatViewportEndPx(
                                        viewportEndPx = layout.viewportEndOffset,
                                        obscuredBottomPx = messageBottomInsetState.value,
                                    )
                                    (last.offset + last.size - visibleEnd).toFloat()
                                } else 0f
                            }
                        }
                        if (abs(correction) >= 0.25f) {
                            messageListState.scrollBy(correction)
                        }
                    }

                    val startedAt = withFrameNanos { it }
                    var frameNanos = startedAt
                    do {
                        frameNanos = withFrameNanos { it }
                        correctViewportAnchor()
                    } while (
                        frameNanos - startedAt <=
                            (WorkingCardExpansionDurationMillis + 72L) * 1_000_000L
                    )

                    repeat(2) {
                        withFrameNanos { }
                        correctViewportAnchor()
                    }

                    if (
                        mutation == WorkingCardMutation.MANUAL_COLLAPSE &&
                        !userDraggingMessageListState.value
                    ) {
                        val collapsedViewport = messageViewportBoundsState.value
                        val collapsed = boundsProvider()
                        if (
                            before != null &&
                            collapsedViewport != null &&
                            collapsed != null &&
                            shouldCenterCollapsedCard(before.height, collapsedViewport.height)
                        ) {
                            val centerCorrection = calculateCenteredCardCorrectionPx(
                                cardTopPx = collapsed.top,
                                cardBottomPx = collapsed.bottom,
                                viewportTopPx = collapsedViewport.top,
                                viewportBottomPx = collapsedViewport.bottom,
                            )
                            if (abs(centerCorrection) >= 1f) {
                                messageListState.animateScrollBy(
                                    centerCorrection,
                                    tween(durationMillis = 180),
                                )
                            }
                        }
                    }
                } finally {
                    workingCardMutationCount = (workingCardMutationCount - 1).coerceAtLeast(0)
                }
            }
            Unit
        }
    }
    val userScrollConnection = remember(messageListState, conversation?.id) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && abs(consumed.y) >= 0.5f) {
                    manualFollowHold = false
                    followMode = if (messageListState.canScrollForward) {
                        ChatFollowMode.DETACHED
                    } else {
                        ChatFollowMode.FOLLOWING
                    }
                }
                return Offset.Zero
            }
        }
    }
    val isAtLatest by remember(messageListState) {
        derivedStateOf {
            messageListState.layoutInfo.totalItemsCount == 0 || !messageListState.canScrollForward
        }
    }

    LaunchedEffect(conversation?.id) {
        showModelPicker = false
        chatMenu = false
        recoveryDetailsMessage = null
        followMode = ChatFollowMode.FOLLOWING
        manualFollowHold = false
        initialPositioned = false
        streamingAnchorTracker.anchor = null
        streamingAnchorTracker.missingAnchorFrames = 0
        stableMessageKeysByUiIndex.clear()
        topAppBarState.contentOffset = 0f
        topAppBarState.heightOffset = 0f
    }

    LaunchedEffect(conversation?.id, paging.itemCount, initialPositioned, messageBottomInsetPx) {
        if (!initialPositioned && paging.itemCount > 0 && messageBottomInsetPx > 0) {
            val snapshot = savedScroll
            if (snapshot != null && !snapshot.atLatest) {
                val sourceIndex = snapshot.anchorNodeId?.let { nodeId ->
                    paging.itemSnapshotList.items.indexOfFirst { it.nodeId == nodeId }
                        .takeIf { it >= 0 }
                }
                val targetIndex = sourceIndex?.let {
                    chronologicalUiIndex(it, paging.itemSnapshotList.items.size)
                } ?: snapshot.firstVisibleItemIndex.coerceIn(0, paging.itemCount - 1)
                messageListState.scrollToItem(targetIndex, snapshot.firstVisibleItemOffset)
                followMode = ChatFollowMode.DETACHED
            } else {
                snapChatToBottom(messageListState, paging.itemCount - 1, messageBottomInsetPx)
            }

            val limit = snapshotFlow { topAppBarState.heightOffsetLimit }.first { it < 0f }
            val restoredHeightOffset = if (paging.itemCount > 0 && (snapshot == null || snapshot.atLatest)) {
                limit
            } else {
                chatTopBarHeightOffsetForScroll(
                    firstVisibleItemIndex = messageListState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = messageListState.firstVisibleItemScrollOffset,
                    startPx = chromeStartPx,
                    endPx = chromeEndPx,
                    heightOffsetLimit = limit,
                )
            }
            topAppBarState.heightOffset = restoredHeightOffset.coerceIn(limit, 0f)
            topAppBarState.contentOffset = restoredHeightOffset
            initialPositioned = true
        }
    }

    LaunchedEffect(messageListState, conversation?.id, topAppBarState, initialPositioned) {
        if (!initialPositioned) return@LaunchedEffect
        val conversationId = conversation?.id ?: return@LaunchedEffect
        snapshotFlow {
            val firstVisible = messageListState.layoutInfo.visibleItemsInfo.firstOrNull()
            PersistedChatScrollSample(
                anchorNodeId = (firstVisible?.key as? String)?.takeUnless { it.startsWith("loading-") },
                firstVisibleItemIndex = messageListState.firstVisibleItemIndex,
                firstVisibleItemOffset = messageListState.firstVisibleItemScrollOffset,
                atLatest = !messageListState.canScrollForward,
                topBarHeightOffset = topAppBarState.heightOffset,
            )
        }
            .distinctUntilChanged()
            .collect { snapshot ->
                viewModel.saveChatScrollSnapshot(
                    conversationId = conversationId,
                    anchorNodeId = snapshot.anchorNodeId,
                    firstVisibleItemIndex = snapshot.firstVisibleItemIndex,
                    firstVisibleItemOffset = snapshot.firstVisibleItemOffset,
                    atLatest = snapshot.atLatest,
                    topBarHeightOffset = snapshot.topBarHeightOffset,
                )
            }
    }

    LaunchedEffect(generating, initialPositioned) {
        if (generating && initialPositioned) {
            manualFollowHold = false
            followMode = ChatFollowMode.FOLLOWING
            streamingAnchorTracker.anchor = null
            streamingAnchorTracker.missingAnchorFrames = 0
        }
    }

    LaunchedEffect(
        messageListState,
        conversation?.id,
        initialPositioned,
        generating,
        messageBottomInsetPx,
        followMode,
        manualFollowHold,
        workingCardMutationCount,
    ) {
        if (
            !initialPositioned ||
            followMode != ChatFollowMode.FOLLOWING ||
            manualFollowHold ||
            workingCardMutationCount > 0
        ) return@LaunchedEffect

        var settleFramesRemaining = if (generating) Int.MAX_VALUE else 36
        var previousFrameNanos = withFrameNanos { it }
        var offscreenSeekElapsedSeconds = 0f
        while (generating || settleFramesRemaining-- > 0) {
            currentCoroutineContext().ensureActive()
            val frameNanos = withFrameNanos { it }
            val frameSeconds = ((frameNanos - previousFrameNanos).coerceAtLeast(1L) / 1_000_000_000f)
                .coerceAtMost(0.05f)
            previousFrameNanos = frameNanos

            if (followMode != ChatFollowMode.FOLLOWING || manualFollowHold) break
            if (userDraggingMessageList) continue

            val layout = messageListState.layoutInfo
            val lastIndex = layout.totalItemsCount - 1
            if (lastIndex < 0) continue
            val firstVisible = layout.visibleItemsInfo.firstOrNull()
            val lastVisible = layout.visibleItemsInfo.lastOrNull()

            val previousAnchor = streamingAnchorTracker.anchor
            val currentFirstIndex = firstVisible?.index ?: messageListState.firstVisibleItemIndex
            if (
                generating &&
                previousAnchor != null &&
                shouldRestoreStreamingAnchor(
                    previousItemIndex = previousAnchor.itemIndex,
                    currentItemIndex = currentFirstIndex,
                    userDragging = userDraggingMessageList,
                )
            ) {
                val currentItems = paging.itemSnapshotList.items
                val sourceIndex = currentItems.indexOfFirst { it.nodeId == previousAnchor.messageNodeId }
                if (sourceIndex >= 0) {
                    val targetUiIndex = chronologicalUiIndex(sourceIndex, currentItems.size)
                    if (targetUiIndex in 0 until layout.totalItemsCount) {
                        messageListState.scrollToItem(
                            targetUiIndex,
                            previousAnchor.scrollOffsetPx.coerceAtLeast(0),
                        )
                        streamingAnchorTracker.missingAnchorFrames = 0
                        withFrameNanos { }
                        continue
                    }
                } else {
                    streamingAnchorTracker.missingAnchorFrames++
                    if (streamingAnchorTracker.missingAnchorFrames > 20) {
                        streamingAnchorTracker.anchor = null
                        streamingAnchorTracker.missingAnchorFrames = 0
                    }
                }
            }

            val currentFirstKey = firstVisible?.key as? String
            if (
                currentFirstKey != null &&
                !currentFirstKey.startsWith("loading-") &&
                (previousAnchor == null || currentFirstIndex >= previousAnchor.itemIndex - 1)
            ) {
                streamingAnchorTracker.anchor = StreamingScrollAnchor(
                    messageNodeId = currentFirstKey,
                    itemIndex = currentFirstIndex,
                    scrollOffsetPx = messageListState.firstVisibleItemScrollOffset,
                )
                streamingAnchorTracker.missingAnchorFrames = 0
            }

            if (lastVisible == null || lastVisible.index < lastIndex) {
                if (messageListState.canScrollForward) {
                    offscreenSeekElapsedSeconds =
                        (offscreenSeekElapsedSeconds + frameSeconds).coerceAtMost(2f)
                    val lastKnownIndex = lastVisible?.index ?: messageListState.firstVisibleItemIndex
                    val hiddenItemCount = (lastIndex - lastKnownIndex).coerceAtLeast(1)
                    val seekSpeed = calculateAutoFollowSeekSpeedPxPerSecond(
                        hiddenItemCount = hiddenItemCount,
                        elapsedSeconds = offscreenSeekElapsedSeconds,
                        minSpeedPxPerSecond = ChatFollowSeekMinSpeedPxPerSecond,
                        maxSpeedPxPerSecond = ChatFollowSeekMaxSpeedPxPerSecond,
                    )
                    val step = min(
                        seekSpeed * frameSeconds,
                        ChatFollowSeekMaxFrameStepPx,
                    )
                    if (step > 0f) messageListState.scrollBy(step)
                }
                continue
            }

            offscreenSeekElapsedSeconds = 0f
            val visibleEnd = calculateVisibleChatViewportEndPx(
                viewportEndPx = layout.viewportEndOffset,
                obscuredBottomPx = messageBottomInsetPx,
            )
            val overflow = (lastVisible.offset + lastVisible.size - visibleEnd).toFloat()
            if (overflow > 0.5f) {
                val step = min(
                    calculateAutoFollowStepPx(
                        distancePx = overflow,
                        frameSeconds = frameSeconds,
                        maxSpeedPxPerSecond = ChatFollowMaxSpeedPxPerSecond,
                    ),
                    ChatFollowMaxFrameStepPx,
                )
                if (step > 0f) messageListState.scrollBy(step)
            }
        }
    }

    LaunchedEffect(messageListState, conversation?.id) {
        snapshotFlow { messageListState.isScrollInProgress to messageListState.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (!scrolling && !canScrollForward && !manualFollowHold) {
                    followMode = ChatFollowMode.FOLLOWING
                }
            }
    }

    LaunchedEffect(focusedMessageNodeId, pagingNodeIds, searchFocusHandled) {
        val target = focusedMessageNodeId ?: return@LaunchedEffect
        if (!searchFocusHandled) {
            val sourceIndex = paging.itemSnapshotList.items.indexOfFirst { it.nodeId == target }
            if (sourceIndex >= 0) {
                val uiIndex = chronologicalUiIndex(sourceIndex, paging.itemCount)
                manualFollowHold = true
                followMode = ChatFollowMode.DETACHED
                messageListState.scrollToItem(uiIndex.coerceAtLeast(0))
                val limit = topAppBarState.heightOffsetLimit
                if (limit < 0f) {
                    val targetOffset = if (uiIndex <= 0) 0f else limit
                    topAppBarState.heightOffset = targetOffset
                    topAppBarState.contentOffset = targetOffset
                }
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
            ChatCollapsingTranslucentTopBar(
                title = conversation?.title ?: stringResource(R.string.app_name),
                scrollBehavior = topAppBarScrollBehavior,
                blurState = blurState,
                blurStrength = chromeBlurStrength,
                edgeSoftness = chromeEdgeSoftness,
                overlayOpacity = chromeOverlayOpacity,
                topPanelHeight = CHAT_TOP_PANEL_HEIGHT_DP.dp,
                navigationIcon = {
                    if (openDrawer != null) {
                        IconButton(onClick = openDrawer) { Icon(Icons.Outlined.Menu, uiText("Conversations")) }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                },
                actions = {
                    if (pending.isNotEmpty()) Badge { Text(pending.size.toString()) }
                    conversation?.let { activeConversation ->
                        IconButton(onClick = { viewModel.requestShareConversation(activeConversation.id) }) {
                            Icon(Icons.Outlined.Share, uiText("Share portable chat"))
                        }
                    }
                    Box {
                        IconButton(onClick = { chatMenu = true }) { Icon(Icons.Outlined.MoreVert, uiText("Chat actions")) }
                        XyluneDropdownMenu(expanded = chatMenu, onDismissRequest = { chatMenu = false }) {
                            DropdownMenuItem(text = { Text(uiText("Regenerate chat name")) }, onClick = { viewModel.regenerateTitle(); chatMenu = false })
                            DropdownMenuItem(text = { Text(uiText("Chat configuration")) }, leadingIcon = { Icon(Icons.Outlined.Tune, null) }, onClick = { showChatConfiguration = true; chatMenu = false })
                        }
                    }
                },
                modelSelector = {
                    Surface(
                        onClick = {
                            if (usableProviders.isEmpty()) viewModel.openProviderSetup()
                            else showModelPicker = true
                        },
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .78f),
                        shape = CircleShape,
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Psychology, null, Modifier.size(14.dp))
                            Text(
                                uiText(buildString {
                                    if (usableProviders.isEmpty()) {
                                        append("Set up provider")
                                        return@buildString
                                    }
                                    val provider = usableProviders.firstOrNull { it.id == conversation?.selectedProviderId }
                                    if (provider != null && usableProviders.size > 1) append(provider.displayName).append(" · ")
                                    append(models.firstOrNull { it.modelId == conversation?.selectedModelId }?.displayName ?: conversation?.selectedModelId ?: "Choose model")
                                }),
                                Modifier.padding(start = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                viewModel = viewModel,
                provider = allProviders.firstOrNull { it.id == conversation?.selectedProviderId },
                model = models.firstOrNull {
                    it.providerId == conversation?.selectedProviderId && it.modelId == conversation?.selectedModelId
                },
                generating = generating,
                providerConfigured = usableProviders.isNotEmpty(),
                linuxInstalled = linuxStatus.installed,
                linuxDistributionName = linuxStatus.distribution.displayName,
                blurState = blurState,
                onOpenLinuxSetup = { viewModel.screen.value = Screen.SANDBOX },
                onImmediateSend = {
                    manualFollowHold = false
                    followMode = ChatFollowMode.FOLLOWING
                    val limit = topAppBarState.heightOffsetLimit
                    if (limit < 0f) {
                        topAppBarState.heightOffset = limit
                        topAppBarState.contentOffset = limit
                    }
                },
            )
        },
    ) { padding ->
        val messageTopGutter = 44.dp
        val messageBottomGutter = 34.dp
        val measuredMessageBottomInsetPx = with(density) {
            (padding.calculateBottomPadding() + messageBottomGutter).roundToPx()
        }
        LaunchedEffect(measuredMessageBottomInsetPx) {
            messageBottomInsetPx = measuredMessageBottomInsetPx
        }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().xyluneBackdropSource(blurState)) {
                if (paging.itemCount == 0 && recoverable.isEmpty()) {
                    EmptyConversation(
                        providerConfigured = usableProviders.isNotEmpty(),
                        onSetUpProvider = viewModel::openProviderSetup,
                        modifier = Modifier.zIndex(1f).padding(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding(),
                        ),
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(userScrollConnection)
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInRoot()
                            if (messageViewportBounds != bounds) messageViewportBounds = bounds
                        },
                    state = messageListState,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = padding.calculateTopPadding() + messageTopGutter,
                        bottom = padding.calculateBottomPadding() + messageBottomGutter,
                    ),
                ) {
                    items(
                        count = paging.itemCount,
                        key = { uiIndex ->
                            val sourceIndex = chronologicalSourceIndex(uiIndex, paging.itemCount)
                            paging.peek(sourceIndex)?.nodeId
                                ?.also { stableMessageKeysByUiIndex[uiIndex] = it }
                                ?: stableMessageKeysByUiIndex[uiIndex]
                                ?: "loading-${conversation?.id.orEmpty()}-$uiIndex"
                        },
                        contentType = { uiIndex ->
                            val sourceIndex = chronologicalSourceIndex(uiIndex, paging.itemCount)
                            paging.peek(sourceIndex)?.role
                        },
                    ) { uiIndex ->
                        val sourceIndex = chronologicalSourceIndex(uiIndex, paging.itemCount)
                        paging[sourceIndex]?.let { persistedMessage ->
                            val message = if (persistedMessage.status == MessageStatus.STREAMING) {
                                streamingPreviews[persistedMessage.nodeId]?.let { preview ->
                                    persistedMessage.copy(
                                        content = preview.content,
                                        reasoning = preview.reasoning,
                                    )
                                } ?: persistedMessage
                            } else persistedMessage
                            val branchOptions = remember(message.nodeId, revisionBranchGroups) {
                                inlineBranchOptions(message, revisionBranchGroups)
                            }
                            val viewportController = remember(
                                messageViewportBounds,
                                messageListState.isScrollInProgress,
                                applyWorkingCardMutation,
                            ) {
                                WorkingCardViewportController(
                                    viewportBounds = messageViewportBounds,
                                    listScrolling = messageListState.isScrollInProgress,
                                    applyMutation = applyWorkingCardMutation,
                                )
                            }
                            MessageCard(
                                message = message,
                                viewModel = viewModel,
                                reasoningVisibility = conversation?.reasoningVisibility ?: ReasoningVisibility.SHOW_WHILE_WORKING,
                                activeModel = selectedActiveModel,
                                branchOptions = branchOptions,
                                workingCardViewport = viewportController,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = paging.itemCount > 0 && followMode == ChatFollowMode.DETACHED && !isAtLatest,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        manualFollowHold = false
                        followMode = ChatFollowMode.FOLLOWING
                        val limit = topAppBarState.heightOffsetLimit
                        if (limit < 0f) {
                            topAppBarState.heightOffset = limit
                            topAppBarState.contentOffset = limit
                        }
                        listScope.launch { snapChatToBottom(messageListState, paging.itemCount - 1, messageBottomInsetPx) }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, uiText("Go to latest message"))
                }
            }
            val interrupted = recoverable.firstOrNull { candidate ->
                isRecoveryNoticeCandidate(
                    message = candidate,
                    activeLeafNodeId = conversation?.activeLeafNodeId,
                    dismissedNoticeKey = dismissedRecoveryNoticeKey,
                )
            }
            AnimatedVisibility(
                visible = interrupted != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding() + 8.dp, start = 12.dp, end = 12.dp),
            ) {
                interrupted?.let { message ->
                    val failed = message.status == MessageStatus.ERROR
                    Surface(
                        color = if (failed) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp))
                                Text(
                                    uiText(if (failed) "Request failed" else "Response paused"),
                                    Modifier.padding(start = 9.dp).weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                IconButton(
                                    onClick = {
                                        dismissedRecoveryNoticeKeys = withDismissedRecoveryNotice(
                                            dismissedRecoveryNoticeKeys,
                                            conversation?.id,
                                            message,
                                        )
                                    },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(Icons.Outlined.Close, uiText("Dismiss error"), Modifier.size(18.dp))
                                }
                            }
                            Text(
                                recoveryErrorSummary(message),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (failed) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { recoveryDetailsMessage = message }) {
                                    Text(uiText("Details"))
                                }
                                TextButton(onClick = {
                                    dismissedRecoveryNoticeKeys = withDismissedRecoveryNotice(
                                        dismissedRecoveryNoticeKeys,
                                        conversation?.id,
                                        message,
                                    )
                                    if (failed) viewModel.retryMessage(message) else viewModel.resume(message)
                                }) {
                                    Text(uiText(if (failed) "Retry" else "Continue"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    recoveryDetailsMessage?.let { message ->
        val dialogContext = LocalContext.current
        val fullError = message.error?.trim().orEmpty().ifBlank {
            "No additional diagnostic text was returned by the provider."
        }
        XyluneAlertDialog(
            onDismissRequest = { recoveryDetailsMessage = null },
            title = {
                Text(uiText(if (message.status == MessageStatus.ERROR) "Request error" else "Interrupted response"))
            },
            text = {
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        uiText(listOfNotNull(message.providerId, message.modelId).joinToString(" · ")
                            .ifBlank { "Provider details unavailable" }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CodeSourcePanel(
                        language = "text",
                        code = fullError,
                        title = if (message.status == MessageStatus.ERROR) "ERROR" else "DETAILS",
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialogContext.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Xylune stream error", fullError))
                }) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(uiText("Copy"))
                }
            },
            confirmButton = {
                TextButton(onClick = { recoveryDetailsMessage = null }) { Text(uiText("Close")) }
            },
        )
    }
    if (showChatConfiguration) {
        conversation?.let { current ->
            ChatConfigurationSheet(current, contextSummary, viewModel) { showChatConfiguration = false }
        } ?: run { showChatConfiguration = false }
    }
    if (showModelPicker) {
        ModelPickerSheet(
            providers = usableProviders,
            models = allModels,
            selectedProviderId = conversation?.selectedProviderId,
            selectedModelId = conversation?.selectedModelId,
            favoriteKeys = favoriteModels,
            recentKeys = recentModels,
            onToggleFavorite = viewModel::toggleFavoriteModel,
            onSelect = viewModel::selectModel,
            onDismiss = { showModelPicker = false },
        )
    }
}

internal fun normalModelPickerModels(models: List<ModelEntity>): List<ModelEntity> =
    models.sortedBy { it.displayName.lowercase() }

internal fun shouldShowOcrCompatibility(isImage: Boolean, modelSupportsVision: Boolean): Boolean =
    isImage && !modelSupportsVision

internal fun unsupportedToolCallingNotice(
    modelSupportsTools: Boolean?,
    toolCallingRequested: Boolean,
): String? = if (modelSupportsTools == false && toolCallingRequested) {
    "This model doesn't support tool calling. Web, Python, and Linux tools won't run."
} else null

@Composable
private fun EmptyConversation(
    providerConfigured: Boolean,
    onSetUpProvider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (providerConfigured) Icons.Outlined.Psychology else Icons.Outlined.Cloud,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.size(14.dp))
        Text(
            uiText(if (providerConfigured) "What are we working on?" else "Connect a model provider"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            uiText(if (providerConfigured) {
                "Ask a question, attach a file, or choose Search and Tools beside the message box."
            } else {
                "Xylune cannot send messages until ChatGPT, an API provider, or a local model server is connected."
            }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!providerConfigured) {
            Spacer(Modifier.size(18.dp))
            Button(onClick = onSetUpProvider) {
                Text(uiText("Set up a provider"))
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: app.xylune.chat.data.MessageEntity,
    viewModel: ChatViewModel,
    reasoningVisibility: ReasoningVisibility,
    activeModel: ModelEntity?,
    branchOptions: List<MessageEntity>,
    modifier: Modifier = Modifier,
    workingCardViewport: WorkingCardViewportController,
) {
    val attachments by viewModel.run { containerAttachments(message.nodeId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val working = message.status == MessageStatus.STREAMING
    val animateStreaming = working
    val user = message.role == MessageRole.USER
    val haptics = rememberXyluneHaptics()
    val encodedTimeline = remember(message.timelineJson) {
        runCatching { ChatMessageJson.decodeFromString<List<MessageTimelineEvent>>(message.timelineJson) }.getOrDefault(emptyList())
    }
    val rawTimeline = remember(encodedTimeline, message.content, message.reasoning) {
        materializeTimelineContent(encodedTimeline, message.content, message.reasoning)
    }
    val deepResearchResponse = remember(message.role, message.requestSnapshotJson) {
        ResearchStateProtocol.isDeepResearchResponse(message.role, message.requestSnapshotJson)
    }
    val researchState = remember(deepResearchResponse, rawTimeline, message.reasoning, message.content) {
        if (!deepResearchResponse) null
        else ResearchStateProtocol.latest(
            if (rawTimeline.isNotEmpty()) rawTimeline.map { it.content }
            else listOf(message.reasoning, message.content),
        )
    }
    val timeline = remember(rawTimeline, deepResearchResponse) {
        rawTimeline.map { event ->
            if (deepResearchResponse) event.copy(content = ResearchStateProtocol.extract(event.content).cleanedText)
            else event
        }.filterNot { event ->
            event.kind in setOf("text", "reasoning") && event.content.isBlank() && event.input.isBlank() && event.output.isBlank()
        }
    }
    val displayReasoning = if (deepResearchResponse) {
        remember(message.reasoning) { ResearchStateProtocol.extract(message.reasoning).cleanedText }
    } else message.reasoning
    val displayContent = if (deepResearchResponse) {
        remember(message.content) { ResearchStateProtocol.extract(message.content).cleanedText }
    } else message.content
    val showRecoveryState = shouldRenderAssistantRecoveryState(message)
    if (
        message.role == MessageRole.ASSISTANT &&
        message.status != MessageStatus.STREAMING &&
        displayContent.isBlank() &&
        displayReasoning.isBlank() &&
        timeline.isEmpty() &&
        attachments.isEmpty() &&
        !showRecoveryState
    ) return
    var editing by remember(message.nodeId) { mutableStateOf(false) }
    var editedText by remember(message.nodeId) { mutableStateOf(message.content) }
    var copied by remember(message.nodeId) { mutableStateOf(false) }
    val context = LocalContext.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = if (user) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium,
            color = if (user) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            modifier = Modifier.fillMaxWidth(if (user) .88f else 1f),
        ) {
            Column(Modifier.padding(if (user) 14.dp else 4.dp)) {
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
                                allowOcr = fallback,
                                onEnableOcr = if (fallback) ({ viewModel.enableOcr(attachment) }) else null,
                            )
                        }
                    }
                }
                if (
                    showRecoveryState &&
                    displayContent.isBlank() &&
                    displayReasoning.isBlank() &&
                    timeline.isEmpty()
                ) {
                    val failed = message.status == MessageStatus.ERROR
                    Surface(
                        color = if (failed) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                uiText(if (failed) "Request failed" else "Response paused"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                recoveryErrorSummary(message),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (failed) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        if (failed) viewModel.retryMessage(message) else viewModel.resume(message)
                                    },
                                ) {
                                    Text(uiText(if (failed) "Retry" else "Continue"))
                                }
                            }
                        }
                    }
                }
                if (deepResearchResponse && researchState != null) {
                    StreamingFade(
                        transitionKey = "${message.nodeId}:research-roadmap",
                        enabled = animateStreaming,
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        ReportedResearchRoadmap(
                            state = researchState,
                            streaming = animateStreaming,
                        )
                    }
                }
                if (timeline.isNotEmpty()) {
                    OrderedMessageTimeline(
                        messageKey = message.nodeId,
                        events = timeline,
                        attachments = attachments,
                        working = working,
                        animateStreaming = animateStreaming,
                        visibility = reasoningVisibility,
                        viewModel = viewModel,
                        workingCardViewport = workingCardViewport,
                    )
                } else {
                    LegacyWorkingBlock(
                        messageKey = message.nodeId,
                        text = displayReasoning,
                        toolTraceJson = message.toolTraceJson,
                        working = working,
                        animateStreaming = animateStreaming,
                        visibility = reasoningVisibility,
                        viewModel = viewModel,
                        workingCardViewport = workingCardViewport,
                    )
                    if (displayContent.isNotBlank() || animateStreaming) RichMessage(
                        operationScope = message.nodeId,
                        text = displayContent,
                        streaming = animateStreaming,
                        staticContent = user,
                        onRunPython = viewModel::executePython,
                        onRunUbuntu = viewModel::executeUbuntu,
                        onReviewPythonPackages = viewModel::reviewPythonPackages,
                        onInstallPackages = viewModel::installPythonPackagesAndContinue,
                        onReviewUbuntuPackages = viewModel::reviewUbuntuPackages,
                        onInstallUbuntuPackages = viewModel::installUbuntuPackagesAndContinue,
                        onWidgetSubmit = viewModel::submitWidgetResponse,
                        onReviewWidgetSecurity = viewModel::reviewWidgetSecurity,
                        onRepairGeneratedBlock = viewModel::repairGeneratedBlock,
                        onAcceptGeneratedEdit = viewModel::acceptGeneratedBlockEdit,
                        workingCardViewport = workingCardViewport,
                    )
                }
                if (
                    animateStreaming &&
                    message.content.isBlank() &&
                    timeline.isEmpty() &&
                    displayReasoning.isBlank() &&
                    message.toolTraceJson.isBlank()
                ) {
                    StreamingTokenPulse(visible = true, label = "Working")
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    val tokens = message.inputTokens + message.outputTokens
                    val cost = message.costMicros / 1_000_000.0
                    Text(
                        uiText(buildString {
                            if (!message.modelId.isNullOrBlank()) append(message.modelId)
                            if (tokens > 0) append(" • $tokens tok")
                            when {
                                message.costKnown -> append(" • $").append("%.5f".format(cost))
                                cost > 0 -> append(" • $").append("%.5f".format(cost)).append(" partial")
                                tokens > 0 -> append(" • cost unavailable")
                            }
                            if (message.status !in setOf(MessageStatus.COMPLETE, MessageStatus.STREAMING)) append(" • ${message.status.name.lowercase()}")
                        }),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (branchOptions.size > 1) {
                        InlineBranchNavigator(
                            activeNodeId = message.nodeId,
                            options = branchOptions,
                            onActivate = viewModel::activateBranch,
                        )
                    }
                    IconButton(onClick = {
                        haptics.selection()
                        val label = if (user) "message" else "response"
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            .setPrimaryClip(android.content.ClipData.newPlainText(label, message.content))
                        copied = true
                    }, modifier = Modifier.size(34.dp)) {
                        Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, if (copied) "Copied" else "Copy", Modifier.size(18.dp))
                    }
                    if (user) {
                        IconButton(onClick = { haptics.tap(); editedText = message.content; editing = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Edit, uiText("Edit message"), Modifier.size(18.dp))
                        }
                    } else if (message.role == MessageRole.ASSISTANT && message.status != MessageStatus.STREAMING) {
                        IconButton(onClick = { haptics.confirm(); viewModel.retryMessage(message) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Refresh, uiText("Retry response"), Modifier.size(18.dp))
                        }
                    }
                    MessageContextMenu(message)
                }
            }
        }
    }
    if (editing) XyluneAlertDialog(
        onDismissRequest = { editing = false },
        title = { Text(uiText("Edit message")) },
        text = {
            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                minLines = 3,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = { AssistChip(onClick = { editing = false }, label = { Text(uiText("Cancel")) }) },
        confirmButton = {
            Button(onClick = { haptics.confirm(); viewModel.editMessage(message, editedText); editing = false }, enabled = editedText.isNotBlank()) {
                Text(uiText("Save & regenerate"))
            }
        },
    )
}

@Composable
private fun InlineBranchNavigator(
    activeNodeId: String,
    options: List<MessageEntity>,
    onActivate: (MessageEntity) -> Unit,
) {
    val haptics = rememberXyluneHaptics()
    val activeIndex = options.indexOfFirst { it.nodeId == activeNodeId }.coerceAtLeast(0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        IconButton(
            onClick = { haptics.selection(); onActivate(options[activeIndex - 1]) },
            enabled = activeIndex > 0,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(Icons.Outlined.ChevronLeft, uiText("Previous branch"), Modifier.size(18.dp))
        }
        Text(
            uiText("${activeIndex + 1} / ${options.size}"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            onClick = { haptics.selection(); onActivate(options[activeIndex + 1]) },
            enabled = activeIndex < options.lastIndex,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(Icons.Outlined.ChevronRight, uiText("Next branch"), Modifier.size(18.dp))
        }
    }
}

@Composable
private fun OrderedMessageTimeline(
    messageKey: String,
    events: List<MessageTimelineEvent>,
    attachments: List<AttachmentEntity>,
    working: Boolean,
    animateStreaming: Boolean,
    visibility: ReasoningVisibility,
    viewModel: ChatViewModel,
    workingCardViewport: WorkingCardViewportController,
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
    val usedSourceUrls = remember(orderedEvents) {
        orderedEvents.filter { it.kind == "fetch" && it.status == "complete" }.mapNotNull { event ->
            runCatching { ChatMessageJson.decodeFromString<WebFetchResponse>(event.output).url }.getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: event.input.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }.toSet()
    }
    val sourceLinks = remember(orderedEvents) {
        buildList {
            orderedEvents.forEach { event ->
                addAll(extractTimelineSourceLinks(event.content))
                if (event.kind == "fetch" && event.status == "complete") {
                    val url = runCatching {
                        ChatMessageJson.decodeFromString<WebFetchResponse>(event.output).url
                    }.getOrNull()?.takeIf(String::isNotBlank)
                        ?: event.input.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    url?.let { target ->
                        val host = runCatching { target.toUri().host }.getOrNull().orEmpty().removePrefix("www.")
                        add(TimelineSourceLink(host.ifBlank { target }, target))
                    }
                }
            }
        }.distinctBy(TimelineSourceLink::url)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEachIndexed { index, segment ->
            if (segment.working) {
                val activeBlock = working && index == segments.lastIndex
                TimelineWorkingBlock(
                    stateKey = "$messageKey:${segment.events.first().id}",
                    events = segment.events,
                    active = activeBlock,
                    animateStreaming = animateStreaming && activeBlock,
                    visibility = visibility,
                    usedSourceUrls = usedSourceUrls,
                    sourceLinks = sourceLinks,
                    viewModel = viewModel,
                    workingCardViewport = workingCardViewport,
                )
            } else {
                segment.events.forEach { event ->
                    val activeEvent = animateStreaming && index == segments.lastIndex && event == segment.events.lastOrNull()
                    StreamingFade(
                        transitionKey = "$messageKey:${event.id}",
                        enabled = activeEvent,
                    ) {
                        if (event.kind == "file") {
                            attachments.firstOrNull { it.id == event.output }?.let { attachment ->
                                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("${uiText("FILE")} • ${event.label.ifBlank { uiText("Sent file") }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    AttachmentCard(attachment, allowOcr = false)
                                }
                            }
                        } else if (event.content.isNotBlank()) RichMessage(
                            operationScope = "$messageKey:${event.id}",
                            text = event.content,
                            streaming = activeEvent,
                            onRunPython = viewModel::executePython,
                            onRunUbuntu = viewModel::executeUbuntu,
                            onReviewPythonPackages = viewModel::reviewPythonPackages,
                            onInstallPackages = viewModel::installPythonPackagesAndContinue,
                            onReviewUbuntuPackages = viewModel::reviewUbuntuPackages,
                            onInstallUbuntuPackages = viewModel::installUbuntuPackagesAndContinue,
                            onWidgetSubmit = viewModel::submitWidgetResponse,
                            onReviewWidgetSecurity = viewModel::reviewWidgetSecurity,
                            onRepairGeneratedBlock = viewModel::repairGeneratedBlock,
                            onAcceptGeneratedEdit = viewModel::acceptGeneratedBlockEdit,
                            workingCardViewport = workingCardViewport,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineWorkingBlock(
    stateKey: String,
    events: List<MessageTimelineEvent>,
    active: Boolean,
    animateStreaming: Boolean,
    visibility: ReasoningVisibility,
    usedSourceUrls: Set<String>,
    sourceLinks: List<TimelineSourceLink>,
    viewModel: ChatViewModel,
    workingCardViewport: WorkingCardViewportController,
) {
    if (events.isEmpty()) return
    val defaultExpanded = workingBlockDefaultExpanded(visibility, active)
    var expanded by rememberSaveable("working-expanded-$stateKey") {
        mutableStateOf(defaultExpanded)
    }
    var previousDefaultExpanded by rememberSaveable("working-default-$stateKey") {
        mutableStateOf(defaultExpanded)
    }
    var cardBounds by remember(stateKey) { mutableStateOf<Rect?>(null) }
    var animateVisibility by remember(stateKey) { mutableStateOf(true) }
    val cardVisible = workingCardViewport.isVisible(cardBounds)
    LaunchedEffect(defaultExpanded, cardVisible, workingCardViewport.listScrolling) {
        if (previousDefaultExpanded != defaultExpanded) {
            animateVisibility = cardVisible && !workingCardViewport.listScrolling
            workingCardViewport.applyMutation(
                if (defaultExpanded) WorkingCardMutation.AUTO_EXPAND else WorkingCardMutation.AUTO_COLLAPSE,
                { cardBounds },
            ) {
                expanded = defaultExpanded
            }
            previousDefaultExpanded = defaultExpanded
            if (!animateVisibility) {
                androidx.compose.runtime.withFrameNanos { }
                animateVisibility = true
            }
        }
    }
    Surface(
        onClick = {
            animateVisibility = true
            workingCardViewport.applyMutation(
                if (expanded) WorkingCardMutation.MANUAL_COLLAPSE else WorkingCardMutation.MANUAL_EXPAND,
                { cardBounds },
            ) {
                expanded = !expanded
            }
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { cardBounds = it.boundsInRoot() },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else if (events.any { it.status == "error" }) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                } else {
                    Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text(
                        workingBlockHeadline(events, active),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        workingBlockSummary(events, active),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Outlined.ChevronRight,
                    if (expanded) "Collapse work details" else "Expand work details",
                    Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = if (animateVisibility) workingCardExpandIn() else EnterTransition.None,
                exit = if (animateVisibility) workingCardCollapseOut() else ExitTransition.None,
            ) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.forEachIndexed { index, event ->
                        val activeEvent = animateStreaming && index == events.lastIndex
                        val runId = if (event.kind == "script") scriptRunId(event.output) else null
                        val superseded = runId != null &&
                            events.drop(index + 1).any { later -> scriptRunId(later.output) == runId }
                        TimelineWorkStep(
                            stateKey = "$stateKey:${event.id}",
                            index = index,
                            event = event,
                            active = activeEvent,
                            superseded = superseded,
                            usedSourceUrls = usedSourceUrls,
                            sourceLinks = sourceLinks,
                            viewModel = viewModel,
                            workingCardViewport = workingCardViewport,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineWorkStep(
    stateKey: String,
    index: Int,
    event: MessageTimelineEvent,
    active: Boolean,
    superseded: Boolean,
    usedSourceUrls: Set<String>,
    sourceLinks: List<TimelineSourceLink>,
    viewModel: ChatViewModel,
    workingCardViewport: WorkingCardViewportController,
) {
    val hasDetails = event.content.isNotBlank() || event.input.isNotBlank() ||
        event.output.isNotBlank() || active || superseded
    val keepExpanded = event.kind in setOf("search", "native_search")
    var expanded by rememberSaveable("work-step-$stateKey") {
        mutableStateOf(active || keepExpanded)
    }
    var previouslyActive by rememberSaveable("work-step-active-$stateKey") {
        mutableStateOf(active)
    }
    LaunchedEffect(active, keepExpanded) {
        if (active != previouslyActive) {
            expanded = active || keepExpanded
            previouslyActive = active
        }
    }

    StreamingFade(transitionKey = "working-event:${event.id}", enabled = active) {
        Surface(
            onClick = { if (hasDetails) expanded = !expanded },
            enabled = hasDetails,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        active || event.status in setOf("preparing", "prepared", "running") ->
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.8.dp)
                        event.status == "error" ->
                            Icon(Icons.Outlined.Close, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        else ->
                            Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text(
                            uiText("${index + 1}. ${workEventTitle(event)}"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (event.status == "error") MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            workEventStateLabel(event),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (event.status == "error") MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (hasDetails) {
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Outlined.ChevronRight,
                            if (expanded) "Collapse step" else "Expand step",
                            Modifier.size(18.dp),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = expanded && hasDetails,
                    enter = workingCardExpandIn(),
                    exit = workingCardCollapseOut(),
                ) {
                    Column(
                        Modifier.padding(top = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (event.content.isNotBlank()) {
                            DisplayOnlyMarkdown(
                                operationScope = "reasoning:${event.id}",
                                text = event.content,
                                streaming = active,
                                workingCardViewport = workingCardViewport,
                            )
                        }
                        when {
                            superseded -> Text(
                                uiText("This attempt continued in the newer step below."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            event.kind in setOf("script", "python", "ubuntu", "search", "native_search", "fetch") ->
                                ToolStepDetails(
                                    event.kind,
                                    event.input,
                                    event.output,
                                    event.status,
                                    usedSourceUrls,
                                    sourceLinks,
                                    viewModel,
                                    workingCardViewport,
                                )
                            else -> {
                                if (event.input.isNotBlank()) {
                                    HighlightedCodeText(
                                        language = event.kind,
                                        code = event.input,
                                        style = MaterialTheme.typography.labelSmall,
                                        softWrap = true,
                                    )
                                }
                                if (event.output.isNotBlank()) {
                                    GenericToolOutputCard(
                                        event.output,
                                        failed = event.status == "error",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun scriptRunId(output: String): String? = output.takeIf(String::isNotBlank)?.let {
    runCatching { ChatMessageJson.decodeFromString<ScriptRunResult>(it).runId }.getOrNull()
}

@Composable
private fun LegacyWorkingBlock(
    messageKey: String,
    text: String,
    toolTraceJson: String,
    working: Boolean,
    animateStreaming: Boolean,
    visibility: ReasoningVisibility,
    viewModel: ChatViewModel,
    workingCardViewport: WorkingCardViewportController,
) {
    val developerSettings by viewModel.developerSettings.collectAsStateWithLifecycle()
    val showDiagnostics = developerSettings.enabled && developerSettings.toolDiagnosticsEnabled
    val traces = remember(toolTraceJson) {
        runCatching { ChatMessageJson.decodeFromString<List<ToolTraceEvent>>(toolTraceJson) }.getOrDefault(emptyList())
    }
    val hasContent = text.isNotBlank() || traces.isNotEmpty()
    if (!hasContent) return
    val defaultExpanded = workingBlockDefaultExpanded(visibility, working)
    var expanded by rememberSaveable("legacy-working-$messageKey") {
        mutableStateOf(defaultExpanded)
    }
    var previousDefaultExpanded by rememberSaveable("legacy-working-default-$messageKey") {
        mutableStateOf(defaultExpanded)
    }
    var cardBounds by remember(messageKey) { mutableStateOf<Rect?>(null) }
    var animateVisibility by remember(messageKey) { mutableStateOf(true) }
    val cardVisible = workingCardViewport.isVisible(cardBounds)
    LaunchedEffect(defaultExpanded, cardVisible, workingCardViewport.listScrolling) {
        if (previousDefaultExpanded != defaultExpanded) {
            animateVisibility = cardVisible && !workingCardViewport.listScrolling
            workingCardViewport.applyMutation(
                if (defaultExpanded) WorkingCardMutation.AUTO_EXPAND else WorkingCardMutation.AUTO_COLLAPSE,
                { cardBounds },
            ) {
                expanded = defaultExpanded
            }
            previousDefaultExpanded = defaultExpanded
            if (!animateVisibility) {
                androidx.compose.runtime.withFrameNanos { }
                animateVisibility = true
            }
        }
    }
    Surface(
        onClick = {
            animateVisibility = true
            workingCardViewport.applyMutation(
                if (expanded) WorkingCardMutation.MANUAL_COLLAPSE else WorkingCardMutation.MANUAL_EXPAND,
                { cardBounds },
            ) {
                expanded = !expanded
            }
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).onGloballyPositioned { cardBounds = it.boundsInRoot() },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else if (traces.any { it.status == "error" }) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                } else {
                    Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text(
                        uiText(when {
                            working -> traces.lastOrNull()?.label?.takeIf(String::isNotBlank) ?: "Reasoning"
                            traces.any { it.status == "error" } -> "Finished with an error"
                            else -> "Work complete"
                        }),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        uiText(if (working) "Running" else "${traces.size + if (text.isNotBlank()) 1 else 0} steps"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Outlined.ChevronRight,
                    if (expanded) "Collapse work details" else "Expand work details",
                    Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = if (animateVisibility) workingCardExpandIn() else EnterTransition.None,
                exit = if (animateVisibility) workingCardCollapseOut() else ExitTransition.None,
            ) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (text.isNotBlank()) DisplayOnlyMarkdown(
                        operationScope = "legacy-reasoning:$messageKey",
                        text = text,
                        streaming = animateStreaming,
                        workingCardViewport = workingCardViewport,
                    )
                    traces.forEach { event ->
                        StreamingFade(
                            transitionKey = "legacy-tool:${event.id}",
                            enabled = animateStreaming && event == traces.lastOrNull(),
                        ) {
                            Column {
                                Text(uiText("${event.label} • ${event.status}"), style = MaterialTheme.typography.labelMedium, color = if (event.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                if (showDiagnostics && event.input.isNotBlank()) {
                                    CodeSourcePanel(
                                        if (event.type.contains("python", true)) "python"
                                        else if (event.type.contains("ubuntu", true) || event.type.contains("shell", true)) "bash"
                                        else "input",
                                        event.input.take(4_000),
                                    )
                                }
                                if (showDiagnostics && event.output.isNotBlank()) {
                                    GenericToolOutputCard(
                                        event.output.take(12_000),
                                        failed = event.status == "error",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolStepDetails(
    kind: String,
    input: String,
    output: String,
    status: String,
    usedSourceUrls: Set<String>,
    sourceLinks: List<TimelineSourceLink>,
    viewModel: ChatViewModel,
    workingCardViewport: WorkingCardViewportController,
) {
    val developerSettings by viewModel.developerSettings.collectAsStateWithLifecycle()
    val showDiagnostics = developerSettings.enabled && developerSettings.toolDiagnosticsEnabled
    val language = if (kind == "python") "python" else if (kind == "ubuntu") "bash" else "text"
    when (kind) {
        "search", "native_search" -> CompactSearchToolCard(
            query = input,
            output = output,
            status = status,
            usedSourceUrls = usedSourceUrls,
            sourceLinks = sourceLinks,
            nativeSearch = kind == "native_search",
        )
        "fetch" -> CompactFetchToolCard(input, output, status)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showDiagnostics && input.isNotBlank()) CodeSourcePanel(
                language,
                input,
                when (kind) {
                    "python" -> "PYTHON CODE"
                    "ubuntu" -> "SHELL COMMAND"
                    else -> "INPUT"
                },
                live = status == "preparing",
            )
            val json = ChatMessageJson
            if (status == "running" && kind in setOf("script", "python", "ubuntu")) {
                val progress = output.takeIf(String::isNotBlank)
                    ?.let { runCatching { json.decodeFromString<ExecutionProgress>(it) }.getOrNull() }
                    ?: ExecutionProgress()
                LiveExecutionCard(
                    progress = progress,
                    title = when (kind) {
                        "python" -> "Python execution"
                        "ubuntu" -> "Linux execution"
                        else -> "Code execution"
                    },
                )
            } else if (output.isNotBlank()) {
                when (kind) {
                    "script", "python", "ubuntu" -> {
                        val run = runCatching { json.decodeFromString<ScriptRunResult>(output) }.getOrNull()
                        val patch = runCatching { json.decodeFromString<AppliedPatchResult>(output) }.getOrNull()
                        val read = runCatching { json.decodeFromString<WorkspaceReadResult>(output) }.getOrNull()
                        when {
                            run != null -> ScriptRunActivityCard(run, viewModel, workingCardViewport)
                            patch != null -> GenericToolOutputCard(
                                if (showDiagnostics) {
                                    "${patch.summary}\nRevision ${patch.revision ?: "workspace"} · ${patch.sourceSha256}"
                                } else {
                                    patch.summary
                                },
                                failed = false,
                            )
                            read != null -> if (showDiagnostics) {
                                CodeSourcePanel(
                                    "text",
                                    read.text,
                                    "${read.path} • lines ${read.startLine}–${read.endLine}",
                                )
                            } else {
                                Text(
                                    uiText("Source read completed"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            kind == "python" && showDiagnostics ->
                                runCatching { json.decodeFromString<ExecutionResult>(output) }.getOrNull()
                                    ?.let { PythonExecutionCard(it, "Python tool result") }
                                    ?: GenericToolOutputCard(output, failed = status == "error")
                            kind == "ubuntu" && showDiagnostics ->
                                runCatching { json.decodeFromString<UbuntuExecutionResult>(output) }.getOrNull()
                                    ?.let { UbuntuExecutionCard(it, "Ubuntu tool result") }
                                    ?: GenericToolOutputCard(output, failed = status == "error")
                            else -> Text(
                                uiText(if (status == "error") "Execution failed" else "Execution completed"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status == "error") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> if (showDiagnostics) {
                        GenericToolOutputCard(output, failed = status == "error")
                    } else {
                        Text(
                            uiText(if (status == "error") "Tool failed" else "Tool completed"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status == "error") MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptRunActivityCard(initial: ScriptRunResult, viewModel: ChatViewModel, workingCardViewport: WorkingCardViewportController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val developerSettings by viewModel.developerSettings.collectAsStateWithLifecycle()
    val showDiagnostics = developerSettings.enabled && developerSettings.toolDiagnosticsEnabled
    var results by remember(initial.runId) { mutableStateOf(listOf(initial)) }
    var source by remember(initial.runId) { mutableStateOf<String?>(null) }
    var error by remember(initial.runId) { mutableStateOf("") }
    var rerunJob by remember(initial.runId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var detailsOpen by rememberSaveable("script-details-${initial.runId}") { mutableStateOf(false) }
    var cardBounds by remember(initial.runId) { mutableStateOf<Rect?>(null) }
    val latest = results.last()
    val failed = latest.exitCode != 0 || latest.timedOut || latest.cancelled
    val diagnostics = latest.diagnostic.ifBlank { latest.stderrTail }.takeLast(4_000)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .noOpBringIntoView()
            .onGloballyPositioned { cardBounds = it.boundsInRoot() },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            scriptRunSummary(latest),
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (error.isNotBlank()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    error = ""
                    rerunJob = scope.launch {
                        runCatching { viewModel.rerunRecordedScript(initial.runId) }
                            .onSuccess { completed ->
                                workingCardViewport.applyMutation(WorkingCardMutation.AUTO_EXPAND, { cardBounds }) {
                                    results = results + completed
                                }
                            }
                            .onFailure { failure ->
                                if (failure !is CancellationException) {
                                    workingCardViewport.applyMutation(
                                        WorkingCardMutation.AUTO_EXPAND,
                                        { cardBounds },
                                    ) {
                                        error = failure.message.orEmpty()
                                    }
                                }
                            }
                    }
                },
                enabled = rerunJob?.isActive != true,
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                if (rerunJob?.isActive == true) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(uiText("Retry"))
                }
            }
            if (showDiagnostics) {
                TextButton(onClick = { detailsOpen = true }) {
                    Text(uiText("Details"))
                }
            }
            if (rerunJob?.isActive == true) {
                TextButton(onClick = { rerunJob?.cancel() }) {
                    Text(uiText("Stop"))
                }
            }
        }
    }
    if (showDiagnostics && detailsOpen) {
        XyluneAlertDialog(
            onDismissRequest = { detailsOpen = false },
            title = { Text(uiText(if (failed) "Run failed" else "Run details")) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        uiText("${latest.runtime.name.lowercase()} · attempt ${latest.attempt} · revision ${latest.revision}"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        latest.scriptPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (diagnostics.isNotBlank()) {
                        CodeSourcePanel("text", diagnostics, "ERROR")
                    }
                    if (latest.stdoutTail.isNotBlank()) {
                        CodeSourcePanel("text", latest.stdoutTail.takeLast(4_000), "OUTPUT")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsOpen = false }) {
                    Text(uiText("Close"))
                }
            },
            dismissButton = {
                Row {
                    if (diagnostics.isNotBlank()) {
                        TextButton(onClick = {
                            context.getSystemService(android.content.ClipboardManager::class.java)
                                .setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "script diagnostics",
                                        diagnostics,
                                    ),
                                )
                        }) {
                            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(uiText("Copy"))
                        }
                    }
                    TextButton(onClick = {
                        detailsOpen = false
                        scope.launch {
                            runCatching { viewModel.readScriptSource(latest.scriptPath).text }
                                .onSuccess { source = it }
                                .onFailure { error = it.message.orEmpty() }
                        }
                    }) {
                        Text(uiText("Source"))
                    }
                }
            },
        )
    }
    if (showDiagnostics) source?.let { text ->
        XyluneAlertDialog(
            onDismissRequest = { source = null },
            title = { Text(latest.scriptPath) },
            text = {
                CodeSourcePanel(
                    if (latest.runtime.name == "PYTHON") "python" else "bash",
                    text,
                    "SOURCE",
                )
            },
            confirmButton = {
                TextButton(onClick = { source = null }) {
                    Text(uiText("Close"))
                }
            },
        )
    }
}

internal fun scriptRunSummary(result: ScriptRunResult): String {
    if (result.cancelled) return "Cancelled"
    if (result.timedOut) return "Timed out"
    if (result.exitCode == 0) {
        return formatExecutionDuration(result.elapsedMs)
            .takeIf(String::isNotBlank)
            ?.let { "Completed in $it" }
            ?: "Completed"
    }
    val lines = result.diagnostic
        .ifBlank { result.stderrTail }
        .ifBlank { result.stdoutTail }
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()
    val useful = lines.lastOrNull { line ->
        listOf("error", "failed", "exception", "not found").any {
            line.contains(it, ignoreCase = true)
        }
    } ?: lines.lastOrNull()
    return useful?.take(240) ?: "Run failed with exit code ${result.exitCode}"
}

@Composable
private fun CompactSearchToolCard(
    query: String,
    output: String,
    status: String,
    usedSourceUrls: Set<String>,
    sourceLinks: List<TimelineSourceLink>,
    nativeSearch: Boolean,
) {
    val parsed = remember(output) {
        runCatching { ChatMessageJson.decodeFromString<WebSearchResponse>(output) }.getOrNull()
    }
    val results = remember(parsed, sourceLinks, nativeSearch) {
        val structured = parsed?.results.orEmpty()
        val providerResults = if (nativeSearch) {
            sourceLinks.map { source ->
                WebSearchResult(
                    title = source.title,
                    url = source.url,
                    snippet = "Result exposed by the model provider's search response.",
                )
            }
        } else emptyList()
        (structured.ifEmpty { providerResults })
            .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
            .distinctBy(WebSearchResult::url)
            .take(12)
    }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    val visibleQuery = parsed?.query?.takeIf(String::isNotBlank)
        ?: query.takeIf(String::isNotBlank)
        ?: "Query unavailable"
    val engine = parsed?.engine?.takeIf(String::isNotBlank)
        ?: if (nativeSearch) "Provider search" else "Web search"

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 7.dp).weight(1f)) {
                    Text(engine, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        uiText(when (status) {
                            "preparing" -> "Preparing query"
                            "prepared" -> "Query ready"
                            "running" -> "Searching"
                            "error" -> "Search failed"
                            else -> if (results.isEmpty()) "No result details" else "${results.size} results"
                        }),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status == "error") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        uiText("QUERY"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        visibleQuery,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (results.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results.size) { index ->
                        val result = results[index]
                        val host = runCatching { result.url.toUri().host }.getOrNull().orEmpty().removePrefix("www.")
                        val used = result.url in usedSourceUrls
                        Box {
                            Surface(
                                onClick = { selectedUrl = result.url },
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.width(260.dp),
                            ) {
                                Column(
                                    Modifier.padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        uiText("${index + 1}. ${result.title.ifBlank { host.ifBlank { result.url } }}"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        host.ifBlank { result.url },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (result.snippet.isNotBlank()) {
                                        Text(
                                            result.snippet,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (used) {
                                        Text(
                                            uiText("Opened by Xylune"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                            XyluneDropdownMenu(
                                expanded = selectedUrl == result.url,
                                onDismissRequest = { selectedUrl = null },
                                modifier = Modifier.width(330.dp),
                            ) {
                                LinkPreviewDetails(
                                    reference = LinkReferencePreview(
                                        kind = LinkReferenceKind.SOURCE,
                                        label = result.title,
                                        target = result.url,
                                        description = result.snippet,
                                    ),
                                    onDismiss = { selectedUrl = null },
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    uiText(when {
                        status == "error" && output.isNotBlank() -> output.take(700)
                        status in setOf("preparing", "prepared", "running") -> "Waiting for search results…"
                        nativeSearch -> "The provider exposed the query but did not return result metadata or citations."
                        else -> "No search results were returned."
                    }),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == "error") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CompactFetchToolCard(url: String, output: String, status: String) {
    val parsed = remember(output) { runCatching { ChatMessageJson.decodeFromString<WebFetchResponse>(output) }.getOrNull() }
    var show by remember { mutableStateOf(false) }
    val target = parsed?.url ?: url
    Box {
        Surface(
            onClick = { if (target.isNotBlank()) show = true },
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.TravelExplore, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(
                        uiText(when (status) {
                            "preparing" -> "Writing source request…"
                            "prepared" -> "Source request ready"
                            "running" -> "Reading source…"
                            "error" -> "Source failed"
                            else -> "Source read"
                        }),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(uiText(runCatching { target.toUri().host }.getOrNull().orEmpty().removePrefix("www.").ifBlank { target }), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        XyluneDropdownMenu(
            expanded = show,
            onDismissRequest = { show = false },
            modifier = Modifier.width(330.dp),
        ) {
            LinkPreviewDetails(
                reference = LinkReferencePreview(
                    kind = LinkReferenceKind.SOURCE,
                    label = "Fetched source",
                    target = target,
                    description = parsed?.contentType.orEmpty(),
                ),
                onDismiss = { show = false },
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun ReportedResearchRoadmap(
    state: ReportedResearchState,
    streaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val effectiveStatus = state.status.takeIf(String::isNotBlank)
        ?: if (streaming) "Research in progress" else "Research state reported"
    val progress = state.progress.coerceIn(0f, 1f)
    val steps = state.steps
    val stateLabel = when (state.reportState) {
        "planning" -> "Planning"
        "researching" -> "Researching"
        "synthesizing" -> "Writing report"
        "complete" -> "Complete"
        "blocked" -> "Blocked"
        else -> if (streaming) "Starting" else "Unreported"
    }

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .42f),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.TravelExplore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                Text(uiText("Research roadmap"), Modifier.padding(start = 7.dp).weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(uiText(stateLabel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Text(uiText(effectiveStatus), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            if (steps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    steps.forEach { step ->
                        val containerColor = when (step.state) {
                            "complete" -> MaterialTheme.colorScheme.primaryContainer
                            "active" -> MaterialTheme.colorScheme.tertiaryContainer
                            "blocked" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        }
                        Surface(color = containerColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                when (step.state) {
                                    "complete" -> Icon(Icons.Outlined.Check, null, Modifier.size(15.dp))
                                    "active" -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.7.dp)
                                    "blocked" -> Icon(Icons.Outlined.Close, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
                                    else -> Icon(Icons.Outlined.Schedule, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.padding(start = 7.dp).weight(1f)) {
                                    Text(step.title, style = MaterialTheme.typography.labelMedium, fontWeight = if (step.state == "active") FontWeight.SemiBold else FontWeight.Normal)
                                    if (step.detail.isNotBlank()) Text(step.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
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
    providerConfigured: Boolean,
    linuxInstalled: Boolean,
    linuxDistributionName: String,
    blurState: XyluneBackdropBlurState,
    onOpenLinuxSetup: () -> Unit,
    onImmediateSend: () -> Unit,
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsStateWithLifecycle()
    val chromeEdgeSoftness by viewModel.chromeEdgeSoftness.collectAsStateWithLifecycle()
    val chromeOverlayOpacity by viewModel.chromeOverlayOpacity.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsState()
    val staged by viewModel.stagedAttachments.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val context = LocalContext.current
    var sendMenu by remember { mutableStateOf(false) }
    var plusMenu by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val haptics = rememberXyluneHaptics()
    val imageGenerationMode = model?.supportsImageGeneration == true
    val imageGenerationBlocked = imageGenerationMode && staged.isNotEmpty()
    val hasPayload = draft.isNotBlank() && !imageGenerationBlocked || (!imageGenerationMode && staged.isNotEmpty())

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

    Box(Modifier.fillMaxWidth().imePadding()) {
        Box(
            Modifier
                .fillMaxWidth()
                .xyluneBackdropBlur(
                    state = blurState,
                    strength = chromeBlurStrength,
                    edgeSoftness = chromeEdgeSoftness,
                    overlayOpacity = chromeOverlayOpacity,
                    tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
                    edge = XyluneBlurEdge.BOTTOM,
                    panelHeight = CHAT_COMPOSER_MIN_PANEL_HEIGHT_DP.dp,
                    expandToMeasuredHeight = true,
                ),
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (generating || pending.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .72f),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (generating) {
                            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.8.dp)
                        } else {
                            Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp))
                        }
                        Text(
                            uiText(when {
                                generating && pending.isNotEmpty() -> "Working · ${pending.size} queued"
                                generating -> "Working"
                                else -> "${pending.size} queued"
                            }),
                            modifier = Modifier.padding(start = 9.dp).weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (generating) {
                            IconButton(
                                onClick = { haptics.reject(); viewModel.stop() },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Filled.Stop, uiText("Stop current response"), Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
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
            if (providerConfigured && !generating) conversation?.let { current ->
                if (imageGenerationMode) {
                    Surface(
                        color = if (imageGenerationBlocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = .72f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (imageGenerationBlocked) Icons.Outlined.WarningAmber else Icons.Outlined.Image,
                                null,
                                Modifier.size(18.dp),
                            )
                            Text(
                                uiText(if (imageGenerationBlocked) "Remove attachments first · image editing is not enabled yet"
                                else "Image generation · describe the image you want to create"),
                                Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                if (!imageGenerationMode) unsupportedToolCallingNotice(
                    modelSupportsTools = model?.supportsTools,
                    toolCallingRequested = current.webSearchEnabled ||
                        current.deepResearchEnabled ||
                        current.agentPythonEnabled ||
                        current.agentUbuntuEnabled,
                )?.let { notice ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .72f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                notice,
                                Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            if (providerConfigured && !generating && !imageGenerationMode) conversation?.let { current ->
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 36.dp,
                        end = 56.dp,
                    ),
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    haptics.tap()
                    plusMenu = true
                }, enabled = !importing && providerConfigured && !imageGenerationMode) {
                    Icon(
                        Icons.Outlined.Add,
                        if (imageGenerationMode) "Attachments are unavailable in image generation mode"
                        else "Attachments and tools",
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = viewModel::setDraft,
                    enabled = providerConfigured,
                    placeholder = {
                        Text(
                            uiText(if (!providerConfigured) "Set up a provider to start"
                            else if (generating) "Add direction…"
                            else if (imageGenerationBlocked) "Remove attachments to generate an image"
                            else if (imageGenerationMode) "Describe an image to generate…"
                            else if (conversation?.deepResearchEnabled == true) "Research request…"
                            else "Message Xylune…"),
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
                    color = if (providerConfigured && hasPayload && !importing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (providerConfigured && hasPayload && !importing) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp).combinedClickable(
                        enabled = providerConfigured && hasPayload && !importing,
                        onClick = {
                            haptics.confirm()
                            onImmediateSend()
                            viewModel.send(if (generating) SendMode.STEER else SendMode.SEND_NOW)
                        },
                        onLongClick = {
                            haptics.longPress()
                            sendMenu = true
                        },
                    ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (generating) Icons.AutoMirrored.Outlined.AltRoute
                            else if (imageGenerationMode) Icons.Outlined.Image
                            else Icons.Filled.ArrowUpward,
                            if (generating) "Steer current response"
                            else if (imageGenerationMode) "Generate image"
                            else "Send",
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
                Text(uiText("Add to chat"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
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
                if (!generating) conversation?.let { current ->
                    Text(
                        uiText("Tools"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                    )
                    ComposerToggleRow(
                        icon = Icons.Outlined.Code,
                        title = "Local Code Execution",
                        subtitle = "Run Python in this chat's persistent workspace",
                        checked = current.agentPythonEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateConversation { it.copy(agentPythonEnabled = enabled) }
                        },
                    )
                    ComposerToggleRow(
                        icon = Icons.Outlined.Terminal,
                        title = "Linux",
                        subtitle = if (linuxInstalled) {
                            "Use the $linuxDistributionName tooling workspace"
                        } else {
                            "Install a Linux workspace before enabling"
                        },
                        checked = current.agentUbuntuEnabled && linuxInstalled,
                        enabled = linuxInstalled,
                        onCheckedChange = { enabled ->
                            viewModel.updateConversation { it.copy(agentUbuntuEnabled = enabled) }
                        },
                    )
                    if (!linuxInstalled) {
                        TextButton(
                            onClick = {
                                plusMenu = false
                                onOpenLinuxSetup()
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text(uiText("Manage Linux workspace"))
                        }
                    }
                }
            }
        }
    }

    if (sendMenu) {
        ModalBottomSheet(onDismissRequest = { sendMenu = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    uiText(if (generating) "While Xylune is working" else "Send options"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                if (generating) {
                    ListItem(
                        headlineContent = { Text(uiText("Queue this message")) },
                        supportingContent = { Text(uiText(if (hasPayload) "Send after the current response finishes" else "Type a message or attach a file first")) },
                        leadingContent = { Icon(Icons.Outlined.Schedule, null) },
                        modifier = Modifier.clickable {
                            if (hasPayload) {
                                viewModel.send(SendMode.QUEUE)
                                sendMenu = false
                            }
                        },
                    )
                    ListItem(
                        headlineContent = { Text(uiText("Stop current response")) },
                        supportingContent = { Text(uiText("Keep the partial answer")) },
                        leadingContent = { Icon(Icons.Filled.Stop, null) },
                        modifier = Modifier.clickable {
                            viewModel.stop()
                            sendMenu = false
                        },
                    )
                } else {
                    ListItem(
                        headlineContent = { Text(uiText("Send now")) },
                        supportingContent = { Text(uiText(if (hasPayload) "Start a response" else "Type a message or attach a file first")) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                        modifier = Modifier.clickable {
                            if (hasPayload) {
                                onImmediateSend()
                                viewModel.send(SendMode.SEND_NOW)
                                sendMenu = false
                            }
                        },
                    )
                }
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
            if (shouldShowOcrCompatibility(isImage, modelSupportsVision)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .94f),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                ) {
                    Text(
                        uiText(if (attachment.ocrJson != null) "OCR" else "OCR on send"),
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
    val haptics = rememberXyluneHaptics()
    val options = remember(
        provider?.id,
        provider?.kind,
        model?.modelId,
        model?.supportsThinking,
        model?.reasoningMetadataAvailable,
        model?.reasoningEffortsCsv,
        model?.reasoningMandatory,
    ) {
        supportedThinkingLevels(provider, model)
    }
    val effectiveEnabled = effectiveThinkingEnabled(model, enabled)
    val effectiveEffort = defaultThinkingEffort(model, effort)
    val selectedIndex = remember(options, effectiveEnabled, effectiveEffort) {
        options.indexOfFirst { option ->
            if (!effectiveEnabled) !option.enabled else option.enabled && option.effort == effectiveEffort
        }.takeIf { it >= 0 } ?: options.indexOfFirst { it.enabled }.coerceAtLeast(0)
    }
    val selected = options.getOrNull(selectedIndex)
    var sliderTarget by remember(options) { mutableFloatStateOf(selectedIndex.toFloat()) }
    var settlingIndex by remember(options) { mutableIntStateOf(-1) }
    val sliderValue by animateFloatAsState(
        targetValue = sliderTarget,
        animationSpec = if (settlingIndex >= 0) {
            spring(dampingRatio = .72f, stiffness = 430f)
        } else {
            snap()
        },
        label = "ThinkingEffortSnap",
        finishedListener = {
            val index = settlingIndex
            if (index >= 0) {
                settlingIndex = -1
                options.getOrNull(index)?.let { option ->
                    onSelection(option.enabled, option.effort)
                }
            }
        },
    )
    LaunchedEffect(selectedIndex, options, menu) {
        if (settlingIndex < 0) sliderTarget = selectedIndex.toFloat()
    }
    val previewIndex = sliderValue.roundToInt().coerceIn(0, options.lastIndex.coerceAtLeast(0))
    val preview = options.getOrNull(previewIndex) ?: selected

    Box {
        Surface(
            onClick = {
                if (options.isNotEmpty()) {
                    haptics.tap()
                    menu = true
                }
            },
            enabled = options.isNotEmpty(),
            color = if (effectiveEnabled && options.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (effectiveEnabled && options.isNotEmpty()) MaterialTheme.colorScheme.onSecondaryContainer
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
                    uiText(when {
                        options.isEmpty() -> "Unavailable"
                        !effectiveEnabled -> "Off"
                        else -> effectiveEffort.composerName
                    }),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Filled.KeyboardArrowDown, uiText("Choose thinking level"), Modifier.size(19.dp))
            }
        }
        XyluneDropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            modifier = Modifier.width(340.dp),
            dismissOnClickOutside = true,
        ) {
            Column(
                Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(uiText("Thinking effort"), style = MaterialTheme.typography.labelLarge)
                Text(
                    uiText(preview?.label.orEmpty()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    uiText(preview?.description.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (options.size > 1) {
                    XyluneSlider(
                        value = sliderValue,
                        onValueChange = { requested ->
                            settlingIndex = -1
                            sliderTarget = requested
                        },
                        valueRange = 0f..options.lastIndex.toFloat(),
                        steps = (options.size - 2).coerceAtLeast(0),
                        snapOnRelease = true,
                        onValueChangeFinished = {
                            val index = sliderTarget.roundToInt().coerceIn(options.indices)
                            if (abs(sliderTarget - index.toFloat()) < .001f) {
                                settlingIndex = -1
                                options[index].let { option ->
                                    onSelection(option.enabled, option.effort)
                                }
                            } else {
                                settlingIndex = index
                                sliderTarget = index.toFloat()
                            }
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(uiText(options.first().label), style = MaterialTheme.typography.labelSmall)
                        Text(uiText(options.last().label), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        uiText("Release to snap to the nearest supported level"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val haptics = rememberXyluneHaptics()
    val label = when {
        deepResearchEnabled -> "Research"
        webEnabled -> "Search"
        else -> "Off"
    }
    val icon = when {
        deepResearchEnabled -> Icons.Outlined.TravelExplore
        else -> Icons.Outlined.Search
    }
    Box {
        Surface(
            onClick = {
                haptics.tap()
                menu = true
            },
            color = when {
                deepResearchEnabled -> MaterialTheme.colorScheme.tertiaryContainer
                webEnabled -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = when {
                deepResearchEnabled -> MaterialTheme.colorScheme.onTertiaryContainer
                webEnabled -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            shape = CircleShape,
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, Modifier.size(17.dp))
                Text(uiText(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.KeyboardArrowDown, uiText("Choose search mode"), Modifier.size(19.dp))
            }
        }
        XyluneDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(uiText("Search off")) },
                onClick = { haptics.selection(); onSelection(false, false); menu = false },
                leadingIcon = { Icon(Icons.Outlined.Close, null) },
            )
            DropdownMenuItem(
                text = { Text(uiText("Web search")) },
                onClick = { haptics.selection(); onSelection(true, false); menu = false },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
            )
            DropdownMenuItem(
                text = { Text(uiText("Deep Research")) },
                onClick = { haptics.selection(); onSelection(true, true); menu = false },
                leadingIcon = { Icon(Icons.Outlined.TravelExplore, null) },
            )
        }
    }
}

@Composable
private fun ToolComposerChip(
    pythonEnabled: Boolean,
    linuxEnabled: Boolean,
    linuxInstalled: Boolean,
    linuxDistributionName: String,
    onOpenLinuxSetup: () -> Unit,
    onPythonEnabled: (Boolean) -> Unit,
    onLinuxEnabled: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = rememberXyluneHaptics()
    val effectiveLinuxEnabled = linuxEnabled && linuxInstalled
    val enabledCount = listOf(pythonEnabled, effectiveLinuxEnabled).count { it }
    val label = when {
        pythonEnabled && effectiveLinuxEnabled -> "2 on"
        pythonEnabled -> "Code"
        effectiveLinuxEnabled -> "Linux"
        else -> "Off"
    }
    Box {
        Surface(
            onClick = {
                haptics.tap()
                menu = true
            },
            color = if (enabledCount > 0) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (enabledCount > 0) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape,
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.Code, null, Modifier.size(17.dp))
                Text(uiText(label), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Icon(Icons.Filled.KeyboardArrowDown, uiText("Choose chat tools"), Modifier.size(19.dp))
            }
        }
        XyluneDropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            modifier = Modifier.width(340.dp),
        ) {
            Text(
                uiText("Tools available to Xylune in this chat"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            ComposerToggleRow(
                icon = Icons.Outlined.Code,
                title = "Local Code Execution",
                subtitle = "Run Python in this chat's persistent workspace",
                checked = pythonEnabled,
                onCheckedChange = onPythonEnabled,
            )
            ComposerToggleRow(
                icon = Icons.Outlined.Terminal,
                title = "Linux",
                subtitle = if (linuxInstalled) {
                    "Use the $linuxDistributionName tooling workspace"
                } else {
                    "Install a Linux workspace before enabling"
                },
                checked = effectiveLinuxEnabled,
                enabled = linuxInstalled,
                onCheckedChange = onLinuxEnabled,
            )
            if (!linuxInstalled) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.WarningAmber, null, Modifier.size(20.dp))
                            Text(uiText("Linux workspace not installed"), fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            uiText("Install Ubuntu, Debian, or Alpine before Xylune can use Linux tools."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = {
                            menu = false
                            onOpenLinuxSetup()
                        }) {
                            Text(uiText("Manage Linux workspace"))
                        }
                    }
                }
            }
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
    val haptics = rememberXyluneHaptics()
    val activate = {
        haptics.selection()
        onClick()
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.combinedClickable(onClick = activate, onLongClick = {
            haptics.longPress()
            onClick()
        }),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComposerToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = rememberXyluneHaptics()
    val toggle = {
        val next = !checked
        haptics.toggle(next)
        onCheckedChange(next)
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = { next ->
                    haptics.toggle(next)
                    onCheckedChange(next)
                },
            )
        },
        modifier = Modifier.combinedClickable(enabled = enabled, onClick = toggle, onLongClick = {
            haptics.longPress()
            onCheckedChange(!checked)
        }),
    )
}

private fun ChatViewModel.containerAttachments(nodeId: String) = observeAttachments(nodeId)
