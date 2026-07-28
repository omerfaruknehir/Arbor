package app.arbor.chat.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.arbor.chat.settings.DeveloperSettings
import app.arbor.chat.settings.PerformanceOverlayPosition

@Composable
fun ArborApp(viewModel: ChatViewModel, activity: Activity) {
    val screen by viewModel.screen.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val archivedConversations by viewModel.archivedConversations.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val selected by viewModel.selectedConversationId.collectAsState()
    val selectedProject by viewModel.selectedProjectId.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()
    val pythonRun by viewModel.pythonRun.collectAsState()
    val linuxRun by viewModel.linuxRun.collectAsState()
    val developerSettings by viewModel.developerSettings.collectAsState()
    val performanceMonitor = remember(activity) { ArborPerformanceMonitor(activity) }
    val showPerformanceOverlay = developerSettings.enabled &&
        (developerSettings.performanceOverlayEnabled || developerSettings.diagnosticProfilerEnabled)
    val drawerState = rememberInteractiveDrawerState()
    val snackbar = remember { SnackbarHostState() }
    val openDrawer = remember(drawerState) { { drawerState.open(); Unit } }

    DisposableEffect(
        performanceMonitor,
        showPerformanceOverlay,
        developerSettings.performanceUpdateIntervalMs,
        developerSettings.diagnosticProfilerEnabled,
    ) {
        if (showPerformanceOverlay) {
            performanceMonitor.start(
                intervalMs = developerSettings.performanceUpdateIntervalMs,
                diagnosticsEnabled = developerSettings.diagnosticProfilerEnabled,
            )
        } else performanceMonitor.stop()
        onDispose { performanceMonitor.stop() }
    }

    SideEffect {
        ArborRenderProfiler.setScreen(screen.name)
        ArborBackdropDebugOverlay.update(
            enabled = developerSettings.enabled && developerSettings.blurBoundaryDebugEnabled,
            thicknessDp = developerSettings.blurBoundaryDebugThicknessDp,
        )
        if (developerSettings.diagnosticProfilerEnabled) ArborRenderProfiler.recordAppRecomposition()
    }

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

    val drawerVisible = drawerState.isVisible
    BackHandler(enabled = drawerVisible) { drawerState.close() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val compactOpenDrawer = if (wide) null else openDrawer
        // Keep this function object stable. PredictiveNavigationHost stores it in
        // rememberUpdatedState; recreating it on every root update invalidated all
        // kept-alive page slots, including the parked Chat tree.
        val screenContent: @Composable (Screen) -> Unit = remember(viewModel, compactOpenDrawer) {
            { destination ->
                when (destination) {
                    Screen.CHAT -> ChatScreen(viewModel, compactOpenDrawer)
                    Screen.SEARCH -> SearchScreen(viewModel, compactOpenDrawer)
                    Screen.SETTINGS -> SettingsScreen(viewModel, compactOpenDrawer)
                    Screen.SANDBOX -> SandboxScreen(viewModel)
                    Screen.TERMINAL -> LinuxTerminalScreen(viewModel)
                }
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
                backEnabled = pageBackEnabled(drawerVisible),
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
                // Chat and the Settings root support pull-to-open. Nested Settings pages
                // still register horizontal Back priority, so the drawer yields to them.
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
        if (showPerformanceOverlay) {
            val bottomPosition = developerSettings.performanceOverlayPosition == PerformanceOverlayPosition.BOTTOM_START ||
                developerSettings.performanceOverlayPosition == PerformanceOverlayPosition.BOTTOM_END
            PerformanceOverlayHost(
                monitor = performanceMonitor,
                settings = developerSettings,
                modifier = Modifier
                    .align(performanceOverlayAlignment(developerSettings.performanceOverlayPosition))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = if (bottomPosition) 80.dp else 12.dp,
                    ),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}


@Composable
private fun PerformanceOverlayHost(
    monitor: ArborPerformanceMonitor,
    settings: DeveloperSettings,
    modifier: Modifier = Modifier,
) {
    // Snapshot updates must not recompose ArborApp, the navigation host, drawer,
    // or the active screen. Keeping the collection in this leaf makes the
    // profiler observe the app instead of becoming a periodic source of work.
    val snapshot by monitor.snapshot.collectAsState()
    ArborPerformanceOverlay(
        snapshot = snapshot,
        detailed = settings.detailedPerformanceOverlay || settings.diagnosticProfilerEnabled,
        backgroundOpacity = settings.performanceOverlayBackgroundOpacity,
        textOpacity = settings.performanceOverlayTextOpacity,
        modifier = modifier,
    )
}

internal fun performanceOverlayAlignment(position: PerformanceOverlayPosition): Alignment = when (position) {
    PerformanceOverlayPosition.TOP_START -> Alignment.TopStart
    PerformanceOverlayPosition.TOP_END -> Alignment.TopEnd
    PerformanceOverlayPosition.BOTTOM_START -> Alignment.BottomStart
    PerformanceOverlayPosition.BOTTOM_END -> Alignment.BottomEnd
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

internal fun drawerSwipeEnabled(screen: Screen): Boolean = screen == Screen.CHAT || screen == Screen.SETTINGS

internal fun pageBackEnabled(drawerVisible: Boolean): Boolean = !drawerVisible
