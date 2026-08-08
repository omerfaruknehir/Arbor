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
    '''internal fun shouldRenderAssistantRecoveryState(message: MessageEntity): Boolean =
    message.role == MessageRole.ASSISTANT && isActionableRecoveryMessage(message)

internal fun workEventStateLabel''',
    '''internal fun shouldRenderAssistantRecoveryState(message: MessageEntity): Boolean =
    message.role == MessageRole.ASSISTANT && isActionableRecoveryMessage(message)

internal fun withDismissedRecoveryNotice(
    current: Map<String, String>,
    conversationId: String?,
    message: MessageEntity,
): Map<String, String> = conversationId?.let { id ->
    current + (id to recoveryNoticeKey(message))
} ?: current

internal fun workEventStateLabel''',
)
replace_once(
    chat,
    '''    var dismissedRecoveryNoticeKey by rememberSaveable(conversation?.id) { mutableStateOf<String?>(null) }
    var recoveryDetailsMessage by remember(conversation?.id) { mutableStateOf<MessageEntity?>(null) }''',
    '''    var dismissedRecoveryNoticeKeys by rememberSaveable {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    val dismissedRecoveryNoticeKey = conversation?.id?.let { dismissedRecoveryNoticeKeys[it] }
    var recoveryDetailsMessage by remember(conversation?.id) { mutableStateOf<MessageEntity?>(null) }''',
)
replace_once(
    chat,
    '''                                    onClick = { dismissedRecoveryNoticeKey = recoveryNoticeKey(message) },''',
    '''                                    onClick = {
                                        dismissedRecoveryNoticeKeys = withDismissedRecoveryNotice(
                                            dismissedRecoveryNoticeKeys,
                                            conversation?.id,
                                            message,
                                        )
                                    },''',
)
replace_once(
    chat,
    '''                                TextButton(onClick = {
                                    dismissedRecoveryNoticeKey = recoveryNoticeKey(message)
                                    if (failed) viewModel.retryMessage(message) else viewModel.resume(message)
                                }) {''',
    '''                                TextButton(onClick = {
                                    dismissedRecoveryNoticeKeys = withDismissedRecoveryNotice(
                                        dismissedRecoveryNoticeKeys,
                                        conversation?.id,
                                        message,
                                    )
                                    if (failed) viewModel.retryMessage(message) else viewModel.resume(message)
                                }) {''',
)

test = "app/src/test/java/app/xylune/chat/ui/SearchAndStreamErrorVisibilityTest.kt"
replace_once(
    test,
    '''    @Test
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
    '''    @Test
    fun `empty terminal assistant remains visible for recovery`() {
        val failed = failedMessage(updatedAt = 10, error = "provider failed before first token")
        assertTrue(shouldRenderAssistantRecoveryState(failed))
        assertFalse(
            shouldRenderAssistantRecoveryState(
                failed.copy(status = MessageStatus.COMPLETE, error = null),
            ),
        )
    }

    @Test
    fun `dismissed recovery notices survive switching conversations`() {
        val first = failedMessage(updatedAt = 10, error = "first")
        val second = first.copy(nodeId = "assistant-2", conversationId = "conversation-2", error = "second")
        val afterFirst = withDismissedRecoveryNotice(emptyMap(), first.conversationId, first)
        val afterSecond = withDismissedRecoveryNotice(afterFirst, second.conversationId, second)
        assertEquals(recoveryNoticeKey(first), afterSecond[first.conversationId])
        assertEquals(recoveryNoticeKey(second), afterSecond[second.conversationId])
    }

    private fun failedMessage''',
)

gradle = "app/build.gradle.kts"
replace_once(
    gradle,
    '        versionCode = 203\n        versionName = "0.24.14"',
    '        versionCode = 204\n        versionName = "0.24.15"',
)

Path("docs/releases/RELEASE_NOTES_0.24.15.md").write_text(
    '''# Xylune 0.24.15

## Reliable error recovery

Failed requests that stop before producing their first token stay visible in the conversation with the provider error and a Retry action. Interrupted responses likewise remain visible with a Continue action, so a failed generation cannot disappear as an empty assistant message.

Recovery banners are limited to the active conversation leaf and their dismissal is tracked per conversation across chat switches. Reopening a chat therefore does not briefly flash an already-dismissed stale error, while a genuinely new error revision can still surface normally.

## Scrollable provider-call usage

The per-message Usage details popup keeps its summary and actions fixed while the provider-call breakdown scrolls inside a bounded area. Long retry and tool-call chains no longer push the dialog beyond the screen.
'''
)
