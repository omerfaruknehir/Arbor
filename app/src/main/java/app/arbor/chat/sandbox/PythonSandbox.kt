package app.arbor.chat.sandbox

import android.content.Context
import android.os.Build
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@Serializable
data class ExecutionResult(
    val stdout: String = "",
    val stderr: String = "",
    val result: String? = null,
    val files: List<String> = emptyList(),
    val elapsedMs: Long = 0,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false,
    val environmentId: String = "",
)

@Serializable
data class InstalledPackage(
    val name: String,
    val version: String,
)

@Serializable
data class PythonEnvironmentInfo(
    val pythonVersion: String = "3.12",
    val environmentId: String = "",
    val packages: List<InstalledPackage> = emptyList(),
    val sizeBytes: Long = 0,
    val nativeErrors: Map<String, String> = emptyMap(),
)

@Serializable
data class PackageInstallResult(
    val success: Boolean = false,
    val stdout: String = "",
    val stderr: String = "",
    val packages: List<String> = emptyList(),
    val importNames: Map<String, List<String>> = emptyMap(),
    val importErrors: Map<String, String> = emptyMap(),
    val elapsedMs: Long = 0,
)

@Serializable
data class RootfsExtractionResult(
    val extracted: Int = 0,
    val skipped: List<String> = emptyList(),
    val elapsedMs: Long = 0,
)

class PythonSandbox(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun execute(conversationId: String, code: String, timeoutSeconds: Int = 90): ExecutionResult =
        withContext(Dispatchers.IO) { mutex.withLock {
            val workspace = workspace(conversationId)
            startPython()
            val raw = Python.getInstance().getModule("sandbox_runner")
                .callAttr("run_code", code, workspace.absolutePath, 1_000_000, timeoutSeconds.coerceIn(1, 600))
                .toString()
            json.decodeFromString<ExecutionResult>(raw)
        } }

    fun requestCancel(conversationId: String) {
        val marker = File(workspace(conversationId), ".arbor-cancel")
        runCatching { marker.writeText("Stop requested by user.\n") }
    }

    suspend fun preflight(conversationId: String, rawRequirements: String, restrictionsEnabled: Boolean): PackagePlan = withContext(Dispatchers.IO) { mutex.withLock {
        val requirements = parseRequirements(rawRequirements, restrictionsEnabled)
        val workspace = workspace(conversationId)
        startPython()
        val raw = Python.getInstance().getModule("sandbox_runner")
            .callAttr("preflight_packages", json.encodeToString(requirements), workspace.absolutePath, platformTag())
            .toString()
        json.decodeFromString<PackagePlan>(raw)
    } }

    suspend fun install(conversationId: String, rawRequirements: String, restrictionsEnabled: Boolean = true, approvedPlan: PackagePlan? = null): PackageInstallResult {
        val plan = preflight(conversationId, rawRequirements, restrictionsEnabled)
        require(plan.isValid) { plan.error ?: "Invalid package request" }
        requireApprovedPlan(approvedPlan, plan)
        val requirements = plan.items
            .filter { it.action == PackageAction.INSTALL || it.action == PackageAction.UPDATE }
            .map { item ->
                if (item.candidateVersion != null && item.detail != "Direct source") {
                    "${item.name}==${item.candidateVersion}"
                } else item.request
            }
        if (requirements.isEmpty()) return PackageInstallResult(success = true, packages = plan.items.map { it.request })
        return withContext(Dispatchers.IO) { mutex.withLock {
        val workspace = workspace(conversationId)
        startPython()
        val raw = Python.getInstance().getModule("sandbox_runner")
            .callAttr("install_packages", json.encodeToString(requirements), workspace.absolutePath, platformTag())
            .toString()
        json.decodeFromString<PackageInstallResult>(raw)
        } }
    }

    suspend fun extractRootfs(archive: File, destination: File, stripComponents: Int = 0): RootfsExtractionResult = withContext(Dispatchers.IO) { mutex.withLock {
        startPython()
        val raw = Python.getInstance().getModule("sandbox_runner")
            .callAttr("extract_rootfs", archive.absolutePath, destination.absolutePath, stripComponents)
            .toString()
        json.decodeFromString<RootfsExtractionResult>(raw)
    } }

    suspend fun environment(conversationId: String): PythonEnvironmentInfo = withContext(Dispatchers.IO) { mutex.withLock {
        val workspace = workspace(conversationId)
        startPython()
        val raw = Python.getInstance().getModule("sandbox_runner")
            .callAttr("environment_info", workspace.absolutePath).toString()
        json.decodeFromString<PythonEnvironmentInfo>(raw)
    } }

    suspend fun remove(conversationId: String, packageNames: List<String>): PythonEnvironmentInfo = withContext(Dispatchers.IO) { mutex.withLock {
        val safeNames = packageNames.map(String::trim).filter { it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")) }.distinct()
        require(safeNames.isNotEmpty()) { "Choose at least one package" }
        val workspace = workspace(conversationId)
        startPython()
        val raw = Python.getInstance().getModule("sandbox_runner")
            .callAttr("remove_packages", json.encodeToString(safeNames), workspace.absolutePath).toString()
        json.decodeFromString<PythonEnvironmentInfo>(raw)
    } }

    suspend fun repair(conversationId: String): PythonEnvironmentInfo = withContext(Dispatchers.IO) { mutex.withLock {
        val workspace = workspace(conversationId)
        startPython()
        val raw = Python.getInstance().getModule("sandbox_runner")
            .callAttr("repair_environment", workspace.absolutePath).toString()
        json.decodeFromString<PythonEnvironmentInfo>(raw)
    } }

    suspend fun resetSession(conversationId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        startPython()
        Python.getInstance().getModule("sandbox_runner")
            .callAttr("reset_session", workspace(conversationId).absolutePath)
        Unit
    } }

    suspend fun deleteWorkspace(conversationId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        workspace(conversationId).deleteRecursively()
        Unit
    } }

    private fun startPython() {
        synchronized(Python::class.java) {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        }
    }

    fun workspace(conversationId: String): File = File(context.filesDir, "workspaces/$conversationId").also { it.mkdirs() }

    private fun platformTag(): String = "android_21_${Build.SUPPORTED_ABIS.firstOrNull().orEmpty().replace('-', '_')}"

    private fun parseRequirements(raw: String, restrictionsEnabled: Boolean): List<String> {
        val values = raw.lineSequence().map(String::trim).filter(String::isNotBlank).distinct().take(20).toList()
        require(values.isNotEmpty()) { "Enter at least one package" }
        require(values.all { it.length <= 500 && '\u0000' !in it && !it.startsWith('-') }) {
            "Package options, empty values, and oversized requirements are blocked"
        }
        if (restrictionsEnabled) {
            val safe = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*(?:\\[[A-Za-z0-9_,.-]+])?(?:(?:==|>=|<=|~=|!=|>|<)[A-Za-z0-9.*+_-]+(?:,(?:==|>=|<=|~=|!=|>|<)[A-Za-z0-9.*+_-]+)*)?$")
            require(values.all(safe::matches)) {
                "Strict mode accepts package names and version constraints only. Enable advanced package sources in Settings for direct URLs or VCS references."
            }
        }
        return values
    }
}
