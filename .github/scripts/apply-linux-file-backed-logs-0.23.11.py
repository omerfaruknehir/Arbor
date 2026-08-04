from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, found {count}: {old[:160]!r}")
    path.write_text(text.replace(old, new, 1))


runtime = Path("app/src/main/java/app/xylune/chat/sandbox/UbuntuRuntime.kt")
text = runtime.read_text()

helper_anchor = '''internal fun drainCappedText(
    reader: java.io.Reader,
    output: StringBuilder,
    limit: Int,
) {
    require(limit >= 0) { "Output limit must not be negative" }
    val buffer = CharArray(8_192)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        synchronized(output) {
            val appendCount = minOf(count, (limit - output.length).coerceAtLeast(0))
            if (appendCount > 0) output.append(buffer, 0, appendCount)
        }
    }
}

data class UbuntuPackageInstallResult(
'''
helper_replacement = '''internal fun drainCappedText(
    reader: java.io.Reader,
    output: StringBuilder,
    limit: Int,
) {
    require(limit >= 0) { "Output limit must not be negative" }
    val buffer = CharArray(8_192)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        synchronized(output) {
            val appendCount = minOf(count, (limit - output.length).coerceAtLeast(0))
            if (appendCount > 0) output.append(buffer, 0, appendCount)
        }
    }
}

internal fun readCappedLogFile(file: File, limitBytes: Int): String {
    require(limitBytes >= 0) { "Log limit must not be negative" }
    if (!file.isFile || limitBytes == 0) return ""
    val byteCount = minOf(file.length(), limitBytes.toLong()).toInt()
    val buffer = ByteArray(byteCount)
    var offset = 0
    file.inputStream().buffered().use { input ->
        while (offset < byteCount) {
            val count = input.read(buffer, offset, byteCount - offset)
            if (count < 0) break
            offset += count
        }
    }
    return String(buffer, 0, offset, Charsets.UTF_8)
}

internal fun readLogTail(file: File, maxChars: Int): String {
    require(maxChars >= 0) { "Tail size must not be negative" }
    if (!file.isFile || maxChars == 0) return ""
    val maxBytes = maxChars.toLong().times(4L).coerceAtMost(LOG_CAPTURE_LIMIT_BYTES.toLong())
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
if text.count(helper_anchor) != 1:
    raise SystemExit("Could not locate log helper anchor")
text = text.replace(helper_anchor, helper_replacement, 1)

start_marker = '''        val started = System.currentTimeMillis()
        val process = builder.start()
'''
end_marker = '''
    private fun configure(root: File, distro: LinuxDistribution) {
'''
start = text.index(start_marker)
end = text.index(end_marker, start)
new_execution = '''        val logDirectory = File(context.cacheDir, "linux-process-logs").also { it.mkdirs() }
        val logToken = "${distro.id}-${System.nanoTime()}"
        val stdoutLog = File(logDirectory, "$logToken.stdout")
        val stderrLog = File(logDirectory, "$logToken.stderr")
        builder.redirectOutput(stdoutLog)
        builder.redirectError(stderrLog)

        val started = System.currentTimeMillis()
        val process = builder.start()
        var complete = false
        var timedOut = false
        var lastProgressSignature = ""
        var lastProgressAt = 0L

        suspend fun emitProgress(force: Boolean = false) {
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

        try {
            try {
                emitProgress(force = true)
                val deadline = started + timeoutSeconds * 1_000L
                while (!process.waitFor(PROGRESS_POLL_MS, TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                    emitProgress()
                    if (System.currentTimeMillis() >= deadline) {
                        timedOut = true
                        process.destroyForcibly()
                        break
                    }
                }
                if (!timedOut) complete = true
            } catch (cancelled: CancellationException) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw cancelled
            } finally {
                if (timedOut) process.waitFor(2, TimeUnit.SECONDS)
                emitProgress(force = true)
            }
            val after = fileState(workspace)
            UbuntuExecutionResult(
                stdout = readCappedLogFile(stdoutLog, LOG_CAPTURE_LIMIT_BYTES),
                stderr = readCappedLogFile(stderrLog, LOG_CAPTURE_LIMIT_BYTES),
                exitCode = if (complete) process.exitValue() else -1,
                files = after.filter { (path, state) -> before[path] != state }.keys.take(500),
                elapsedMs = System.currentTimeMillis() - started,
                timedOut = timedOut,
            )
        } finally {
            stdoutLog.delete()
            stderrLog.delete()
        }
    }
'''
text = text[:start] + new_execution + text[end:]

text = text.replace(
    '''        private const val LIVE_OUTPUT_TAIL_CHARS = 16_000
        private const val INSTALL_STEP_COUNT = 8
''',
    '''        private const val LIVE_OUTPUT_TAIL_CHARS = 16_000
        private const val LOG_CAPTURE_LIMIT_BYTES = 1_000_000
        private const val INSTALL_STEP_COUNT = 8
''',
    1,
)
runtime.write_text(text)

# Add regression coverage for file-backed capture and keep the old drain test as a guard.
test = Path("app/src/test/java/app/xylune/chat/sandbox/PackageInstallProgressTest.kt")
replace_once(
    test,
    '''    @Test
    fun fallsBackToHumanReadableAptOutput() {
''',
    '''    @Test
    fun fileBackedCaptureKeepsAReadableTailWithoutPipes() {
        val log = kotlin.io.path.createTempFile("xylune-log", ".txt").toFile()
        try {
            val body = "prefix-" + "x".repeat(32_000) + "-final-status"
            log.writeText(body)

            assertEquals(body.take(512), readCappedLogFile(log, 512))
            assertEquals(body.takeLast(256), readLogTail(log, 256))
        } finally {
            log.delete()
        }
    }

    @Test
    fun fallsBackToHumanReadableAptOutput() {
''',
)

# Source-level contract: package processes must no longer expose Android pipes to dpkg scripts.
onboarding = Path("app/src/test/java/app/xylune/chat/ui/OnboardingFlowTest.kt")
replace_once(
    onboarding,
    '''        assertTrue(runtime.contains("countedInodes.add"))
        assertFalse(runtime.contains("root.walkTopDown().filter(File::isFile).sumOf(File::length)"))
''',
    '''        assertTrue(runtime.contains("countedInodes.add"))
        assertTrue(runtime.contains("builder.redirectOutput(stdoutLog)"))
        assertTrue(runtime.contains("builder.redirectError(stderrLog)"))
        assertTrue(runtime.contains("readLogTail(stdoutLog"))
        assertFalse(runtime.contains("root.walkTopDown().filter(File::isFile).sumOf(File::length)"))
''',
)

build = Path("app/build.gradle.kts")
replace_once(
    build,
    '''        versionCode = 179
        versionName = "0.23.10"
''',
    '''        versionCode = 180
        versionName = "0.23.11"
''',
)

changelog = Path("CHANGELOG.md")
changelog.write_text(
    '''## 0.23.11 — 2026-08-04

- Capture Linux command output in app-private temporary files instead of Java pipes, so `dpkg` maintainer scripts cannot lose stdout/stderr and fail with `I/O error` during certificate setup.
- Keep live installer progress by tailing those files while the process runs, while retaining a strict one-megabyte result cap and deleting logs afterward.
- Add regression tests for capped file capture, live tails, and the no-pipe process contract.

''' + changelog.read_text()
)

Path("docs/releases/RELEASE_NOTES_0.23.11.md").write_text(
    '''# Xylune 0.23.11

## Ubuntu certificate installation repair

The previous repair still depended on Android process pipes. On affected devices, `update-ca-certificates` could still inherit a stream that became unusable and abort with `echo: I/O error`.

Xylune now gives Linux processes app-private regular files for stdout and stderr. The UI tails those files for live progress, retains at most one megabyte in the final result, and removes the temporary logs after each command. This removes the broken-pipe failure mode without sacrificing progress reporting.
'''
)
