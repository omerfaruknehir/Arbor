package app.arbor.chat.generated

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Serializable
enum class GeneratedRepairStatus { PENDING, COMPILING, REPAIRING, ACCEPTED, EXHAUSTED, PROVIDER_FAILED }

@Serializable
data class GeneratedRepairAttempt(
    val number: Int,
    val candidateFingerprint: String,
    val normalizedFingerprint: String,
    val errors: List<GeneratedValidationError>,
    val malformedResponse: Boolean = false,
    val repeatedCandidate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class GeneratedBlockRepairState(
    val blockId: String,
    val messageId: String,
    val conversationId: String,
    val type: GeneratedBlockType,
    val canonicalFence: String,
    val originalSource: String,
    val originalFingerprint: String,
    val currentCandidate: String,
    val attemptCount: Int = 0,
    val maxAttempts: Int = 3,
    val cycle: Int = 1,
    val errors: List<GeneratedValidationError>,
    val attempts: List<GeneratedRepairAttempt> = emptyList(),
    val candidateFingerprints: List<String> = emptyList(),
    val status: GeneratedRepairStatus = GeneratedRepairStatus.PENDING,
    val acceptedSource: String? = null,
    val providerError: String? = null,
    val contractVersion: String = GeneratedContentCapabilityRegistry.CONTRACT_VERSION,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Persisted, bounded compile-and-repair loop for a single stable generated block. */
class GeneratedBlockRepairCoordinator(
    private val workspace: (String) -> File,
    private val compileCandidate: suspend (GeneratedBlockType, String) -> GeneratedCompilationResult = { type, source ->
        val validation = GeneratedContentCapabilityRegistry.validate(type, source)
        GeneratedCompilationResult(source, validation.errors)
    },
    private val requestRepair: suspend (GeneratedBlockRepairState) -> String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val blockLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun repair(
        conversationId: String,
        messageId: String,
        blockId: String,
        type: GeneratedBlockType,
        originalSource: String,
        initialErrors: List<GeneratedValidationError>,
        maxAttempts: Int,
        newCycle: Boolean = false,
        progress: (GeneratedBlockRepairState) -> Unit = {},
    ): GeneratedBlockRepairState = blockLocks.getOrPut("$conversationId:$blockId") { Mutex() }.withLock {
        repairLocked(conversationId, messageId, blockId, type, originalSource, initialErrors, maxAttempts, newCycle, progress)
    }

    private suspend fun repairLocked(
        conversationId: String,
        messageId: String,
        blockId: String,
        type: GeneratedBlockType,
        originalSource: String,
        initialErrors: List<GeneratedValidationError>,
        maxAttempts: Int,
        newCycle: Boolean,
        progress: (GeneratedBlockRepairState) -> Unit,
    ): GeneratedBlockRepairState {
        val capability = GeneratedContentCapabilityRegistry.fences.first { it.type == type }
        val maximum = maxAttempts.coerceIn(1, 5)
        var state = withContext(Dispatchers.IO) {
            val saved = load(conversationId, blockId)
            val fingerprint = fingerprint(originalSource)
            when {
                saved == null || saved.originalFingerprint != fingerprint || saved.contractVersion != GeneratedContentCapabilityRegistry.CONTRACT_VERSION ->
                    GeneratedBlockRepairState(
                        blockId, messageId, conversationId, type, capability.canonicalFence,
                        originalSource, fingerprint, originalSource, maxAttempts = maximum, errors = initialErrors,
                    )
                newCycle -> saved.copy(
                    currentCandidate = saved.acceptedSource ?: saved.currentCandidate,
                    attemptCount = 0,
                    maxAttempts = maximum,
                    cycle = saved.cycle + 1,
                    errors = initialErrors,
                    status = GeneratedRepairStatus.PENDING,
                    acceptedSource = null,
                    providerError = null,
                    updatedAt = System.currentTimeMillis(),
                )
                else -> saved.copy(maxAttempts = maximum)
            }.also(::save)
        }
        if (!newCycle && state.status in setOf(GeneratedRepairStatus.ACCEPTED, GeneratedRepairStatus.EXHAUSTED, GeneratedRepairStatus.PROVIDER_FAILED)) {
            progress(state)
            return state
        }

        // Every newly generated widget enters here even when its JSON schema is already valid.
        // The compiler executes actions, preflights HTTP data, and renders launcher viewports
        // before the block is allowed to appear as usable content.
        if (state.attemptCount == 0) {
            state = state.copy(status = GeneratedRepairStatus.COMPILING, updatedAt = System.currentTimeMillis()).also {
                withContext(Dispatchers.IO) { save(it) }
                progress(it)
            }
            val compilation = compileSafely(type, state.currentCandidate)
            if (compilation.valid) {
                val accepted = state.copy(
                    currentCandidate = compilation.compiledSource,
                    errors = emptyList(),
                    status = GeneratedRepairStatus.ACCEPTED,
                    acceptedSource = compilation.compiledSource,
                    updatedAt = System.currentTimeMillis(),
                )
                withContext(Dispatchers.IO) { save(accepted) }
                progress(accepted)
                return accepted
            }
            state = state.copy(
                currentCandidate = compilation.compiledSource.ifBlank { state.currentCandidate },
                errors = compilation.errors,
                status = GeneratedRepairStatus.PENDING,
                updatedAt = System.currentTimeMillis(),
            ).also {
                withContext(Dispatchers.IO) { save(it) }
                progress(it)
            }
        }

        while (state.attemptCount < maximum) {
            state = state.copy(status = GeneratedRepairStatus.REPAIRING, updatedAt = System.currentTimeMillis()).also {
                withContext(Dispatchers.IO) { save(it) }
                progress(it)
            }
            val raw = try {
                requestRepair(state)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val failed = state.copy(
                    status = GeneratedRepairStatus.PROVIDER_FAILED,
                    providerError = safeProviderError(error),
                    updatedAt = System.currentTimeMillis(),
                )
                withContext(Dispatchers.IO) { save(failed) }
                progress(failed)
                return failed
            }

            val extracted = GeneratedContentCapabilityRegistry.extractSingleReplacement(raw, state.canonicalFence)
            val candidate = extracted.getOrNull().orEmpty()
            val compilation = if (extracted.isSuccess) {
                state = state.copy(
                    currentCandidate = candidate,
                    status = GeneratedRepairStatus.COMPILING,
                    updatedAt = System.currentTimeMillis(),
                ).also {
                    withContext(Dispatchers.IO) { save(it) }
                    progress(it)
                }
                compileSafely(type, candidate)
            } else GeneratedCompilationResult(
                candidate,
                listOf(GeneratedValidationError(
                    "repair_response",
                    "/",
                    extracted.exceptionOrNull()?.message ?: "Malformed repair response",
                )),
            )
            val compiled = compilation.compiledSource.ifBlank { candidate }
            val rawFingerprint = fingerprint(compiled.ifEmpty { raw })
            val normalizedFingerprint = fingerprint(compiled.ifEmpty { raw }.trim().replace(Regex("\\s+"), " "))
            val repeated = normalizedFingerprint in state.attempts.map { it.normalizedFingerprint }
            val attempt = GeneratedRepairAttempt(
                number = state.attemptCount + 1,
                candidateFingerprint = rawFingerprint,
                normalizedFingerprint = normalizedFingerprint,
                errors = compilation.errors,
                malformedResponse = extracted.isFailure,
                repeatedCandidate = repeated,
            )
            state = state.copy(
                currentCandidate = compiled.ifBlank { state.currentCandidate },
                attemptCount = state.attemptCount + 1,
                errors = compilation.errors,
                attempts = state.attempts + attempt,
                candidateFingerprints = state.candidateFingerprints + rawFingerprint,
                status = if (compilation.valid) GeneratedRepairStatus.ACCEPTED else if (state.attemptCount + 1 >= maximum) GeneratedRepairStatus.EXHAUSTED else GeneratedRepairStatus.PENDING,
                acceptedSource = compiled.takeIf { compilation.valid },
                updatedAt = System.currentTimeMillis(),
            )
            withContext(Dispatchers.IO) { save(state) }
            progress(state)
            if (compilation.valid || state.status == GeneratedRepairStatus.EXHAUSTED) return state
        }
        return state.copy(status = GeneratedRepairStatus.EXHAUSTED, updatedAt = System.currentTimeMillis()).also {
            withContext(Dispatchers.IO) { save(it) }
            progress(it)
        }
    }

    suspend fun acceptManualEdit(state: GeneratedBlockRepairState, source: String): GeneratedBlockRepairState {
        val compilation = compileSafely(state.type, source)
        require(compilation.valid) { compilation.errors.joinToString("\n") { "${it.path}: ${it.message} (${it.phase})" } }
        return state.copy(
            currentCandidate = compilation.compiledSource,
            acceptedSource = compilation.compiledSource,
            errors = emptyList(),
            status = GeneratedRepairStatus.ACCEPTED,
            updatedAt = System.currentTimeMillis(),
        ).also { withContext(Dispatchers.IO) { save(it) } }
    }

    private suspend fun compileSafely(type: GeneratedBlockType, source: String): GeneratedCompilationResult = try {
        compileCandidate(type, source)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        GeneratedCompilationResult(
            source,
            listOf(GeneratedValidationError("compiler", "/", (error.message ?: error::class.java.simpleName).take(500))),
        )
    }

    private fun stateFile(conversationId: String, blockId: String): File {
        val root = workspace(conversationId).canonicalFile
        val directory = File(root, ".arbor/generated-repairs").also(File::mkdirs).canonicalFile
        require(directory.path.startsWith(root.path + File.separator)) { "Repair state path escaped workspace" }
        return File(directory, "${fingerprint(blockId).take(32)}.json")
    }

    private fun load(conversationId: String, blockId: String): GeneratedBlockRepairState? =
        stateFile(conversationId, blockId).takeIf(File::isFile)?.let { runCatching { json.decodeFromString<GeneratedBlockRepairState>(it.readText()) }.getOrNull() }

    private fun save(state: GeneratedBlockRepairState) {
        val destination = stateFile(state.conversationId, state.blockId)
        val temporary = File.createTempFile("repair-", ".tmp", destination.parentFile)
        try {
            temporary.writeText(json.encodeToString(state))
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    companion object {
        fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun safeProviderError(error: Throwable): String = (error.message ?: error::class.java.simpleName)
            .replace(Regex("(?i)(?:api[_ -]?key|authorization|bearer)\\s*[:=]?\\s*[^\\s,;]+"), "credential [redacted]")
            .take(1_000)
    }
}
