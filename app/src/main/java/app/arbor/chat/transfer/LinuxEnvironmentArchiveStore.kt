package app.arbor.chat.transfer

import android.content.Context
import androidx.core.content.edit
import app.arbor.chat.sandbox.PythonSandbox
import app.arbor.chat.sandbox.UbuntuRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Serializable
data class PortableLinuxEnvironment(
    val id: String,
    val displayName: String,
    val release: String,
    val archiveEntry: String,
    val targetRelativePath: String,
    val sizeBytes: Long,
    val fileCount: Long,
    val selected: Boolean,
)

data class PreparedLinuxEnvironment(
    val metadata: PortableLinuxEnvironment,
    val archive: File,
) {
    fun delete() {
        archive.delete()
    }
}

/**
 * Preserves rootfs permissions, symlinks, hardlinks, and timestamps by nesting
 * a deterministic tar.gz inside the portable Arbor backup ZIP.
 */
class LinuxEnvironmentArchiveStore(
    private val context: Context,
    private val python: PythonSandbox,
    private val runtime: UbuntuRuntime,
) {
    suspend fun prepareSnapshots(): List<PreparedLinuxEnvironment> = withContext(Dispatchers.IO) {
        runtime.withFilesystemSnapshot {
        val selected = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_DISTRIBUTION, "UBUNTU")
            .orEmpty()
            .lowercase()
        val candidates = buildList {
            val legacyUbuntu = File(context.filesDir, "ubuntu")
            if (isInstalledEnvironment(legacyUbuntu)) add(EnvironmentRoot("ubuntu", legacyUbuntu, "ubuntu"))
            val modernRoot = File(context.filesDir, "linux-runtimes")
            modernRoot.listFiles()?.filter(File::isDirectory)?.sortedBy(File::getName)?.forEach { root ->
                val id = root.name.lowercase()
                if (id != "ubuntu" && SAFE_ID.matches(id) && isInstalledEnvironment(root)) {
                    add(EnvironmentRoot(id, root, "linux-runtimes/$id"))
                }
            }
        }
        val tempRoot = File(context.cacheDir, "linux-backup-prep").apply { mkdirs() }
        candidates.map { candidate ->
            val properties = readRuntimeProperties(candidate.root)
            val archive = File(tempRoot, "${candidate.id}-${UUID.randomUUID()}.tar.gz")
            val result = python.createPortableTar(candidate.root, archive)
            require(archive.isFile && archive.length() <= MAX_LINUX_ENVIRONMENT_BYTES) {
                "${candidate.id} Linux environment is too large for a portable backup"
            }
            PreparedLinuxEnvironment(
                metadata = PortableLinuxEnvironment(
                    id = candidate.id,
                    displayName = candidate.id.replaceFirstChar { it.uppercase() },
                    release = properties["release"].orEmpty(),
                    archiveEntry = "linux/environments/${candidate.id}.tar.gz",
                    targetRelativePath = candidate.targetRelativePath,
                    sizeBytes = archive.length(),
                    fileCount = result.fileCount,
                    selected = selected == candidate.id,
                ),
                archive = archive,
            )
        }
    } }

    fun writePrepared(zip: ZipOutputStream, prepared: List<PreparedLinuxEnvironment>) {
        prepared.forEach { value ->
            zip.putNextEntry(ZipEntry(value.metadata.archiveEntry))
            value.archive.inputStream().buffered().use { input ->
                copyWithLimit(input, zip, MAX_LINUX_ENVIRONMENT_BYTES)
            }
            zip.closeEntry()
        }
    }

    suspend fun restore(
        zip: ZipFile,
        environments: List<PortableLinuxEnvironment>,
    ): Int = withContext(Dispatchers.IO) {
        runtime.withFilesystemSnapshot {
        var restored = 0
        var selectedId: String? = null
        environments.forEach { metadata ->
            require(SAFE_ID.matches(metadata.id)) { "Linux environment id is invalid" }
            val expectedTarget = if (metadata.id == "ubuntu") "ubuntu" else "linux-runtimes/${metadata.id}"
            require(metadata.targetRelativePath == expectedTarget) { "Linux environment target is invalid" }
            require(metadata.archiveEntry == "linux/environments/${metadata.id}.tar.gz") {
                "Linux environment archive path is invalid"
            }
            val entry = requireNotNull(zip.getEntry(metadata.archiveEntry)) {
                "Linux environment ${metadata.id} is missing from the backup"
            }
            require(!entry.isDirectory && (entry.size < 0L || entry.size <= MAX_LINUX_ENVIRONMENT_BYTES)) {
                "Linux environment ${metadata.id} is too large"
            }
            val target = File(context.filesDir, metadata.targetRelativePath)
            val staging = File(requireNotNull(target.parentFile), ".${target.name}.restore-${UUID.randomUUID()}")
            val previous = File(requireNotNull(target.parentFile), ".${target.name}.before-restore-${UUID.randomUUID()}")
            val tar = File(context.cacheDir, "linux-restore-${metadata.id}-${UUID.randomUUID()}.tar.gz")
            staging.deleteRecursively()
            staging.mkdirs()
            try {
                zip.getInputStream(entry).buffered().use { input ->
                    tar.outputStream().buffered().use { output ->
                        copyWithLimit(input, output, MAX_LINUX_ENVIRONMENT_BYTES)
                    }
                }
                python.extractPortableTar(tar, staging)
                require(File(staging, "runtime.properties").isFile && File(staging, "rootfs").isDirectory) {
                    "Linux environment ${metadata.id} is incomplete"
                }
                target.parentFile?.mkdirs()
                if (target.exists()) {
                    require(target.renameTo(previous)) { "Could not stage the existing ${metadata.displayName} environment" }
                }
                if (!staging.renameTo(target)) {
                    if (previous.exists()) previous.renameTo(target)
                    error("Could not activate the restored ${metadata.displayName} environment")
                }
                previous.deleteRecursively()
                restored += 1
                if (metadata.selected) selectedId = metadata.id
            } catch (error: Throwable) {
                staging.deleteRecursively()
                if (!target.exists() && previous.exists()) previous.renameTo(target)
                throw error
            } finally {
                tar.delete()
                previous.deleteRecursively()
            }
        }
        selectedId?.let { id ->
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit(commit = true) {
                putString(KEY_DISTRIBUTION, id.uppercase())
            }
        }
        restored
    } }

    private fun isInstalledEnvironment(root: File): Boolean =
        File(root, "runtime.properties").isFile && File(root, "rootfs").isDirectory

    private fun readRuntimeProperties(root: File): Map<String, String> = runCatching {
        File(root, "runtime.properties").readLines().mapNotNull { line ->
            val key = line.substringBefore('=', "").trim()
            if (key.isBlank()) null else key to line.substringAfter('=', "").trim()
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun copyWithLimit(input: java.io.InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(256 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Linux environment exceeds the supported backup size" }
            output.write(buffer, 0, count)
        }
    }

    private data class EnvironmentRoot(
        val id: String,
        val root: File,
        val targetRelativePath: String,
    )

    private companion object {
        const val PREFERENCES = "arbor_linux_runtime"
        const val KEY_DISTRIBUTION = "selected_distribution"
        const val MAX_LINUX_ENVIRONMENT_BYTES = 12L * 1024 * 1024 * 1024
        val SAFE_ID = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
    }
}
