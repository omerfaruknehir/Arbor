package app.xylune.chat.ui

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xylune.chat.R
import app.xylune.chat.sandbox.ExecutionResult
import app.xylune.chat.sandbox.PackageInstallResult
import app.xylune.chat.sandbox.PackageInstallProgress
import app.xylune.chat.sandbox.PythonEnvironmentInfo
import app.xylune.chat.sandbox.PythonPackageSearchResult
import app.xylune.chat.sandbox.PackageAction
import app.xylune.chat.sandbox.PackageApprovalState
import app.xylune.chat.sandbox.PackageReview
import app.xylune.chat.sandbox.LinuxDistribution
import app.xylune.chat.sandbox.UbuntuExecutionResult
import app.xylune.chat.sandbox.UbuntuPackageInstallResult
import app.xylune.chat.sandbox.UbuntuStage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class WorkspaceSection { OVERVIEW, PYTHON, LINUX }

private fun formatSetupDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) "${minutes}m ${seconds}s" else "${seconds}s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(viewModel: ChatViewModel) {
    val appName = stringResource(R.string.app_name)
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsState()
    val chromeEdgeSoftness by viewModel.chromeEdgeSoftness.collectAsState()
    val chromeOverlayOpacity by viewModel.chromeOverlayOpacity.collectAsState()
    var code by remember {
        mutableStateOf(
            "from pathlib import Path\n\n" +
                "print('Hello from $appName bundled Python')\n" +
                "print('Workspace:', Path.cwd())\n",
        )
    }
    var packages by remember { mutableStateOf("") }
    var packageQuery by remember { mutableStateOf("") }
    var packageSearching by remember { mutableStateOf(false) }
    var packageSearchResults by remember { mutableStateOf<List<PythonPackageSearchResult>>(emptyList()) }
    var confirmInstall by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var installResult by remember { mutableStateOf<PackageInstallResult?>(null) }
    var packageReview by remember { mutableStateOf<PackageReview?>(null) }
    var environment by remember { mutableStateOf<PythonEnvironmentInfo?>(null) }
    var environmentBusy by remember { mutableStateOf(false) }
    var removePackage by remember { mutableStateOf<String?>(null) }
    var timeoutSeconds by remember { mutableStateOf("90") }
    var ubuntuPackages by remember { mutableStateOf("") }
    var ubuntuReview by remember { mutableStateOf<PackageReview?>(null) }
    var confirmUbuntuInstall by remember { mutableStateOf(false) }
    var ubuntuPackageResult by remember { mutableStateOf<UbuntuPackageInstallResult?>(null) }
    var ubuntuInstallProgress by remember { mutableStateOf<PackageInstallProgress?>(null) }
    var ubuntuInstalling by remember { mutableStateOf(false) }
    var showUbuntuPlan by remember { mutableStateOf(false) }
    var showPythonPlan by remember { mutableStateOf(false) }
    var confirmLinuxRemoval by remember { mutableStateOf(false) }
    val ubuntuStatus by viewModel.ubuntuStatus.collectAsState()
    val linuxSetupActive = ubuntuStatus.stage in setOf(
        UbuntuStage.DOWNLOADING,
        UbuntuStage.VERIFYING,
        UbuntuStage.EXTRACTING,
        UbuntuStage.CONFIGURING,
    )
    var workspaceSection by remember { mutableStateOf(WorkspaceSection.OVERVIEW) }
    val pythonRun by viewModel.pythonRun.collectAsState()
    val running = pythonRun?.running == true
    val result = pythonRun?.result ?: pythonRun?.error?.let { ExecutionResult(stderr = it) }
    val conversationId by viewModel.selectedConversationId.collectAsState()
    val setupTemporarilyAway by viewModel.setupTemporarilyAway.collectAsState()
    val scope = rememberCoroutineScope()
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dismissedLongRun by remember { mutableLongStateOf(0L) }
    suspend fun refreshEnvironment() {
        environmentBusy = true
        environment = runCatching { viewModel.pythonEnvironment() }.getOrNull()
        environmentBusy = false
    }
    suspend fun installPythonNow() {
        installing = true
        installResult = runCatching { viewModel.installPythonPackages(packages, packageReview?.plan ?: error("Run preflight again")) }
            .getOrElse { PackageInstallResult(stderr = it.message.orEmpty()) }
        refreshEnvironment()
        installing = false
    }
    suspend fun installUbuntuPackagesNow() {
        ubuntuInstalling = true
        ubuntuInstallProgress = PackageInstallProgress("Starting package installation", 0f)
        ubuntuPackageResult = runCatching {
            viewModel.installUbuntuPackages(
                ubuntuPackages,
                ubuntuReview?.plan ?: error("Run preflight again"),
            ) { progress ->
                withContext(Dispatchers.Main.immediate) { ubuntuInstallProgress = progress }
            }
        }
            .getOrElse { UbuntuPackageInstallResult(false, stderr = it.message.orEmpty()) }
        ubuntuInstalling = false
    }
    LaunchedEffect(conversationId) { refreshEnvironment(); viewModel.refreshUbuntu() }
    LaunchedEffect(running, linuxSetupActive) {
        while (running || linuxSetupActive) {
            clock = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LaunchedEffect(packageQuery) {
        delay(350)
        if (packageQuery.trim().length < 2) {
            packageSearchResults = emptyList()
            return@LaunchedEffect
        }
        packageSearching = true
        packageSearchResults = runCatching { viewModel.searchPythonPackages(packageQuery) }.getOrDefault(emptyList())
        packageSearching = false
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val blurState = rememberXyluneBackdropBlurState()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CollapsingTranslucentTopBar(
                title = "Runtime manager",
                scrollBehavior = scrollBehavior,
                blurState = blurState,
                blurStrength = chromeBlurStrength,
                edgeSoftness = chromeEdgeSoftness,
                overlayOpacity = chromeOverlayOpacity,
                blurArea = STANDARD_TOP_PANEL_HEIGHT_DP.dp,
                navigationIcon = {
                    IconButton(onClick = {
                        if (setupTemporarilyAway) viewModel.screen.value = Screen.CHAT
                        else viewModel.screen.value = Screen.SETTINGS
                    }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            if (setupTemporarilyAway) "Back to setup" else "Back to Settings",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .xyluneBackdropSource(blurState)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(uiText("Runtime manager"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                uiText("Python and Linux share this chat's private /workspace. Runtime setup, packages, health, tests, and removal have one owner here."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = workspaceSection == WorkspaceSection.OVERVIEW,
                    onClick = { workspaceSection = WorkspaceSection.OVERVIEW },
                    label = { Text(uiText("Overview")) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = workspaceSection == WorkspaceSection.PYTHON,
                    onClick = { workspaceSection = WorkspaceSection.PYTHON },
                    label = { Text(uiText("Python")) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = workspaceSection == WorkspaceSection.LINUX,
                    onClick = { workspaceSection = WorkspaceSection.LINUX },
                    label = { Text(uiText("Linux")) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (workspaceSection == WorkspaceSection.OVERVIEW) {
                Text(uiText("Runtimes"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    uiText("Python is built in. Linux is an optional compatibility layer with a separate download. Pick a runtime only when you need to inspect or change it."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().clickable { workspaceSection = WorkspaceSection.PYTHON },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Code, null)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(uiText("Python · Ready"), fontWeight = FontWeight.SemiBold)
                            Text(
                                uiText(environment?.let { "Python ${it.pythonVersion} · ${it.packages.size} packages · ${Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, it.sizeBytes)}" }
                                    ?: "Bundled runtime · loading this chat's environment…"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(Icons.Outlined.CheckCircle, null)
                    }
                }
                Surface(
                    color = if (ubuntuStatus.installed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (ubuntuStatus.installed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().clickable { workspaceSection = WorkspaceSection.LINUX },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storage, null)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                uiText(if (ubuntuStatus.installed) "${ubuntuStatus.distribution.displayName} ${ubuntuStatus.release} · Ready" else "Linux · Not installed"),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                uiText(if (ubuntuStatus.installed) "Terminal, native CLI tools, and Linux packages"
                                else "Optional download for tools that cannot run in bundled Python"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (ubuntuStatus.installed) Icon(Icons.Outlined.CheckCircle, null)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(uiText("What this screen manages"), fontWeight = FontWeight.SemiBold)
                        Text(uiText("• Runtime health and per-chat Python packages"), style = MaterialTheme.typography.bodySmall)
                        Text(uiText("• Linux installation, packages, terminal, and removal"), style = MaterialTheme.typography.bodySmall)
                        Text(uiText("• Test runs only; chat tool permissions are controlled per chat"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (workspaceSection == WorkspaceSection.PYTHON) {
            Text(uiText("Python workspace"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(uiText("Each chat has a persistent bundled-Python session and isolated .packages directory. It works without installing Linux and remains confined by Android's app sandbox."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            environment?.let { info ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(uiText("Environment ${info.environmentId}"), fontWeight = FontWeight.SemiBold)
                        Text(uiText("Python ${info.pythonVersion} • ${info.packages.size} packages • ${Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, info.sizeBytes)}"), style = MaterialTheme.typography.bodySmall)
                        if (info.nativeErrors.isNotEmpty()) Text(
                            uiText("Native compatibility warning: " + info.nativeErrors.entries.joinToString { "${it.key}: ${it.value}" }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        info.packages.forEach { pkg ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(uiText("${pkg.name} ${pkg.version}"), Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                IconButton(onClick = { removePackage = pkg.name }) { Icon(Icons.Outlined.Delete, uiText("Remove ${pkg.name}")) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { scope.launch { environmentBusy = true; environment = viewModel.repairPythonEnvironment(); environmentBusy = false } },
                                enabled = !environmentBusy,
                            ) { Icon(Icons.Outlined.Refresh, null); Text(uiText("Repair"), Modifier.padding(start = 6.dp)) }
                            OutlinedButton(
                                onClick = { scope.launch { viewModel.resetPythonSession(); viewModel.clearPythonRun() } },
                                enabled = !environmentBusy,
                            ) { Icon(Icons.Outlined.RestartAlt, null); Text(uiText("Clear run state"), Modifier.padding(start = 6.dp)) }
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiText("Install packages"), fontWeight = FontWeight.SemiBold)
                    Text(uiText("One package requirement per line. Xylune resolves Android-compatible Python 3.12 wheels before applying your approval policy."), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = packageQuery,
                        onValueChange = { packageQuery = it.take(100) },
                        label = { Text(uiText("Search PyPI")) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        supportingText = { Text(uiText(if (packageSearching) "Searching…" else "Tap a result to add it to the install list")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    packageSearchResults.take(8).forEach { result ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().clickable {
                                val existing = packages.lineSequence().map(String::trim).filter(String::isNotBlank).toMutableList()
                                if (existing.none { it.substringBefore('=') == result.name }) existing += result.name
                                packages = existing.joinToString("\n")
                            },
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Text(uiText("${result.name} ${result.version}"), fontWeight = FontWeight.SemiBold)
                                if (result.summary.isNotBlank()) Text(result.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    OutlinedTextField(
                        packages, { packages = it },
                        label = { Text(uiText("requests==2.32.4\nnumpy==1.26.2")) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = {
                        scope.launch {
                            installing = true
                            installResult = null
                            packageReview = null
                            packageReview = runCatching { viewModel.reviewPythonPackages(packages) }
                                .onFailure { installResult = PackageInstallResult(stderr = it.message.orEmpty()) }
                                .getOrNull()
                            installing = false
                            when (packageReview?.state) {
                                PackageApprovalState.APPROVED -> installPythonNow()
                                PackageApprovalState.REQUIRED -> confirmInstall = true
                                else -> Unit
                            }
                        }
                    }, enabled = packages.isNotBlank() && !installing) {
                        Text(uiText(if (installing && packageReview?.state == PackageApprovalState.APPROVED) "Installing…" else if (installing) "Checking…" else "Preflight & review"))
                    }
                    Surface(
                        color = if (installResult?.success == false || installResult?.importErrors?.isNotEmpty() == true) MaterialTheme.colorScheme.errorContainer.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            uiText(when {
                                installing && packageReview?.state == PackageApprovalState.APPROVED -> "Approved automatically • installing and verifying imports…"
                                installing -> "Checking the current Python environment…"
                                installResult?.success == true && installResult?.importErrors?.isEmpty() == true -> "Installation and import verification completed."
                                installResult != null -> "Installation or import verification failed."
                                packageReview?.state == PackageApprovalState.NOT_NEEDED -> "Everything requested is already installed."
                                packageReview?.state == PackageApprovalState.APPROVED -> "Approved automatically • starting installation…"
                                packageReview?.state == PackageApprovalState.REQUIRED -> "Plan ready for your confirmation."
                                packageReview?.state == PackageApprovalState.DENIED -> "Installation blocked • ${packageReview?.reason.orEmpty()}"
                                else -> "No package transaction running."
                            }),
                            Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    packageReview?.let { review ->
                        val changes = review.plan.items.count { it.action == PackageAction.INSTALL || it.action == PackageAction.UPDATE }
                        Text(uiText("${review.plan.items.size} packages resolved • $changes changes"), style = MaterialTheme.typography.labelMedium)
                        Text(uiText("${review.decidedBy}: ${review.reason}"), style = MaterialTheme.typography.labelSmall)
                        OutlinedButton(onClick = { showPythonPlan = !showPythonPlan }) { Text(uiText(if (showPythonPlan) "Collapse plan" else "Show package plan")) }
                        if (showPythonPlan) review.plan.items.forEach { item ->
                            Text(uiText("${item.name} • ${item.action.name.lowercase().replace('_', ' ')}${item.installedVersion?.let { " • installed $it" }.orEmpty()}${item.candidateVersion?.let { " → $it" }.orEmpty()}"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    installResult?.let { install ->
                        Text(
                            uiText(if (install.success && install.importErrors.isEmpty()) "Installed and import-verified ${install.packages.joinToString()}"
                            else if (install.success) "Installed, but import verification found a problem"
                            else "Installation failed"),
                            color = if (install.success && install.importErrors.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        if (install.success && install.importNames.isNotEmpty()) Text(
                            uiText(install.importNames.entries.joinToString("\n") { (distribution, names) -> "$distribution → import ${names.joinToString().ifBlank { "name unavailable" }}" }),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (install.importErrors.isNotEmpty()) Text(
                            uiText("Import verification warning:\n" + install.importErrors.entries.joinToString("\n") { "${it.key}: ${it.value}" }),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (!install.success && install.stderr.isNotBlank()) Text(uiText(install.stderr.lines().takeLast(12).joinToString("\n").takeLast(1_200)), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            OutlinedTextField(
                timeoutSeconds,
                { timeoutSeconds = it.filter(Char::isDigit).take(3) },
                label = { Text(uiText("Execution deadline (seconds)")) },
                supportingText = { Text(uiText("1–600 seconds. Pure Python is interrupted at the deadline; a blocking native extension may return later.")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                code, { code = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = rememberCodeVisualTransformation("python"),
                minLines = 12,
                label = { Text(uiText("Python script")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.startPythonRun(code, timeoutSeconds.toIntOrNull()?.coerceIn(1, 600) ?: 90) },
                enabled = code.isNotBlank() && !running,
            ) { Icon(Icons.Outlined.PlayArrow, null); Text(uiText(if (running) "Running…" else "Run"), Modifier.padding(start = 8.dp)) }
            if (running) {
                Text(
                    uiText("Running in the background • ${(clock - (pythonRun?.startedAt ?: clock)) / 1_000}s • you can browse other chats"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                LiveExecutionCard(pythonRun?.progress ?: app.xylune.chat.sandbox.ExecutionProgress(), "Python execution")
            }
            result?.let { output ->
                PythonExecutionCard(output)
            }

            } else {
            Text(uiText("Linux workspace"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(uiText("Choose a rootless user-space distribution for broader third-party CLIs and libraries. Each distribution keeps its own packages, shares this chat's files at /workspace, and is a compatibility layer—not a security boundary."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ubuntuStatus.installed) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(uiText("Before the first install"), fontWeight = FontWeight.SemiBold)
                        Text(uiText("• Requires a network download and app-private storage."), style = MaterialTheme.typography.bodySmall)
                        Text(uiText("• Xylune verifies the archive before extraction and exposes progress for every stage."), style = MaterialTheme.typography.bodySmall)
                        Text(uiText("• A failed or interrupted setup can be retried; /workspace chat files are not deleted."), style = MaterialTheme.typography.bodySmall)
                        Text(uiText("• No Android root access is used or requested."), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LinuxDistribution.entries.forEach { option ->
                    FilterChip(
                        selected = ubuntuStatus.distribution == option,
                        onClick = {
                            viewModel.selectLinuxDistribution(option)
                            viewModel.clearLinuxRun()
                            ubuntuReview = null
                            ubuntuPackageResult = null
                        },
                        enabled = ubuntuStatus.stage !in setOf(UbuntuStage.DOWNLOADING, UbuntuStage.VERIFYING, UbuntuStage.EXTRACTING, UbuntuStage.CONFIGURING),
                        label = { Text(uiText(option.displayName)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(uiText("${ubuntuStatus.distribution.description} • ${ubuntuStatus.distribution.release} • ${ubuntuStatus.distribution.packageManager.command}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiText("${ubuntuStatus.distribution.displayName} • ${ubuntuStatus.stage.name.lowercase().replace('_', ' ')} • ${ubuntuStatus.architecture}"), fontWeight = FontWeight.SemiBold)
                    if (!linuxSetupActive) {
                        Text(ubuntuStatus.detail, style = MaterialTheme.typography.bodySmall)
                    }
                    if (ubuntuStatus.sizeBytes > 0) {
                        Text(
                            uiText("Linux data on disk: ${Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, ubuntuStatus.sizeBytes)}"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (linuxSetupActive) {
                        val measuredProgress = ubuntuStatus.progress?.coerceIn(0f, 1f)
                        val animatedProgress by animateFloatAsState(
                            targetValue = measuredProgress ?: 0f,
                            animationSpec = tween(durationMillis = 450),
                            label = "linux-setup-progress",
                        )
                        val step = ubuntuStatus.currentStep.coerceAtLeast(1)
                        val total = ubuntuStatus.totalSteps.coerceAtLeast(step)
                        val elapsedMs = if (ubuntuStatus.startedAtMs > 0L) {
                            (clock - ubuntuStatus.startedAtMs).coerceAtLeast(0L)
                        } else {
                            0L
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .78f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        uiText("Step $step of $total"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        uiText(measuredProgress?.let { "${(it * 100).toInt()}%" } ?: "Working…"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (measuredProgress != null) {
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    repeat(total) { index ->
                                        val segmentStep = index + 1
                                        val segmentColor = when {
                                            segmentStep < step -> MaterialTheme.colorScheme.primary
                                            segmentStep == step -> MaterialTheme.colorScheme.primary.copy(alpha = .62f)
                                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)
                                        }
                                        Surface(
                                            color = segmentColor,
                                            shape = RoundedCornerShape(999.dp),
                                            modifier = Modifier.weight(1f).height(4.dp),
                                        ) {}
                                    }
                                }
                                Text(
                                    ubuntuStatus.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        uiText("Elapsed ${formatSetupDuration(elapsedMs)}"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        uiText("Keep Xylune open"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!ubuntuStatus.installed) Button(
                            onClick = { scope.launch { viewModel.installUbuntu() } },
                            enabled = !linuxSetupActive,
                        ) { Text(uiText(if (ubuntuStatus.stage == UbuntuStage.ERROR) "Retry setup" else "Install ${ubuntuStatus.distribution.displayName}")) }
                        else OutlinedButton(onClick = { scope.launch { viewModel.refreshUbuntu() } }) { Icon(Icons.Outlined.Refresh, null); Text(uiText("Refresh"), Modifier.padding(start = 6.dp)) }
                        if (ubuntuStatus.installed) OutlinedButton(onClick = { confirmLinuxRemoval = true }) { Icon(Icons.Outlined.Delete, null); Text(uiText("Remove"), Modifier.padding(start = 6.dp)) }
                    }
                }
            }
            if (ubuntuStatus.installed) {
                Button(
                    onClick = { viewModel.screen.value = Screen.TERMINAL },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Terminal, null)
                    Text(uiText("Open ${ubuntuStatus.distribution.displayName} terminal"), Modifier.padding(start = 8.dp))
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(uiText("Install ${ubuntuStatus.distribution.displayName} packages"), fontWeight = FontWeight.SemiBold)
                        Text(uiText("${ubuntuStatus.distribution.packageManager.command} simulates the complete transaction first and checks what is already installed before approval."), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(ubuntuPackages, { ubuntuPackages = it }, label = { Text(uiText("ripgrep\nffmpeg")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    ubuntuInstalling = true
                                    ubuntuPackageResult = null
                                    ubuntuInstallProgress = null
                                    ubuntuReview = runCatching { viewModel.reviewUbuntuPackages(ubuntuPackages) }
                                        .onFailure { ubuntuPackageResult = UbuntuPackageInstallResult(false, stderr = it.message.orEmpty()) }
                                        .getOrNull()
                                    ubuntuInstalling = false
                                    when (ubuntuReview?.state) {
                                        PackageApprovalState.APPROVED -> installUbuntuPackagesNow()
                                        PackageApprovalState.REQUIRED -> confirmUbuntuInstall = true
                                        else -> Unit
                                    }
                                }
                            },
                            enabled = ubuntuPackages.isNotBlank() && !ubuntuInstalling,
                        ) { Text(uiText(if (ubuntuInstalling && ubuntuReview?.state == PackageApprovalState.APPROVED) "Installing…" else if (ubuntuInstalling) "Checking…" else "Preflight & review")) }
                        Surface(
                            color = if (ubuntuPackageResult?.success == false) MaterialTheme.colorScheme.errorContainer.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                uiText(when {
                                    ubuntuInstalling && ubuntuReview?.state == PackageApprovalState.APPROVED -> "Approved automatically • repairing package state and installing…"
                                    ubuntuInstalling -> "Simulating ${ubuntuStatus.distribution.packageManager.command} transaction…"
                                    ubuntuPackageResult?.success == true -> "Installation completed successfully."
                                    ubuntuPackageResult?.success == false -> "Installation failed. No success was recorded."
                                    ubuntuReview?.state == PackageApprovalState.NOT_NEEDED -> "Everything requested is already installed."
                                    ubuntuReview?.state == PackageApprovalState.APPROVED -> "Approved automatically • starting installation…"
                                    ubuntuReview?.state == PackageApprovalState.REQUIRED -> "Plan ready for your confirmation."
                                    ubuntuReview?.state == PackageApprovalState.DENIED -> "Installation blocked • ${ubuntuReview?.reason.orEmpty()}"
                                    else -> "No package transaction running."
                                }),
                                Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        ubuntuInstallProgress?.let { progress ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (progress.percent != null) {
                                    LinearProgressIndicator(
                                        progress = { progress.percent.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else if (ubuntuInstalling) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(uiText(progress.phase), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    progress.percent?.let { Text(uiText("${(it.coerceIn(0f, 1f) * 100).toInt()}%"), style = MaterialTheme.typography.labelMedium) }
                                }
                                progress.currentPackage?.let {
                                    Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                }
                                if (progress.detail.isNotBlank()) {
                                    Text(uiText(progress.detail), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val liveLog = listOf(progress.stdoutTail, progress.stderrTail)
                                    .filter(String::isNotBlank)
                                    .joinToString("\n")
                                    .takeLast(12_000)
                                if (liveLog.isNotBlank() && ubuntuInstalling) GenericToolOutputCard(liveLog)
                            }
                        }
                        ubuntuReview?.let { review ->
                            val changes = review.plan.items.count { it.action == PackageAction.INSTALL || it.action == PackageAction.UPDATE }
                            val dependencies = review.plan.items.count { it.detail == "Dependency" }
                            Text(uiText("${review.plan.items.size} packages resolved • $changes changes • $dependencies dependencies"), style = MaterialTheme.typography.labelMedium)
                            if (review.plan.downloadSummary.isNotBlank()) Text(uiText("Download: ${review.plan.downloadSummary}"), style = MaterialTheme.typography.labelSmall)
                            if (review.plan.diskSummary.isNotBlank()) Text(uiText("Disk: ${review.plan.diskSummary}"), style = MaterialTheme.typography.labelSmall)
                            Text(uiText("${review.decidedBy}: ${review.reason}"), style = MaterialTheme.typography.labelSmall)
                            OutlinedButton(onClick = { showUbuntuPlan = !showUbuntuPlan }) { Text(uiText(if (showUbuntuPlan) "Collapse complete plan" else "Show complete package plan")) }
                            if (showUbuntuPlan) review.plan.items.forEach { item -> Text(uiText("${item.name} • ${item.action.name.lowercase().replace('_', ' ')}${item.candidateVersion?.let { " → $it" }.orEmpty()}${item.detail.takeIf(String::isNotBlank)?.let { " • $it" }.orEmpty()}"), style = MaterialTheme.typography.labelSmall) }
                        }
                        ubuntuPackageResult?.let { installed ->
                            Text(uiText(if (installed.success) "Installed ${installed.packages.joinToString()}" else "${ubuntuStatus.distribution.packageManager.command} install failed"), color = if (installed.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            val log = installed.stderr.ifBlank { installed.stdout }.takeLast(12_000)
                            if (log.isNotBlank()) GenericToolOutputCard(log, failed = !installed.success)
                        }
                    }
                }
            }
            }
        }
    }
    if (confirmInstall) XyluneAlertDialog(
        onDismissRequest = { confirmInstall = false },
        title = { Text(uiText("Allow package installation?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(uiText("Only missing or outdated packages will change. Install scripts run with Xylune's app permissions."))
                packageReview?.plan?.items?.filter { it.action != PackageAction.ALREADY_INSTALLED }?.forEach { item ->
                    Text(uiText("• ${item.name}: ${item.action.name.lowercase()}${item.candidateVersion?.let { " $it" }.orEmpty()}"), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { OutlinedButton(onClick = { confirmInstall = false }) { Text(uiText("Cancel")) } },
        confirmButton = {
            Button(onClick = {
                confirmInstall = false
                scope.launch { installPythonNow() }
            }, enabled = packageReview?.plan?.hasChanges == true) { Text(uiText("Allow and install")) }
        },
    )
    if (confirmUbuntuInstall) XyluneAlertDialog(
        onDismissRequest = { confirmUbuntuInstall = false },
        title = { Text(uiText("Allow ${ubuntuStatus.distribution.packageManager.command} package changes?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(uiText("This is the complete ${ubuntuStatus.distribution.packageManager.command} simulation, including dependencies:"))
                ubuntuReview?.plan?.items?.filter { it.action != PackageAction.ALREADY_INSTALLED }?.forEach { item ->
                    Text(uiText("• ${item.name}: ${item.action.name.lowercase()}${item.candidateVersion?.let { " $it" }.orEmpty()}${item.detail.takeIf(String::isNotBlank)?.let { " • $it" }.orEmpty()}"), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                ubuntuReview?.plan?.downloadSummary?.takeIf(String::isNotBlank)?.let { Text(uiText("Download: $it")) }
                ubuntuReview?.plan?.diskSummary?.takeIf(String::isNotBlank)?.let { Text(uiText("Disk: $it")) }
            }
        },
        dismissButton = { OutlinedButton(onClick = { confirmUbuntuInstall = false }) { Text(uiText("Cancel")) } },
        confirmButton = {
            Button(onClick = {
                confirmUbuntuInstall = false
                scope.launch { installUbuntuPackagesNow() }
            }, enabled = ubuntuReview?.plan?.hasChanges == true) { Text(uiText("Allow and install")) }
        },
    )
    removePackage?.let { name ->
        XyluneAlertDialog(
            onDismissRequest = { removePackage = null },
            title = { Text(uiText("Remove $name?")) },
            text = { Text(uiText("$appName will remove this Python package from the current chat environment. Other packages and the optional Linux runtime are kept.")) },
            dismissButton = { OutlinedButton(onClick = { removePackage = null }) { Text(uiText("Cancel")) } },
            confirmButton = {
                Button(onClick = {
                    removePackage = null
                    scope.launch { environmentBusy = true; environment = viewModel.removePythonPackages(listOf(name)); environmentBusy = false }
                }) { Text(uiText("Remove")) }
            },
        )
    }
    if (confirmLinuxRemoval) {
        XyluneAlertDialog(
            onDismissRequest = { confirmLinuxRemoval = false },
            title = { Text(uiText("Remove ${ubuntuStatus.distribution.displayName}?")) },
            text = {
                Text(uiText("This removes the selected Linux root filesystem and its installed packages for all chats. Chat files in /workspace and bundled Python packages are kept."))
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmLinuxRemoval = false }) { Text(uiText("Cancel")) }
            },
            confirmButton = {
                Button(onClick = {
                    confirmLinuxRemoval = false
                    scope.launch { viewModel.removeUbuntu() }
                }) { Text(uiText("Remove Linux runtime")) }
            },
        )
    }
    val longPython = pythonRun?.takeIf { it.running && clock - it.startedAt >= 10_000 && dismissedLongRun != it.startedAt }
    if (longPython != null) {
        val startedAt = longPython.startedAt
        val seconds = ((clock - startedAt) / 1_000).coerceAtLeast(10)
        XyluneAlertDialog(
            onDismissRequest = { dismissedLongRun = startedAt },
            title = { Text(uiText("Python is still running")) },
            text = {
                Text(uiText("This has taken $seconds seconds. It will keep running while you browse Xylune, up to its hard deadline. You can leave it in the background or stop it now. A blocking native Python extension may take a moment to return after Stop."))
            },
            dismissButton = { OutlinedButton(onClick = { dismissedLongRun = startedAt }) { Text(uiText("Keep in background")) } },
            confirmButton = {
                Button(onClick = {
                    viewModel.stopPythonRun()
                    dismissedLongRun = startedAt
                }) { Text(uiText("Stop")) }
            },
        )
    }
}
