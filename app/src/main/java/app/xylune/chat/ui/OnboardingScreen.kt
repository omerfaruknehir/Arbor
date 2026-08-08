package app.xylune.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class OnboardingStep(val setupTitle: String) {
    WELCOME("Welcome"),
    PROVIDER("Model access"),
    READY("Finish"),
}

internal fun shouldBlockForProviderCatalog(catalogReady: Boolean, graceExpired: Boolean): Boolean =
    !catalogReady && !graceExpired

internal fun shouldShowProviderOnboarding(
    catalogReady: Boolean,
    hasConfiguredProvider: Boolean,
    dismissedForSession: Boolean,
): Boolean = catalogReady && !hasConfiguredProvider && !dismissedForSession

@Composable
internal fun XyluneStartupScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(uiText("Preparing Xylune…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun OnboardingScreen(
    viewModel: ChatViewModel,
    providerCatalogDelayed: Boolean,
    configuredProviderCount: Int,
    stepIndex: Int,
    stepOffsetFraction: Float,
    scrollOffsetForStep: (Int) -> Int,
    onPagerPositionChanged: (Int, Float) -> Unit,
    onStepScrollChanged: (Int, Int) -> Unit,
    onOpenProviderSetup: () -> Unit,
    onSkipForNow: () -> Unit,
    onFinish: () -> Unit,
) {
    val steps = OnboardingStep.entries
    val initialPage = stepIndex.coerceIn(0, steps.lastIndex)
    val pageScrollStates = listOf(
        rememberScrollState(initial = scrollOffsetForStep(0)),
        rememberScrollState(initial = scrollOffsetForStep(1)),
        rememberScrollState(initial = scrollOffsetForStep(2)),
    )
    val haptics = rememberXyluneHaptics()
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        initialPageOffsetFraction = stepOffsetFraction.coerceIn(-0.499f, 0.499f),
        pageCount = { steps.size },
    )
    val visiblePage = pagerState.currentPage.coerceIn(0, steps.lastIndex)
    val step = steps[visiblePage]

    fun moveTo(page: Int) {
        val target = page.coerceIn(0, steps.lastIndex)
        coroutineScope.launch { pagerState.animateScrollToPage(target) }
    }

    LaunchedEffect(stepIndex) {
        val target = stepIndex.coerceIn(0, steps.lastIndex)
        if (!pagerState.isScrollInProgress &&
            (pagerState.currentPage != target || pagerState.currentPageOffsetFraction != 0f)
        ) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.currentPageOffsetFraction }
            .distinctUntilChanged()
            .collect { (page, offset) -> onPagerPositionChanged(page, offset) }
    }
    pageScrollStates.forEachIndexed { page, state ->
        SetupScrollReporter(page, state, onStepScrollChanged)
    }

    BackHandler(enabled = visiblePage > 0) {
        haptics.selection()
        moveTo(visiblePage - 1)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            OnboardingProgressHeader(
                currentStepIndex = visiblePage,
                pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                stepCount = steps.size,
                stepTitle = step.setupTitle,
                showBack = visiblePage > 0,
                showLater = visiblePage < steps.lastIndex,
                onBack = {
                    haptics.selection()
                    moveTo(visiblePage - 1)
                },
                onSkipForNow = {
                    haptics.selection()
                    onSkipForNow()
                },
            )
            Spacer(Modifier.height(10.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 0,
                userScrollEnabled = true,
                key = { steps[it] },
            ) { page ->
                val destination = steps[page]
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(pageScrollStates[page])
                            .padding(top = 8.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (destination) {
                            OnboardingStep.WELCOME -> WelcomeStep(viewModel)
                            OnboardingStep.PROVIDER -> ProviderStep(
                                providerCatalogDelayed = providerCatalogDelayed,
                                configuredProviderCount = configuredProviderCount,
                            )
                            OnboardingStep.READY -> ReadyStep(configuredProviderCount)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    OnboardingStepActions(
                        step = destination,
                        configuredProviderCount = configuredProviderCount,
                        onContinue = { moveTo(page + 1) },
                        onOpenProviderSetup = onOpenProviderSetup,
                        onFinish = onFinish,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepActions(
    step: OnboardingStep,
    configuredProviderCount: Int,
    onContinue: () -> Unit,
    onOpenProviderSetup: () -> Unit,
    onFinish: () -> Unit,
) {
    val haptics = rememberXyluneHaptics()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (step) {
            OnboardingStep.WELCOME -> PrimaryNextButton("Start setup", onContinue)
            OnboardingStep.PROVIDER -> ProviderStepActions(
                configuredProviderCount = configuredProviderCount,
                onContinue = onContinue,
                onOpenProviderSetup = onOpenProviderSetup,
            )
            OnboardingStep.READY -> {
                Button(
                    onClick = {
                        haptics.confirm()
                        onFinish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(uiText("Enter Xylune")) }
            }
        }
    }
}

@Composable
private fun ProviderStepActions(
    configuredProviderCount: Int,
    onContinue: () -> Unit,
    onOpenProviderSetup: () -> Unit,
) {
    val haptics = rememberXyluneHaptics()
    if (configuredProviderCount > 0) {
        Button(
            onClick = {
                haptics.confirm()
                onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(uiText("Continue")) }
        OutlinedButton(
            onClick = {
                haptics.selection()
                onOpenProviderSetup()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(uiText("Manage providers")) }
    } else {
        Button(
            onClick = {
                haptics.confirm()
                onOpenProviderSetup()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(uiText("Connect a provider")) }
        OutlinedButton(
            onClick = {
                haptics.selection()
                onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(uiText("Continue without one")) }
    }
}

@Composable
private fun PrimaryNextButton(label: String, onClick: () -> Unit) {
    val haptics = rememberXyluneHaptics()
    Button(
        onClick = {
            haptics.confirm()
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(uiText(label)) }
}

internal fun setupProgressForSegment(pagePosition: Float, segmentIndex: Int): Float =
    (pagePosition - segmentIndex + 1f).coerceIn(0f, 1f)

@Composable
private fun SetupScrollReporter(
    stepIndex: Int,
    scrollState: ScrollState,
    onStepScrollChanged: (Int, Int) -> Unit,
) {
    LaunchedEffect(stepIndex, scrollState) {
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collect { onStepScrollChanged(stepIndex, it) }
    }
}

@Composable
private fun OnboardingProgressHeader(
    currentStepIndex: Int,
    pagePosition: Float,
    stepCount: Int,
    stepTitle: String,
    showBack: Boolean,
    showLater: Boolean,
    onBack: () -> Unit,
    onSkipForNow: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, uiText("Previous setup step"))
                }
            } else {
                XyluneMark(modifier = Modifier.size(40.dp), contentDescription = null)
            }
            Column(Modifier.weight(1f)) {
                Text(uiText(stepTitle), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    uiText("Step ${currentStepIndex + 1} of $stepCount"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showLater) {
                TextButton(onClick = onSkipForNow) { Text(uiText("Skip for now")) }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(stepCount) { index ->
                val fill = setupProgressForSegment(pagePosition, index)
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(viewModel: ChatViewModel) {
    Spacer(Modifier.height(2.dp))
    XyluneMark(modifier = Modifier.size(72.dp), contentDescription = uiText("Xylune"))
    Text(
        uiText("Set up Xylune"),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Text(
        uiText("Restore a backup if you have one, then connect the model provider you actually want to use. Everything optional stays out of your way."),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            OnboardingValueRow(Icons.Outlined.Lock, "Private by design", "Chats, credentials, and tool workspaces stay on this device.")
            OnboardingValueRow(Icons.Outlined.Cloud, "Bring your own models", "Use a ChatGPT account, API provider, or local server.")
            OnboardingValueRow(Icons.Outlined.Code, "Optional tools stay optional", "Python and Linux are managed later from Settings → Local execution.")
        }
    }
    SetupRestoreActions(viewModel)
    Text(
        uiText("Starting fresh? Ignore the restore card and tap Continue."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProviderStep(
    providerCatalogDelayed: Boolean,
    configuredProviderCount: Int,
) {
    SetupHeading(
        if (configuredProviderCount > 0) "Model access connected" else "Connect a model provider",
        if (configuredProviderCount > 0) {
            "Xylune detected ${providerCountLabel(configuredProviderCount)}. Continue or open the provider manager to make changes."
        } else {
            "A provider is needed only when you send a message. You may connect one now or continue and do it later."
        },
    )
    if (configuredProviderCount > 0) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(uiText("${providerCountLabel(configuredProviderCount)} ready"), fontWeight = FontWeight.SemiBold)
                    Text(
                        uiText("Credentials were found and this step is complete."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (providerCatalogDelayed) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                uiText("The built-in provider catalog is delayed. Setup remains usable and Xylune will keep retrying in the background."),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            OnboardingValueRow(Icons.Outlined.AccountCircle, "ChatGPT account", "Sign in without pasting an API key.")
            OnboardingValueRow(Icons.Outlined.Cloud, "API provider", "Use OpenAI, Anthropic, Gemini, DeepSeek, or another compatible endpoint.")
            OnboardingValueRow(Icons.Outlined.Storage, "Local server", "Connect to Ollama, llama.cpp, or LM Studio.")
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null)
            Text(uiText("Credentials are encrypted with Android Keystore and sent only to the provider you choose."), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReadyStep(configuredProviderCount: Int) {
    SetupHeading(
        "Xylune is ready",
        "You can start chatting now. Appearance, local execution, backups, memory, and other optional features remain in clearly grouped Settings.",
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            OnboardingValueRow(
                if (configuredProviderCount > 0) Icons.Outlined.CheckCircle else Icons.Outlined.Cloud,
                "Model provider",
                if (configuredProviderCount > 0) "${providerCountLabel(configuredProviderCount)} connected" else "Not connected yet",
            )
            OnboardingValueRow(Icons.Outlined.CheckCircle, "Focused defaults", "Local execution starts off; enable Python or Linux only when a chat needs it.")
            OnboardingValueRow(Icons.Outlined.CheckCircle, "Settings stay optional", "Appearance, backups, memory, and advanced behavior are grouped for later.")
        }
    }
    Text(
        uiText("Enter Xylune now. You can return to Providers & models from Settings at any time."),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SetupHeading(title: String, subtitle: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(uiText(subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun OnboardingValueRow(icon: ImageVector, title: String, subtitle: String) {
    ListItem(
        headlineContent = { Text(uiText(title), fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private fun providerCountLabel(count: Int): String =
    if (count == 1) "1 configured provider" else "$count configured providers"
