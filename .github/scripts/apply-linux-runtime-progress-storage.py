from pathlib import Path
import re


def once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, found {count}: {old!r}")
    path.write_text(text.replace(old, new, 1))


def regex_once(path: Path, pattern: str, replacement: str) -> None:
    text = path.read_text()
    updated, count = re.subn(pattern, lambda _: replacement, text, count=1, flags=re.DOTALL)
    if count != 1:
        raise SystemExit(f"Expected one regex match in {path}, found {count}: {pattern!r}")
    path.write_text(updated)


path = Path("app/src/main/java/app/xylune/chat/sandbox/UbuntuRuntime.kt")
once(path, "import android.os.StatFs\n", "import android.os.StatFs\nimport android.system.Os\nimport android.system.OsConstants\n")
once(path, "import java.security.MessageDigest\n", "import java.security.MessageDigest\nimport java.util.ArrayDeque\n")
once(
    path,
    """    val sizeBytes: Long = 0,
    val detail: String = "",
) {
""",
    """    val sizeBytes: Long = 0,
    val detail: String = "",
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val startedAtMs: Long = 0L,
) {
""",
)
once(
    path,
    """    val rawPercent = Regex("""(?<!\\d)(100|[0-9]{1,2})%""").findAll(combined).lastOrNull()
        ?.groupValues?.getOrNull(1)?.toFloatOrNull()?.div(100f)
    val latest = combined.lineSequence().map(String::trim).lastOrNull(String::isNotBlank).orEmpty()
    return PackageInstallProgress(
        phase = inferPackagePhase(latest, fallbackPhase),
        percent = rawPercent?.let { rangeStart + it.coerceIn(0f, 1f) * (rangeEnd - rangeStart) },
""",
    """    val rawPercent = Regex("""(?<!\\d)(100|[0-9]{1,2})%""").findAll(combined).lastOrNull()
        ?.groupValues?.getOrNull(1)?.toFloatOrNull()?.div(100f)
    val counterPercent = Regex("""\\((\\d+)/(\\d+)\\)""").findAll(combined).lastOrNull()?.let { match ->
        val current = match.groupValues.getOrNull(1)?.toFloatOrNull() ?: return@let null
        val total = match.groupValues.getOrNull(2)?.toFloatOrNull()?.takeIf { it > 0f } ?: return@let null
        (current / total).coerceIn(0f, 1f)
    }
    val latest = combined.lineSequence().map(String::trim).lastOrNull(String::isNotBlank).orEmpty()
    return PackageInstallProgress(
        phase = inferPackagePhase(latest, fallbackPhase),
        percent = (rawPercent ?: counterPercent)?.let { rangeStart + it.coerceIn(0f, 1f) * (rangeEnd - rangeStart) },
""",
)

install = r'''    suspend fun install(): UbuntuRuntimeStatus = withContext(Dispatchers.IO) { lifecycleMutex.withLock {
        val distro = distribution.value
        val spec = currentSpec() ?: return@withLock UbuntuRuntimeStatus(
            UbuntuStage.UNSUPPORTED,
            distribution = distro,
            architecture = Build.SUPPORTED_ABIS.joinToString(),
            detail = "${distro.displayName} is available for arm64-v8a and x86_64 devices.",
        ).also { _status.value = it }
        if (inspect().installed) return@withLock refresh()
        rootfsMarker().delete()
        rootfs().deleteRecursively()
        val available = StatFs(context.filesDir.absolutePath).availableBytes
        check(available >= MIN_FREE_BYTES) { "Linux setup needs at least 300 MiB of free app storage" }
        val archive = File(context.cacheDir, "${spec.fileName}.part")
        val staging = File(runtimeDir(), "rootfs-installing")
        val startedAt = System.currentTimeMillis()
        archive.delete()
        staging.deleteRecursively()
        staging.mkdirs()

        fun publish(stage: UbuntuStage, progress: Float?, step: Int, detail: String) {
            _status.value = UbuntuRuntimeStatus(
                stage = stage,
                distribution = distro,
                architecture = spec.arch,
                progress = progress?.coerceIn(0f, 1f),
                detail = detail.take(500),
                currentStep = step,
                totalSteps = INSTALL_STEP_COUNT,
                startedAtMs = startedAt,
            )
        }

        fun latestLine(progress: ExecutionProgress, fallback: String): String =
            sequenceOf(progress.stdoutTail, progress.stderrTail)
                .flatMap { it.lineSequence() }
                .map(String::trim)
                .filter { it.isNotBlank() && !AptStatusLine.matches(it) }
                .lastOrNull()
                ?.take(320)
                ?: fallback

        try {
            publish(UbuntuStage.DOWNLOADING, 0f, 1, "Starting ${distro.displayName} ${distro.release} download")
            val request = Request.Builder().url(spec.url).header("User-Agent", "Xylune/$APP_RUNTIME_VERSION Android").build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "${distro.displayName} download failed with HTTP ${response.code}" }
                val body = requireNotNull(response.body) { "The Linux download returned no data" }
                val total = body.contentLength()
                var copied = 0L
                archive.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            val fraction = if (total > 0L) copied.toFloat() / total else null
                            val detail = if (total > 0L) {
                                "Downloaded ${copied / 1_048_576} of ${total / 1_048_576} MiB"
                            } else {
                                "Downloaded ${copied / 1_048_576} MiB"
                            }
                            publish(UbuntuStage.DOWNLOADING, fraction?.times(0.30f), 1, detail)
                        }
                    }
                }
            }

            publish(UbuntuStage.VERIFYING, 0.30f, 2, "Verifying the publisher's pinned SHA-256")
            check(sha256(archive).equals(spec.sha256, ignoreCase = true)) { "${distro.displayName} archive checksum did not match" }

            publish(UbuntuStage.EXTRACTING, 0.35f, 3, "Unpacking the Linux root filesystem")
            val extraction = python.extractRootfs(archive, staging, spec.stripComponents)
            check(spec.essential.all { File(staging, it).exists() }) { "${distro.displayName} archive is incomplete" }
            publish(UbuntuStage.EXTRACTING, 0.52f, 3, "Unpacked ${extraction.extracted} archive entries")

            publish(UbuntuStage.CONFIGURING, 0.54f, 4, "Writing DNS, hosts, and ${distro.packageManager.command} configuration")
            configure(staging, distro)
            rootfs().deleteRecursively()
            check(staging.renameTo(rootfs())) { "Could not activate the ${distro.displayName} root filesystem" }

            publish(UbuntuStage.CONFIGURING, 0.58f, 5, "Running the Linux launcher self-test")
            val smoke = executeInternal(
                "set -e; probe=/tmp/.xylune-write-test; rm -f \"\$probe\" \"\$probe-link\"; printf x > \"\$probe\"; ln \"\$probe\" \"\$probe-link\"; rm -f \"\$probe\" \"\$probe-link\"; printf 'xylune-linux-ok\\n'; command -v sh ${distro.packageManager.command} >/dev/null",
                sharedWorkspace(), 60, allowBeforeMarker = true,
            ) { progress ->
                publish(UbuntuStage.CONFIGURING, 0.61f, 5, latestLine(progress, "Validating the rootless launcher"))
            }
            check(smoke.exitCode == 0 && "xylune-linux-ok" in smoke.stdout) {
                "${distro.displayName} launcher self-test failed: ${smoke.stderr.ifBlank { smoke.stdout }.takeLast(500)}"
            }

            publish(UbuntuStage.CONFIGURING, 0.64f, 6, "Refreshing ${distro.packageManager.command} package indexes")
            val updateCommand = if (distro.packageManager == LinuxPackageManager.APT) {
                "DEBIAN_FRONTEND=noninteractive apt-get -o Dpkg::Use-Pty=0 update"
            } else {
                "apk update"
            }
            val update = executeInternal(updateCommand, sharedWorkspace(), 300, allowBeforeMarker = true) { progress ->
                publish(UbuntuStage.CONFIGURING, 0.68f, 6, latestLine(progress, "Refreshing package indexes"))
            }

            publish(UbuntuStage.CONFIGURING, 0.74f, 7, "Installing Python and certificate tools")
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
            check(pythonSetup.exitCode == 0) {
                "Python setup failed inside ${distro.displayName}: ${stripAptStatusLines(pythonSetup.stderr.ifBlank { pythonSetup.stdout }).takeLast(600)}"
            }

            publish(UbuntuStage.CONFIGURING, 0.99f, 8, "Finalizing the Linux workspace")
            rootfsMarker().writeText("distribution=${distro.id}\nrelease=${distro.release}\narchitecture=${spec.arch}\nsha256=${spec.sha256}\n")
            val detail = if (update.exitCode == 0) {
                "${distro.displayName} ${distro.release} is ready; package indexes are current."
            } else {
                "${distro.displayName} is ready. Index refresh can be retried later: ${update.stderr.ifBlank { update.stdout }.takeLast(300)}"
            }
            refresh().copy(detail = detail).also { _status.value = it }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            val previous = _status.value
            UbuntuRuntimeStatus(
                stage = UbuntuStage.ERROR,
                distribution = distro,
                architecture = spec.arch,
                progress = previous.progress,
                detail = error.message ?: error::class.java.simpleName,
                currentStep = previous.currentStep,
                totalSteps = previous.totalSteps,
                startedAtMs = previous.startedAtMs,
            ).also { _status.value = it }
        } finally {
            archive.delete()
        }
    } }

'''
regex_once(
    path,
    r"    suspend fun install\(\): UbuntuRuntimeStatus = withContext\(Dispatchers\.IO\) \{ lifecycleMutex\.withLock \{.*?\n    suspend fun remove\(\): UbuntuRuntimeStatus",
    install + "    suspend fun remove(): UbuntuRuntimeStatus",
)
once(
    path,
    """UbuntuRuntimeStatus(UbuntuStage.READY, distro, architecture = spec.arch, sizeBytes = directorySize(rootfs()), detail = "${distro.displayName} ${distro.release} tool layer")
        } else if (rootfsMarker().exists() || rootfs().exists()) {
            UbuntuRuntimeStatus(UbuntuStage.ERROR, distro, architecture = spec.arch, sizeBytes = directorySize(rootfs()), detail = "${distro.displayName} files are incomplete or from another runtime version. Retry setup to repair them.")
""",
    """UbuntuRuntimeStatus(UbuntuStage.READY, distro, architecture = spec.arch, sizeBytes = directorySize(runtimeDir()), detail = "${distro.displayName} ${distro.release} tool layer")
        } else if (rootfsMarker().exists() || rootfs().exists()) {
            UbuntuRuntimeStatus(UbuntuStage.ERROR, distro, architecture = spec.arch, sizeBytes = directorySize(runtimeDir()), detail = "${distro.displayName} files are incomplete or from another runtime version. Retry setup to repair them.")
""",
)
once(
    path,
    """    private fun directorySize(root: File): Long = root.walkTopDown().filter(File::isFile).sumOf(File::length)
    private fun fileState(root: File): Map<String, Pair<Long, Long>> = root.walkTopDown().filter(File::isFile).associate { it.relativeTo(root).path to (it.length() to it.lastModified()) }
""",
    """    private fun directorySize(root: File): Long {
        if (!root.exists()) return 0L
        val pending = ArrayDeque<File>().apply { addLast(root) }
        val countedInodes = HashSet<String>()
        var total = 0L
        while (pending.isNotEmpty()) {
            val file = pending.removeLast()
            val stat = runCatching { Os.lstat(file.absolutePath) }.getOrNull() ?: continue
            if (!countedInodes.add("${stat.st_dev}:${stat.st_ino}")) continue
            total += when {
                stat.st_blocks > 0L -> stat.st_blocks * FILE_SYSTEM_BLOCK_BYTES
                OsConstants.S_ISREG(stat.st_mode) -> stat.st_size.coerceAtLeast(0L)
                else -> 0L
            }
            if (OsConstants.S_ISDIR(stat.st_mode)) file.listFiles()?.forEach { pending.addLast(it) }
        }
        return total
    }

    private fun fileState(root: File): Map<String, Pair<Long, Long>> = root.walkTopDown().filter(File::isFile).associate { it.relativeTo(root).path to (it.length() to it.lastModified()) }
""",
)
once(
    path,
    """        private const val LIVE_OUTPUT_TAIL_CHARS = 16_000
""",
    """        private const val LIVE_OUTPUT_TAIL_CHARS = 16_000
        private const val INSTALL_STEP_COUNT = 8
        private const val FILE_SYSTEM_BLOCK_BYTES = 512L
""",
)
