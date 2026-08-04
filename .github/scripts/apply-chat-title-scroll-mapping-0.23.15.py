from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, found {count}: {old[:180]!r}")
    path.write_text(text.replace(old, new, 1))


chat = Path("app/src/main/java/app/xylune/chat/ui/ChatScreen.kt")
replace_once(
    chat,
    '''internal fun chatTopBarHeightOffsetForScroll(
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
''',
    '''internal fun chatTopBarHeightOffsetForScroll(
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

internal data class ChatChromeProjection(
    val heightOffset: Float,
    val contentOffset: Float,
)

internal fun projectChatChromeFromScroll(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    startPx: Int,
    endPx: Int,
    heightOffsetLimit: Float,
): ChatChromeProjection {
    val safeIndex = firstVisibleItemIndex.coerceAtLeast(0)
    val safeOffset = firstVisibleItemScrollOffset.coerceAtLeast(0)
    val cumulativeScrollPx = (
        safeIndex.toLong() * endPx.coerceAtLeast(1).toLong() + safeOffset.toLong()
    ).coerceAtMost(Int.MAX_VALUE.toLong())
    return ChatChromeProjection(
        heightOffset = chatTopBarHeightOffsetForScroll(
            firstVisibleItemIndex = safeIndex,
            firstVisibleItemScrollOffset = safeOffset,
            startPx = startPx,
            endPx = endPx,
            heightOffsetLimit = heightOffsetLimit,
        ),
        contentOffset = -cumulativeScrollPx.toFloat(),
    )
}

private data class ChatChromeScrollSample(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val heightOffsetLimit: Float,
)

internal fun calculateAutoFollowStepPx(
''',
)

replace_once(
    chat,
    '''            // Restore chrome once after the list anchor is restored. From this
            // point onward Material's nested-scroll connection is the only live
            // owner of app-bar movement, so one finger pixel remains one consumed
            // scroll pixel instead of also shifting content through changing inset.
            val limit = snapshotFlow { topAppBarState.heightOffsetLimit }.first { it < 0f }
            val restoredHeightOffset = snapshot?.topBarHeightOffset
                ?: chatTopBarHeightOffsetForScroll(
                    firstVisibleItemIndex = messageListState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = messageListState.firstVisibleItemScrollOffset,
                    startPx = chromeStartPx,
                    endPx = chromeEndPx,
                    heightOffsetLimit = limit,
                )
            topAppBarState.heightOffset = restoredHeightOffset.coerceIn(limit, 0f)
            topAppBarState.contentOffset = -(
                messageListState.firstVisibleItemIndex * chromeEndPx +
                    messageListState.firstVisibleItemScrollOffset
                ).toFloat()
            initialPositioned = true
''',
    '''            // The header is always reconstructed from the restored list anchor.
            // A separately saved app-bar offset can become stale when paging keys,
            // message heights, or the composer inset change between sessions.
            val limit = snapshotFlow { topAppBarState.heightOffsetLimit }.first { it < 0f }
            val projection = projectChatChromeFromScroll(
                firstVisibleItemIndex = messageListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = messageListState.firstVisibleItemScrollOffset,
                startPx = chromeStartPx,
                endPx = chromeEndPx,
                heightOffsetLimit = limit,
            )
            topAppBarState.heightOffset = projection.heightOffset.coerceIn(limit, 0f)
            topAppBarState.contentOffset = projection.contentOffset
            initialPositioned = true
''',
)

persistence_anchor = '''    LaunchedEffect(messageListState, conversation?.id, topAppBarState, initialPositioned) {
        if (!initialPositioned) return@LaunchedEffect
        val conversationId = conversation?.id ?: return@LaunchedEffect
'''
scroll_mapping_effect = '''    // Make the title a direct projection of the LazyColumn position. This observes
    // user drags, fling settling, auto-follow scrollBy calls, scrollToItem jumps,
    // search navigation, restored anchors, and viewport corrections identically.
    // The Material nested-scroll connection is deliberately not attached to the
    // Scaffold, so there is only one owner and no double-consumed scroll distance.
    LaunchedEffect(messageListState, conversation?.id, topAppBarState, initialPositioned) {
        if (!initialPositioned) return@LaunchedEffect
        snapshotFlow {
            ChatChromeScrollSample(
                firstVisibleItemIndex = messageListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = messageListState.firstVisibleItemScrollOffset,
                heightOffsetLimit = topAppBarState.heightOffsetLimit,
            )
        }
            .distinctUntilChanged()
            .collect { sample ->
                if (sample.heightOffsetLimit >= 0f) return@collect
                val projection = projectChatChromeFromScroll(
                    firstVisibleItemIndex = sample.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = sample.firstVisibleItemScrollOffset,
                    startPx = chromeStartPx,
                    endPx = chromeEndPx,
                    heightOffsetLimit = sample.heightOffsetLimit,
                )
                topAppBarState.heightOffset = projection.heightOffset.coerceIn(
                    sample.heightOffsetLimit,
                    0f,
                )
                topAppBarState.contentOffset = projection.contentOffset
            }
    }

''' + persistence_anchor
replace_once(chat, persistence_anchor, scroll_mapping_effect)

replace_once(
    chat,
    '''    Scaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
''',
    '''    Scaffold(
        contentWindowInsets = WindowInsets(0),
''',
)

header = Path("app/src/main/java/app/xylune/chat/ui/ChatCollapsingTranslucentTopBar.kt")
replace_once(
    header,
    '''/**
 * Chat counterpart of [CollapsingTranslucentTopBar]. The Material top-app-bar
 * state owns the collapse distance, exactly as it does on Settings screens.
 * There is no message-index, anchor-item, timer, or independent animation state.
 */
''',
    '''/**
 * Chat counterpart of [CollapsingTranslucentTopBar]. Its Material state is driven
 * directly from the chat LazyList position, so finger scrolling, programmatic
 * auto-follow, search jumps, restoration, and viewport correction all share the
 * same collapse fraction without an independent animation owner.
 */
''',
)

test = Path("app/src/test/java/app/xylune/chat/ui/KeyboardSearchDriveRegressionTest.kt")
replace_once(
    test,
    '''    @Test
    fun topBarsUseNestedScrollAsTheirOnlyLiveScrollOwner() {
        val chat = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        assertTrue(chat.contains("Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)"))
        assertTrue(settings.contains("Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)"))
        assertFalse(chat.contains("Triple(\\n                messageListState.firstVisibleItemIndex"))
        assertFalse(settings.contains("snapshotFlow { scrollState.value to state.heightOffsetLimit }"))
        assertTrue(chat.contains("Restore chrome once after the list anchor is restored"))
        assertTrue(settings.contains("initialize the title once after measurement"))
    }
''',
    '''    @Test
    fun chatTopBarMapsEveryListMovementWhileSettingsKeepsNestedScroll() {
        val chat = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        assertFalse(chat.contains("Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)"))
        assertTrue(settings.contains("Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)"))
        assertTrue(chat.contains("ChatChromeScrollSample("))
        assertTrue(chat.contains("projectChatChromeFromScroll("))
        assertTrue(chat.contains("user drags, fling settling, auto-follow scrollBy calls"))
        assertTrue(chat.contains("topAppBarState.heightOffset = projection.heightOffset"))
        assertFalse(chat.contains("snapshot?.topBarHeightOffset"))
        assertTrue(settings.contains("initialize the title once after measurement"))
    }
''',
)

replace_once(
    test,
    '''    @Test
    fun fullCollapseBoundaryRemainsCollapsed() {
        assertEquals(1f, calculateTopChromeProgress(0, 176, 56, 176), 0f)
        assertEquals(1f, calculateTopChromeProgress(1, 0, 56, 176), 0f)
    }
''',
    '''    @Test
    fun fullCollapseBoundaryRemainsCollapsed() {
        assertEquals(1f, calculateTopChromeProgress(0, 176, 56, 176), 0f)
        assertEquals(1f, calculateTopChromeProgress(1, 0, 56, 176), 0f)
    }

    @Test
    fun chatChromeProjectionTracksTheActualListPosition() {
        val expanded = projectChatChromeFromScroll(0, 0, 56, 176, -120f)
        assertEquals(0f, expanded.heightOffset, 0f)
        assertEquals(0f, expanded.contentOffset, 0f)

        val halfway = projectChatChromeFromScroll(0, 116, 56, 176, -120f)
        assertEquals(-60f, halfway.heightOffset, .0001f)
        assertEquals(-116f, halfway.contentOffset, .0001f)

        val laterItem = projectChatChromeFromScroll(1, 0, 56, 176, -120f)
        assertEquals(-120f, laterItem.heightOffset, 0f)
        assertEquals(-176f, laterItem.contentOffset, 0f)
    }
''',
)

build = Path("app/build.gradle.kts")
replace_once(
    build,
    '''        versionCode = 183
        versionName = "0.23.14"
''',
    '''        versionCode = 184
        versionName = "0.23.15"
''',
)

changelog = Path("CHANGELOG.md")
changelog.write_text(
    '''## 0.23.15 — 2026-08-04

- Drive the chat title collapse directly from the LazyColumn's first visible item and pixel offset instead of relying on Material nested-scroll callbacks.
- Keep the header synchronized during user scrolling, auto-follow after sending, streaming, search jumps, restored positions, card expansion corrections, and programmatic scrolls.
- Reconstruct the header from the restored list anchor instead of restoring an independently persisted offset that can become stale and overlap messages.

''' + changelog.read_text()
)

Path("docs/releases/RELEASE_NOTES_0.23.15.md").write_text(
    '''# Xylune 0.23.15

## Chat title scroll synchronization

The chat header is now a direct projection of the message list's real scroll position. It collapses and expands consistently during finger scrolling, flings, automatic following after Send, streaming updates, search navigation, restored chat positions, and programmatic viewport corrections.

This also removes the separate nested-scroll owner and ignores stale saved app-bar offsets, preventing the expanded title from remaining over newly positioned messages.
'''
)
