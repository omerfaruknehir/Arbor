package app.arbor.chat.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.arbor.chat.R
import app.arbor.chat.sandbox.ExecutionResult
import app.arbor.chat.sandbox.PackageInstallResult
import app.arbor.chat.sandbox.PythonEnvironmentInfo
import app.arbor.chat.sandbox.PythonPackageSearchResult
import app.arbor.chat.sandbox.PackageAction
import app.arbor.chat.sandbox.PackageApprovalState
import app.arbor.chat.sandbox.PackageReview
import app.arbor.chat.sandbox.LinuxDistribution
import app.arbor.chat.sandbox.UbuntuExecutionResult
import app.arbor.chat.sandbox.UbuntuPackageInstallResult
import app.arbor.chat.sandbox.UbuntuStage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(viewModel: ChatViewModel) {
    val appName = stringResource(R.string.app_name)
    val chromeBlurEnabled by viewModel.chromeBlurEnabled.collectAsState()
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsState()
    var code by remember {
        mutableStateOf(
            "from pathlib import Path\n\n" +
                "print('Hello from $appName distro Python')\n" +
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
    var linuxTimeoutSeconds by remember { mutableStateOf("180") }
    var ubuntuCommand by remember { mutableStateOf("uname -a\npython3 --version || true\nls -la") }
    var ubuntuPackages by remember { mutableStateOf("") }
    var ubuntuReview by remember { mutableStateOf<PackageReview?>(null) }
    var confirmUbuntuInstall by remember { mutableStateOf(false) }
    var ubuntuPackageResult by remember { mutableStateOf<UbuntuPackageInstallResult?>(null) }
    var ubuntuInstalling by remember { mutableStateOf(false) }
    var showUbuntuPlan by remember { mutableStateOf(false) }
    var showPythonPlan by remember { mutableStateOf(false) }
    val ubuntuStatus by viewModel.ubuntuStatus.collectAsState()
    val pythonRun by viewModel.pythonRun.collectAsState()
    val linuxRun by viewModel.linuxRun.collectAsState()
    val running = pythonRun?.running == true
    val ubuntuRunning = linuxRun?.running == true
    val result = pythonRun?.result ?: pythonRun?.error?.let { ExecutionResult(stderr = it) }
    val ubuntuResult = linuxRun?.result ?: linuxRun?.error?.let { UbuntuExecutionResult(stderr = it) }
    val conversationId by viewModel.selectedConversationId.collectAsState()
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
        ubuntuPackageResult = runCatching { viewModel.installUbuntuPackages(ubuntuPackages, ubuntuReview?.plan ?: error("Run preflight again")) }
            .getOrElse { UbuntuPackageInstallResult(false, stderr = it.message.orEmpty()) }
        ubuntuInstalling = false
    }
    LaunchedEffect(conversationId) { refreshEnvironment(); viewModel.refreshUbuntu() }
    LaunchedEffect(running, ubuntuRunning) {
        while (running || ubuntuRunning) {
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
    val blurState = rememberArborBackdropBlurState()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CollapsingTranslucentTopBar(
                title = "Local Code Execution",
                scrollBehavior = scrollBehavior,
                blurState = blurState,
                blurEnabled = chromeBlurEnabled,
                blurStrength = chromeBlurStrength,
                navigationIcon = {
                    IconButton(onClick = { viewModel.screen.value = Screen.SETTINGS }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .arborBackdropSource(blurState)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Persistent local workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Python runs as root inside the selected Linux distribution. Each chat has a persistent /workspace and isolated .arbor-venv; Android still confines the whole app outside the PRoot distribution.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            environment?.let { info ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Environment ${info.environmentId}", fontWeight = FontWeight.SemiBold)
                        Text("Python ${info.pythonVersion} • ${info.packages.size} packages • ${Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, info.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                        if (info.nativeErrors.isNotEmpty()) Text(
                            "Native compatibility warning: " + info.nativeErrors.entries.joinToString { "${it.key}: ${it.value}" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        info.packages.forEach { pkg ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("${pkg.name} ${pkg.version}", Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                IconButton(onClick = { removePackage = pkg.name }) { Icon(Icons.Outlined.Delete, "Remove ${pkg.name}") }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { scope.launch { environmentBusy = true; environment = viewModel.repairPythonEnvironment(); environmentBusy = false } },
                                enabled = !environmentBusy,
                            ) { Icon(Icons.Outlined.Refresh, null); Text("Repair", Modifier.padding(start = 6.dp)) }
                            OutlinedButton(
                                onClick = { scope.launch { viewModel.resetPythonSession(); viewModel.clearPythonRun() } },
                                enabled = !environmentBusy,
                            ) { Icon(Icons.Outlined.RestartAlt, null); Text("Clear run state", Modifier.padding(start = 6.dp)) }
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Install packages", fontWeight = FontWeight.SemiBold)
                    Text("One package requirement per line. Search PyPI, then Arbor preflights the distro-backed virtual environment before applying your approval policy.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = packageQuery,
                        onValueChange = { packageQuery = it.take(100) },
                        label = { Text("Search PyPI") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        supportingText = { Text(if (packageSearching) "Searching…" else "Tap a result to add it to the install list") },
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
                                Text("${result.name} ${result.version}", fontWeight = FontWeight.SemiBold)
                                if (result.summary.isNotBlank()) Text(result.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    OutlinedTextField(
                        packages, { packages = it },
                        label = { Text("requests==2.32.4\nnumpy==1.26.2") },
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
                        Text(if (installing && packageReview?.state == PackageApprovalState.APPROVED) "Installing…" else if (installing) "Checking…" else "Preflight & review")
                    }
                    Surface(
                        color = if (installResult?.success == false || installResult?.importErrors?.isNotEmpty() == true) MaterialTheme.colorScheme.errorContainer.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                installing && packageReview?.state == PackageApprovalState.APPROVED -> "Approved automatically • installing and verifying imports…"
                                installing -> "Checking the current Python environment…"
                                installResult?.success == true && installResult?.importErrors?.isEmpty() == true -> "Installation and import verification completed."
                                installResult != null -> "Installation or import verification failed."
                                packageReview?.state == PackageApprovalState.NOT_NEEDED -> "Everything requested is already installed."
                                packageReview?.state == PackageApprovalState.APPROVED -> "Approved automatically • starting installation…"
                                packageReview?.state == PackageApprovalState.REQUIRED -> "Plan ready for your confirmation."
                                packageReview?.state == PackageApprovalState.DENIED -> "Installation blocked • ${packageReview?.reason.orEmpty()}"
                                else -> "No package transaction running."
                            },
                            Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    packageReview?.let { review ->
                        val changes = review.plan.items.count { it.action == PackageAction.INSTALL || it.action == PackageAction.UPDATE }
                        Text("${review.plan.items.size} packages resolved • $changes changes", style = MaterialTheme.typography.labelMedium)
                        Text("${review.decidedBy}: ${review.reason}", style = MaterialTheme.typography.labelSmall)
                        OutlinedButton(onClick = { showPythonPlan = !showPythonPlan }) { Text(if (showPythonPlan) "Collapse plan" else "Show package plan") }
                        if (showPythonPlan) review.plan.items.forEach { item ->
                            Text("${item.name} • ${item.action.name.lowercase().replace('_', ' ')}${item.installedVersion?.let { " • installed $it" }.orEmpty()}${item.candidateVersion?.let { " → $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    installResult?.let { install ->
                        Text(
                            if (install.success && install.importErrors.isEmpty()) "Installed and import-verified ${install.packages.joinToString()}"
                            else if (install.success) "Installed, but import verification found a problem"
                            else "Installation failed",
                            color = if (install.success && install.importErrors.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        if (install.success && install.importNames.isNotEmpty()) Text(
                            install.importNames.entries.joinToString("\n") { (distribution, names) -> "$distribution → import ${names.joinToString().ifBlank { "name unavailable" }}" },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (install.importErrors.isNotEmpty()) Text(
                            "Import verification warning:\n" + install.importErrors.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (!install.success && install.stderr.isNotBlank()) Text(install.stderr.lines().takeLast(12).joinToString("\n").takeLast(1_200), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            OutlinedTextField(
                timeoutSeconds,
                { timeoutSeconds = it.filter(Char::isDigit).take(3) },
                label = { Text("Execution deadline (seconds)") },
                supportingText = { Text("1–600 seconds. Pure Python is interrupted at the deadline; a blocking native extension may return later.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                code, { code = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = rememberCodeVisualTransformation("python"),
                minLines = 12,
                label = { Text("Distro Python (root)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.startPythonRun(code, timeoutSeconds.toIntOrNull()?.coerceIn(1, 600) ?: 90) },
                enabled = code.isNotBlank() && !running,
            ) { Icon(Icons.Outlined.PlayArrow, null); Text(if (running) "Running…" else "Run", Modifier.padding(start = 8.dp)) }
            if (running) Text(
                "Running in the background • ${(clock - (pythonRun?.startedAt ?: clock)) / 1_000}s • you can browse other chats",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            result?.let { output ->
                PythonExecutionCard(output)
            }

            Text("Linux tool layer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Choose a rootless user-space distribution for broader third-party CLIs and libraries. Each distribution keeps its own packages, shares this chat's files at /workspace, and is a compatibility layer—not a security boundary.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        label = { Text(option.displayName) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text("${ubuntuStatus.distribution.description} • ${ubuntuStatus.distribution.release} • ${ubuntuStatus.distribution.packageManager.command}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${ubuntuStatus.distribution.displayName} • ${ubuntuStatus.stage.name.lowercase().replace('_', ' ')} • ${ubuntuStatus.architecture}", fontWeight = FontWeight.SemiBold)
                    Text(ubuntuStatus.detail, style = MaterialTheme.typography.bodySmall)
                    if (ubuntuStatus.sizeBytes > 0) Text("Installed size: ${Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, ubuntuStatus.sizeBytes)}", style = MaterialTheme.typography.labelSmall)
                    if (ubuntuStatus.stage in setOf(UbuntuStage.DOWNLOADING, UbuntuStage.VERIFYING, UbuntuStage.EXTRACTING, UbuntuStage.CONFIGURING)) {
                        ubuntuStatus.progress?.let { progress -> LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) }
                            ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!ubuntuStatus.installed) Button(
                            onClick = { scope.launch { viewModel.installUbuntu() } },
                            enabled = ubuntuStatus.stage !in setOf(UbuntuStage.DOWNLOADING, UbuntuStage.VERIFYING, UbuntuStage.EXTRACTING, UbuntuStage.CONFIGURING),
                        ) { Text(if (ubuntuStatus.stage == UbuntuStage.ERROR) "Retry setup" else "Install ${ubuntuStatus.distribution.displayName}") }
                        else OutlinedButton(onClick = { scope.launch { viewModel.refreshUbuntu() } }) { Icon(Icons.Outlined.Refresh, null); Text("Refresh", Modifier.padding(start = 6.dp)) }
                        if (ubuntuStatus.installed) OutlinedButton(onClick = { scope.launch { viewModel.removeUbuntu() } }) { Icon(Icons.Outlined.Delete, null); Text("Remove", Modifier.padding(start = 6.dp)) }
                    }
                }
            }
            if (ubuntuStatus.installed) {
                OutlinedTextField(
                    linuxTimeoutSeconds,
                    { linuxTimeoutSeconds = it.filter(Char::isDigit).take(4) },
                    label = { Text("Shell deadline (seconds)") },
                    supportingText = { Text("1–3600 seconds. Long commands keep running while you browse; Arbor warns after 10 seconds and lets you stop them.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    ubuntuCommand, { ubuntuCommand = it },
                    label = { Text("${ubuntuStatus.distribution.displayName} shell command") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    visualTransformation = rememberCodeVisualTransformation("bash"),
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.startLinuxRun(ubuntuCommand, linuxTimeoutSeconds.toIntOrNull()?.coerceIn(1, 3_600) ?: 180) },
                    enabled = ubuntuCommand.isNotBlank() && !ubuntuRunning,
                ) { Icon(Icons.Outlined.PlayArrow, null); Text(if (ubuntuRunning) "Running…" else "Run in ${ubuntuStatus.distribution.displayName}", Modifier.padding(start = 8.dp)) }
                if (ubuntuRunning) Text(
                    "Running in the background • ${(clock - (linuxRun?.startedAt ?: clock)) / 1_000}s • safe to leave this screen",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                ubuntuResult?.let { output ->
                    UbuntuExecutionCard(output)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Install ${ubuntuStatus.distribution.displayName} packages", fontWeight = FontWeight.SemiBold)
                        Text("${ubuntuStatus.distribution.packageManager.command} simulates the complete transaction first and checks what is already installed before approval.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(ubuntuPackages, { ubuntuPackages = it }, label = { Text("ripgrep\nffmpeg") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    ubuntuInstalling = true
                                    ubuntuPackageResult = null
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
                        ) { Text(if (ubuntuInstalling && ubuntuReview?.state == PackageApprovalState.APPROVED) "Installing…" else if (ubuntuInstalling) "Checking…" else "Preflight & review") }
                        Surface(
                            color = if (ubuntuPackageResult?.success == false) MaterialTheme.colorScheme.errorContainer.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    ubuntuInstalling && ubuntuReview?.state == PackageApprovalState.APPROVED -> "Approved automatically • repairing package state and installing…"
                                    ubuntuInstalling -> "Simulating ${ubuntuStatus.distribution.packageManager.command} transaction…"
                                    ubuntuPackageResult?.success == true -> "Installation completed successfully."
                                    ubuntuPackageResult?.success == false -> "Installation failed. No success was recorded."
                                    ubuntuReview?.state == PackageApprovalState.NOT_NEEDED -> "Everything requested is already installed."
                                    ubuntuReview?.state == PackageApprovalState.APPROVED -> "Approved automatically • starting installation…"
                                    ubuntuReview?.state == PackageApprovalState.REQUIRED -> "Plan ready for your confirmation."
                                    ubuntuReview?.state == PackageApprovalState.DENIED -> "Installation blocked • ${ubuntuReview?.reason.orEmpty()}"
                                    else -> "No package transaction running."
                                },
                                Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        ubuntuReview?.let { review ->
                            val changes = review.plan.items.count { it.action == PackageAction.INSTALL || it.action == PackageAction.UPDATE }
                            val dependencies = review.plan.items.count { it.detail == "Dependency" }
                            Text("${review.plan.items.size} packages resolved • $changes changes • $dependencies dependencies", style = MaterialTheme.typography.labelMedium)
                            if (review.plan.downloadSummary.isNotBlank()) Text("Download: ${review.plan.downloadSummary}", style = MaterialTheme.typography.labelSmall)
                            if (review.plan.diskSummary.isNotBlank()) Text("Disk: ${review.plan.diskSummary}", style = MaterialTheme.typography.labelSmall)
                            Text("${review.decidedBy}: ${review.reason}", style = MaterialTheme.typography.labelSmall)
                            OutlinedButton(onClick = { showUbuntuPlan = !showUbuntuPlan }) { Text(if (showUbuntuPlan) "Collapse complete plan" else "Show complete package plan") }
                            if (showUbuntuPlan) review.plan.items.forEach { item -> Text("${item.name} • ${item.action.name.lowercase().replace('_', ' ')}${item.candidateVersion?.let { " → $it" }.orEmpty()}${item.detail.takeIf(String::isNotBlank)?.let { " • $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall) }
                        }
                        ubuntuPackageResult?.let { installed ->
                            Text(if (installed.success) "Installed ${installed.packages.joinToString()}" else "${ubuntuStatus.distribution.packageManager.command} install failed", color = if (installed.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            val log = installed.stderr.ifBlank { installed.stdout }.takeLast(12_000)
                            if (log.isNotBlank()) GenericToolOutputCard(log, failed = !installed.success)
                        }
                    }
                }
            }
        }
    }
    if (confirmInstall) AlertDialog(
        onDismissRequest = { confirmInstall = false },
        title = { Text("Allow package installation?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Only missing or outdated packages will change. Install scripts run with Arbor's app permissions.")
                packageReview?.plan?.items?.filter { it.action != PackageAction.ALREADY_INSTALLED }?.forEach { item ->
                    Text("• ${item.name}: ${item.action.name.lowercase()}${item.candidateVersion?.let { " $it" }.orEmpty()}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { OutlinedButton(onClick = { confirmInstall = false }) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = {
                confirmInstall = false
                scope.launch { installPythonNow() }
            }, enabled = packageReview?.plan?.hasChanges == true) { Text("Allow and install") }
        },
    )
    if (confirmUbuntuInstall) AlertDialog(
        onDismissRequest = { confirmUbuntuInstall = false },
        title = { Text("Allow ${ubuntuStatus.distribution.packageManager.command} package changes?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("This is the complete ${ubuntuStatus.distribution.packageManager.command} simulation, including dependencies:")
                ubuntuReview?.plan?.items?.filter { it.action != PackageAction.ALREADY_INSTALLED }?.forEach { item ->
                    Text("• ${item.name}: ${item.action.name.lowercase()}${item.candidateVersion?.let { " $it" }.orEmpty()}${item.detail.takeIf(String::isNotBlank)?.let { " • $it" }.orEmpty()}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                ubuntuReview?.plan?.downloadSummary?.takeIf(String::isNotBlank)?.let { Text("Download: $it") }
                ubuntuReview?.plan?.diskSummary?.takeIf(String::isNotBlank)?.let { Text("Disk: $it") }
            }
        },
        dismissButton = { OutlinedButton(onClick = { confirmUbuntuInstall = false }) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = {
                confirmUbuntuInstall = false
                scope.launch { installUbuntuPackagesNow() }
            }, enabled = ubuntuReview?.plan?.hasChanges == true) { Text("Allow and install") }
        },
    )
    removePackage?.let { name ->
        AlertDialog(
            onDismissRequest = { removePackage = null },
            title = { Text("Remove $name?") },
            text = { Text("$appName will remove this distribution from the current chat environment. Shared dependencies are kept unless you remove them separately.") },
            dismissButton = { OutlinedButton(onClick = { removePackage = null }) { Text("Cancel") } },
            confirmButton = {
                Button(onClick = {
                    removePackage = null
                    scope.launch { environmentBusy = true; environment = viewModel.removePythonPackages(listOf(name)); environmentBusy = false }
                }) { Text("Remove") }
            },
        )
    }
    val longPython = pythonRun?.takeIf { it.running && clock - it.startedAt >= 10_000 && dismissedLongRun != it.startedAt }
    val longLinux = linuxRun?.takeIf { it.running && clock - it.startedAt >= 10_000 && dismissedLongRun != it.startedAt }
    if (longPython != null || longLinux != null) {
        val isPython = longPython != null
        val startedAt = longPython?.startedAt ?: longLinux!!.startedAt
        val seconds = ((clock - startedAt) / 1_000).coerceAtLeast(10)
        AlertDialog(
            onDismissRequest = { dismissedLongRun = startedAt },
            title = { Text("${if (isPython) "Python" else longLinux!!.distribution.displayName} is still running") },
            text = {
                Text("This has taken $seconds seconds. It will keep running while you browse Arbor, up to its hard deadline. You can leave it in the background or stop it now.${if (isPython) " A blocking native Python extension may take a moment to return after Stop." else ""}")
            },
            dismissButton = { OutlinedButton(onClick = { dismissedLongRun = startedAt }) { Text("Keep in background") } },
            confirmButton = {
                Button(onClick = {
                    if (isPython) viewModel.stopPythonRun() else viewModel.stopLinuxRun()
                    dismissedLongRun = startedAt
                }) { Text("Stop") }
            },
        )
    }
}
