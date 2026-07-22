package app.arbor.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun shouldOpenDrawerFromEdgeSwipe(
    startX: Float,
    edgeWidthPx: Float,
    totalDragX: Float,
    totalDragY: Float,
    triggerDistancePx: Float,
): Boolean =
    startX <= edgeWidthPx &&
        totalDragX >= triggerDistancePx &&
        totalDragX > abs(totalDragY) * 1.05f

private fun Modifier.edgeSwipeToOpenDrawer(
    enabled: Boolean,
    edgeWidthPx: Float,
    triggerDistancePx: Float,
    onOpen: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(edgeWidthPx, triggerDistancePx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x > edgeWidthPx) return@awaitEachGesture

            val pointerId = down.id
            var totalDragX = 0f
            var totalDragY = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) break

                totalDragX += change.position.x - change.previousPosition.x
                totalDragY += change.position.y - change.previousPosition.y

                if (
                    shouldOpenDrawerFromEdgeSwipe(
                        startX = down.position.x,
                        edgeWidthPx = edgeWidthPx,
                        totalDragX = totalDragX,
                        totalDragY = totalDragY,
                        triggerDistancePx = triggerDistancePx,
                    )
                ) {
                    change.consume()
                    onOpen()
                    break
                }

                // Give clearly vertical edge gestures back to the current list.
                if (abs(totalDragY) > triggerDistancePx * 1.5f && abs(totalDragY) > abs(totalDragX)) break
                if (totalDragX < -triggerDistancePx) break
            }
        }
    }
}

@Composable
fun ArborApp(viewModel: ChatViewModel) {
    val screen by viewModel.screen.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val archivedConversations by viewModel.archivedConversations.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val selected by viewModel.selectedConversationId.collectAsState()
    val selectedProject by viewModel.selectedProjectId.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()
    val pythonRun by viewModel.pythonRun.collectAsState()
    val linuxRun by viewModel.linuxRun.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val openDrawer = { scope.launch { drawerState.open() }; Unit }

    LaunchedEffect(viewModel) {
        viewModel.notices.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(pythonRun?.startedAt, pythonRun?.running, linuxRun?.startedAt, linuxRun?.running) {
        val activePython = pythonRun?.takeIf { it.running }
        val activeLinux = linuxRun?.takeIf { it.running }
        val active = activePython ?: activeLinux ?: return@LaunchedEffect
        val label = if (activePython != null) "Local code execution" else activeLinux!!.distribution.displayName
        val deadline = if (activePython != null) activePython.timeoutSeconds else activeLinux!!.timeoutSeconds
        if (snackbar.showSnackbar("$label is running in the background • ${deadline}s deadline", "Stop", duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed) {
            if (activePython != null) viewModel.stopPythonRun() else viewModel.stopLinuxRun()
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val density = LocalDensity.current
        val drawerEdgeWidthPx = with(density) { 56.dp.toPx() }
        val drawerOpenTriggerPx = with(density) { 10.dp.toPx() }
        val screenContent: @Composable (Screen) -> Unit = { destination ->
            when (destination) {
                Screen.CHAT -> ChatScreen(viewModel, if (wide) null else openDrawer)
                Screen.SEARCH -> SearchScreen(viewModel, if (wide) null else openDrawer)
                Screen.SETTINGS -> SettingsScreen(viewModel, if (wide) null else openDrawer)
                Screen.SANDBOX -> SandboxScreen(viewModel)
                Screen.TERMINAL -> LinuxTerminalScreen(viewModel)
            }
        }
        val content: @Composable () -> Unit = {
            PredictiveNavigationHost(
                targetState = screen,
                backTarget = backDestination(screen),
                onBack = { viewModel.screen.value = it },
                depth = ::screenDepth,
                backEnabled = drawerState.isClosed,
                modifier = Modifier.fillMaxSize(),
                label = "ArborPageNavigation",
                content = screenContent,
            )
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                ConversationSidebar(
                    conversations = if (showArchived) archivedConversations else conversations,
                    projects = projects,
                    selectedId = selected,
                    selectedProjectId = selectedProject,
                    showArchived = showArchived,
                    onSelect = viewModel::selectConversation,
                    onNew = viewModel::newConversation,
                    onScreen = { viewModel.screen.value = it },
                    onProjectFilter = { viewModel.selectedProjectId.value = it },
                    onShowArchived = { viewModel.showArchived.value = it },
                    onRename = viewModel::renameConversation,
                    onArchive = viewModel::archiveConversation,
                    onPin = viewModel::pinConversation,
                    onMove = viewModel::moveConversation,
                    onDelete = viewModel::deleteConversation,
                    onCreateProject = viewModel::createProject,
                    onRenameProject = viewModel::renameProject,
                    onDeleteProject = viewModel::deleteProject,
                    modifier = Modifier.width(310.dp),
                )
                content()
            }
        } else {
            ModalNavigationDrawer(
                modifier = Modifier.edgeSwipeToOpenDrawer(
                    enabled = drawerState.isClosed,
                    edgeWidthPx = drawerEdgeWidthPx,
                    triggerDistancePx = drawerOpenTriggerPx,
                    onOpen = openDrawer,
                ),
                drawerState = drawerState,
                // Use Material's gesture only while the drawer is already open,
                // so pull-to-close remains native. Opening is handled by a more
                // sensitive edge-only recognizer that does not steal chat scrolls.
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    ModalDrawerSheet(drawerState = drawerState) {
                        ConversationSidebar(
                            conversations = if (showArchived) archivedConversations else conversations,
                            projects = projects,
                            selectedId = selected,
                            selectedProjectId = selectedProject,
                            showArchived = showArchived,
                            onSelect = { viewModel.selectConversation(it); scope.launch { drawerState.close() } },
                            onNew = { viewModel.newConversation(); scope.launch { drawerState.close() } },
                            onScreen = { viewModel.screen.value = it; scope.launch { drawerState.close() } },
                            onProjectFilter = { viewModel.selectedProjectId.value = it },
                            onShowArchived = { viewModel.showArchived.value = it },
                            onRename = viewModel::renameConversation,
                            onArchive = viewModel::archiveConversation,
                            onPin = viewModel::pinConversation,
                            onMove = viewModel::moveConversation,
                            onDelete = viewModel::deleteConversation,
                            onCreateProject = viewModel::createProject,
                            onRenameProject = viewModel::renameProject,
                            onDeleteProject = viewModel::deleteProject,
                            modifier = Modifier.width(310.dp),
                        )
                    }
                },
                content = content,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

internal fun backDestination(screen: Screen): Screen? = when (screen) {
    Screen.SANDBOX, Screen.TERMINAL -> Screen.SETTINGS
    Screen.SEARCH, Screen.SETTINGS -> Screen.CHAT
    Screen.CHAT -> null
}

internal fun screenDepth(screen: Screen): Int = when (screen) {
    Screen.CHAT -> 0
    Screen.SEARCH, Screen.SETTINGS -> 1
    Screen.SANDBOX, Screen.TERMINAL -> 2
}
