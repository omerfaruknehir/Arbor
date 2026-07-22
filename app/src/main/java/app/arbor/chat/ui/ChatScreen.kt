package app.arbor.chat.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import app.arbor.chat.R
import app.arbor.chat.data.MessageEntity
import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.MessageStatus
import app.arbor.chat.data.AttachmentEntity
import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ReasoningVisibility
import app.arbor.chat.data.SendMode
import app.arbor.chat.data.ThinkingEffort
import app.arbor.chat.agent.ToolTraceEvent
import app.arbor.chat.agent.WebFetchResponse
import app.arbor.chat.agent.WebSearchResponse
import app.arbor.chat.provider.ThinkingLevelOption
import app.arbor.chat.provider.supportedThinkingLevels
import app.arbor.chat.agent.MessageTimelineEvent
import app.arbor.chat.agent.materializeTimelineContent
import app.arbor.chat.agent.groupOrderedTimeline
import app.arbor.chat.sandbox.ExecutionResult
import coil.compose.AsyncImage
import app.arbor.chat.sandbox.UbuntuExecutionResult
import app.arbor.chat.sandbox.AppliedPatchResult
import app.arbor.chat.sandbox.ScriptRunMetadata
import app.arbor.chat.sandbox.ScriptRunResult
import app.arbor.chat.sandbox.WorkspaceReadResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
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

internal fun calculateAutoFollowStepPx(
    distancePx: Float,
    frameSeconds: Float,
    maxSpeedPxPerSecond: Float,
): Float {
    if (distancePx <= 0f || frameSeconds <= 0f || maxSpeedPxPerSecond <= 0f) return 0f

    // Distance-sensitive exponential response. Small corrections stay gentle,
    // while a large streamed insertion receives a dramatically higher response
    // rate instead of crawling at one constant velocity.
    val distanceBoost = 1f - exp(-(distancePx / 180f).coerceAtLeast(0f))
    val responseRatePerSecond = 14f + (110f * distanceBoost)
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

    // The off-screen phase cannot measure the exact pixel distance to the tail,
    // so combine remaining-item distance with elapsed catch-up time. Both inputs
    // use exponential curves and are then smooth-stepped: the scroll accelerates
    // hard when far behind, but transitions continuously into the measured-tail
    // correction once the last item enters the viewport.
    val itemFactor = 1f - exp(-0.65f * hiddenItemCount.toFloat())
    val timeFactor = 1f - exp(-5f * elapsedSeconds)
    val combined = 1f - ((1f - itemFactor) * (1f - timeFactor))
    val shaped = combined * combined * (3f - (2f * combined))
    return minSpeedPxPerSecond + ((maxSpeedPxPerSecond - minSpeedPxPerSecond) * shaped)
}

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

private const val ChatFollowMaxSpeedPxPerSecond = 48_000f
private const val ChatFollowSeekMinSpeedPxPerSecond = 6_000f
private const val ChatFollowSeekMaxSpeedPxPerSecond = 72_000f

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val chromeBlurEnabled by viewModel.chromeBlurEnabled.collectAsStateWithLifecycle()
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val allProviders by viewModel.providers.collectAsStateWithLifecycle()
    val credentialRevision by viewModel.credentialRevision.collectAsStateWithLifecycle()
    val usableProviders = remember(allProviders, credentialRevision) { viewModel.configuredProviders(allProviders) }
    val recoverable by viewModel.recoverable.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val generating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val revisionHistory by viewModel.revisionHistory.collectAsStateWithLifecycle()
    val contextSummary by viewModel.contextSummary.collectAsStateWithLifecycle()
    val revisionBranchGroups = remember(revisionHistory) { buildRevisionBranchGroups(revisionHistory) }
    val paging = viewModel.messages.collectAsLazyPagingItems()
    val focusedMessageNodeId by viewModel.focusedMessageNodeId.collectAsState()
    var modelMenu by remember { mutableStateOf(false) }
    var chatMenu by remember { mutableStateOf(false) }
    var showChatConfiguration by remember { mutableStateOf(false) }
    val messageListState = rememberLazyListState()
    val userDraggingMessageList by messageListState.interactionSource.collectIsDraggedAsState()
    val listScope = rememberCoroutineScope()
    val blurState = rememberArborBackdropBlurState()
    val density = LocalDensity.current
    val topAppBarState = rememberTopAppBarState()
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val chromeStartPx = with(density) { 56.dp.roundToPx() }
    val chromeEndPx = with(density) { 176.dp.roundToPx() }
    var followMode by remember(conversation?.id) { mutableStateOf(ChatFollowMode.FOLLOWING) }
    var manualFollowHold by remember(conversation?.id) { mutableStateOf(false) }
    var initialPositioned by remember(conversation?.id) { mutableStateOf(false) }
    var messageViewportBounds by remember(conversation?.id) { mutableStateOf<Rect?>(null) }
    var messageBottomInsetPx by remember(conversation?.id) { mutableStateOf(0) }
    var workingCardMutationCount by remember(conversation?.id) { mutableIntStateOf(0) }
    val messageViewportBoundsState = rememberUpdatedState(messageViewportBounds)
    val messageBottomInsetState = rememberUpdatedState(messageBottomInsetPx)
    val followModeState = rememberUpdatedState(followMode)
    val manualFollowHoldState = rememberUpdatedState(manualFollowHold)
    val userDraggingMessageListState = rememberUpdatedState(userDraggingMessageList)
    var searchFocusHandled by remember(conversation?.id, focusedMessageNodeId) { mutableStateOf(false) }
    val streamingAnchorTracker = remember(conversation?.id) { StreamingScrollAnchorTracker() }
    val stableMessageKeysByUiIndex = remember(conversation?.id) { mutableMapOf<Int, String>() }

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
    val composerChromeProgress by remember(messageListState, chromeStartPx, chromeEndPx) {
        derivedStateOf {
            calculateComposerChromeProgressFromBottom(
                layoutInfo = messageListState.layoutInfo,
                startPx = chromeStartPx,
                endPx = chromeEndPx,
            )
        }
    }
    val topChromeProgress by remember(messageListState, chromeStartPx, chromeEndPx) {
        derivedStateOf {
            calculateTopChromeProgress(
                firstVisibleItemIndex = messageListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = messageListState.firstVisibleItemScrollOffset,
                startPx = chromeStartPx,
                endPx = chromeEndPx,
            )
        }
    }

    LaunchedEffect(conversation?.id) {
        modelMenu = false
        chatMenu = false
        followMode = ChatFollowMode.FOLLOWING
        manualFollowHold = false
        initialPositioned = false
        streamingAnchorTracker.anchor = null
        streamingAnchorTracker.missingAnchorFrames = 0
        stableMessageKeysByUiIndex.clear()
        topAppBarState.contentOffset = 0f
        topAppBarState.heightOffset = 0f

        val collapsedOffset = snapshotFlow { topAppBarState.heightOffsetLimit }
            .first { it < 0f }
        topAppBarState.heightOffset = collapsedOffset
    }

    LaunchedEffect(conversation?.id, paging.itemCount, initialPositioned, messageBottomInsetPx) {
        // Do not lock the initial position until Scaffold has measured the live
        // composer. Positioning with a zero inset is what briefly left the last
        // response behind the input controls.
        if (!initialPositioned && paging.itemCount > 0 && messageBottomInsetPx > 0) {
            snapChatToBottom(messageListState, paging.itemCount - 1, messageBottomInsetPx)
            initialPositioned = true
        }
    }

    // Reattach follow mode when generation starts, but never hard-position the
    // list here. paging.itemCount changes as tool/file/result cards are appended;
    // snapping to lastIndex on each change briefly aligned that item at the top
    // before the nonlinear follower moved back down. During generation, the
    // frame-paced follower below is the sole owner of list movement.
    LaunchedEffect(generating, initialPositioned) {
        if (generating && initialPositioned) {
            manualFollowHold = false
            followMode = ChatFollowMode.FOLLOWING
            streamingAnchorTracker.anchor = null
            streamingAnchorTracker.missingAnchorFrames = 0
        }
    }

    // Follow the growing response from the rendered layout, not from database
    // token events. A frame-paced loop remains active for the whole generation
    // and briefly after completion so the final batched Markdown frame is not
    // left under the composer. User input immediately switches followMode to
    // DETACHED and cancels this effect.
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
            // Only a finger drag suspends follow. isScrollInProgress also becomes
            // true for our own scrollBy/animate calls, which previously caused the
            // loop to suppress itself and made auto-scroll stop altogether.
            if (userDraggingMessageList) continue

            val layout = messageListState.layoutInfo
            val lastIndex = layout.totalItemsCount - 1
            if (lastIndex < 0) continue
            val firstVisible = layout.visibleItemsInfo.firstOrNull()
            val lastVisible = layout.visibleItemsInfo.lastOrNull()

            // Room invalidates the PagingSource whenever the streamed message is
            // updated. In a narrow refresh window LazyColumn can temporarily lose
            // its key anchor and report item 0, even though neither the user nor
            // the follower requested an upward scroll. Preserve the last visible
            // message key and immediately restore it before continuing downward.
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
                // Accelerate non-linearly while the tail is still off-screen. The
                // old constant-speed seek was visibly slow after large tables,
                // file cards, or tool groups appeared in one batch.
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
                    val step = seekSpeed * frameSeconds
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
                val step = calculateAutoFollowStepPx(
                    distancePx = overflow,
                    frameSeconds = frameSeconds,
                    maxSpeedPxPerSecond = ChatFollowMaxSpeedPxPerSecond,
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

    LaunchedEffect(focusedMessageNodeId, paging.itemSnapshotList.items.map { it.nodeId }, searchFocusHandled) {
        val target = focusedMessageNodeId ?: return@LaunchedEffect
        if (!searchFocusHandled) {
            val sourceIndex = paging.itemSnapshotList.items.indexOfFirst { it.nodeId == target }
            if (sourceIndex >= 0) {
                val uiIndex = chronologicalUiIndex(sourceIndex, paging.itemCount)
                manualFollowHold = true
                followMode = ChatFollowMode.DETACHED
                messageListState.scrollToItem(uiIndex.coerceAtLeast(0))
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
                blurEnabled = chromeBlurEnabled,
                blurStrength = chromeBlurStrength,
                blurProgress = topChromeProgress,
                navigationIcon = {
                    if (openDrawer != null) {
                        IconButton(onClick = openDrawer) { Icon(Icons.Outlined.Menu, "Conversations") }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                },
                actions = {
                    if (pending.isNotEmpty()) Badge { Text(pending.size.toString()) }
                    Box {
                        IconButton(onClick = { chatMenu = true }) { Icon(Icons.Outlined.MoreVert, "Chat actions") }
                        DropdownMenu(expanded = chatMenu, onDismissRequest = { chatMenu = false }) {
                            DropdownMenuItem(text = { Text("Regenerate chat name") }, onClick = { viewModel.regenerateTitle(); chatMenu = false })
                            DropdownMenuItem(text = { Text("Chat configuration") }, leadingIcon = { Icon(Icons.Outlined.Tune, null) }, onClick = { showChatConfiguration = true; chatMenu = false })
                        }
                    }
                },
                modelSelector = {
                    Box {
                        Surface(
                            onClick = { modelMenu = true },
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .78f),
                            shape = CircleShape,
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Psychology, null, Modifier.size(14.dp))
                                Text(
                                    buildString {
                                        val provider = usableProviders.firstOrNull { it.id == conversation?.selectedProviderId }
                                        if (provider != null && usableProviders.size > 1) append(provider.displayName).append(" · ")
                                        append(models.firstOrNull { it.modelId == conversation?.selectedModelId }?.displayName ?: conversation?.selectedModelId ?: "Choose model")
                                    },
                                    Modifier.padding(start = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
                chromeProgress = composerChromeProgress,
                blurState = blurState,
                onImmediateSend = {
                    manualFollowHold = false
                    followMode = ChatFollowMode.FOLLOWING
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
        val currentPagingSnapshot = paging.itemSnapshotList.items
        currentPagingSnapshot.forEachIndexed { sourceIndex, message ->
            stableMessageKeysByUiIndex[
                chronologicalUiIndex(sourceIndex, currentPagingSnapshot.size)
            ] = message.nodeId
        }
        if (!generating && currentPagingSnapshot.isNotEmpty()) {
            stableMessageKeysByUiIndex.keys.removeAll { it !in currentPagingSnapshot.indices }
        }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().arborBackdropSource(blurState)) {
                if (paging.itemCount == 0 && recoverable.isEmpty()) {
                    EmptyConversation(
                        modifier = Modifier.padding(
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
                        paging[sourceIndex]?.let { message ->
                            MessageCard(
                                message = message,
                                viewModel = viewModel,
                                reasoningVisibility = conversation?.reasoningVisibility ?: ReasoningVisibility.SHOW_WHILE_WORKING,
                                activeModel = models.firstOrNull { it.modelId == conversation?.selectedModelId },
                                branchOptions = inlineBranchOptions(message, revisionBranchGroups),
                                workingCardViewport = WorkingCardViewportController(
                                    viewportBounds = messageViewportBounds,
                                    listScrolling = messageListState.isScrollInProgress,
                                    applyMutation = applyWorkingCardMutation,
                                ),
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
                        listScope.launch { snapChatToBottom(messageListState, paging.itemCount - 1, messageBottomInsetPx) }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Go to latest message")
                }
            }
            val interrupted = recoverable.firstOrNull { it.status == MessageStatus.INTERRUPTED || it.status == MessageStatus.ERROR }
            AnimatedVisibility(interrupted != null, modifier = Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding() + 12.dp)) {
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
private fun EmptyConversation(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text("One native workspace for every model.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Text("Attach files, run local code, branch long chats, or hold Send to queue and steer.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageCard(
    message: app.arbor.chat.data.MessageEntity,
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
                                allowOcr = user,
                                onEnableOcr = if (user) ({ viewModel.enableOcr(attachment) }) else null,
                            )
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
                        workingCardViewport = workingCardViewport,
                    )
                    if (displayContent.isNotBlank()) RichMessage(
                        operationScope = message.nodeId,
                        text = displayContent,
                        streaming = animateStreaming,
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
                if (animateStreaming && message.content.isBlank()) {
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
                        val label = if (user) "message" else "response"
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            .setPrimaryClip(android.content.ClipData.newPlainText(label, message.content))
                        copied = true
                    }, modifier = Modifier.size(34.dp)) {
                        Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, if (copied) "Copied" else "Copy", Modifier.size(18.dp))
                    }
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
private fun InlineBranchNavigator(
    activeNodeId: String,
    options: List<MessageEntity>,
    onActivate: (MessageEntity) -> Unit,
) {
    val activeIndex = options.indexOfFirst { it.nodeId == activeNodeId }.coerceAtLeast(0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        IconButton(
            onClick = { onActivate(options[activeIndex - 1]) },
            enabled = activeIndex > 0,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(Icons.Outlined.ChevronLeft, "Previous branch", Modifier.size(18.dp))
        }
        Text(
            "${activeIndex + 1} / ${options.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            onClick = { onActivate(options[activeIndex + 1]) },
            enabled = activeIndex < options.lastIndex,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(Icons.Outlined.ChevronRight, "Next branch", Modifier.size(18.dp))
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
                                    Text("FILE • ${event.label.ifBlank { "Sent file" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
    @Suppress("UNUSED_PARAMETER") visibility: ReasoningVisibility,
    usedSourceUrls: Set<String>,
    viewModel: ChatViewModel,
    workingCardViewport: WorkingCardViewportController,
) {
    if (events.isEmpty()) return
    // Expansion belongs to this working block, not to chat scroll position or the
    // message-wide streaming flag. A block opens when it becomes the active work,
    // closes once that work finishes, and remains freely user-toggleable between
    // those state transitions.
    var expanded by rememberSaveable("working-expanded-$stateKey") { mutableStateOf(active) }
    var previousActive by rememberSaveable("working-active-$stateKey") { mutableStateOf(active) }
    var cardBounds by remember(stateKey) { mutableStateOf<Rect?>(null) }
    var animateVisibility by remember(stateKey) { mutableStateOf(true) }
    val cardVisible = workingCardViewport.isVisible(cardBounds)
    LaunchedEffect(active, cardVisible, workingCardViewport.listScrolling) {
        if (previousActive != active) {
            animateVisibility = cardVisible && !workingCardViewport.listScrolling
            workingCardViewport.applyMutation(
                if (active) WorkingCardMutation.AUTO_EXPAND else WorkingCardMutation.AUTO_COLLAPSE,
                { cardBounds },
            ) {
                expanded = active
            }
            previousActive = active
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
                    else Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (events.size == 1 && events.first().kind == "reasoning") "Working" else "Working • ${events.size} steps",
                        Modifier.padding(start = 8.dp).weight(1f),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(if (expanded) "Collapse" else "Expand", style = MaterialTheme.typography.labelMedium)
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = if (animateVisibility) workingCardExpandIn() else EnterTransition.None,
                    exit = if (animateVisibility) workingCardCollapseOut() else ExitTransition.None,
                ) {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        events.forEachIndexed { index, event ->
                            val activeEvent = animateStreaming && index == events.lastIndex
                            StreamingFade(transitionKey = "working-event:${event.id}", enabled = activeEvent) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val duration = event.finishedAt?.let { (it - event.startedAt).coerceAtLeast(0) }
                                    Text(
                                        buildString {
                                            append(index + 1).append(". ")
                                            append(event.label.ifBlank { if (event.kind == "reasoning") "Reasoning" else event.kind.replaceFirstChar(Char::uppercase) })
                                            if (duration != null) append(" • ").append(duration).append(" ms")
                                            when (event.status) {
                                                "preparing" -> append(" • streaming call")
                                                "prepared" -> append(" • ready")
                                                "running" -> append(" • running")
                                                "error" -> append(" • error")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (event.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                    if (event.content.isNotBlank()) StreamingPlainText(
                                        text = event.content,
                                        streaming = activeEvent,
                                    )
                                    val runId = if (event.kind == "script") scriptRunId(event.output) else null
                                    val supersededScriptActivity = runId != null && events.drop(index + 1).any { later -> scriptRunId(later.output) == runId }
                                    if (event.kind in setOf("script", "python", "ubuntu", "search", "fetch") && !supersededScriptActivity) {
                                        ToolStepDetails(event.kind, event.input, event.output, event.status, usedSourceUrls, viewModel, workingCardViewport)
                                    } else if (supersededScriptActivity) {
                                        Text("Continued in the latest attempt below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        if (event.input.isNotBlank()) HighlightedCodeText(
                                            language = event.kind,
                                            code = event.input,
                                            style = MaterialTheme.typography.labelSmall,
                                            softWrap = true,
                                        )
                                        if (event.output.isNotBlank()) {
                                            GenericToolOutputCard(event.output, failed = event.status == "error")
                                        }
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
    workingCardViewport: WorkingCardViewportController,
) {
    val traces = remember(toolTraceJson) {
        runCatching { ChatMessageJson.decodeFromString<List<ToolTraceEvent>>(toolTraceJson) }.getOrDefault(emptyList())
    }
    val hasContent = text.isNotBlank() || traces.isNotEmpty()
    if (!hasContent) return
    var expanded by rememberSaveable("legacy-working-$messageKey") { mutableStateOf(working) }
    var previousActive by rememberSaveable("legacy-working-active-$messageKey") { mutableStateOf(working) }
    var cardBounds by remember(messageKey) { mutableStateOf<Rect?>(null) }
    var animateVisibility by remember(messageKey) { mutableStateOf(true) }
    val cardVisible = workingCardViewport.isVisible(cardBounds)
    LaunchedEffect(working, cardVisible, workingCardViewport.listScrolling) {
        if (previousActive != working) {
            animateVisibility = cardVisible && !workingCardViewport.listScrolling
            workingCardViewport.applyMutation(
                if (working) WorkingCardMutation.AUTO_EXPAND else WorkingCardMutation.AUTO_COLLAPSE,
                { cardBounds },
            ) {
                expanded = working
            }
            previousActive = working
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
                else Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Working", Modifier.padding(start = 8.dp).weight(1f), fontWeight = FontWeight.Medium)
                Text(if (expanded) "Collapse" else "Expand", style = MaterialTheme.typography.labelMedium)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = if (animateVisibility) workingCardExpandIn() else EnterTransition.None,
                exit = if (animateVisibility) workingCardCollapseOut() else ExitTransition.None,
            ) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (text.isNotBlank()) StreamingPlainText(
                        text = text,
                        streaming = animateStreaming,
                    )
                    traces.forEach { event ->
                        StreamingFade(
                            transitionKey = "legacy-tool:${event.id}",
                            enabled = animateStreaming && event == traces.lastOrNull(),
                        ) {
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
}

@Composable
private fun ToolStepDetails(
    kind: String,
    input: String,
    output: String,
    status: String,
    usedSourceUrls: Set<String>,
    viewModel: ChatViewModel,
    workingCardViewport: WorkingCardViewportController,
) {
    val language = if (kind == "python") "python" else if (kind == "ubuntu") "bash" else "text"
    when (kind) {
        "search" -> CompactSearchToolCard(input, output, status, usedSourceUrls)
        "fetch" -> CompactFetchToolCard(input, output, status)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (input.isNotBlank()) CodeSourcePanel(
                language,
                input,
                when (kind) {
                    "python" -> "PYTHON CODE"
                    "ubuntu" -> "SHELL COMMAND"
                    else -> "INPUT"
                },
                live = status == "preparing",
            )
            if (output.isNotBlank()) {
                val json = ChatMessageJson
                when (kind) {
                    "script", "python", "ubuntu" -> {
                        val run = runCatching { json.decodeFromString<ScriptRunResult>(output) }.getOrNull()
                        val patch = runCatching { json.decodeFromString<AppliedPatchResult>(output) }.getOrNull()
                        val read = runCatching { json.decodeFromString<WorkspaceReadResult>(output) }.getOrNull()
                        when {
                            run != null -> ScriptRunActivityCard(run, viewModel, workingCardViewport)
                            patch != null -> GenericToolOutputCard("${patch.summary}\nRevision ${patch.revision ?: "workspace"} · ${patch.sourceSha256}", failed = false)
                            read != null -> CodeSourcePanel("text", read.text, "${read.path} • lines ${read.startLine}–${read.endLine}")
                            kind == "python" -> runCatching { json.decodeFromString<ExecutionResult>(output) }.getOrNull()?.let { PythonExecutionCard(it, "Python tool result") }
                                ?: GenericToolOutputCard(output, failed = status == "error")
                            else -> runCatching { json.decodeFromString<UbuntuExecutionResult>(output) }.getOrNull()?.let { UbuntuExecutionCard(it, "Ubuntu tool result") }
                                ?: GenericToolOutputCard(output, failed = status == "error")
                        }
                    }
                    else -> GenericToolOutputCard(output, failed = status == "error")
                }
            }
        }
    }
}

@Composable
private fun ScriptRunActivityCard(initial: ScriptRunResult, viewModel: ChatViewModel, workingCardViewport: WorkingCardViewportController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var results by remember(initial.runId) { mutableStateOf(listOf(initial)) }
    var metadata by remember(initial.runId) { mutableStateOf<ScriptRunMetadata?>(null) }
    var source by remember(initial.runId) { mutableStateOf<String?>(null) }
    var error by remember(initial.runId) { mutableStateOf("") }
    var rerunJob by remember(initial.runId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var cardBounds by remember(initial.runId) { mutableStateOf<Rect?>(null) }
    LaunchedEffect(initial.runId, results.size) {
        runCatching { viewModel.scriptRunMetadata(initial.runId) }.getOrNull()?.let { loaded ->
            workingCardViewport.applyMutation(WorkingCardMutation.AUTO_EXPAND, { cardBounds }) { metadata = loaded }
        }
    }
    val latest = results.last()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().noOpBringIntoView().onGloballyPositioned { cardBounds = it.boundsInRoot() },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Code, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 7.dp).weight(1f)) {
                    Text("Script run", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text("${latest.runtime.name.lowercase()} · ${latest.scriptPath}", style = MaterialTheme.typography.labelSmall)
                }
                if (rerunJob?.isActive == true) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
            }
            (metadata?.attempts.orEmpty()).forEach { attempt ->
                val changed = metadata?.patches?.filter { it.revision == attempt.revision }?.sumOf { it.changedLines } ?: 0
                Text(
                    "Revision ${attempt.revision} · ${if (attempt.exitCode == 0 && !attempt.timedOut && !attempt.cancelled) "succeeded" else "failed"}" +
                        (if (changed > 0) " · $changed lines changed" else "") + " · attempt ${attempt.attempt}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (attempt.exitCode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            val diagnostics = latest.diagnostic.ifBlank { latest.stderrTail }.takeLast(4_000)
            if (diagnostics.isNotBlank()) CodeSourcePanel("text", diagnostics, "DIAGNOSTICS")
            if (latest.stdoutTail.isNotBlank()) CodeSourcePanel("text", latest.stdoutTail.takeLast(4_000), "OUTPUT")
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {
                    scope.launch {
                        runCatching { viewModel.readScriptSource(latest.scriptPath).text }
                            .onSuccess { source = it }
                            .onFailure { error = it.message.orEmpty() }
                    }
                }, label = { Text("Open source") })
                AssistChip(onClick = {
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("script path", latest.scriptPath))
                }, label = { Text("Copy path") })
                AssistChip(onClick = {
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("script diagnostics", diagnostics))
                }, label = { Text("Copy diagnostics") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    error = ""
                    rerunJob = scope.launch {
                        runCatching { viewModel.rerunRecordedScript(initial.runId) }
                            .onSuccess { completed ->
                                workingCardViewport.applyMutation(WorkingCardMutation.AUTO_EXPAND, { cardBounds }) { results = results + completed }
                            }
                            .onFailure { failure ->
                                if (failure !is CancellationException) workingCardViewport.applyMutation(WorkingCardMutation.AUTO_EXPAND, { cardBounds }) {
                                    error = failure.message.orEmpty()
                                }
                            }
                    }
                }, enabled = rerunJob?.isActive != true) { Icon(Icons.Outlined.Refresh, null); Text("Rerun") }
                if (rerunJob?.isActive == true) Button(onClick = { rerunJob?.cancel() }) { Icon(Icons.Filled.Stop, null); Text("Stop") }
            }
        }
    }
    source?.let { text ->
        AlertDialog(
            onDismissRequest = { source = null },
            title = { Text(latest.scriptPath) },
            text = { CodeSourcePanel(if (latest.runtime.name == "PYTHON") "python" else "bash", text, "BOUNDED WORKSPACE SOURCE") },
            confirmButton = { Button(onClick = { source = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun CompactSearchToolCard(query: String, output: String, status: String, usedSourceUrls: Set<String>) {
    val parsed = remember(output) { runCatching { ChatMessageJson.decodeFromString<WebSearchResponse>(output) }.getOrNull() }
    val usedHosts = remember(usedSourceUrls) { usedSourceUrls.mapNotNull { runCatching { Uri.parse(it).host }.getOrNull() }.toSet() }
    val sites = remember(parsed, usedHosts) {
        parsed?.results.orEmpty()
            .filter { result -> usedHosts.isNotEmpty() && runCatching { Uri.parse(result.url).host }.getOrNull() in usedHosts }
            .distinctBy { runCatching { Uri.parse(it.url).host }.getOrNull() }
            .take(8)
    }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Search", Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    when (status) {
                        "preparing" -> "Writing query…"
                        "prepared" -> "Ready"
                        "running" -> "Searching…"
                        else -> if (sites.isEmpty()) "No opened sources" else "${sites.size} used"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(parsed?.query ?: query, style = MaterialTheme.typography.bodyMedium)
            if (sites.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sites.size) { index ->
                        val result = sites[index]
                        val host = runCatching { Uri.parse(result.url).host }.getOrNull().orEmpty().removePrefix("www.")
                        Box {
                            AssistChip(
                                onClick = { selectedUrl = result.url },
                                label = { Text(host.ifBlank { result.title }, maxLines = 1) },
                                leadingIcon = { Icon(Icons.Outlined.TravelExplore, null, Modifier.size(15.dp)) },
                            )
                            DropdownMenu(
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
            } else if (output.isNotBlank() && status == "error") {
                Text(output.take(500), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                        when (status) {
                            "preparing" -> "Writing source request…"
                            "prepared" -> "Source request ready"
                            "running" -> "Reading source…"
                            else -> "Source read"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(runCatching { Uri.parse(target).host }.getOrNull().orEmpty().removePrefix("www.").ifBlank { target }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        DropdownMenu(
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
                Text("Research roadmap", Modifier.padding(start = 7.dp).weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Text(effectiveStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
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
    chromeProgress: Float,
    blurState: ArborBackdropBlurState,
    onImmediateSend: () -> Unit,
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val chromeBlurEnabled by viewModel.chromeBlurEnabled.collectAsStateWithLifecycle()
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsStateWithLifecycle()
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

    Box(Modifier.fillMaxWidth().imePadding()) {
        Box(
            Modifier
                .fillMaxWidth()
                .arborBackdropBlur(
                    state = blurState,
                    enabled = chromeBlurEnabled,
                    progress = chromeProgress,
                    strength = chromeBlurStrength,
                    tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
                    edge = ArborBlurEdge.BOTTOM,
                    fadeDistance = 112.dp,
                ),
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
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
                        onClick = {
                            if (generating && draft.isBlank() && staged.isEmpty()) {
                                viewModel.stop()
                            } else {
                                onImmediateSend()
                                viewModel.send()
                            }
                        },
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
                        onClick = { if (hasPayload) { onImmediateSend(); viewModel.send(if (generating) SendMode.STEER else SendMode.SEND_NOW); sendMenu = false } },
                        onLongClick = { if (hasPayload) { onImmediateSend(); viewModel.send(if (generating) SendMode.STEER else SendMode.SEND_NOW); sendMenu = false } },
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
                    modifier = Modifier.combinedClickable(onClick = { if (hasPayload) { onImmediateSend(); viewModel.send(SendMode.SEND_NOW); sendMenu = false } }, onLongClick = { if (hasPayload) { onImmediateSend(); viewModel.send(SendMode.SEND_NOW); sendMenu = false } }),
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
        deepResearchEnabled -> "Deep research"
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
