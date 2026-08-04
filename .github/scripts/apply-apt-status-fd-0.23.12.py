from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, found {count}: {old[:180]!r}")
    path.write_text(text.replace(old, new, 1))


runtime = Path("app/src/main/java/app/xylune/chat/sandbox/UbuntuRuntime.kt")
text = runtime.read_text()

# Remove the obsolete pipe-draining implementation left behind after 0.23.11.
for import_line in (
    "import java.io.IOException\n",
    "import java.io.InterruptedIOException\n",
    "import java.util.concurrent.atomic.AtomicBoolean\n",
    "import java.util.concurrent.atomic.AtomicReference\n",
):
    text = text.replace(import_line, "")

helper_anchor = '''internal fun readLogTail(file: File, maxChars: Int): String {
    require(maxChars >= 0) { "Tail size must not be negative" }
    if (!file.isFile || maxChars == 0) return ""
    val maxBytes = maxChars.toLong().times(4L).coerceAtMost(1_000_000L)
    val length = file.length()
    val start = (length - maxBytes).coerceAtLeast(0L)
    val byteCount = (length - start).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val buffer = ByteArray(byteCount)
    var offset = 0
    file.inputStream().use { input ->
        input.channel.position(start)
        while (offset < byteCount) {
            val count = input.read(buffer, offset, byteCount - offset)
            if (count < 0) break
            offset += count
        }
    }
    return String(buffer, 0, offset, Charsets.UTF_8).takeLast(maxChars)
}

data class UbuntuPackageInstallResult(
'''
helper_replacement = '''internal fun readLogTail(file: File, maxChars: Int): String {
    require(maxChars >= 0) { "Tail size must not be negative" }
    if (!file.isFile || maxChars == 0) return ""
    val maxBytes = maxChars.toLong().times(4L).coerceAtMost(1_000_000L)
    val length = file.length()
    val start = (length - maxBytes).coerceAtLeast(0L)
    val byteCount = (length - start).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val buffer = ByteArray(byteCount)
    var offset = 0
    file.inputStream().use { input ->
        input.channel.position(start)
        while (offset < byteCount) {
            val count = input.read(buffer, offset, byteCount - offset)
            if (count < 0) break
            offset += count
        }
    }
    return String(buffer, 0, offset, Charsets.UTF_8).takeLast(maxChars)
}

internal fun buildAptCommandWithStatusFile(arguments: String, guestStatusPath: String): String {
    require(arguments.isNotBlank()) { "APT arguments are empty" }
    require(guestStatusPath.matches(Regex("/tmp/[A-Za-z0-9._-]+"))) { "Unsafe APT status path" }
    val statusPath = shellQuote(guestStatusPath)
    return "rm -f $statusPath; : > $statusPath; exec 3>>$statusPath; " +
        "DEBIAN_FRONTEND=noninteractive apt-get " +
        "-o APT::Status-Fd=3 -o Dpkg::Progress-Fancy=0 -o Dpkg::Use-Pty=0 $arguments"
}

data class UbuntuPackageInstallResult(
'''
if text.count(helper_anchor) != 1:
    raise SystemExit("Could not locate log helper anchor")
text = text.replace(helper_anchor, helper_replacement, 1)

setup_old = '''            publish(UbuntuStage.CONFIGURING, 0.74f, 7, "Installing Python and certificate tools")
            val aptProgressOptions = "-o APT::Status-Fd=1 -o Dpkg::Progress-Fancy=0 -o Dpkg::Use-Pty=0"
            val pythonPackages = if (distro.packageManager == LinuxPackageManager.APT) {
                "DEBIAN_FRONTEND=noninteractive apt-get $aptProgressOptions install -y --no-install-recommends python3 python3-pip python3-venv ca-certificates"
            } else {
                "apk add --progress python3 py3-pip py3-virtualenv ca-certificates"
            }
            val pythonSetup = executeInternal(pythonPackages, sharedWorkspace(), 900, allowBeforeMarker = true) { progress ->
                val parsed = if (distro.packageManager == LinuxPackageManager.APT) {
                    packageInstallProgressFromApt(progress, "Installing Python tools", 0.74f, 0.98f)
                } else {
                    packageInstallProgressFromOutput(progress, "Installing Python tools", 0.74f, 0.98f)
                }
                val detail = parsed.phase + parsed.currentPackage?.let { " • $it" }.orEmpty() +
                    parsed.detail.takeIf(String::isNotBlank)?.let { " — $it" }.orEmpty()
                publish(UbuntuStage.CONFIGURING, parsed.percent ?: 0.74f, 7, detail)
            }
'''
setup_new = '''            publish(UbuntuStage.CONFIGURING, 0.74f, 7, "Installing Python and certificate tools")
            val pythonSetup = if (distro.packageManager == LinuxPackageManager.APT) {
                executeAptInternal(
                    arguments = "install -y --no-install-recommends python3 python3-pip python3-venv ca-certificates",
                    workspace = sharedWorkspace(),
                    timeoutSeconds = 900,
                    allowBeforeMarker = true,
                ) { progress ->
                    val parsed = packageInstallProgressFromApt(progress, "Installing Python tools", 0.74f, 0.98f)
                    val detail = parsed.phase + parsed.currentPackage?.let { " • $it" }.orEmpty() +
                        parsed.detail.takeIf(String::isNotBlank)?.let { " — $it" }.orEmpty()
                    publish(UbuntuStage.CONFIGURING, parsed.percent ?: 0.74f, 7, detail)
                }
            } else {
                executeInternal(
                    command = "apk add --progress python3 py3-pip py3-virtualenv ca-certificates",
                    workspace = sharedWorkspace(),
                    timeoutSeconds = 900,
                    allowBeforeMarker = true,
                ) { progress ->
                    val parsed = packageInstallProgressFromOutput(progress, "Installing Python tools", 0.74f, 0.98f)
                    val detail = parsed.phase + parsed.currentPackage?.let { " • $it" }.orEmpty() +
                        parsed.detail.takeIf(String::isNotBlank)?.let { " — $it" }.orEmpty()
                    publish(UbuntuStage.CONFIGURING, parsed.percent ?: 0.74f, 7, detail)
                }
            }
'''
if text.count(setup_old) != 1:
    raise SystemExit("Could not locate initial Python package installation block")
text = text.replace(setup_old, setup_new, 1)

package_old = '''        val aptProgressOptions = "-o APT::Status-Fd=1 -o Dpkg::Progress-Fancy=0 -o Dpkg::Use-Pty=0"
        val repair = execute(
            conversationId,
            "DEBIAN_FRONTEND=noninteractive apt-get $aptProgressOptions -f install -y --no-install-recommends",
            900,
        ) { progress ->
            emit(packageInstallProgressFromApt(progress, "Repairing dependencies", 0.10f, 0.30f))
        }
'''
package_new = '''        val repair = executeApt(
            conversationId = conversationId,
            arguments = "-f install -y --no-install-recommends",
            timeoutSeconds = 900,
        ) { progress ->
            emit(packageInstallProgressFromApt(progress, "Repairing dependencies", 0.10f, 0.30f))
        }
'''
if text.count(package_old) != 1:
    raise SystemExit("Could not locate dependency repair apt block")
text = text.replace(package_old, package_new, 1)

install_old = '''        val install = execute(
            conversationId,
            "DEBIAN_FRONTEND=noninteractive apt-get $aptProgressOptions install -y --no-install-recommends ${requests.joinToString(" ") { shellQuote(it) }}",
            900,
        ) { progress ->
            emit(packageInstallProgressFromApt(progress, "Installing packages", 0.30f, 0.99f))
        }
'''
install_new = '''        val install = executeApt(
            conversationId = conversationId,
            arguments = "install -y --no-install-recommends ${requests.joinToString(" ") { shellQuote(it) }}",
            timeoutSeconds = 900,
        ) { progress ->
            emit(packageInstallProgressFromApt(progress, "Installing packages", 0.30f, 0.99f))
        }
'''
if text.count(install_old) != 1:
    raise SystemExit("Could not locate package install apt block")
text = text.replace(install_old, install_new, 1)

execute_anchor = '''    private suspend fun executeInternal(
        command: String,
        workspace: File,
        timeoutSeconds: Int,
        allowBeforeMarker: Boolean = false,
        onProgress: suspend (ExecutionProgress) -> Unit = {},
    ): UbuntuExecutionResult = withContext(Dispatchers.IO) {
'''
execute_replacement = '''    private suspend fun executeApt(
        conversationId: String,
        arguments: String,
        timeoutSeconds: Int,
        onProgress: suspend (ExecutionProgress) -> Unit = {},
    ): UbuntuExecutionResult = processMutex.withLock {
        val distro = distribution.value
        check(status.value.installed || rootfsMarker().isFile) { "Install ${distro.displayName} from Tool workspaces first." }
        executeAptInternal(
            arguments = arguments,
            workspace = python.workspace(conversationId),
            timeoutSeconds = timeoutSeconds.coerceIn(1, 3_600),
            onProgress = onProgress,
        )
    }

    private suspend fun executeAptInternal(
        arguments: String,
        workspace: File,
        timeoutSeconds: Int,
        allowBeforeMarker: Boolean = false,
        onProgress: suspend (ExecutionProgress) -> Unit = {},
    ): UbuntuExecutionResult {
        val statusName = ".xylune-apt-status-${System.nanoTime()}"
        val statusFile = File(rootfs(), "tmp/$statusName").also {
            it.parentFile?.mkdirs()
            it.delete()
        }
        return try {
            executeInternal(
                command = buildAptCommandWithStatusFile(arguments, "/tmp/$statusName"),
                workspace = workspace,
                timeoutSeconds = timeoutSeconds,
                allowBeforeMarker = allowBeforeMarker,
                additionalProgressFiles = listOf(statusFile),
                onProgress = onProgress,
            )
        } finally {
            statusFile.delete()
        }
    }

    private suspend fun executeInternal(
        command: String,
        workspace: File,
        timeoutSeconds: Int,
        allowBeforeMarker: Boolean = false,
        additionalProgressFiles: List<File> = emptyList(),
        onProgress: suspend (ExecutionProgress) -> Unit = {},
    ): UbuntuExecutionResult = withContext(Dispatchers.IO) {
'''
if text.count(execute_anchor) != 1:
    raise SystemExit("Could not locate executeInternal declaration")
text = text.replace(execute_anchor, execute_replacement, 1)

progress_old = '''        suspend fun emitProgress(force: Boolean = false) {
            val now = System.currentTimeMillis()
            val stdoutSnapshot = readLogTail(stdoutLog, LIVE_OUTPUT_TAIL_CHARS)
            val stderrSnapshot = readLogTail(stderrLog, LIVE_OUTPUT_TAIL_CHARS)
            val signature = "${stdoutLog.length()}:${stderrLog.length()}:${stdoutSnapshot.takeLast(64)}:${stderrSnapshot.takeLast(64)}"
            if (force || signature != lastProgressSignature || now - lastProgressAt >= 1_000L) {
                onProgress(ExecutionProgress(stdoutSnapshot, stderrSnapshot, now - started))
                lastProgressSignature = signature
                lastProgressAt = now
            }
        }
'''
progress_new = '''        suspend fun emitProgress(force: Boolean = false) {
            val now = System.currentTimeMillis()
            val primaryStdout = readLogTail(stdoutLog, LIVE_OUTPUT_TAIL_CHARS)
            val extraProgress = additionalProgressFiles
                .joinToString("\n") { readLogTail(it, LIVE_OUTPUT_TAIL_CHARS) }
            val stdoutSnapshot = listOf(primaryStdout, extraProgress)
                .filter(String::isNotBlank)
                .joinToString("\n")
                .takeLast(LIVE_OUTPUT_TAIL_CHARS)
            val stderrSnapshot = readLogTail(stderrLog, LIVE_OUTPUT_TAIL_CHARS)
            val extraLengths = additionalProgressFiles.joinToString(",") { it.length().toString() }
            val signature = "${stdoutLog.length()}:${stderrLog.length()}:$extraLengths:${stdoutSnapshot.takeLast(64)}:${stderrSnapshot.takeLast(64)}"
            if (force || signature != lastProgressSignature || now - lastProgressAt >= 1_000L) {
                onProgress(ExecutionProgress(stdoutSnapshot, stderrSnapshot, now - started))
                lastProgressSignature = signature
                lastProgressAt = now
            }
        }
'''
if text.count(progress_old) != 1:
    raise SystemExit("Could not locate live progress collector")
text = text.replace(progress_old, progress_new, 1)

result_old = '''            val after = fileState(workspace)
            UbuntuExecutionResult(
                stdout = readCappedLogFile(stdoutLog, LOG_CAPTURE_LIMIT_BYTES),
                stderr = readCappedLogFile(stderrLog, LOG_CAPTURE_LIMIT_BYTES),
                exitCode = if (complete) process.exitValue() else -1,
'''
result_new = '''            val after = fileState(workspace)
            val stdout = listOf(
                readCappedLogFile(stdoutLog, LOG_CAPTURE_LIMIT_BYTES),
                additionalProgressFiles.joinToString("\n") { readCappedLogFile(it, LOG_CAPTURE_LIMIT_BYTES) },
            ).filter(String::isNotBlank).joinToString("\n").take(LOG_CAPTURE_LIMIT_BYTES)
            UbuntuExecutionResult(
                stdout = stdout,
                stderr = readCappedLogFile(stderrLog, LOG_CAPTURE_LIMIT_BYTES),
                exitCode = if (complete) process.exitValue() else -1,
'''
if text.count(result_old) != 1:
    raise SystemExit("Could not locate execution result block")
text = text.replace(result_old, result_new, 1)

# Delete dead pipe-reader code. It was not used after 0.23.11 and obscured the real issue.
dead_start = text.index("    private fun startStreamPump(")
dead_end = text.index("\n    companion object {", dead_start)
text = text[:dead_start] + text[dead_end:]

runtime.write_text(text)

# Unit regression: APT progress must use a dedicated regular-file fd, never stdout/stderr.
test = Path("app/src/test/java/app/xylune/chat/sandbox/PackageInstallProgressTest.kt")
replace_once(
    test,
    '''    @Test
    fun fallsBackToHumanReadableAptOutput() {
''',
    '''    @Test
    fun aptProgressUsesDedicatedFileDescriptorInsteadOfMaintainerScriptOutput() {
        val command = buildAptCommandWithStatusFile(
            arguments = "install -y python3 ca-certificates",
            guestStatusPath = "/tmp/.xylune-apt-status-test",
        )

        assertTrue(command.contains("exec 3>>'/tmp/.xylune-apt-status-test'"))
        assertTrue(command.contains("APT::Status-Fd=3"))
        assertTrue(command.contains("Dpkg::Use-Pty=0"))
        assertFalse(command.contains("APT::Status-Fd=1"))
    }

    @Test
    fun fallsBackToHumanReadableAptOutput() {
''',
)

onboarding = Path("app/src/test/java/app/xylune/chat/ui/OnboardingFlowTest.kt")
replace_once(
    onboarding,
    '''        assertTrue(runtime.contains("readLogTail(stdoutLog"))
        assertFalse(runtime.contains("root.walkTopDown().filter(File::isFile).sumOf(File::length)"))
''',
    '''        assertTrue(runtime.contains("readLogTail(stdoutLog"))
        assertTrue(runtime.contains("APT::Status-Fd=3"))
        assertTrue(runtime.contains("additionalProgressFiles = listOf(statusFile)"))
        assertFalse(runtime.contains("APT::Status-Fd=1"))
        assertFalse(runtime.contains("root.walkTopDown().filter(File::isFile).sumOf(File::length)"))
''',
)

build = Path("app/build.gradle.kts")
replace_once(
    build,
    '''        versionCode = 180
        versionName = "0.23.11"
''',
    '''        versionCode = 181
        versionName = "0.23.12"
''',
)

changelog = Path("CHANGELOG.md")
changelog.write_text(
    '''## 0.23.12 — 2026-08-04

- Fix the remaining Ubuntu `ca-certificates` setup failure by moving APT machine-readable progress off file descriptor 1. APT now writes status records to a dedicated app-private regular file on fd 3, while package maintainer scripts keep normal stdout/stderr.
- Tail the dedicated APT status file into the existing live progress UI without exposing package scripts to an internal progress pipe.
- Apply the same safe APT execution path to later package installs and dependency repairs, not only first-run Python setup.
- Remove the obsolete Java pipe-reader implementation and add regression checks forbidding `APT::Status-Fd=1`.

''' + changelog.read_text()
)

Path("docs/releases/RELEASE_NOTES_0.23.12.md").write_text(
    '''# Xylune 0.23.12

## Ubuntu setup: actual `ca-certificates` fix

0.23.11 redirected the outer PRoot process output to files, but APT was still configured with `APT::Status-Fd=1`. That reused standard output as APT's internal progress channel. Under Android/PRoot, package maintainer scripts such as `update-ca-certificates` could then lose their output stream and fail with `echo: I/O error`.

0.23.12 gives APT a separate file descriptor (fd 3) backed by an app-private regular file. Xylune tails that file for live progress, while maintainer scripts retain ordinary stdout and stderr. The same path is used for first setup, dependency repair, and user-requested package installation.
'''
)
