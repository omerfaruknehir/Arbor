package app.xylune.chat.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.xylune.chat.settings.DeveloperSettings
import app.xylune.chat.settings.PerformanceOverlayPosition
import app.xylune.chat.update.RepositoryUpdateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Composable
fun XyluneApp(viewModel: ChatViewModel, activity: Activity) {
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
    val providers by viewModel.providers.collectAsState()
    val activeConversation by viewModel.conversation.collectAsState()
    val activeModels by viewModel.models.collectAsState()
    val credentialRevision by viewModel.credentialRevision.collectAsState()
    val providerCatalogReady by viewModel.providerCatalogReady.collectAsState()
    val configuredProviders = remember(providers, credentialRevision) {
        viewModel.configuredProviders(providers)
    }
    val imageWorkspaceActive = remember(activeConversation, activeModels) {
        activeConversation?.selectedModelId?.let { selectedModelId ->
            activeModels.firstOrNull { it.modelId == selectedModelId }?.supportsImageGeneration == true
        } == true
    }
    var providerCatalogGraceExpired by rememberSaveable { mutableStateOf(false) }
    val setupActive by viewModel.setupActive.collectAsState()
    val setupStepIndex by viewModel.setupStepIndex.collectAsState()
    val setupPageOffsetFraction by viewModel.setupPageOffsetFraction.collectAsState()
    val setupTemporarilyAway by viewModel.setupTemporarilyAway.collectAsState()
    val setupDismissed by viewModel.setupDismissed.collectAsState()
    val shareConversationId by viewModel.shareConversationId.collectAsState()
    val incomingArchive by viewModel.incomingArchive.collectAsState()
    val repositoryUpdateState by viewModel.repositoryUpdateState.collectAsState()
    val performanceMonitor = remember(activity) { XylunePerformanceMonitor(activity) }
    val showPerformanceOverlay = developerSettings.enabled &&
        (developerSettings.performanceOverlayEnabled || developerSettings.diagnosticProfilerEnabled)
    val drawerState = rememberInteractiveDrawerState()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val snackbar = remember { SnackbarHostState() }
    val uiLanguage = LocalXyluneUiLanguage.current
    val openDrawer = remember(drawerState) { { drawerState.open(); Unit } }

    LaunchedEffect(viewModel) {
        viewModel.notices.collect { snackbar.showSnackbar(localizeUiText(it, uiLanguage)) }
    }
    LaunchedEffect(providerCatalogReady) {
        if (providerCatalogReady) {
            providerCatalogGraceExpired = false
        } else {
            delay(8_000)
            providerCatalogGraceExpired = true
        }
    }
    if (shouldBlockForProviderCatalog(providerCatalogReady, providerCatalogGraceExpired)) {
        XyluneStartupScreen()
        return
    }
    val onboardingCatalogUsable = providerCatalogReady || providerCatalogGraceExpired
    LaunchedEffect(onboardingCatalogUsable, configuredProviders.isEmpty(), setupDismissed, setupActive) {
        if (onboardingCatalogUsable && configuredProviders.isEmpty() && !setupDismissed && !setupActive) {
            viewModel.startSetup()
        }
    }
    val latestSetupContent = rememberUpdatedState<@Composable () -> Unit> {
        Box(Modifier.fillMaxSize()) {
            OnboardingScreen(
                viewModel = viewModel,
                providerCatalogDelayed = !providerCatalogReady,
                configuredProviderCount = configuredProviders.size,
                stepIndex = setupStepIndex,
                stepOffsetFraction = setupPageOffsetFraction,
                scrollOffsetForStep = viewModel::setupScrollOffset,
                onPagerPositionChanged = viewModel::updateSetupPagerPosition,
                onStepScrollChanged = viewModel::saveSetupScrollOffset,
                onOpenProviderSetup = viewModel::openProviderSetupFromSetup,
                onSkipForNow = viewModel::skipSetup,
                onFinish = viewModel::finishSetup,
            )
            incomingArchive?.let { state -> IncomingArchiveDialog(viewModel, state) }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
    val latestSetupTemporarilyAway = rememberUpdatedState(setupTemporarilyAway)
    val latestImageWorkspaceActive = rememberUpdatedState(imageWorkspaceActive)
    if (onboardingCatalogUsable && setupActive && !setupTemporarilyAway) {
        latestSetupContent.value()
        return
    }

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
        XyluneRenderProfiler.setScreen(screen.name)
        XyluneBackdropDebugOverlay.update(
            enabled = developerSettings.enabled && developerSettings.blurBoundaryDebugEnabled,
            thicknessDp = developerSettings.blurBoundaryDebugThicknessDp,
        )
        if (developerSettings.diagnosticProfilerEnabled) XyluneRenderProfiler.recordAppRecomposition()
    }

    LaunchedEffect(repositoryUpdateState) {
        val available = repositoryUpdateState as? RepositoryUpdateState.Available
            ?: return@LaunchedEffect
        val release = available.release
        if (!viewModel.shouldPromptRepositoryUpdate(release.tagName)) return@LaunchedEffect
        val target = if (release.directInstallCompatible && release.apkDownloadUrl != null) {
            release.apkDownloadUrl
        } else {
            release.releasePageUrl
        }
        val result = snackbar.showSnackbar(
            message = "Xylune ${release.versionName} is available from ${release.repository}",
            actionLabel = if (release.directInstallCompatible) "Download" else "Open release",
            duration = SnackbarDuration.Long,
        )
        viewModel.markRepositoryUpdatePrompted(release.tagName)
        if (result == SnackbarResult.ActionPerformed) {
            runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
            }.onFailure { viewModel.postNotice("Could not open the update link") }
        }
    }
    LaunchedEffect(pythonRun?.startedAt, pythonRun?.running, linuxRun?.startedAt, linuxRun?.running) {
        val activePython = pythonRun?.takeIf { it.running }
        val activeLinux = linuxRun?.takeIf { it.running }
        val active = activePython ?: activeLinux ?: return@LaunchedEffect
        val label = if (activePython != null) "Local code execution" else activeLinux!!.distribution.displayName
        val deadline = if (activePython != null) activePython.timeoutSeconds else activeLinux!!.timeoutSeconds
        if (snackbar.showSnackbar(localizeUiText("$label is running in the background • ${deadline}s deadline", uiLanguage), localizeUiText("Stop", uiLanguage), duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed) {
            if (activePython != null) viewModel.stopPythonRun() else viewModel.stopLinuxRun()
        }
    }

    val drawerVisible = drawerState.isVisible
    val drawerClaimsBack = drawerState.claimsBack
    PredictiveBackHandler(
        enabled = appBackHandlerEnabled(drawerClaimsBack, imeVisible),
    ) { events ->
        drawerState.beginPredictiveBack()
        try {
            events.collect { event -> drawerState.updatePredictiveBack(event.progress) }
            drawerState.commitPredictiveBack()
        } catch (cancelled: CancellationException) {
            drawerState.cancelPredictiveBack()
            throw cancelled
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val compactOpenDrawer = if (wide) null else openDrawer
        // Keep this function object stable. PredictiveNavigationHost stores it in
        // rememberUpdatedState; recreating it on every root update invalidated all
        // kept-alive page slots, including the parked Chat tree.
        val screenContent: @Composable (Screen) -> Unit = remember(viewModel, compactOpenDrawer) {
            { destination ->
                when (destination) {
                    Screen.CHAT -> if (latestSetupTemporarilyAway.value) {
                        latestSetupContent.value()
                    } else if (latestImageWorkspaceActive.value) {
                        ImageGenerationScreen(viewModel, compactOpenDrawer)
                    } else {
                        ChatScreen(viewModel, compactOpenDrawer)
                    }
                    Screen.SEARCH -> SearchScreen(viewModel, compactOpenDrawer)
                    Screen.SETTINGS -> Box(Modifier.fillMaxSize()) {
                        SettingsScreen(viewModel, compactOpenDrawer)
                        SettingsLeftBackEdgeGuard()
                    }
                    Screen.SANDBOX -> SandboxScreen(viewModel)
                    Screen.TERMINAL -> LinuxTerminalScreen(viewModel)
                }
            }
        }
        val content: @Composable () -> Unit = {
            PredictiveNavigationHost(
                targetState = screen,
                backTarget = if (
                    latestSetupTemporarilyAway.value &&
                    (screen == Screen.SANDBOX || screen == Screen.SETTINGS)
                ) Screen.CHAT else backDestination(screen),
                onBack = { target -> viewModel.screen.value = target },
                depth = ::screenDepth,
                onSettled = { settled ->
                    if (latestSetupTemporarilyAway.value && settled == Screen.CHAT) {
                        viewModel.returnToSetup()
                    }
                },
                // Once a closing drawer is no longer visible, page Back must immediately
                // take ownership. Waiting for its spring job to finish creates a gap where
                // Android can fall through to Activity exit.
                backEnabled = pageBackEnabled(drawerClaimsBack, imeVisible),
                keepAlive = { it == Screen.CHAT },
                modifier = Modifier.fillMaxSize(),
                label = "XylunePageNavigation",
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
                    onScreen = { destination ->
                        if (destination == Screen.SETTINGS) viewModel.openSettingsHome()
                        else viewModel.screen.value = destination
                    },
                    onProjectFilter = { viewModel.selectedProjectId.value = it },
                    onShowArchived = { viewModel.showArchived.value = it },
                    onRename = viewModel::renameConversation,
                    onArchive = viewModel::archiveConversation,
                    onPin = viewModel::pinConversation,
                    onMove = viewModel::moveConversation,
                    onShare = viewModel::requestShareConversation,
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
                // Chat and Settings retain pull-to-open. In Settings, a narrow
                // non-consuming priority strip reserves the actual system Back edge.
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
                            onScreen = { destination ->
                                if (destination == Screen.SETTINGS) viewModel.openSettingsHome()
                                else viewModel.screen.value = destination
                                drawerState.close()
                            },
                            onProjectFilter = { viewModel.selectedProjectId.value = it },
                            onShowArchived = { viewModel.showArchived.value = it },
                            onRename = viewModel::renameConversation,
                            onArchive = viewModel::archiveConversation,
                            onPin = viewModel::pinConversation,
                            onMove = viewModel::moveConversation,
                            onShare = viewModel::requestShareConversation,
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
        shareConversationId?.let { conversationId ->
            ChatShareDialog(
                viewModel = viewModel,
                conversationId = conversationId,
                onDismiss = viewModel::dismissShareConversation,
            )
        }
        incomingArchive?.let { state -> IncomingArchiveDialog(viewModel, state) }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}


@Composable
private fun PerformanceOverlayHost(
    monitor: XylunePerformanceMonitor,
    settings: DeveloperSettings,
    modifier: Modifier = Modifier,
) {
    // Snapshot updates must not recompose XyluneApp, the navigation host, drawer,
    // or the active screen. Keeping the collection in this leaf makes the
    // profiler observe the app instead of becoming a periodic source of work.
    val snapshot by monitor.snapshot.collectAsState()
    XylunePerformanceOverlay(
        snapshot = snapshot,
        detailed = settings.detailedPerformanceOverlay || settings.diagnosticProfilerEnabled,
        backgroundOpacity = settings.performanceOverlayBackgroundOpacity,
        textOpacity = settings.performanceOverlayTextOpacity,
        scale = settings.performanceOverlayScale,
        modifier = modifier,
    )
}

@Composable
private fun SettingsLeftBackEdgeGuard() {
    // The drawer can still be pulled from the Settings content, but the first
    // 48 dp are owned by Android Back. This node only registers geometry; it
    // consumes no pointer input and therefore cannot block taps or scrolling.
    Box(
        Modifier
            .fillMaxHeight()
            .width(48.dp)
            .horizontalGesturePriority(),
    )
}

internal fun performanceOverlayAlignment(position: PerformanceOverlayPosition): Alignment = when (position) {
    PerformanceOverlayPosition.TOP_START -> Alignment.TopStart
    PerformanceOverlayPosition.TOP_END -> Alignment.TopEnd
    PerformanceOverlayPosition.BOTTOM_START -> Alignment.BottomStart
    PerformanceOverlayPosition.BOTTOM_END -> Alignment.BottomEnd
}

internal fun backDestination(screen: Screen): Screen? = when (screen) {
    Screen.SANDBOX -> Screen.SETTINGS
    Screen.TERMINAL -> Screen.SANDBOX
    Screen.SEARCH, Screen.SETTINGS -> Screen.CHAT
    Screen.CHAT -> null
}

internal fun screenDepth(screen: Screen): Int = when (screen) {
    Screen.CHAT -> 0
    Screen.SEARCH, Screen.SETTINGS -> 1
    Screen.SANDBOX -> 2
    Screen.TERMINAL -> 3
}

internal fun drawerSwipeEnabled(screen: Screen): Boolean = screen == Screen.CHAT || screen == Screen.SETTINGS

internal fun pageBackEnabled(drawerVisible: Boolean, imeVisible: Boolean = false): Boolean =
    !drawerVisible && !imeVisible
