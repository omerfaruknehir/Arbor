package app.arbor.chat.sandbox

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.StatFs
import androidx.core.content.edit
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

enum class UbuntuStage { NOT_INSTALLED, DOWNLOADING, VERIFYING, EXTRACTING, CONFIGURING, READY, ERROR, UNSUPPORTED }

enum class LinuxPackageManager(val command: String) { APT("apt"), APK("apk") }

enum class LinuxDistribution(
    val id: String,
    val displayName: String,
    val release: String,
    val packageManager: LinuxPackageManager,
    val description: String,
) {
    UBUNTU("ubuntu", "Ubuntu", "26.04", LinuxPackageManager.APT, "Broad compatibility and the largest package selection"),
    DEBIAN("debian", "Debian", "13 (trixie)", LinuxPackageManager.APT, "Stable, compact, and compatible with Debian packages"),
    ALPINE("alpine", "Alpine", "3.24.1", LinuxPackageManager.APK, "Smallest download; uses musl and apk"),
}

data class UbuntuRuntimeStatus(
    val stage: UbuntuStage,
    val distribution: LinuxDistribution = LinuxDistribution.UBUNTU,
    val release: String = distribution.release,
    val architecture: String = "",
    val progress: Float? = null,
    val sizeBytes: Long = 0,
    val detail: String = "",
) {
    val installed: Boolean get() = stage == UbuntuStage.READY
}

@Serializable
data class UbuntuExecutionResult(
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = -1,
    val files: List<String> = emptyList(),
    val elapsedMs: Long = 0,
    val timedOut: Boolean = false,
)

data class UbuntuPackageInstallResult(
    val success: Boolean,
    val stdout: String = "",
    val stderr: String = "",
    val packages: List<String> = emptyList(),
    val elapsedMs: Long = 0,
)

@Serializable
data class PythonPackageSearchResult(
    val name: String,
    val version: String = "",
    val summary: String = "",
)

/**
 * Historical class name retained for database/API compatibility. The runtime
 * now manages one selected rootless Linux distribution at a time.
 */
class UbuntuRuntime(
    private val context: Context,
    private val python: PythonSandbox,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build(),
) {
    private val preferences = context.getSharedPreferences("arbor_linux_runtime", Context.MODE_PRIVATE)
    private val lifecycleMutex = Mutex()
    private val processMutex = Mutex()
    private val _distribution = MutableStateFlow(readDistribution())
    val distribution: StateFlow<LinuxDistribution> = _distribution.asStateFlow()
    private val _status = MutableStateFlow(inspect())
    val status: StateFlow<UbuntuRuntimeStatus> = _status.asStateFlow()

    fun selectDistribution(value: LinuxDistribution) {
        if (_distribution.value == value) return
        _distribution.value = value
        preferences.edit { putString(KEY_DISTRIBUTION, value.name) }
        _status.value = inspect()
    }

    suspend fun refresh(): UbuntuRuntimeStatus = withContext(Dispatchers.IO) {
        inspect().also { _status.value = it }
    }

    suspend fun install(): UbuntuRuntimeStatus = withContext(Dispatchers.IO) { lifecycleMutex.withLock {
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
        archive.delete()
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            _status.value = UbuntuRuntimeStatus(UbuntuStage.DOWNLOADING, distro, architecture = spec.arch, progress = 0f, detail = "${distro.displayName} ${distro.release}")
            val request = Request.Builder().url(spec.url).header("User-Agent", "Arbor/$APP_RUNTIME_VERSION Android").build()
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
                            _status.value = _status.value.copy(
                                progress = if (total > 0) copied.toFloat() / total else null,
                                detail = "Downloaded ${copied / 1_048_576} MiB",
                            )
                        }
                    }
                }
            }
            _status.value = UbuntuRuntimeStatus(UbuntuStage.VERIFYING, distro, architecture = spec.arch, detail = "Verifying the publisher's pinned SHA-256")
            check(sha256(archive).equals(spec.sha256, ignoreCase = true)) { "${distro.displayName} archive checksum did not match" }
            _status.value = UbuntuRuntimeStatus(UbuntuStage.EXTRACTING, distro, architecture = spec.arch, detail = "Unpacking the Linux tool layer")
            python.extractRootfs(archive, staging, spec.stripComponents)
            check(spec.essential.all { File(staging, it).exists() }) { "${distro.displayName} archive is incomplete" }
            _status.value = UbuntuRuntimeStatus(UbuntuStage.CONFIGURING, distro, architecture = spec.arch, detail = "Configuring DNS and ${distro.packageManager.command}")
            configure(staging, distro)
            rootfs().deleteRecursively()
            check(staging.renameTo(rootfs())) { "Could not activate the ${distro.displayName} root filesystem" }
            val smoke = executeInternal(
                "set -e; probe=/tmp/.arbor-write-test; rm -f \"\$probe\" \"\$probe-link\"; printf x > \"\$probe\"; ln \"\$probe\" \"\$probe-link\"; rm -f \"\$probe\" \"\$probe-link\"; printf 'arbor-linux-ok\\n'; command -v sh ${distro.packageManager.command} >/dev/null",
                sharedWorkspace(), 60, allowBeforeMarker = true,
            )
            check(smoke.exitCode == 0 && "arbor-linux-ok" in smoke.stdout) {
                "${distro.displayName} launcher self-test failed: ${smoke.stderr.ifBlank { smoke.stdout }.takeLast(500)}"
            }
            val updateCommand = if (distro.packageManager == LinuxPackageManager.APT) "apt-get update" else "apk update"
            val update = executeInternal(updateCommand, sharedWorkspace(), 300, allowBeforeMarker = true)
            val pythonPackages = if (distro.packageManager == LinuxPackageManager.APT) {
                "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends python3 python3-pip python3-venv ca-certificates"
            } else {
                "apk add --no-progress python3 py3-pip py3-virtualenv ca-certificates"
            }
            val pythonSetup = executeInternal(pythonPackages, sharedWorkspace(), 900, allowBeforeMarker = true)
            check(pythonSetup.exitCode == 0) { "Python setup failed inside ${distro.displayName}: ${pythonSetup.stderr.ifBlank { pythonSetup.stdout }.takeLast(600)}" }
            rootfsMarker().writeText("distribution=${distro.id}\nrelease=${distro.release}\narchitecture=${spec.arch}\nsha256=${spec.sha256}\n")
            val detail = if (update.exitCode == 0) {
                "${distro.displayName} ${distro.release} is ready; package indexes are current."
            } else {
                "${distro.displayName} is ready. Index refresh can be retried later: ${update.stderr.ifBlank { update.stdout }.takeLast(300)}"
            }
            refresh().copy(detail = detail).also { _status.value = it }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            UbuntuRuntimeStatus(UbuntuStage.ERROR, distro, architecture = spec.arch, detail = error.message ?: error::class.java.simpleName).also { _status.value = it }
        } finally {
            archive.delete()
        }
    } }

    suspend fun remove(): UbuntuRuntimeStatus = withContext(Dispatchers.IO) { lifecycleMutex.withLock {
        runtimeDir().deleteRecursively()
        inspect().also { _status.value = it }
    } }

    fun workspace(conversationId: String): File = python.workspace(conversationId)

    suspend fun execute(conversationId: String, command: String, timeoutSeconds: Int = 180): UbuntuExecutionResult =
        processMutex.withLock {
            val distro = distribution.value
            check(status.value.installed || rootfsMarker().isFile) { "Install ${distro.displayName} from Tool workspaces first." }
            require(command.isNotBlank()) { "Command is empty" }
            executeInternal(command, python.workspace(conversationId), timeoutSeconds.coerceIn(1, 3_600))
        }

    suspend fun executePython(conversationId: String, code: String, timeoutSeconds: Int = 90): ExecutionResult {
        require(code.isNotBlank()) { "Python code is empty" }
        ensurePythonEnvironment(conversationId)
        val workspace = python.workspace(conversationId)
        val script = File(workspace, ".arbor-python-run.py")
        script.writeText(code)
        val result = execute(conversationId, "/workspace/.arbor-venv/bin/python /workspace/.arbor-python-run.py", timeoutSeconds.coerceIn(1, 600))
        return ExecutionResult(
            stdout = result.stdout,
            stderr = result.stderr,
            files = result.files.filterNot { it == script.name },
            elapsedMs = result.elapsedMs,
            timedOut = result.timedOut,
            environmentId = "${distribution.value.id}-root-${conversationId.take(8)}",
        )
    }

    suspend fun pythonEnvironment(conversationId: String): PythonEnvironmentInfo {
        ensurePythonEnvironment(conversationId)
        val code = """import json, sys, os, importlib.metadata as m
packages=sorted(({"name":d.metadata.get("Name", d.name),"version":d.version} for d in m.distributions()), key=lambda x:x["name"].lower())
print(json.dumps({"pythonVersion":sys.version.split()[0],"packages":packages}))"""
        val result = executePython(conversationId, code, 60)
        if (result.stderr.isNotBlank() && result.stdout.isBlank()) error(result.stderr.takeLast(1_000))
        val parsed = Json.parseToJsonElement(result.stdout.lineSequence().last(String::isNotBlank)).jsonObject
        val packages = parsed["packages"]?.jsonArray.orEmpty().map { row ->
            val obj = row.jsonObject
            InstalledPackage(obj["name"]?.jsonPrimitive?.content.orEmpty(), obj["version"]?.jsonPrimitive?.content.orEmpty())
        }
        val workspace = python.workspace(conversationId)
        return PythonEnvironmentInfo(
            pythonVersion = parsed["pythonVersion"]?.jsonPrimitive?.content.orEmpty(),
            environmentId = "${distribution.value.id}-root-${conversationId.take(8)}",
            packages = packages,
            sizeBytes = workspace.walkTopDown().filter(File::isFile).sumOf(File::length),
        )
    }

    suspend fun preflightPythonPackages(conversationId: String, raw: String, restrictionsEnabled: Boolean): PackagePlan {
        ensurePythonEnvironment(conversationId)
        val requests = parsePythonRequirements(raw, restrictionsEnabled)
        val installed = pythonEnvironment(conversationId).packages.associate { normalizePythonName(it.name) to it.version }
        val items = requests.map { request ->
            val name = request.substringBefore('[').substringBefore('=').substringBefore('<').substringBefore('>').substringBefore('!').substringBefore('~')
            val installedVersion = installed[normalizePythonName(name)]
            PackagePlanItem(
                request = request,
                name = name,
                installedVersion = installedVersion,
                candidateVersion = null,
                action = if (installedVersion == null) PackageAction.INSTALL else if (request == name) PackageAction.ALREADY_INSTALLED else PackageAction.UPDATE,
                detail = "Resolved by pip inside ${distribution.value.displayName} at install time",
            )
        }
        return PackagePlan(PackageEcosystem.PIP, items, rawPreview = "Python and pip run as root inside ${distribution.value.displayName}; packages are isolated in /workspace/.arbor-venv.")
    }

    suspend fun installPythonPackages(conversationId: String, raw: String, restrictionsEnabled: Boolean, approvedPlan: PackagePlan? = null): PackageInstallResult {
        val plan = preflightPythonPackages(conversationId, raw, restrictionsEnabled)
        require(plan.isValid) { plan.error ?: "Invalid package request" }
        requireApprovedPlan(approvedPlan, plan)
        val changes = plan.items.filter { it.action == PackageAction.INSTALL || it.action == PackageAction.UPDATE }
        if (changes.isEmpty()) return PackageInstallResult(success = true, packages = plan.items.map { it.name })
        val command = "/workspace/.arbor-venv/bin/python -m pip install --disable-pip-version-check --no-input " + changes.joinToString(" ") { shellQuote(it.request) }
        val result = execute(conversationId, command, 900)
        return PackageInstallResult(
            success = result.exitCode == 0,
            stdout = result.stdout,
            stderr = result.stderr,
            packages = changes.map { it.name },
            elapsedMs = result.elapsedMs,
        )
    }

    suspend fun removePythonPackages(conversationId: String, names: List<String>): PythonEnvironmentInfo {
        ensurePythonEnvironment(conversationId)
        val safe = names.map(String::trim).filter { it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")) }.distinct()
        require(safe.isNotEmpty()) { "Choose at least one package" }
        val result = execute(conversationId, "/workspace/.arbor-venv/bin/python -m pip uninstall -y ${safe.joinToString(" ") { shellQuote(it) }}", 600)
        check(result.exitCode == 0) { result.stderr.ifBlank { result.stdout }.takeLast(1_000) }
        return pythonEnvironment(conversationId)
    }

    suspend fun repairPythonEnvironment(conversationId: String): PythonEnvironmentInfo {
        ensurePythonEnvironment(conversationId, forceRepair = true)
        return pythonEnvironment(conversationId)
    }

    suspend fun searchPythonPackages(query: String): List<PythonPackageSearchResult> = withContext(Dispatchers.IO) {
        val clean = query.trim().take(100)
        if (clean.length < 2) return@withContext emptyList()
        val url = "https://pypi.org/search/?q=" + URLEncoder.encode(clean, "UTF-8")
        val request = Request.Builder().url(url).header("User-Agent", "Arbor/$APP_RUNTIME_VERSION Android").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "PyPI search failed with HTTP ${response.code}" }
            val html = response.body?.string().orEmpty().take(2_000_000)
            val blocks = Regex("<a[^>]+class=\\\"package-snippet\\\"[\\s\\S]*?</a>", RegexOption.IGNORE_CASE).findAll(html).take(20)
            blocks.mapNotNull { match ->
                val block = match.value
                val name = Regex("package-snippet__name[^>]*>([^<]+)", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.trim().orEmpty()
                if (name.isBlank()) null else PythonPackageSearchResult(
                    name = name,
                    version = Regex("package-snippet__version[^>]*>([^<]+)", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.trim().orEmpty(),
                    summary = Regex("package-snippet__description[^>]*>([^<]*)", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.trim().orEmpty(),
                )
            }.toList()
        }
    }

    private suspend fun ensurePythonEnvironment(conversationId: String, forceRepair: Boolean = false) {
        check(status.value.installed || rootfsMarker().isFile) { "Install a Linux distribution before using Local Code Execution." }
        val reset = if (forceRepair) "rm -rf /workspace/.arbor-venv; " else ""
        val bootstrap = if (distribution.value.packageManager == LinuxPackageManager.APT) {
            "if ! command -v python3 >/dev/null 2>&1 || ! python3 -m venv --help >/dev/null 2>&1; then apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends python3 python3-pip python3-venv ca-certificates; fi; "
        } else {
            "if ! command -v python3 >/dev/null 2>&1; then apk add --no-progress python3 py3-pip py3-virtualenv ca-certificates; fi; "
        }
        val command = reset + bootstrap + "if [ ! -x /workspace/.arbor-venv/bin/python ]; then python3 -m venv /workspace/.arbor-venv; /workspace/.arbor-venv/bin/python -m pip install --disable-pip-version-check --upgrade pip setuptools wheel; fi"
        val result = execute(conversationId, command, 900)
        check(result.exitCode == 0) { "Could not prepare distro Python: ${result.stderr.ifBlank { result.stdout }.takeLast(1_000)}" }
    }

    private fun parsePythonRequirements(raw: String, restrictionsEnabled: Boolean): List<String> {
        val values = raw.lineSequence().map(String::trim).filter(String::isNotBlank).distinct().take(20).toList()
        require(values.isNotEmpty()) { "Enter at least one package" }
        require(values.all { it.length <= 500 && '\u0000' !in it && !it.startsWith('-') }) { "Package options and oversized requirements are blocked" }
        if (restrictionsEnabled) {
            val safe = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*(?:\\[[A-Za-z0-9_,.-]+])?(?:(?:==|>=|<=|~=|!=|>|<)[A-Za-z0-9.*+_-]+(?:,(?:==|>=|<=|~=|!=|>|<)[A-Za-z0-9.*+_-]+)*)?$")
            require(values.all(safe::matches)) { "Strict mode accepts package names and version constraints only" }
        }
        return values
    }

    private fun normalizePythonName(value: String) = value.lowercase().replace(Regex("[-_.]+"), "-")

    suspend fun preflightPackages(conversationId: String, raw: String, restrictionsEnabled: Boolean): PackagePlan =
        if (distribution.value.packageManager == LinuxPackageManager.APK) preflightApk(conversationId, raw, restrictionsEnabled)
        else preflightApt(conversationId, raw, restrictionsEnabled)

    private suspend fun preflightApt(conversationId: String, raw: String, restrictionsEnabled: Boolean): PackagePlan {
        val requests = parsePackageRequests(raw, restrictionsEnabled)
        val quoted = requests.joinToString(" ") { shellQuote(it) }
        val installedRun = execute(conversationId, "dpkg-query -W -f='\${binary:Package}\\t\${Version}\\n' -- $quoted 2>/dev/null || true", 60)
        val installed = installedRun.stdout.lineSequence().mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size == 2) parts[0].substringBefore(':') to parts[1] else null
        }.toMap()
        val simulation = execute(conversationId, "DEBIAN_FRONTEND=noninteractive apt-get -s --no-install-recommends install $quoted", 180)
        if (simulation.exitCode != 0) return PackagePlan(
            ecosystem = PackageEcosystem.APT,
            items = requests.map { PackagePlanItem(it, packageName(it), installed[packageName(it)], action = PackageAction.INVALID, detail = simulation.stderr.takeLast(600)) },
            rawPreview = simulation.stdout.takeLast(4_000),
            error = simulation.stderr.ifBlank { simulation.stdout }.takeLast(1_000),
        )
        val combined = simulation.stdout + "\n" + simulation.stderr
        val changes = Regex("(?m)^Inst\\s+(\\S+)(?:\\s+\\[([^]]+)])?\\s+\\(([^ )]+)").findAll(combined).associate { match ->
            match.groupValues[1].substringBefore(':') to (match.groupValues[2].ifBlank { null } to match.groupValues[3])
        }
        val items = mutableListOf<PackagePlanItem>()
        requests.forEach { request ->
            val name = packageName(request)
            val change = changes[name]
            items += if (change == null) PackagePlanItem(request, name, installed[name], installed[name], PackageAction.ALREADY_INSTALLED, "Already at the requested candidate")
            else PackagePlanItem(request, name, change.first ?: installed[name], change.second, if (change.first != null || installed[name] != null) PackageAction.UPDATE else PackageAction.INSTALL)
        }
        changes.filterKeys { changed -> items.none { it.name == changed } }.forEach { (name, versions) ->
            items += PackagePlanItem(name, name, versions.first, versions.second, if (versions.first == null) PackageAction.INSTALL else PackageAction.UPDATE, "Dependency")
        }
        return PackagePlan(
            ecosystem = PackageEcosystem.APT,
            items = items,
            downloadSummary = Regex("(?m)^Need to get (.+?) of archives\\.").find(combined)?.groupValues?.get(1).orEmpty(),
            diskSummary = Regex("(?m)^After this operation, (.+? disk space .+?)\\.").find(combined)?.groupValues?.get(1).orEmpty(),
            rawPreview = combined.takeLast(6_000),
        )
    }

    private suspend fun preflightApk(conversationId: String, raw: String, restrictionsEnabled: Boolean): PackagePlan {
        val requests = parsePackageRequests(raw, restrictionsEnabled)
        val installed = mutableMapOf<String, String>()
        requests.forEach { request ->
            val name = packageName(request)
            val check = execute(conversationId, "if apk info -e ${shellQuote(name)}; then apk info -v ${shellQuote(name)} | head -n 1; fi", 30)
            check.stdout.lineSequence().lastOrNull(String::isNotBlank)?.removePrefix("$name-")?.let { installed[name] = it }
        }
        val simulation = execute(conversationId, "apk add --simulate --no-progress ${requests.joinToString(" ") { shellQuote(it) }}", 180)
        val combined = simulation.stdout + "\n" + simulation.stderr
        if (simulation.exitCode != 0) return PackagePlan(
            ecosystem = PackageEcosystem.APK,
            items = requests.map { PackagePlanItem(it, packageName(it), installed[packageName(it)], action = PackageAction.INVALID, detail = combined.takeLast(600)) },
            rawPreview = combined.takeLast(4_000),
            error = combined.takeLast(1_000),
        )
        val changes = Regex("(?m)^\\(\\d+/\\d+\\)\\s+(Installing|Upgrading)\\s+(\\S+)\\s+\\(([^)]+)\\)").findAll(combined).associate { match ->
            match.groupValues[2] to (match.groupValues[1] to match.groupValues[3])
        }
        val items = mutableListOf<PackagePlanItem>()
        requests.forEach { request ->
            val name = packageName(request)
            val change = changes[name]
            items += when {
                change != null -> PackagePlanItem(request, name, installed[name], change.second, if (installed[name] == null) PackageAction.INSTALL else PackageAction.UPDATE)
                installed[name] != null -> PackagePlanItem(request, name, installed[name], installed[name], PackageAction.ALREADY_INSTALLED, "Already installed")
                else -> PackagePlanItem(request, name, null, "resolved by apk", PackageAction.INSTALL)
            }
        }
        changes.filterKeys { changed -> items.none { it.name == changed } }.forEach { (name, change) ->
            items += PackagePlanItem(name, name, null, change.second, PackageAction.INSTALL, "Dependency")
        }
        return PackagePlan(PackageEcosystem.APK, items, rawPreview = combined.takeLast(6_000))
    }

    suspend fun installPackages(conversationId: String, raw: String, restrictionsEnabled: Boolean, approvedPlan: PackagePlan? = null): UbuntuPackageInstallResult {
        val plan = preflightPackages(conversationId, raw, restrictionsEnabled)
        require(plan.isValid) { plan.error ?: "Invalid package request" }
        requireApprovedPlan(approvedPlan, plan)
        if (!plan.hasChanges) return UbuntuPackageInstallResult(true, packages = plan.items.map { it.name })
        val requests = parsePackageRequests(raw, restrictionsEnabled)
        if (distribution.value.packageManager == LinuxPackageManager.APK) {
            val result = execute(conversationId, "apk add --no-progress ${requests.joinToString(" ") { shellQuote(it) }}", 900)
            return UbuntuPackageInstallResult(result.exitCode == 0, result.stdout, result.stderr, requests, result.elapsedMs)
        }
        val recovery = execute(conversationId, "DEBIAN_FRONTEND=noninteractive dpkg --configure -a", 900)
        if (recovery.exitCode != 0) return UbuntuPackageInstallResult(
            false, recovery.stdout, "Package database recovery failed before installation:\n${recovery.stderr}", requests, recovery.elapsedMs,
        )
        val command = "DEBIAN_FRONTEND=noninteractive apt-get -f install -y --no-install-recommends && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ${requests.joinToString(" ") { shellQuote(it) }}"
        val result = execute(conversationId, command, 900)
        return UbuntuPackageInstallResult(
            result.exitCode == 0,
            listOf(recovery.stdout, result.stdout).filter(String::isNotBlank).joinToString("\n"),
            listOf(recovery.stderr, result.stderr).filter(String::isNotBlank).joinToString("\n"),
            requests,
            recovery.elapsedMs + result.elapsedMs,
        )
    }

    private suspend fun executeInternal(command: String, workspace: File, timeoutSeconds: Int, allowBeforeMarker: Boolean = false): UbuntuExecutionResult = withContext(Dispatchers.IO) {
        val distro = distribution.value
        if (!allowBeforeMarker) check(rootfsMarker().isFile) { "${distro.displayName} is not installed" }
        workspace.mkdirs()
        val before = fileState(workspace)
        val native = context.applicationInfo.nativeLibraryDir
        val proot = File(native, "libarbor_proot.so")
        val loader = File(native, "libarbor_proot_loader.so")
        check(proot.isFile && loader.isFile) { "This APK does not contain the Linux launcher for ${Build.SUPPORTED_ABIS.firstOrNull()}" }
        val tmp = File(context.cacheDir, "proot-tmp-${distro.id}").also { it.mkdirs() }
        val args = mutableListOf(
            proot.absolutePath, "--kill-on-exit", "--link2symlink", "-0", "-r", rootfs().absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "${workspace.absolutePath}:/workspace", "-w", "/workspace",
            "/usr/bin/env", "-i", "HOME=/root", "USER=root", "LOGNAME=root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8", "TERM=xterm-256color", "TMPDIR=/tmp",
            "/bin/sh", "-lc", command,
        )
        val builder = ProcessBuilder(args)
        builder.environment().apply {
            put("LD_LIBRARY_PATH", native)
            put("PROOT_LOADER", loader.absolutePath)
            put("PROOT_TMP_DIR", tmp.absolutePath)
            put("PROOT_NO_SECCOMP", "1")
        }
        val started = System.currentTimeMillis()
        val process = builder.start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val closingStreams = AtomicBoolean(false)
        val stdoutFailure = AtomicReference<IOException?>(null)
        val stderrFailure = AtomicReference<IOException?>(null)
        val outThread = startStreamPump(
            name = "arbor-linux-stdout",
            input = process.inputStream,
            output = stdout,
            closing = closingStreams,
            failure = stdoutFailure,
        )
        val errThread = startStreamPump(
            name = "arbor-linux-stderr",
            input = process.errorStream,
            output = stderr,
            closing = closingStreams,
            failure = stderrFailure,
        )
        var complete = false
        var timedOut = false
        try {
            val deadline = started + timeoutSeconds * 1_000L
            while (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                currentCoroutineContext().ensureActive()
                if (System.currentTimeMillis() >= deadline) {
                    timedOut = true
                    process.destroyForcibly()
                    break
                }
            }
            if (!timedOut) complete = true
        } catch (cancelled: CancellationException) {
            process.destroyForcibly()
            throw cancelled
        } finally {
            closingStreams.set(true)
            if (timedOut) process.waitFor(2, TimeUnit.SECONDS)
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            outThread.join(2_000)
            errThread.join(2_000)
        }
        val after = fileState(workspace)
        val captureWarning = listOfNotNull(stdoutFailure.get(), stderrFailure.get())
            .distinctBy { it::class.java.name to it.message }
            .joinToString("\n") { "Output capture warning: ${it.message ?: it::class.java.simpleName}" }
        UbuntuExecutionResult(
            stdout = stdout.toString(),
            stderr = listOf(stderr.toString(), captureWarning).filter(String::isNotBlank).joinToString("\n"),
            exitCode = if (complete) process.exitValue() else -1,
            files = after.filter { (path, state) -> before[path] != state }.keys.take(500),
            elapsedMs = System.currentTimeMillis() - started,
            timedOut = timedOut,
        )
    }

    private fun configure(root: File, distro: LinuxDistribution) {
        val dnsServers = runCatching {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            connectivity.getLinkProperties(connectivity.activeNetwork)?.dnsServers
                ?.mapNotNull { it.hostAddress?.substringBefore('%') }
                ?.filter(String::isNotBlank)?.distinct().orEmpty()
        }.getOrDefault(emptyList()).ifEmpty { listOf("1.1.1.1", "8.8.8.8") }
        File(root, "etc/resolv.conf").apply {
            parentFile?.mkdirs(); delete()
            writeText(dnsServers.joinToString(separator = "\n", postfix = "\n") { "nameserver $it" })
        }
        File(root, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
        if (distro.packageManager == LinuxPackageManager.APT) File(root, "etc/apt/apt.conf.d/99arbor").apply {
            parentFile?.mkdirs()
            writeText("APT::Sandbox::User \"root\";\nAcquire::Retries \"3\";\nDpkg::Use-Pty \"0\";\n")
        }
        listOf("tmp", "proc", "sys", "dev", "root").forEach { File(root, it).mkdirs() }
    }

    private fun inspect(): UbuntuRuntimeStatus {
        val distro = distribution.value
        val spec = currentSpec() ?: return UbuntuRuntimeStatus(UbuntuStage.UNSUPPORTED, distro, architecture = Build.SUPPORTED_ABIS.joinToString(), detail = "Unsupported device ABI")
        val marker = rootfsMarker().takeIf(File::isFile)?.let { runCatching { it.readText() }.getOrNull() }.orEmpty()
        val essential = spec.essential.all { File(rootfs(), it).exists() }
        val markerMatches = "distribution=${distro.id}" in marker && "release=${distro.release}" in marker && "architecture=${spec.arch}" in marker && "sha256=${spec.sha256}" in marker
        return if (markerMatches && essential) {
            UbuntuRuntimeStatus(UbuntuStage.READY, distro, architecture = spec.arch, sizeBytes = directorySize(rootfs()), detail = "${distro.displayName} ${distro.release} tool layer")
        } else if (rootfsMarker().exists() || rootfs().exists()) {
            UbuntuRuntimeStatus(UbuntuStage.ERROR, distro, architecture = spec.arch, sizeBytes = directorySize(rootfs()), detail = "${distro.displayName} files are incomplete or from another runtime version. Retry setup to repair them.")
        } else UbuntuRuntimeStatus(UbuntuStage.NOT_INSTALLED, distro, architecture = spec.arch, detail = "Optional ${spec.downloadMiB} MiB download; stored only inside Arbor")
    }

    private fun parsePackageRequests(raw: String, restrictionsEnabled: Boolean): List<String> {
        val requests = raw.lineSequence().flatMap { it.split(' ', '\t', ',').asSequence() }.map(String::trim).filter(String::isNotBlank).distinct().take(50).toList()
        require(requests.isNotEmpty()) { "Enter at least one ${distribution.value.packageManager.command} package" }
        val strict = Regex("^[a-z0-9][a-z0-9+.-]*(?::[a-z0-9_-]+)?(?:=[A-Za-z0-9:~+._-]+)?$")
        val advanced = Regex("^[A-Za-z0-9][A-Za-z0-9+.:~=_-]*$")
        require(requests.all { (if (restrictionsEnabled) strict else advanced).matches(it) }) {
            "Package names${if (restrictionsEnabled) " and optional exact versions" else ""} only; options and shell syntax are blocked"
        }
        return requests
    }

    private fun packageName(value: String): String = value.substringBefore('=').substringBefore(':')
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    private fun runtimeDir(): File = if (distribution.value == LinuxDistribution.UBUNTU) File(context.filesDir, "ubuntu") else File(context.filesDir, "linux-runtimes/${distribution.value.id}")
    private fun rootfs() = File(runtimeDir(), "rootfs")
    private fun rootfsMarker() = File(runtimeDir(), "runtime.properties")
    private fun sharedWorkspace() = File(context.filesDir, "workspaces/_linux_setup_${distribution.value.id}").also { it.mkdirs() }

    private fun currentSpec(): RootfsSpec? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "x86_64" } ?: return null
        val arm = abi == "arm64-v8a"
        return when (distribution.value) {
            LinuxDistribution.UBUNTU -> {
                val arch = if (arm) "arm64" else "amd64"
                val sha = if (arm) "b2b46a37324ea1954e93f293fe6d7c2241daf2fc298c4022e6e4caceeed74cab" else "046fcabb7f16f45a80ae11824664f2a07e01386c6fb1ed9dc1e225a66a6553a2"
                val file = "ubuntu-base-${LinuxDistribution.UBUNTU.release}-base-$arch.tar.gz"
                RootfsSpec(arch, file, "https://cdimage.ubuntu.com/ubuntu-base/releases/${LinuxDistribution.UBUNTU.release}/release/$file", sha, 0, listOf("bin/sh", "bin/bash", "usr/bin/apt-get", "usr/bin/dpkg"), 33)
            }
            LinuxDistribution.DEBIAN -> if (arm) RootfsSpec(
                "aarch64", "debian-trixie-aarch64-pd-v4.26.0.tar.xz",
                "https://github.com/termux/proot-distro/releases/download/v4.26.0/debian-trixie-aarch64-pd-v4.26.0.tar.xz",
                "cda75346f2c9e09e8a802665745b5a7e2bd6d8584dbf1c86c8c57ef54c4e2d3c", 1,
                listOf("bin/sh", "usr/bin/apt-get", "usr/bin/dpkg"), 34,
            ) else RootfsSpec(
                "x86_64", "debian-trixie-x86_64-pd-v4.26.0.tar.xz",
                "https://github.com/termux/proot-distro/releases/download/v4.26.0/debian-trixie-x86_64-pd-v4.26.0.tar.xz",
                "e2edc15363395936cf0cba8c440a108458dba58fb496d3d962909d7a8d9777ae", 1,
                listOf("bin/sh", "usr/bin/apt-get", "usr/bin/dpkg"), 35,
            )
            LinuxDistribution.ALPINE -> {
                val arch = if (arm) "aarch64" else "x86_64"
                val sha = if (arm) "f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259" else "41f73e3cf5fa919b8aa5ca6b30dc48f0da2720776d7423e2a7748211456fe081"
                val file = "alpine-minirootfs-${LinuxDistribution.ALPINE.release}-$arch.tar.gz"
                RootfsSpec(arch, file, "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/$arch/$file", sha, 0, listOf("bin/sh", "sbin/apk"), 4)
            }
        }
    }

    private data class RootfsSpec(
        val arch: String,
        val fileName: String,
        val url: String,
        val sha256: String,
        val stripComponents: Int,
        val essential: List<String>,
        val downloadMiB: Int,
    )

    private fun readDistribution(): LinuxDistribution = runCatching {
        LinuxDistribution.valueOf(preferences.getString(KEY_DISTRIBUTION, null) ?: LinuxDistribution.UBUNTU.name)
    }.getOrDefault(LinuxDistribution.UBUNTU)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun directorySize(root: File): Long = root.walkTopDown().filter(File::isFile).sumOf(File::length)
    private fun fileState(root: File): Map<String, Pair<Long, Long>> = root.walkTopDown().filter(File::isFile).associate { it.relativeTo(root).path to (it.length() to it.lastModified()) }
    private fun startStreamPump(
        name: String,
        input: java.io.InputStream,
        output: StringBuilder,
        closing: AtomicBoolean,
        failure: AtomicReference<IOException?>,
    ): Thread = Thread({
        try {
            copyCapped(input, output, closing, failure)
        } catch (error: Throwable) {
            // Never let a process-reader daemon terminate through Android's
            // global uncaught-exception handler. Process streams are routinely
            // closed from the coroutine thread during cancellation/timeout.
            if (!isExpectedStreamShutdown(error, closing.get())) {
                failure.compareAndSet(null, error as? IOException ?: IOException("Output capture failed", error))
            }
        }
    }, name).apply {
        isDaemon = true
        start()
    }

    private fun copyCapped(
        input: java.io.InputStream,
        output: StringBuilder,
        closing: AtomicBoolean,
        failure: AtomicReference<IOException?>,
        limit: Int = 1_000_000,
    ) {
        try {
            input.bufferedReader().use { reader ->
                val buffer = CharArray(8_192)
                while (output.length < limit) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, limit - output.length))
                    if (count < 0) break
                    output.append(buffer, 0, count)
                }
            }
        } catch (error: Throwable) {
            if (!isExpectedStreamShutdown(error, closing.get())) {
                failure.compareAndSet(null, error as? IOException ?: IOException("Output capture failed", error))
            }
        }
    }

    private fun isExpectedStreamShutdown(error: Throwable, closing: Boolean): Boolean {
        if (closing) return true
        return generateSequence(error as Throwable?) { it.cause }.any { cause ->
            cause is InterruptedIOException ||
                cause.message?.contains("read interrupted by close", ignoreCase = true) == true ||
                cause.message?.contains("stream closed", ignoreCase = true) == true ||
                cause.message?.contains("closed", ignoreCase = true) == true && cause is IOException
        }
    }

    companion object {
        const val RELEASE = "26.04"
        private const val APP_RUNTIME_VERSION = "0.11.0"
        private const val MIN_FREE_BYTES = 300L * 1024 * 1024
        private const val KEY_DISTRIBUTION = "selected_distribution"
    }
}
