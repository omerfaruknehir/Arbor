package app.arbor.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    val drawerState = rememberInteractiveDrawerState()
    val snackbar = remember { SnackbarHostState() }
    val openDrawer = { drawerState.open(); Unit }

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

    BackHandler(enabled = drawerState.isVisible) { drawerState.close() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
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
                // Once a closing drawer is no longer visible, page Back must immediately
                // take ownership. Waiting for its spring job to finish creates a gap where
                // Android can fall through to Activity exit.
                backEnabled = pageBackEnabled(drawerState.isVisible),
                keepAlive = { it == Screen.CHAT },
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
            InteractiveNavigationDrawer(
                state = drawerState,
                modifier = Modifier.fillMaxSize(),
                // The left-edge system gesture and pull-to-open have the same direction.
                // Secondary pages reserve both edges for Back; their menu button still
                // opens this same drawer state.
                gesturesEnabled = drawerSwipeEnabled(screen),
                drawerContent = { drawerModifier ->
                    ModalDrawerSheet(modifier = drawerModifier) {
                        ConversationSidebar(
                            conversations = if (showArchived) archivedConversations else conversations,
                            projects = projects,
                            selectedId = selected,
                            selectedProjectId = selectedProject,
                            showArchived = showArchived,
                            onSelect = { viewModel.selectConversation(it); drawerState.close() },
                            onNew = { viewModel.newConversation(); drawerState.close() },
                            onScreen = { viewModel.screen.value = it; drawerState.close() },
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
                            modifier = Modifier.fillMaxSize(),
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

internal fun drawerSwipeEnabled(screen: Screen): Boolean = screen == Screen.CHAT

internal fun pageBackEnabled(drawerVisible: Boolean): Boolean = !drawerVisible
