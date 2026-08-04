#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    content = read(path)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:120]!r}")
    write(path, content.replace(old, new, 1))


# Process-local, non-durable stream preview. Room remains the source of truth.
write(
    "app/src/main/java/app/xylune/chat/generation/StreamingPreviewStore.kt",
    '''package app.xylune.chat.generation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class StreamingPreview(
    val content: String,
    val reasoning: String,
)

/**
 * Carries the newest in-process provider text directly to the visible chat.
 * Durable Room writes remain batched for efficiency and recovery, but the UI no
 * longer waits for PagingSource invalidation before it can display a token.
 */
internal object StreamingPreviewStore {
    private val mutablePreviews = MutableStateFlow<Map<String, StreamingPreview>>(emptyMap())
    val previews = mutablePreviews.asStateFlow()

    fun publish(nodeId: String, content: String, reasoning: String) {
        mutablePreviews.update { current ->
            val next = StreamingPreview(content = content, reasoning = reasoning)
            if (current[nodeId] == next) current else current + (nodeId to next)
        }
    }

    fun clear(nodeId: String) {
        mutablePreviews.update { current ->
            if (nodeId !in current) current else current - nodeId
        }
    }
}
''',
)

# Generation worker: publish every provider chunk, while keeping existing Room batching.
replace_once(
    "app/src/main/java/app/xylune/chat/generation/GenerationWorker.kt",
    """        var persistedContentLength = savedContent.length
        var persistedReasoningLength = savedReasoning.length

        // A response started on an older app version has no ordered timeline.
""",
    """        var persistedContentLength = savedContent.length
        var persistedReasoningLength = savedReasoning.length

        fun publishPreview() {
            StreamingPreviewStore.publish(
                nodeId = assistantId,
                content = savedContent,
                reasoning = savedReasoning,
            )
        }
        publishPreview()

        // A response started on an older app version has no ordered timeline.
""",
)
replace_once(
    "app/src/main/java/app/xylune/chat/generation/GenerationWorker.kt",
    """        suspend fun persistTimeline(forceMetadata: Boolean = false) {
            if (forceMetadata || timelineDirty || tracesDirty) {
""",
    """        suspend fun persistTimeline(forceMetadata: Boolean = false) {
            publishPreview()
            if (forceMetadata || timelineDirty || tracesDirty) {
""",
)
replace_once(
    "app/src/main/java/app/xylune/chat/generation/GenerationWorker.kt",
    """                        if (chunk.reasoning.isNotEmpty()) {
                            savedReasoning += chunk.reasoning
""",
    """                        val previewChanged = chunk.reasoning.isNotEmpty() || chunk.text.isNotEmpty()
                        if (chunk.reasoning.isNotEmpty()) {
                            savedReasoning += chunk.reasoning
""",
)
replace_once(
    "app/src/main/java/app/xylune/chat/generation/GenerationWorker.kt",
    """                        passFinishReason = chunk.finishReason ?: passFinishReason
                        if (pendingCharacters >= STREAM_FLUSH_CHARACTERS || System.currentTimeMillis() - lastFlush >= STREAM_FLUSH_MS) flush()
""",
    """                        passFinishReason = chunk.finishReason ?: passFinishReason
                        if (previewChanged) publishPreview()
                        if (pendingCharacters >= STREAM_FLUSH_CHARACTERS || System.currentTimeMillis() - lastFlush >= STREAM_FLUSH_MS) flush()
""",
)
replace_once(
    "app/src/main/java/app/xylune/chat/generation/GenerationWorker.kt",
    """            advanceQueue()
            Result.success()
        }
    }

    private suspend fun advanceQueue() {
""",
    """            advanceQueue()
            Result.success()
        } finally {
            // The durable row has been flushed or marked retrying/interrupted by
            // every exit path above. Avoid retaining completed previews forever.
            StreamingPreviewStore.clear(assistantId)
        }
    }

    private suspend fun advanceQueue() {
""",
)

# Chat UI: overlay the process-local preview on the durable Paging row.
replace_once(
    "app/src/main/java/app/xylune/chat/ui/ChatScreen.kt",
    "import app.xylune.chat.agent.MessageTimelineEvent\n",
    "import app.xylune.chat.agent.MessageTimelineEvent\nimport app.xylune.chat.generation.StreamingPreviewStore\n",
)
replace_once(
    "app/src/main/java/app/xylune/chat/ui/ChatScreen.kt",
    """    val generating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val revisionHistory by viewModel.revisionHistory.collectAsStateWithLifecycle()
""",
    """    val generating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingPreviews by StreamingPreviewStore.previews.collectAsStateWithLifecycle()
    val revisionHistory by viewModel.revisionHistory.collectAsStateWithLifecycle()
""",
)
replace_once(
    "app/src/main/java/app/xylune/chat/ui/ChatScreen.kt",
    """                        paging[sourceIndex]?.let { message ->
                            val branchOptions = remember(message.nodeId, revisionBranchGroups) {
""",
    """                        paging[sourceIndex]?.let { persistedMessage ->
                            val message = if (persistedMessage.status == MessageStatus.STREAMING) {
                                streamingPreviews[persistedMessage.nodeId]?.let { preview ->
                                    persistedMessage.copy(
                                        content = preview.content,
                                        reasoning = preview.reasoning,
                                    )
                                } ?: persistedMessage
                            } else persistedMessage
                            val branchOptions = remember(message.nodeId, revisionBranchGroups) {
""",
)
replace_once(
    "app/src/main/java/app/xylune/chat/ui/ChatScreen.kt",
    """                    if (displayContent.isNotBlank()) RichMessage(
""",
    """                    // Keep the renderer alive from the empty first frame so a
                    // provider's first large chunk is revealed progressively too.
                    if (displayContent.isNotBlank() || animateStreaming) RichMessage(
""",
)

# Auto-follow: cap per-frame movement so a delayed frame cannot teleport the list.
replace_once(
    "app/src/main/java/app/xylune/chat/ui/ChatScreen.kt",
    """    // Distance-sensitive exponential response. Small corrections stay gentle,
    // while a large streamed insertion receives a dramatically higher response
    // rate instead of crawling at one constant velocity.
    val distanceBoost = 1f - exp(-(distancePx / 180f).coerceAtLeast(0f))
    val responseRatePerSecond = 14f + (110f * distanceBoost)
""",
    """    // Distance-sensitive response without the previous near-teleport rate.
    // A separate per-frame cap below also protects against a delayed/janky frame.
    val distanceBoost = 1f - exp(-(distancePx / 180f).coerceAtLeast(0f))
    val responseRatePerSecond = 10f + (28f * distanceBoost)
""",
)
replace_once(
    "app/src/main/java/app/xylune/chat/ui/ChatScreen.kt",
    """private const val ChatFollowMaxSpeedPxPerSecond = 48_000f
private const val ChatFollowSeekMinSpeedPxPerSecond = 6_000f
private const val ChatFollowSeekMaxSpeedPxPerSecond = 72_000f
private const val STREAM_HAPTIC_CHARACTER_INTERVAL = 32
""",
    """private const val ChatFollowMaxSpeedPxPerSecond = 8_000f
private const val ChatFollowSeekMinSpeedPxPerSecond = 1_800f
private const val ChatFollowSeekMaxSpeedPxPerSecond = 12_000f
private const val ChatFollowMaxFrameStepPx = 128f
private const val ChatFollowSeekMaxFrameStepPx = 176f
private const val STREAM_HAPTIC_CHARACTER_INTERVAL = 32
""",
)
replace_once(
    "app/src/main/java/app/xylune/chat/ui/ChatScreen.kt",
    """                    val step = seekSpeed * frameSeconds
                    if (step > 0f) messageListState.scrollBy(step)
""",
    """                    val step = min(
                        seekSpeed * frameSeconds,
                        ChatFollowSeekMaxFrameStepPx,
                    )
                    if (step > 0f) messageListState.scrollBy(step)
""",
)
replace_once(
    "app/src/main/java/app/xylune/chat/ui/ChatScreen.kt",
    """                val step = calculateAutoFollowStepPx(
                    distancePx = overflow,
                    frameSeconds = frameSeconds,
                    maxSpeedPxPerSecond = ChatFollowMaxSpeedPxPerSecond,
                )
                if (step > 0f) messageListState.scrollBy(step)
""",
    """                val step = min(
                    calculateAutoFollowStepPx(
                        distancePx = overflow,
                        frameSeconds = frameSeconds,
                        maxSpeedPxPerSecond = ChatFollowMaxSpeedPxPerSecond,
                    ),
                    ChatFollowMaxFrameStepPx,
                )
                if (step > 0f) messageListState.scrollBy(step)
""",
)

# Streaming presentation: many small frame-aligned steps, never 96-char dumps.
motion_path = "app/src/main/java/app/xylune/chat/ui/StreamingMotion.kt"
motion = read(motion_path)
pattern = re.compile(
    r"internal fun nextStreamingTextFrame\(.*?\n}\n\n/\*\*\n \* Frame-aligns streaming commits",
    re.S,
)
replacement = '''internal fun nextStreamingTextFrame(
    rendered: String,
    target: String,
    maxStepChars: Int = 48,
): String = when {
    target == rendered -> rendered
    target.startsWith(rendered) -> {
        val backlog = target.length - rendered.length
        val adaptiveStep = when {
            backlog > 2_048 -> 48
            backlog > 1_024 -> 40
            backlog > 512 -> 32
            backlog > 256 -> 24
            backlog > 128 -> 16
            backlog > 64 -> 12
            backlog > 24 -> 10
            else -> minOf(backlog, 6)
        }
        val step = if (maxStepChars == Int.MAX_VALUE) {
            backlog
        } else {
            minOf(backlog, adaptiveStep, maxStepChars.coerceAtLeast(1))
        }
        target.take(rendered.length + step.coerceAtLeast(1))
    }
    else -> target
}

/**
 * Frame-aligns streaming commits'''
motion, count = pattern.subn(replacement, motion, count=1)
if count != 1:
    raise RuntimeError(f"{motion_path}: failed to replace nextStreamingTextFrame")
motion = motion.replace(
    "The renderer stays at 30 visible updates per\n * second",
    "The prose renderer can update at display cadence",
    1,
)
motion = motion.replace(
    "intervalNanos: Long = 33_000_000L,",
    "intervalNanos: Long = 16_500_000L,",
    1,
)
write(motion_path, motion)

replace_once(
    "app/src/main/java/app/xylune/chat/ui/RichMessage.kt",
    """        intervalNanos = if (useTableCadence) 250_000_000L else 50_000_000L,
        maxStepChars = if (useTableCadence) Int.MAX_VALUE else 96,
""",
    """        intervalNanos = if (useTableCadence) 250_000_000L else 16_500_000L,
        maxStepChars = if (useTableCadence) Int.MAX_VALUE else 48,
""",
)

# Regression tests.
write(
    "app/src/test/java/app/xylune/chat/ui/StreamingMotionTest.kt",
    '''package app.xylune.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingMotionTest {
    @Test fun largeAppendIsRevealedProgressively() {
        val target = "a".repeat(1_000)
        val first = nextStreamingTextFrame("", target)
        assertEquals(32, first.length)
        assertTrue(target.startsWith(first))
        assertTrue(first.length < target.length)
    }

    @Test fun smallAppendUsesTokenSizedMicroBatch() {
        assertEquals("hello strea", nextStreamingTextFrame("hello", "hello streaming"))
    }

    @Test fun nonAppendCorrectionIsAppliedImmediately() {
        assertEquals("replacement", nextStreamingTextFrame("old text", "replacement"))
    }

    @Test fun configuredCapIsActuallyHonored() {
        val target = "x".repeat(2_000)
        assertEquals(12, nextStreamingTextFrame("", target, maxStepChars = 12).length)
    }

    @Test fun tableCadenceCanCommitOneCompleteSnapshot() {
        val target = "x".repeat(2_000)
        assertEquals(target, nextStreamingTextFrame("", target, maxStepChars = Int.MAX_VALUE))
    }

    @Test fun finalBacklogStaysOnStreamingRenderPathUntilCaughtUp() {
        assertTrue(isStreamingRenderActive(providerStreaming = false, renderedText = "partial", targetText = "partial tail"))
        assertTrue(!isStreamingRenderActive(providerStreaming = false, renderedText = "done", targetText = "done"))
    }
}
''',
)
write(
    "app/src/test/java/app/xylune/chat/ui/StreamingPreviewIntegrationTest.kt",
    '''package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPreviewIntegrationTest {
    @Test
    fun livePreviewBypassesRoomCadenceAndScrollHasFrameCaps() {
        val worker = java.io.File("src/main/java/app/xylune/chat/generation/GenerationWorker.kt").readText()
        val screen = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val motion = java.io.File("src/main/java/app/xylune/chat/ui/StreamingMotion.kt").readText()
        val rich = java.io.File("src/main/java/app/xylune/chat/ui/RichMessage.kt").readText()

        assertTrue(worker.contains("if (previewChanged) publishPreview()"))
        assertTrue(worker.contains("StreamingPreviewStore.clear(assistantId)"))
        assertTrue(screen.contains("StreamingPreviewStore.previews.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("ChatFollowMaxFrameStepPx"))
        assertTrue(screen.contains("ChatFollowSeekMaxFrameStepPx"))
        assertTrue(motion.contains("intervalNanos: Long = 16_500_000L"))
        assertTrue(rich.contains("else 16_500_000L"))
        assertTrue(rich.contains("else 48"))
    }
}
''',
)

# Version and release documentation.
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 185\n        versionName = "0.23.16"',
    '        versionCode = 186\n        versionName = "0.23.17"',
)
changelog = read("CHANGELOG.md")
write(
    "CHANGELOG.md",
    """## 0.23.17 — 2026-08-05

- Feed in-process provider chunks directly to the visible response instead of waiting for Room/Paging invalidation.
- Replace 96-character/50-ms prose dumps with display-paced adaptive micro-batches whose configured cap is actually enforced.
- Cap auto-follow movement per frame and reduce extreme seek speeds so a delayed frame cannot teleport the chat.

""" + changelog,
)
write(
    "docs/releases/RELEASE_NOTES_0.23.17.md",
    '''# Xylune 0.23.17

## Smoother live responses

The visible response now receives each in-process provider chunk immediately through a transient preview store. Room remains the durable source of truth and is still written in efficient batches, but its PagingSource invalidation cadence no longer determines what the user sees.

Ordinary prose is revealed in small display-paced steps instead of dumping as many as 96 characters every 50 ms. The adaptive catch-up logic now respects its maximum step, and chat auto-follow has explicit per-frame movement caps so a delayed frame cannot produce a large scroll jump.
''',
)

print("Applied Xylune 0.23.17 stream preview and motion hotfix")
