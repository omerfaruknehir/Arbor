#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1))


def replace_all_checked(path: str, old: str, new: str, minimum: int = 1) -> int:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count < minimum:
        raise SystemExit(f"{path}: expected at least {minimum} matches, found {count}: {old!r}")
    file.write_text(text.replace(old, new))
    return count


predictive = "app/src/main/java/app/xylune/chat/ui/PredictiveNavigation.kt"
replace_once(
    predictive,
    """internal fun predictiveBackCompletionDurationMillis(progress: Float): Int {
    val remaining = 1f - progress.coerceIn(0f, 1f)
    return (80f + 70f * remaining).roundToInt().coerceIn(80, 150)
}
""",
    """internal fun predictiveBackCompletionDurationMillis(progress: Float): Int {
    val remaining = 1f - progress.coerceIn(0f, 1f)
    return (160f + 200f * remaining).roundToInt().coerceIn(160, 360)
}
""",
)
replace_once(
    predictive,
    """internal fun predictiveBackSourceScale(progress: Float): Float =
    1f - 0.04f * predictiveBackVisualProgress(progress)

""",
    """internal fun pageSlideOffset(widthPx: Float, progress: Float): Float =
    widthPx.coerceAtLeast(0f) * progress.coerceIn(0f, 1f)

""",
)
replace_once(
    predictive,
    """    onBack: (T) -> Unit,
    depth: (T) -> Int,
    modifier: Modifier = Modifier,
""",
    """    onBack: (T) -> Unit,
    depth: (T) -> Int,
    onSettled: (T) -> Unit = {},
    modifier: Modifier = Modifier,
""",
)
replace_once(
    predictive,
    """    val latestOnBack by rememberUpdatedState(onBack)
    val latestKeepAlive by rememberUpdatedState(keepAlive)
""",
    """    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnSettled by rememberUpdatedState(onSettled)
    val latestKeepAlive by rememberUpdatedState(keepAlive)
""",
)
replace_once(
    predictive,
    """            progress.animateTo(1f, tween(120, easing = NavigationEasing))
            settleOn(destination)
            progress.snapTo(0f)
""",
    """            progress.animateTo(1f, tween(280, easing = NavigationEasing))
            settleOn(destination)
            progress.snapTo(0f)
            latestOnSettled(destination.state)
""",
)
replace_once(
    predictive,
    """            latestOnBack(destinationState)
            backDispatched = true
            settleOn(destination)
            progress.snapTo(0f)
""",
    """            latestOnBack(destinationState)
            backDispatched = true
            settleOn(destination)
            progress.snapTo(0f)
            latestOnSettled(destination.state)
""",
)
replace_once(
    predictive,
    """                        if (!backDispatched) latestOnBack(destinationState)
                        settleOn(destination)
                        progress.snapTo(0f)
""",
    """                        if (!backDispatched) latestOnBack(destinationState)
                        settleOn(destination)
                        progress.snapTo(0f)
                        latestOnSettled(destination.state)
""",
)
replace_once(
    predictive,
    "progress.animateTo(0f, tween(160, easing = NavigationEasing))",
    "progress.animateTo(0f, tween(220, easing = NavigationEasing))",
)
replace_once(
    predictive,
    """                                NavigationTransitionMode.PREDICTIVE -> {
                                    val visualProgress = predictiveBackVisualProgress(p)
                                    when {
                                        isSource -> {
                                            // Keep the active page fully opaque. Fading a whole
                                            // Compose tree exposed intermediate surfaces and made
                                            // text/panels appear to blink on real devices.
                                            translationX = predictiveDirection * widthPx * 0.10f * visualProgress
                                            val sourceScale = predictiveBackSourceScale(p)
                                            scaleX = sourceScale
                                            scaleY = sourceScale
                                        }
                                        isDestination -> {
                                            translationX = -predictiveDirection * widthPx * 0.04f * (1f - visualProgress)
                                        }
                                    }
                                }
                                NavigationTransitionMode.ORDINARY -> {
                                    if (transitionForward) {
                                        when {
                                            isSource -> translationX = -widthPx / 18f * p
                                            isDestination -> translationX = widthPx / 8f * (1f - p)
                                        }
                                    } else {
                                        when {
                                            isSource -> translationX = widthPx / 8f * p
                                            isDestination -> translationX = -widthPx / 18f * (1f - p)
                                        }
                                    }
                                }
""",
    """                                NavigationTransitionMode.PREDICTIVE -> {
                                    val visualProgress = predictiveBackVisualProgress(p)
                                    val slide = pageSlideOffset(widthPx, visualProgress)
                                    when {
                                        isSource -> {
                                            // Keep the two opaque pages edge-to-edge throughout the
                                            // gesture. At commit the source reaches a complete
                                            // off-screen position instead of disappearing after a
                                            // short preview translation.
                                            translationX = predictiveDirection * slide
                                        }
                                        isDestination -> {
                                            translationX = -predictiveDirection * (widthPx - slide)
                                        }
                                    }
                                }
                                NavigationTransitionMode.ORDINARY -> {
                                    val slide = pageSlideOffset(widthPx, p)
                                    if (transitionForward) {
                                        when {
                                            isSource -> translationX = -slide
                                            isDestination -> translationX = widthPx - slide
                                        }
                                    } else {
                                        when {
                                            isSource -> translationX = slide
                                            isDestination -> translationX = -(widthPx - slide)
                                        }
                                    }
                                }
""",
)

math_test = "app/src/test/java/app/xylune/chat/ui/PredictiveNavigationMathTest.kt"
replace_once(
    math_test,
    """        assertEquals(150, predictiveBackCompletionDurationMillis(0f))
        assertEquals(115, predictiveBackCompletionDurationMillis(.5f))
        assertEquals(80, predictiveBackCompletionDurationMillis(1f))
""",
    """        assertEquals(360, predictiveBackCompletionDurationMillis(0f))
        assertEquals(260, predictiveBackCompletionDurationMillis(.5f))
        assertEquals(160, predictiveBackCompletionDurationMillis(1f))
""",
)
replace_once(
    math_test,
    """    @Test
    fun predictiveSourceScaleRemainsVisibleAndEndsAtNinetySixPercent() {
        assertEquals(1f, predictiveBackSourceScale(0f), .0001f)
        assertEquals(.96f, predictiveBackSourceScale(1f), .0001f)
        assertTrue(predictiveBackSourceScale(.5f) in .96f..1f)
    }

""",
    """    @Test
    fun pageSlideTravelsTheEntireViewportBeforeTheSourceIsRetired() {
        assertEquals(0f, pageSlideOffset(1080f, 0f), .0001f)
        assertEquals(540f, pageSlideOffset(1080f, .5f), .0001f)
        assertEquals(1080f, pageSlideOffset(1080f, 1f), .0001f)
        assertEquals(1080f, pageSlideOffset(1080f, 2f), .0001f)
    }

""",
)

app = "app/src/main/java/app/xylune/chat/ui/XyluneApp.kt"
replace_once(
    app,
    "import androidx.compose.runtime.remember\n",
    "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberUpdatedState\n",
)
old_setup = """    if (onboardingCatalogUsable && setupActive && !setupTemporarilyAway) {
        Box(Modifier.fillMaxSize()) {
            OnboardingScreen(
                viewModel = viewModel,
                currentThemeMode = themeMode,
                currentPalette = palette,
                matchLauncherIconToPalette = matchLauncherIconToPalette,
                amoled = amoled,
                providerCatalogDelayed = !providerCatalogReady,
                configuredProviderCount = configuredProviders.size,
                pythonEnabled = newChatDefaults.agentPythonEnabled,
                stepIndex = setupStepIndex,
                stepOffsetFraction = setupPageOffsetFraction,
                scrollOffsetForStep = viewModel::setupScrollOffset,
                onPagerPositionChanged = viewModel::updateSetupPagerPosition,
                onStepScrollChanged = viewModel::saveSetupScrollOffset,
                linuxEnabled = newChatDefaults.agentUbuntuEnabled,
                linuxStatus = ubuntuStatus,
                onThemeModeChanged = viewModel::setThemeMode,
                onPaletteChanged = viewModel::setPalette,
                onMatchLauncherIconToPaletteChanged = viewModel::setMatchLauncherIconToPalette,
                onAmoledChanged = viewModel::setAmoled,
                onPythonEnabledChanged = { enabled ->
                    viewModel.updateNewChatDefaults { it.copy(agentPythonEnabled = enabled) }
                },
                onLinuxEnabledChanged = { enabled ->
                    viewModel.updateNewChatDefaults { it.copy(agentUbuntuEnabled = enabled) }
                },
                onOpenProviderSetup = viewModel::openProviderSetupFromSetup,
                onOpenLinuxSetup = viewModel::openLinuxSetupFromSetup,
                onSkipForNow = viewModel::skipSetup,
                onFinish = viewModel::finishSetup,
            )
            incomingArchive?.let { state -> IncomingArchiveDialog(viewModel, state) }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
        return
    }
"""
new_setup = """    val latestSetupContent = rememberUpdatedState<@Composable () -> Unit> {
        Box(Modifier.fillMaxSize()) {
            OnboardingScreen(
                viewModel = viewModel,
                currentThemeMode = themeMode,
                currentPalette = palette,
                matchLauncherIconToPalette = matchLauncherIconToPalette,
                amoled = amoled,
                providerCatalogDelayed = !providerCatalogReady,
                configuredProviderCount = configuredProviders.size,
                pythonEnabled = newChatDefaults.agentPythonEnabled,
                stepIndex = setupStepIndex,
                stepOffsetFraction = setupPageOffsetFraction,
                scrollOffsetForStep = viewModel::setupScrollOffset,
                onPagerPositionChanged = viewModel::updateSetupPagerPosition,
                onStepScrollChanged = viewModel::saveSetupScrollOffset,
                linuxEnabled = newChatDefaults.agentUbuntuEnabled,
                linuxStatus = ubuntuStatus,
                onThemeModeChanged = viewModel::setThemeMode,
                onPaletteChanged = viewModel::setPalette,
                onMatchLauncherIconToPaletteChanged = viewModel::setMatchLauncherIconToPalette,
                onAmoledChanged = viewModel::setAmoled,
                onPythonEnabledChanged = { enabled ->
                    viewModel.updateNewChatDefaults { it.copy(agentPythonEnabled = enabled) }
                },
                onLinuxEnabledChanged = { enabled ->
                    viewModel.updateNewChatDefaults { it.copy(agentUbuntuEnabled = enabled) }
                },
                onOpenProviderSetup = viewModel::openProviderSetupFromSetup,
                onOpenLinuxSetup = viewModel::openLinuxSetupFromSetup,
                onSkipForNow = viewModel::skipSetup,
                onFinish = viewModel::finishSetup,
            )
            incomingArchive?.let { state -> IncomingArchiveDialog(viewModel, state) }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
    val latestSetupTemporarilyAway = rememberUpdatedState(setupTemporarilyAway)
    if (onboardingCatalogUsable && setupActive && !setupTemporarilyAway) {
        latestSetupContent.value()
        return
    }
"""
replace_once(app, old_setup, new_setup)
replace_once(
    app,
    """                    Screen.CHAT -> ChatScreen(viewModel, compactOpenDrawer)
                    Screen.SEARCH -> SearchScreen(viewModel, compactOpenDrawer)
""",
    """                    Screen.CHAT -> if (latestSetupTemporarilyAway.value) {
                        latestSetupContent.value()
                    } else {
                        ChatScreen(viewModel, compactOpenDrawer)
                    }
                    Screen.SEARCH -> SearchScreen(viewModel, compactOpenDrawer)
""",
)
replace_once(
    app,
    """                backTarget = if (setupTemporarilyAway && screen == Screen.SANDBOX) Screen.CHAT else backDestination(screen),
                onBack = { target ->
                    if (setupTemporarilyAway && screen == Screen.SANDBOX) viewModel.returnToSetup()
                    else viewModel.screen.value = target
                },
                depth = ::screenDepth,
""",
    """                backTarget = if (
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
""",
)

sandbox = "app/src/main/java/app/xylune/chat/ui/SandboxScreen.kt"
replace_once(
    sandbox,
    "if (setupTemporarilyAway) viewModel.returnToSetup()\n                        else viewModel.screen.value = Screen.SETTINGS",
    "if (setupTemporarilyAway) viewModel.screen.value = Screen.CHAT\n                        else viewModel.screen.value = Screen.SETTINGS",
)

settings = "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt"
replace_once(
    settings,
    """        backTarget = when (route) {
            SettingsRoute.HOME -> null
            SettingsRoute.DEVELOPER, SettingsRoute.LICENSES -> SettingsRoute.ABOUT
            else -> SettingsRoute.HOME
        },
        onBack = { target ->
            if (setupTemporarilyAway && route == SettingsRoute.PROVIDERS) viewModel.returnToSetup()
            else viewModel.settingsRoute.value = target
        },
""",
    """        backTarget = if (setupTemporarilyAway && route == SettingsRoute.PROVIDERS) {
            // The app-level host owns this Back so it can slide the complete
            // Settings page back to the preserved setup page.
            null
        } else when (route) {
            SettingsRoute.HOME -> null
            SettingsRoute.DEVELOPER, SettingsRoute.LICENSES -> SettingsRoute.ABOUT
            else -> SettingsRoute.HOME
        },
        onBack = { target -> viewModel.settingsRoute.value = target },
""",
)
replace_once(
    settings,
    """                            if (setupTemporarilyAway && currentRoute == SettingsRoute.PROVIDERS) {
                                viewModel.returnToSetup()
""",
    """                            if (setupTemporarilyAway && currentRoute == SettingsRoute.PROVIDERS) {
                                viewModel.screen.value = Screen.CHAT
""",
)

view_model = "app/src/main/java/app/xylune/chat/ui/ChatViewModel.kt"
replaced_detours = replace_all_checked(
    view_model,
    "if (setupTemporarilyAway.value) returnToSetup()",
    "if (setupTemporarilyAway.value) screen.value = Screen.CHAT",
    minimum=1,
)
print(f"Converted {replaced_detours} automatic setup returns into animated root transitions")

onboarding_test = "app/src/test/java/app/xylune/chat/ui/OnboardingFlowTest.kt"
replace_once(
    onboarding_test,
    """        assertTrue(settings.contains("viewModel.returnToSetup()"))
        assertTrue(settings.contains("title = \\"Finish setup\\""))
""",
    """        assertTrue(app.contains("onSettled = { settled ->"))
        assertTrue(settings.contains("viewModel.screen.value = Screen.CHAT"))
        assertFalse(settings.contains("viewModel.returnToSetup()"))
        assertTrue(settings.contains("title = \\"Finish setup\\""))
""",
)

build = "app/build.gradle.kts"
replace_once(build, 'versionCode = 181', 'versionCode = 182')
replace_once(build, 'versionName = "0.23.12"', 'versionName = "0.23.13"')

changelog = Path("CHANGELOG.md")
text = changelog.read_text()
entry = """## 0.23.13 — 2026-08-04

- Finish ordinary and predictive Back transitions as complete edge-to-edge page slides instead of moving a page only a few percent and then abruptly removing it.
- Lengthen commit and rollback timing so the remaining motion is visible after release without becoming sluggish.
- Keep the setup page composed as the real destination while returning from Providers or Tool workspace, and defer the setup state switch until the navigation host has fully settled.
- Route toolbar Back and successful provider detours through the same animated root transition, eliminating immediate root-content cuts.

"""
if not text.startswith("## 0.23.12"):
    raise SystemExit("CHANGELOG.md did not start at 0.23.12")
changelog.write_text(entry + text)

Path("docs/releases/RELEASE_NOTES_0.23.13.md").write_text(
    """# Xylune 0.23.13

## Back transitions now finish

Back navigation no longer performs a short preview movement and then removes the current page. Ordinary Back and Android predictive Back now move both opaque pages edge-to-edge across the full viewport and settle before the source composition is retired.

Returning from the setup detours is also handled by the same navigation host. Providers and Tool workspace slide directly back to the preserved setup page; Xylune changes the setup state only after that animation has completed, preventing the whole host from being replaced mid-transition.
"""
)

# Final source-level contract checks.
predictive_source = Path(predictive).read_text()
app_source = Path(app).read_text()
settings_source = Path(settings).read_text()
assert "widthPx * 0.10f" not in predictive_source
assert "widthPx / 8f" not in predictive_source
assert "pageSlideOffset(widthPx" in predictive_source
assert "latestOnSettled(destination.state)" in predictive_source
assert "latestSetupContent.value()" in app_source
assert "onSettled = { settled ->" in app_source
assert "viewModel.returnToSetup()" not in settings_source
assert 'versionName = "0.23.13"' in Path(build).read_text()
print("Applied complete Back animation repair for Xylune 0.23.13")
