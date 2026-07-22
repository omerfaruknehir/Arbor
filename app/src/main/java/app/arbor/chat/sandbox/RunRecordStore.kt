package app.arbor.chat.sandbox

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

@Serializable
enum class ScriptRuntime { PYTHON, LINUX }

@Serializable
data class ScriptAttempt(
    val attempt: Int,
    val revision: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val exitCode: Int,
    val timedOut: Boolean,
    val cancelled: Boolean,
    val elapsedMs: Long,
    val changedFiles: List<String> = emptyList(),
)

@Serializable
data class ScriptPatchRecord(
    val revision: Int,
    val patchFile: String,
    val changedLines: Int,
    val sourceSha256: String,
    val createdAt: Long,
)

@Serializable
data class ScriptRunMetadata(
    val runId: String,
    val conversationId: String,
    val runtime: ScriptRuntime,
    val scriptPath: String,
    val currentRevision: Int = 1,
    val sourceSha256: String,
    val originalCommand: String,
    val originalArgs: List<String> = emptyList(),
    val workingDirectory: String = "/workspace",
    val environment: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val exitCode: Int? = null,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false,
    val stdoutPath: String,
    val stderrPath: String,
    val changedFiles: List<String> = emptyList(),
    val attemptCount: Int = 0,
    val attempts: List<ScriptAttempt> = emptyList(),
    val patches: List<ScriptPatchRecord> = emptyList(),
)

@Serializable
data class WorkspaceReadResult(
    val path: String,
    val sourceSha256: String,
    val startLine: Int,
    val endLine: Int,
    val truncated: Boolean,
    val text: String,
)

@Serializable
data class AppliedPatchResult(
    val path: String,
    val runId: String? = null,
    val revision: Int? = null,
    val previousSha256: String,
    val sourceSha256: String,
    val changedLines: Int,
    val summary: String,
)

@Serializable
data class ScriptRunResult(
    val runId: String,
    val revision: Int,
    val attempt: Int,
    val runtime: ScriptRuntime,
    val scriptPath: String,
    val sourceSha256: String,
    val exitCode: Int,
    val timedOut: Boolean,
    val cancelled: Boolean,
    val elapsedMs: Long,
    val stdoutTail: String = "",
    val stderrTail: String = "",
    val diagnostic: String = "",
    val sourceExcerpt: String = "",
    val changedFiles: List<String> = emptyList(),
    val instruction: String = "",
)

/** Durable run records and atomic, hash-guarded workspace editing. */
class RunRecordStore(private val workspace: (String) -> File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun create(
        conversationId: String,
        runtime: ScriptRuntime,
        source: String,
        originalCommand: String,
        args: List<String>,
        timeoutSeconds: Int,
        environment: Map<String, String>,
    ): ScriptRunMetadata {
        require(source.isNotBlank() && source.length <= MAX_SOURCE_CHARS && '\u0000' !in source) { "Script source is empty or too large" }
        val runId = "run-${UUID.randomUUID()}"
        val extension = if (runtime == ScriptRuntime.PYTHON) "py" else "sh"
        val relative = "$RUNS_DIRECTORY/$runId/main.$extension"
        val directory = resolveDirectory(conversationId, "$RUNS_DIRECTORY/$runId")
        val script = File(directory, "main.$extension")
        script.writeText(source)
        File(directory, "revisions").mkdirs()
        File(directory, "stdout.log").createNewFile()
        File(directory, "stderr.log").createNewFile()
        val now = System.currentTimeMillis()
        return ScriptRunMetadata(
            runId = runId,
            conversationId = conversationId,
            runtime = runtime,
            scriptPath = relative,
            sourceSha256 = sha256(script),
            originalCommand = originalCommand.take(MAX_COMMAND_CHARS),
            originalArgs = args.take(MAX_ARGS).map { it.take(MAX_ARG_CHARS) },
            environment = environment.filterKeys { it in SAFE_ENVIRONMENT_KEYS }.mapValues { it.value.take(200) },
            timeoutSeconds = timeoutSeconds,
            createdAt = now,
            stdoutPath = "$RUNS_DIRECTORY/$runId/stdout.log",
            stderrPath = "$RUNS_DIRECTORY/$runId/stderr.log",
        ).also(::save)
    }

    fun load(conversationId: String, runId: String): ScriptRunMetadata {
        validateRunId(runId)
        val file = File(resolveDirectory(conversationId, "$RUNS_DIRECTORY/$runId"), METADATA_FILE)
        val metadata = json.decodeFromString<ScriptRunMetadata>(file.takeIf(File::isFile)?.readText() ?: error("Unknown script run: $runId"))
        require(metadata.conversationId == conversationId && metadata.runId == runId) { "Run metadata does not belong to this conversation" }
        return metadata
    }

    fun markStarted(metadata: ScriptRunMetadata, timeoutSeconds: Int, args: List<String>): ScriptRunMetadata =
        metadata.copy(
            timeoutSeconds = timeoutSeconds,
            originalArgs = args.take(MAX_ARGS).map { it.take(MAX_ARG_CHARS) },
            startedAt = System.currentTimeMillis(),
            finishedAt = null,
            attemptCount = metadata.attemptCount + 1,
        ).also(::save)

    fun finish(
        metadata: ScriptRunMetadata,
        stdout: String,
        stderr: String,
        exitCode: Int,
        timedOut: Boolean,
        cancelled: Boolean,
        elapsedMs: Long,
        changedFiles: List<String>,
    ): ScriptRunResult {
        val directory = resolveDirectory(metadata.conversationId, "$RUNS_DIRECTORY/${metadata.runId}")
        File(directory, "stdout.log").writeText(stdout.takeLast(MAX_LOG_FILE_CHARS))
        File(directory, "stderr.log").writeText(stderr.takeLast(MAX_LOG_FILE_CHARS))
        val finished = System.currentTimeMillis()
        val attempt = ScriptAttempt(
            metadata.attemptCount,
            metadata.currentRevision,
            metadata.startedAt ?: finished,
            finished,
            exitCode,
            timedOut,
            cancelled,
            elapsedMs,
            changedFiles.take(MAX_CHANGED_FILES),
        )
        val updated = metadata.copy(
            finishedAt = finished,
            exitCode = exitCode,
            timedOut = timedOut,
            cancelled = cancelled,
            changedFiles = changedFiles.take(MAX_CHANGED_FILES),
            attempts = (metadata.attempts + attempt).takeLast(MAX_ATTEMPTS),
        ).also(::save)
        val script = resolveFile(metadata.conversationId, updated.scriptPath, mustExist = true)
        val diagnostic = stderr.takeLast(MAX_DIAGNOSTIC_CHARS)
        return ScriptRunResult(
            runId = updated.runId,
            revision = updated.currentRevision,
            attempt = updated.attemptCount,
            runtime = updated.runtime,
            scriptPath = updated.scriptPath,
            sourceSha256 = sha256(script),
            exitCode = exitCode,
            timedOut = timedOut,
            cancelled = cancelled,
            elapsedMs = elapsedMs,
            stdoutTail = stdout.takeLast(MAX_RESULT_LOG_CHARS),
            stderrTail = stderr.takeLast(MAX_RESULT_LOG_CHARS),
            diagnostic = diagnostic,
            sourceExcerpt = relevantExcerpt(script.readText(), diagnostic),
            changedFiles = updated.changedFiles,
            instruction = if (exitCode != 0 || timedOut || cancelled) {
                "Inspect only the necessary ranges with workspace_read, patch the existing script with apply_patch using sourceSha256, then use rerun_script. Do not resend the full source."
            } else "Run completed successfully.",
        )
    }

    fun readWorkspace(conversationId: String, path: String, startLine: Int?, endLine: Int?, maxBytes: Int?): WorkspaceReadResult {
        val file = resolveFile(conversationId, path, mustExist = true)
        require(file.length() <= MAX_READABLE_FILE_BYTES) { "Workspace file is too large for text reading" }
        val lines = file.readLines()
        val start = (startLine ?: 1).coerceAtLeast(1)
        val requestedEnd = (endLine ?: minOf(lines.size, start + DEFAULT_READ_LINES - 1)).coerceAtLeast(start)
        val end = minOf(lines.size, requestedEnd, start + MAX_READ_LINES - 1)
        val limit = (maxBytes ?: DEFAULT_READ_BYTES).coerceIn(256, MAX_READ_BYTES)
        val output = StringBuilder()
        var actualEnd = start - 1
        for (lineNumber in start..end) {
            val row = "${lineNumber.toString().padStart(6)} | ${lines.getOrNull(lineNumber - 1).orEmpty()}\n"
            if (output.toString().toByteArray().size + row.toByteArray().size > limit) break
            output.append(row)
            actualEnd = lineNumber
        }
        return WorkspaceReadResult(path, sha256(file), start, actualEnd, actualEnd < requestedEnd || end < requestedEnd, output.toString())
    }

    fun applyPatch(conversationId: String, path: String, unifiedDiff: String, expectedSha256: String): AppliedPatchResult {
        require(unifiedDiff.length in 1..MAX_PATCH_CHARS) { "Patch is empty or too large" }
        val file = resolveFile(conversationId, path, mustExist = true)
        val beforeBytes = file.readBytes()
        val beforeHash = sha256(beforeBytes)
        require(expectedSha256.equals(beforeHash, ignoreCase = true)) { "Stale source hash: expected $expectedSha256, current is $beforeHash" }
        val applied = UnifiedPatch.apply(beforeBytes.toString(Charsets.UTF_8), unifiedDiff)
        require(applied.source != beforeBytes.toString(Charsets.UTF_8)) { "Patch does not change the file" }
        val candidateBytes = applied.source.toByteArray(Charsets.UTF_8)
        require(candidateBytes.size <= MAX_SOURCE_CHARS) { "Patched source is too large" }

        val temporary = File.createTempFile("arbor-patch-", ".tmp", file.parentFile)
        try {
            temporary.writeBytes(candidateBytes)
            Files.move(
                temporary.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        val afterHash = sha256(file)
        val owningRun = owningRun(conversationId, file)
        val updated = owningRun?.let { metadata ->
            val patchNumber = metadata.patches.size + 1
            val patchName = "${patchNumber.toString().padStart(4, '0')}.patch"
            val runDirectory = resolveDirectory(conversationId, "$RUNS_DIRECTORY/${metadata.runId}")
            File(runDirectory, "revisions/$patchName").writeText(unifiedDiff)
            metadata.copy(
                currentRevision = metadata.currentRevision + 1,
                sourceSha256 = afterHash,
                patches = (metadata.patches + ScriptPatchRecord(
                    metadata.currentRevision + 1,
                    "revisions/$patchName",
                    applied.changedLines,
                    afterHash,
                    System.currentTimeMillis(),
                )).takeLast(MAX_PATCHES),
            ).also(::save)
        }
        return AppliedPatchResult(
            path = path,
            runId = updated?.runId,
            revision = updated?.currentRevision,
            previousSha256 = beforeHash,
            sourceSha256 = afterHash,
            changedLines = applied.changedLines,
            summary = "${applied.changedLines} line(s) changed; unchanged lines were preserved",
        )
    }

    fun scriptFile(metadata: ScriptRunMetadata): File = resolveFile(metadata.conversationId, metadata.scriptPath, mustExist = true)

    private fun owningRun(conversationId: String, file: File): ScriptRunMetadata? {
        val relative = file.relativeTo(workspace(conversationId).canonicalFile).invariantSeparatorsPath
        val match = Regex("^\\.arbor/runs/(run-[A-Za-z0-9-]+)/main\\.(?:py|sh)$").matchEntire(relative) ?: return null
        return load(conversationId, match.groupValues[1])
    }

    private fun save(metadata: ScriptRunMetadata) {
        val directory = resolveDirectory(metadata.conversationId, "$RUNS_DIRECTORY/${metadata.runId}")
        atomicWrite(File(directory, METADATA_FILE), json.encodeToString(metadata))
    }

    private fun resolveDirectory(conversationId: String, relative: String): File {
        val root = workspace(conversationId).canonicalFile
        val directory = File(root, relative).canonicalFile
        require(directory.path.startsWith(root.path + File.separator)) { "Path escapes the conversation workspace" }
        directory.mkdirs()
        return directory
    }

    fun resolveFile(conversationId: String, path: String, mustExist: Boolean): File {
        require(path.isNotBlank() && !File(path).isAbsolute && '\u0000' !in path) { "Use a relative workspace path" }
        val root = workspace(conversationId).canonicalFile
        val file = File(root, path.removePrefix("/workspace/")).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Path escapes the conversation workspace" }
        if (mustExist) require(file.isFile) { "Workspace file does not exist: $path" }
        return file
    }

    private fun validateRunId(runId: String) = require(runId.matches(Regex("run-[A-Za-z0-9-]{8,80}"))) { "Invalid runId" }

    companion object {
        const val RUNS_DIRECTORY = ".arbor/runs"
        private const val METADATA_FILE = "metadata.json"
        private const val MAX_SOURCE_CHARS = 1_000_000
        private const val MAX_PATCH_CHARS = 250_000
        private const val MAX_COMMAND_CHARS = 8_000
        private const val MAX_ARG_CHARS = 1_000
        private const val MAX_ARGS = 64
        private const val MAX_LOG_FILE_CHARS = 1_000_000
        private const val MAX_RESULT_LOG_CHARS = 8_000
        private const val MAX_DIAGNOSTIC_CHARS = 12_000
        private const val MAX_READABLE_FILE_BYTES = 4_000_000L
        private const val DEFAULT_READ_BYTES = 16_000
        private const val MAX_READ_BYTES = 64_000
        private const val DEFAULT_READ_LINES = 120
        private const val MAX_READ_LINES = 500
        private const val MAX_CHANGED_FILES = 100
        private const val MAX_ATTEMPTS = 100
        private const val MAX_PATCHES = 100
        private val SAFE_ENVIRONMENT_KEYS = setOf("distribution", "release", "architecture", "python", "executionMode")

        fun sha256(file: File): String = sha256(file.readBytes())
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        internal fun relevantExcerpt(source: String, diagnostic: String): String {
            val line = listOf(
                Regex("(?i)line\\s+(\\d+)").findAll(diagnostic).lastOrNull()?.groupValues?.get(1)?.toIntOrNull(),
                Regex("(?m)main\\.(?:py|sh):(\\d+)").findAll(diagnostic).lastOrNull()?.groupValues?.get(1)?.toIntOrNull(),
            ).filterNotNull().firstOrNull() ?: return ""
            val rows = source.lines()
            val start = (line - 4).coerceAtLeast(1)
            val end = (line + 4).coerceAtMost(rows.size)
            return (start..end).joinToString("\n") { number ->
                "${number.toString().padStart(6)} | ${rows[number - 1]}"
            }.take(MAX_DIAGNOSTIC_CHARS)
        }

        private fun atomicWrite(destination: File, content: String) {
            val temporary = File.createTempFile(destination.name, ".tmp", destination.parentFile)
            try {
                temporary.writeText(content)
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }
}

internal data class UnifiedPatchResult(val source: String, val changedLines: Int)

/** Strict single-file unified diff applier. It builds the candidate in memory before the atomic write. */
internal object UnifiedPatch {
    private val HUNK = Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$")

    fun apply(source: String, diff: String): UnifiedPatchResult {
        val newline = if ("\r\n" in source) "\r\n" else "\n"
        val original = source.replace("\r\n", "\n").split('\n')
        val rows = diff.replace("\r\n", "\n").split('\n')
        val oldHeaders = rows.withIndex().filter { it.value.startsWith("--- ") }
        val newHeaders = rows.withIndex().filter { it.value.startsWith("+++ ") }
        require(oldHeaders.size <= 1 && newHeaders.size <= 1) { "Patch must describe exactly one workspace file" }
        val output = mutableListOf<String>()
        var sourceIndex = 0
        var index = rows.indexOfFirst { it.startsWith("@@ ") }
        require(index >= 0) { "Unified diff has no hunk header" }
        require(oldHeaders.all { it.index < index } && newHeaders.all { it.index < index }) { "Additional file headers inside patch hunks are ambiguous" }
        var changed = 0
        var hunkCount = 0
        while (index < rows.size) {
            val match = HUNK.matchEntire(rows[index]) ?: error("Malformed hunk header at patch line ${index + 1}")
            hunkCount++
            val oldStart = match.groupValues[1].toInt()
            val oldCount = match.groupValues[2].ifBlank { "1" }.toInt()
            val newCount = match.groupValues[4].ifBlank { "1" }.toInt()
            val targetIndex = (oldStart - 1).coerceAtLeast(0)
            require(targetIndex >= sourceIndex && targetIndex <= original.size) { "Overlapping or out-of-range hunk" }
            output += original.subList(sourceIndex, targetIndex)
            sourceIndex = targetIndex
            index++
            var consumedOld = 0
            var producedNew = 0
            while (index < rows.size && !rows[index].startsWith("@@ ")) {
                val row = rows[index]
                if (row.startsWith("--- ") || row.startsWith("+++ ")) { index++; continue }
                if (row == "\\ No newline at end of file") { index++; continue }
                require(row.isNotEmpty()) { "Malformed unified diff line ${index + 1}" }
                val body = row.substring(1)
                when (row[0]) {
                    ' ' -> {
                        require(original.getOrNull(sourceIndex) == body) { "Context mismatch at source line ${sourceIndex + 1}" }
                        output += original[sourceIndex++]
                        consumedOld++; producedNew++
                    }
                    '-' -> {
                        require(original.getOrNull(sourceIndex) == body) { "Removal mismatch at source line ${sourceIndex + 1}" }
                        sourceIndex++; consumedOld++; changed++
                    }
                    '+' -> { output += body; producedNew++; changed++ }
                    else -> error("Unsupported unified diff marker '${row[0]}' at patch line ${index + 1}")
                }
                index++
            }
            require(consumedOld == oldCount) { "Hunk consumed $consumedOld old lines; header declares $oldCount" }
            require(producedNew == newCount) { "Hunk produced $producedNew new lines; header declares $newCount" }
        }
        require(hunkCount > 0 && changed > 0) { "Patch contains no changes" }
        output += original.subList(sourceIndex, original.size)
        return UnifiedPatchResult(output.joinToString("\n").let { if (newline == "\r\n") it.replace("\n", "\r\n") else it }, changed)
    }
}
