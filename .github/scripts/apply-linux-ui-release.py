from pathlib import Path


def once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, found {count}: {old!r}")
    path.write_text(text.replace(old, new, 1))


screen = Path("app/src/main/java/app/xylune/chat/ui/SandboxScreen.kt")
once(
    screen,
    """    val ubuntuStatus by viewModel.ubuntuStatus.collectAsState()
    var workspaceSection by remember {
""",
    """    val ubuntuStatus by viewModel.ubuntuStatus.collectAsState()
    val linuxSetupActive = ubuntuStatus.stage in setOf(
        UbuntuStage.DOWNLOADING,
        UbuntuStage.VERIFYING,
        UbuntuStage.EXTRACTING,
        UbuntuStage.CONFIGURING,
    )
    var workspaceSection by remember {
""",
)
once(
    screen,
    """    LaunchedEffect(running) {
        while (running) {
            clock = System.currentTimeMillis()
            delay(1_000)
        }
    }
""",
    """    LaunchedEffect(running, linuxSetupActive) {
        while (running || linuxSetupActive) {
            clock = System.currentTimeMillis()
            delay(1_000)
        }
    }
""",
)
once(
    screen,
    """                    Text(ubuntuStatus.detail, style = MaterialTheme.typography.bodySmall)
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
""",
    """                    Text(ubuntuStatus.detail, style = MaterialTheme.typography.bodySmall)
                    if (ubuntuStatus.sizeBytes > 0) {
                        Text(
                            "Linux data on disk: ${Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, ubuntuStatus.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (linuxSetupActive) {
                        ubuntuStatus.progress?.let { progress ->
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val step = ubuntuStatus.currentStep.coerceAtLeast(1)
                            val total = ubuntuStatus.totalSteps.coerceAtLeast(step)
                            Text("Step $step of $total", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            ubuntuStatus.progress?.let {
                                Text("${(it.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (ubuntuStatus.startedAtMs > 0L) {
                            Text(
                                "Elapsed: ${(clock - ubuntuStatus.startedAtMs).coerceAtLeast(0L) / 1_000}s • keep Xylune open until setup finishes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!ubuntuStatus.installed) Button(
                            onClick = { scope.launch { viewModel.installUbuntu() } },
                            enabled = !linuxSetupActive,
                        ) { Text(if (ubuntuStatus.stage == UbuntuStage.ERROR) "Retry setup" else "Install ${ubuntuStatus.distribution.displayName}") }
""",
)

progress_test = Path("app/src/test/java/app/xylune/chat/sandbox/PackageInstallProgressTest.kt")
once(
    progress_test,
    """    @Test
    fun fallsBackToHumanReadableAptOutput() {
""",
    """    @Test
    fun parsesApkPackageCounterIntoProgress() {
        val progress = packageInstallProgressFromOutput(
            ExecutionProgress(stdoutTail = "(6/12) Installing python3 (3.12.11-r0)"),
            fallbackPhase = "Installing Python tools",
            rangeStart = 0.74f,
            rangeEnd = 0.98f,
        )

        assertEquals(0.86f, progress.percent ?: -1f, 0.001f)
        assertTrue(progress.detail.contains("Installing python3"))
    }

    @Test
    fun fallsBackToHumanReadableAptOutput() {
""",
)

flow_test = Path("app/src/test/java/app/xylune/chat/ui/OnboardingFlowTest.kt")
once(
    flow_test,
    """        assertTrue(workspace.contains("Install \\${ubuntuStatus.distribution.displayName}"))
        assertTrue(workspace.contains("Remove Linux workspace"))
""",
    """        assertTrue(workspace.contains("Install \\${ubuntuStatus.distribution.displayName}"))
        assertTrue(workspace.contains("Step $step of $total"))
        assertTrue(workspace.contains("Elapsed:"))
        assertTrue(workspace.contains("Linux data on disk:"))
        assertTrue(workspace.contains("Remove Linux workspace"))
""",
)
once(
    flow_test,
    """        assertFalse(terminal.contains("removeUbuntu"))
    }
""",
    """        assertFalse(terminal.contains("removeUbuntu"))
        val runtime = java.io.File("src/main/java/app/xylune/chat/sandbox/UbuntuRuntime.kt").readText()
        assertTrue(runtime.contains("val currentStep: Int = 0"))
        assertTrue(runtime.contains("val totalSteps: Int = 0"))
        assertTrue(runtime.contains("Os.lstat(file.absolutePath)"))
        assertTrue(runtime.contains("countedInodes.add"))
        assertFalse(runtime.contains("root.walkTopDown().filter(File::isFile).sumOf(File::length)"))
    }
""",
)

gradle = Path("app/build.gradle.kts")
once(
    gradle,
    """        versionCode = 177
        versionName = "0.23.8"
""",
    """        versionCode = 178
        versionName = "0.23.9"
""",
)

changelog = Path("CHANGELOG.md")
changelog.write_text(
    """## 0.23.9 — 2026-08-04

- Show continuous, monotonic Linux setup progress across download, verification, extraction, configuration, package-index refresh, Python installation, and finalization.
- Add live step count, percentage where measurable, detailed package activity, and elapsed time instead of leaving setup apparently frozen.
- Correct Linux storage reporting by counting allocated blocks for unique filesystem inodes, avoiding severe overcounting of hard-linked files.
- Rename the UI metric to **Linux data on disk** so it is not confused with the APK size or total Android app data.

""" + changelog.read_text()
)

Path("docs/releases/RELEASE_NOTES_0.23.9.md").write_text(
    """# Xylune 0.23.9

## Linux setup progress

- Uses one monotonic progress range for the complete installation instead of restarting or stalling the bar at each internal phase.
- Shows the current step out of eight, elapsed time, current download amount, package-manager activity, and package names while Python tools are installed.
- Keeps indeterminate progress only for work whose exact completion fraction is unavailable, while still updating the visible phase and activity text.

## Correct storage reporting

- Replaces the old logical file-length sum, which counted every path to a hard-linked file and could report several times the real usage.
- Counts allocated filesystem blocks once per unique device/inode pair and does not follow symbolic links while traversing the Linux runtime.
- Labels the value **Linux data on disk**. It is the Linux environment's storage use, not the APK size and not Android's total app-data figure.

## Validation

- Adds regression coverage for apk package-counter progress and structural checks for step reporting and inode-aware storage accounting.
- Runs release unit tests, lint, and release APK assembly before the patch branch is committed.
"""
)

Path(".github/scripts/apply-linux-runtime-progress-storage.py").unlink()
Path(".github/scripts/apply-linux-ui-release.py").unlink()
Path(".github/workflows/apply-linux-progress-storage.yml").unlink()
