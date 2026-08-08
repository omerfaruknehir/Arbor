from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}")
    p.write_text(text.replace(old, new, 1))


chat = "app/src/main/java/app/xylune/chat/ui/ChatScreen.kt"

replace_once(
    chat,
    '''    } else {
        "The response stopped before it completed."
    }

internal fun workEventStateLabel(event: MessageTimelineEvent): String = when (event.status) {''',
    '''    } else {
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

internal fun workEventStateLabel(event: MessageTimelineEvent): String = when (event.status) {''',
)

replace_once(
    chat,
    '''        recoveryDetailsMessage = null
        dismissedRecoveryNoticeKey = null
        followMode = ChatFollowMode.FOLLOWING''',
    '''        recoveryDetailsMessage = null
        followMode = ChatFollowMode.FOLLOWING''',
)

replace_once(
    chat,
    '''            val interrupted = recoverable.firstOrNull { candidate ->
                val recoverableStatus = candidate.status == MessageStatus.ERROR ||
                    (candidate.status == MessageStatus.INTERRUPTED &&
                        candidate.error !in setOf("Steered by user", "Replaced by an edited message"))
                recoverableStatus && recoveryNoticeKey(candidate) != dismissedRecoveryNoticeKey
            }
            AnimatedVisibility(
                visible = interrupted != null && !generating,''',
    '''            val interrupted = recoverable.firstOrNull { candidate ->
                isRecoveryNoticeCandidate(
                    message = candidate,
                    activeLeafNodeId = conversation?.activeLeafNodeId,
                    dismissedNoticeKey = dismissedRecoveryNoticeKey,
                )
            }
            AnimatedVisibility(
                visible = interrupted != null,''',
)

replace_once(
    chat,
    '''    if (
        message.role == MessageRole.ASSISTANT &&
        message.status != MessageStatus.STREAMING &&
        displayContent.isBlank() &&
        displayReasoning.isBlank() &&
        timeline.isEmpty() &&
        attachments.isEmpty()
    ) return''',
    '''    val showRecoveryState = shouldRenderAssistantRecoveryState(message)
    if (
        message.role == MessageRole.ASSISTANT &&
        message.status != MessageStatus.STREAMING &&
        displayContent.isBlank() &&
        displayReasoning.isBlank() &&
        timeline.isEmpty() &&
        attachments.isEmpty() &&
        !showRecoveryState
    ) return''',
)

replace_once(
    chat,
    '''                if (deepResearchResponse && researchState != null) {''',
    '''                if (
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
                                if (failed) "Request failed" else "Response paused",
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
                                    Text(if (failed) "Retry" else "Continue")
                                }
                            }
                        }
                    }
                }
                if (deepResearchResponse && researchState != null) {''',
)

test = "app/src/test/java/app/xylune/chat/ui/SearchAndStreamErrorVisibilityTest.kt"
replace_once(
    test,
    '''import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue''',
    '''import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue''',
)
replace_once(
    test,
    '''    @Test
    fun `recovery notice identity changes for a new error revision`() {
        val first = failedMessage(updatedAt = 10, error = "HTTP 429: rate limited")
        val updated = first.copy(updatedAt = 11, error = "HTTP 503: unavailable")
        assertNotEquals(recoveryNoticeKey(first), recoveryNoticeKey(updated))
        assertTrue(recoveryErrorSummary(first).contains("429"))
    }

    private fun failedMessage''',
    '''    @Test
    fun `recovery notice identity changes for a new error revision`() {
        val first = failedMessage(updatedAt = 10, error = "HTTP 429: rate limited")
        val updated = first.copy(updatedAt = 11, error = "HTTP 503: unavailable")
        assertNotEquals(recoveryNoticeKey(first), recoveryNoticeKey(updated))
        assertTrue(recoveryErrorSummary(first).contains("429"))
    }

    @Test
    fun `recovery notice only targets active undismissed failure`() {
        val failed = failedMessage(updatedAt = 10, error = "HTTP 503: unavailable")
        assertTrue(isRecoveryNoticeCandidate(failed, failed.nodeId, null))
        assertFalse(isRecoveryNoticeCandidate(failed, "assistant-2", null))
        assertFalse(isRecoveryNoticeCandidate(failed, failed.nodeId, recoveryNoticeKey(failed)))
        assertFalse(
            isRecoveryNoticeCandidate(
                failed.copy(status = MessageStatus.INTERRUPTED, error = "Steered by user"),
                failed.nodeId,
                null,
            ),
        )
    }

    @Test
    fun `empty terminal assistant remains visible for recovery`() {
        val failed = failedMessage(updatedAt = 10, error = "provider failed before first token")
        assertTrue(shouldRenderAssistantRecoveryState(failed))
        assertFalse(
            shouldRenderAssistantRecoveryState(
                failed.copy(status = MessageStatus.COMPLETE, error = null),
            ),
        )
    }

    private fun failedMessage''',
)

gradle = "app/build.gradle.kts"
replace_once(
    gradle,
    '        versionCode = 202\n        versionName = "0.24.13"',
    '        versionCode = 203\n        versionName = "0.24.14"',
)

Path("docs/releases/RELEASE_NOTES_0.24.14.md").write_text(
    '''# Xylune 0.24.14

## Reliable error recovery

Failed requests that stop before producing their first token now stay visible in the conversation with the provider error and a Retry action. Interrupted responses likewise remain visible with a Continue action, so a failed generation can no longer disappear as an empty assistant message.

The recovery banner now follows only the active conversation leaf, remembers dismissals while switching chats, and is no longer hidden by an unrelated streaming row. This prevents stale error banners from briefly flashing when a chat is reopened.

## Scrollable provider-call usage

The per-message Usage details popup now keeps its summary and actions fixed while the provider-call breakdown scrolls inside a bounded area. Long retry and tool-call chains no longer push the dialog beyond the screen.
'''
)
