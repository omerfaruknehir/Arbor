package app.xylune.chat.transfer

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import app.xylune.chat.data.XyluneDatabase
import app.xylune.chat.data.AutomationSettingsEntity
import app.xylune.chat.data.AuxiliaryMode
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.MemoryEntity
import app.xylune.chat.data.PackageApprovalMode
import app.xylune.chat.data.ProjectEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import app.xylune.chat.data.ReasoningVisibility
import app.xylune.chat.data.SystemPromptMode
import app.xylune.chat.data.SystemPromptProfileEntity
import app.xylune.chat.data.ThinkingEffort
import app.xylune.chat.provider.ProviderEndpointPolicy
import app.xylune.chat.settings.AppPreferences
import app.xylune.chat.settings.ColorPalette
import app.xylune.chat.settings.AppLanguage
import app.xylune.chat.settings.DeveloperSettings
import app.xylune.chat.settings.NewChatDefaults
import app.xylune.chat.settings.PerformanceOverlayPosition
import app.xylune.chat.settings.ThemeMode
import java.util.UUID
import kotlinx.serialization.Serializable

private const val PORTABLE_SETTINGS_SCHEMA = "xylune-portable-settings-v1"

@Serializable
data class PortableAppSettings(
    val schema: String = PORTABLE_SETTINGS_SCHEMA,
    val preferences: PortablePreferenceSettings,
    val providers: List<PortableProviderSettings> = emptyList(),
    val models: List<PortableModelSettings> = emptyList(),
    val projects: List<PortableProjectSettings> = emptyList(),
    val systemPromptProfiles: List<PortableSystemPromptSettings> = emptyList(),
    val automation: PortableAutomationSettings? = null,
    val memories: List<PortableMemorySettings> = emptyList(),
)

@Serializable
data class PortablePreferenceSettings(
    val themeMode: String,
    val appLanguage: String = AppLanguage.SYSTEM.name,
    val palette: String,
    val amoled: Boolean,
    val matchLauncherIconToPalette: Boolean,
    val chromeBlurStrength: Float,
    val chromeEdgeSoftness: Float,
    val chromeOverlayOpacity: Float,
    val generatedRepairMaxAttempts: Int,
    val lessEmojiEnabled: Boolean = true,
    val automaticUpdateChecks: Boolean = true,
    val newChatDefaults: PortableNewChatDefaults,
    val developerSettings: PortableDeveloperSettings,
    val selectedLinuxDistribution: String,
)

@Serializable
data class PortableNewChatDefaults(
    val selectedProviderId: String,
    val selectedModelId: String,
    val contextPairs: Int,
    val contextTokenLimit: Int,
    val workingTokenLimit: Int,
    val maxOutputTokens: Int,
    val systemPromptProfileId: String? = null,
    val reasoningVisibility: String,
    val thinkingEnabled: Boolean,
    val thinkingEffort: String,
    val webSearchEnabled: Boolean,
    val agentPythonEnabled: Boolean,
    val agentUbuntuEnabled: Boolean,
    val deepResearchEnabled: Boolean,
    val hybridTokenCountingEnabled: Boolean,
)

@Serializable
data class PortableDeveloperSettings(
    val enabled: Boolean,
    val toolDiagnosticsEnabled: Boolean,
    val performanceOverlayEnabled: Boolean,
    val diagnosticProfilerEnabled: Boolean,
    val detailedPerformanceOverlay: Boolean,
    val performanceUpdateIntervalMs: Int,
    val performanceOverlayPosition: String,
    val performanceOverlayBackgroundOpacity: Float,
    val performanceOverlayTextOpacity: Float,
    val performanceOverlayScale: Float,
    val blurBoundaryDebugEnabled: Boolean,
    val blurBoundaryDebugThicknessDp: Float,
)

@Serializable
data class PortableProviderSettings(
    val id: String,
    val displayName: String,
    val kind: String,
    val baseUrl: String,
    val enabled: Boolean,
    val registered: Boolean,
    val apiKeyRequired: Boolean,
)

@Serializable
data class PortableModelSettings(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val inputCacheHitUsdPerMillion: Double,
    val inputCacheMissUsdPerMillion: Double,
    val outputUsdPerMillion: Double,
    val pricingConfigured: Boolean,
    val supportsVision: Boolean,
    val supportsFiles: Boolean,
    val supportsThinking: Boolean,
    val supportsTools: Boolean,
    val supportsImageGeneration: Boolean,
    val description: String = "",
    val createdAtEpochSeconds: Long = 0,
    val reasoningMetadataAvailable: Boolean = false,
    val reasoningEffortsCsv: String = "",
    val reasoningDefaultEffort: String = "",
    val reasoningDefaultEnabled: Boolean = false,
    val reasoningMandatory: Boolean = false,
    val reasoningSupportsMaxTokens: Boolean = false,
    val metadataSource: String = "",
    val metadataUpdatedAt: Long = 0,
)

@Serializable
data class PortableProjectSettings(
    val id: String,
    val name: String,
    val colorArgb: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class PortableSystemPromptSettings(
    val id: String,
    val name: String,
    val prompt: String,
    val mode: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class PortableMemorySettings(
    val id: String,
    val content: String,
    val category: String,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class PortableAutomationSettings(
    val titleMode: String,
    val titleProviderId: String,
    val titleModelId: String,
    val compressionMode: String,
    val compressionProviderId: String,
    val compressionModelId: String,
    val packageApprovalMode: String,
    val approvalProviderId: String,
    val approvalModelId: String,
    val packageRestrictionsEnabled: Boolean,
    val trustedPythonPackages: String,
    val trustedUbuntuPackages: String,
    val memoryEnabled: Boolean = true,
    val memoryAutoSave: Boolean = true,
)

data class AppSettingsRestoreResult(
    val restored: Boolean = false,
    val projectIds: Map<String, String> = emptyMap(),
    val systemPromptProfileIds: Map<String, String> = emptyMap(),
)

/**
 * Portable, explicit settings backup. Secrets are intentionally not represented:
 * no API keys, OAuth sessions, encrypted database key, provider authorization
 * headers, cloud grants, drafts, or transient navigation state can enter it.
 */
class AppSettingsArchiveStore(
    private val context: Context,
    private val preferences: AppPreferences,
    private val database: XyluneDatabase,
) {
    suspend fun snapshot(): PortableAppSettings {
        val defaults = preferences.newChatDefaults.value
        val developer = preferences.developerSettings.value
        val automation = database.automationSettingsDao().get()
        return PortableAppSettings(
            preferences = PortablePreferenceSettings(
                themeMode = preferences.themeMode.value.name,
                appLanguage = preferences.appLanguage.value.name,
                palette = preferences.palette.value.name,
                amoled = preferences.amoled.value,
                matchLauncherIconToPalette = preferences.matchLauncherIconToPalette.value,
                chromeBlurStrength = preferences.chromeBlurStrength.value,
                chromeEdgeSoftness = preferences.chromeEdgeSoftness.value,
                chromeOverlayOpacity = preferences.chromeOverlayOpacity.value,
                generatedRepairMaxAttempts = preferences.generatedRepairMaxAttempts.value,
                lessEmojiEnabled = preferences.lessEmojiEnabled.value,
                automaticUpdateChecks = preferences.automaticUpdateChecks.value,
                newChatDefaults = PortableNewChatDefaults(
                    selectedProviderId = defaults.selectedProviderId,
                    selectedModelId = defaults.selectedModelId,
                    contextPairs = defaults.contextPairs,
                    contextTokenLimit = defaults.contextTokenLimit,
                    workingTokenLimit = defaults.workingTokenLimit,
                    maxOutputTokens = defaults.maxOutputTokens,
                    systemPromptProfileId = defaults.systemPromptProfileId,
                    reasoningVisibility = defaults.reasoningVisibility.name,
                    thinkingEnabled = defaults.thinkingEnabled,
                    thinkingEffort = defaults.thinkingEffort.name,
                    webSearchEnabled = defaults.webSearchEnabled,
                    agentPythonEnabled = defaults.agentPythonEnabled,
                    agentUbuntuEnabled = defaults.agentUbuntuEnabled,
                    deepResearchEnabled = defaults.deepResearchEnabled,
                    hybridTokenCountingEnabled = defaults.hybridTokenCountingEnabled,
                ),
                developerSettings = PortableDeveloperSettings(
                    enabled = developer.enabled,
                    toolDiagnosticsEnabled = developer.toolDiagnosticsEnabled,
                    performanceOverlayEnabled = developer.performanceOverlayEnabled,
                    diagnosticProfilerEnabled = developer.diagnosticProfilerEnabled,
                    detailedPerformanceOverlay = developer.detailedPerformanceOverlay,
                    performanceUpdateIntervalMs = developer.performanceUpdateIntervalMs,
                    performanceOverlayPosition = developer.performanceOverlayPosition.name,
                    performanceOverlayBackgroundOpacity = developer.performanceOverlayBackgroundOpacity,
                    performanceOverlayTextOpacity = developer.performanceOverlayTextOpacity,
                    performanceOverlayScale = developer.performanceOverlayScale,
                    blurBoundaryDebugEnabled = developer.blurBoundaryDebugEnabled,
                    blurBoundaryDebugThicknessDp = developer.blurBoundaryDebugThicknessDp,
                ),
                selectedLinuxDistribution = context.getSharedPreferences(LINUX_PREFERENCES, Context.MODE_PRIVATE)
                    .getString(KEY_DISTRIBUTION, "UBUNTU").orEmpty(),
            ),
            providers = database.catalogDao().allProviders().map { provider ->
                PortableProviderSettings(
                    id = provider.id,
                    displayName = provider.displayName,
                    kind = provider.kind.name,
                    baseUrl = provider.baseUrl,
                    enabled = provider.enabled,
                    registered = provider.registered,
                    apiKeyRequired = provider.apiKeyRequired,
                )
            },
            models = database.catalogDao().allModels().map { model ->
                PortableModelSettings(
                    providerId = model.providerId,
                    modelId = model.modelId,
                    displayName = model.displayName,
                    contextWindow = model.contextWindow,
                    maxOutputTokens = model.maxOutputTokens,
                    inputCacheHitUsdPerMillion = model.inputCacheHitUsdPerMillion,
                    inputCacheMissUsdPerMillion = model.inputCacheMissUsdPerMillion,
                    outputUsdPerMillion = model.outputUsdPerMillion,
                    pricingConfigured = model.pricingConfigured,
                    supportsVision = model.supportsVision,
                    supportsFiles = model.supportsFiles,
                    supportsThinking = model.supportsThinking,
                    supportsTools = model.supportsTools,
                    supportsImageGeneration = model.supportsImageGeneration,
                    description = model.description,
                    createdAtEpochSeconds = model.createdAtEpochSeconds,
                    reasoningMetadataAvailable = model.reasoningMetadataAvailable,
                    reasoningEffortsCsv = model.reasoningEffortsCsv,
                    reasoningDefaultEffort = model.reasoningDefaultEffort,
                    reasoningDefaultEnabled = model.reasoningDefaultEnabled,
                    reasoningMandatory = model.reasoningMandatory,
                    reasoningSupportsMaxTokens = model.reasoningSupportsMaxTokens,
                    metadataSource = model.metadataSource,
                    metadataUpdatedAt = model.metadataUpdatedAt,
                )
            },
            projects = database.projectDao().all().map { project ->
                PortableProjectSettings(
                    id = project.id,
                    name = project.name,
                    colorArgb = project.colorArgb,
                    createdAt = project.createdAt,
                    updatedAt = project.updatedAt,
                )
            },
            systemPromptProfiles = database.systemPromptProfileDao().all().map { profile ->
                PortableSystemPromptSettings(
                    id = profile.id,
                    name = profile.name,
                    prompt = profile.prompt,
                    mode = profile.mode.name,
                    createdAt = profile.createdAt,
                    updatedAt = profile.updatedAt,
                )
            },
            memories = database.memoryDao().all().map { memory ->
                PortableMemorySettings(
                    id = memory.id,
                    content = memory.content,
                    category = memory.category,
                    enabled = memory.enabled,
                    createdAt = memory.createdAt,
                    updatedAt = memory.updatedAt,
                )
            },
            automation = automation?.let { value ->
                PortableAutomationSettings(
                    titleMode = value.titleMode.name,
                    titleProviderId = value.titleProviderId,
                    titleModelId = value.titleModelId,
                    compressionMode = value.compressionMode.name,
                    compressionProviderId = value.compressionProviderId,
                    compressionModelId = value.compressionModelId,
                    packageApprovalMode = value.packageApprovalMode.name,
                    approvalProviderId = value.approvalProviderId,
                    approvalModelId = value.approvalModelId,
                    packageRestrictionsEnabled = value.packageRestrictionsEnabled,
                    trustedPythonPackages = value.trustedPythonPackages,
                    trustedUbuntuPackages = value.trustedUbuntuPackages,
                    memoryEnabled = value.memoryEnabled,
                    memoryAutoSave = value.memoryAutoSave,
                )
            },
        )
    }

    suspend fun restore(value: PortableAppSettings): AppSettingsRestoreResult {
        require(value.schema == PORTABLE_SETTINGS_SCHEMA) { "Unsupported Xylune settings backup" }
        val projectIds = linkedMapOf<String, String>()
        val promptIds = linkedMapOf<String, String>()
        database.withTransaction {
            restoreProviders(value)
            restoreProjects(value.projects, projectIds)
            restorePromptProfiles(value.systemPromptProfiles, promptIds)
            restoreMemories(value.memories)
            value.automation?.let { restoreAutomation(it) }
        }
        restorePreferences(value.preferences, promptIds)
        return AppSettingsRestoreResult(
            restored = true,
            projectIds = projectIds,
            systemPromptProfileIds = promptIds,
        )
    }

    private suspend fun restoreProviders(value: PortableAppSettings) {
        val catalog = database.catalogDao()
        val restoredProviderIds = linkedSetOf<String>()
        value.providers.take(MAX_PROVIDERS).forEach { portable ->
            if (!SAFE_ID.matches(portable.id) || portable.displayName.isBlank()) return@forEach
            val kind = portable.kind.enumOr(ProviderKind.OPENAI_COMPATIBLE)
            val baseUrl = runCatching { ProviderEndpointPolicy.validate(portable.baseUrl) }.getOrNull() ?: return@forEach
            val canRemainRegistered = portable.registered && !portable.apiKeyRequired && kind != ProviderKind.OPENAI_OAUTH
            catalog.upsertProvider(
                ProviderEntity(
                    id = portable.id,
                    displayName = portable.displayName.take(120),
                    kind = kind,
                    baseUrl = baseUrl,
                    enabled = portable.enabled,
                    customHeadersJson = "{}",
                    registered = canRemainRegistered,
                    apiKeyRequired = portable.apiKeyRequired,
                ),
            )
            restoredProviderIds += portable.id
        }
        value.models.groupBy(PortableModelSettings::providerId).forEach { (providerId, rows) ->
            if (providerId !in restoredProviderIds) return@forEach
            val models = rows.take(MAX_MODELS_PER_PROVIDER).mapNotNull { portable ->
                if (portable.modelId.isBlank() || portable.modelId.length > 240) return@mapNotNull null
                ModelEntity(
                    providerId = providerId,
                    modelId = portable.modelId,
                    displayName = portable.displayName.take(160).ifBlank { portable.modelId.take(160) },
                    contextWindow = portable.contextWindow.coerceIn(1_024, 2_000_000),
                    maxOutputTokens = portable.maxOutputTokens.coerceIn(1, 384_000),
                    inputCacheHitUsdPerMillion = portable.inputCacheHitUsdPerMillion.coerceAtLeast(0.0),
                    inputCacheMissUsdPerMillion = portable.inputCacheMissUsdPerMillion.coerceAtLeast(0.0),
                    outputUsdPerMillion = portable.outputUsdPerMillion.coerceAtLeast(0.0),
                    pricingConfigured = portable.pricingConfigured,
                    supportsVision = portable.supportsVision,
                    supportsFiles = portable.supportsFiles,
                    supportsThinking = portable.supportsThinking,
                    supportsTools = portable.supportsTools,
                    supportsImageGeneration = portable.supportsImageGeneration,
                    description = portable.description.take(2_000),
                    createdAtEpochSeconds = portable.createdAtEpochSeconds.coerceAtLeast(0),
                    reasoningMetadataAvailable = portable.reasoningMetadataAvailable,
                    reasoningEffortsCsv = portable.reasoningEffortsCsv.take(120),
                    reasoningDefaultEffort = portable.reasoningDefaultEffort.take(24),
                    reasoningDefaultEnabled = portable.reasoningDefaultEnabled,
                    reasoningMandatory = portable.reasoningMandatory,
                    reasoningSupportsMaxTokens = portable.reasoningSupportsMaxTokens,
                    metadataSource = portable.metadataSource.take(120),
                    metadataUpdatedAt = portable.metadataUpdatedAt.coerceAtLeast(0),
                )
            }
            if (models.isNotEmpty()) {
                catalog.deleteModels(providerId)
                catalog.upsertModels(models)
            }
        }
    }

    private suspend fun restoreProjects(
        values: List<PortableProjectSettings>,
        mappings: MutableMap<String, String>,
    ) {
        val dao = database.projectDao()
        val existing = dao.all().associateBy { it.name.trim().lowercase() }.toMutableMap()
        values.take(MAX_PROJECTS).forEach { portable ->
            val name = portable.name.trim().take(100)
            if (name.isBlank()) return@forEach
            val key = name.lowercase()
            val current = existing[key]
            val local = if (current != null) {
                current.copy(colorArgb = portable.colorArgb, updatedAt = maxOf(current.updatedAt, portable.updatedAt)).also { dao.update(it) }
            } else {
                val id = portable.id.takeIf(SAFE_ID::matches)?.takeIf { dao.get(it) == null } ?: UUID.randomUUID().toString()
                ProjectEntity(id, name, portable.colorArgb, portable.createdAt, portable.updatedAt).also {
                    dao.insert(it)
                    existing[key] = it
                }
            }
            mappings[portable.id] = local.id
        }
    }

    private suspend fun restorePromptProfiles(
        values: List<PortableSystemPromptSettings>,
        mappings: MutableMap<String, String>,
    ) {
        val dao = database.systemPromptProfileDao()
        val existing = dao.all().associateBy { it.name.trim().lowercase() }.toMutableMap()
        values.take(MAX_PROMPT_PROFILES).forEach { portable ->
            val name = portable.name.trim().take(120)
            if (name.isBlank() || portable.prompt.length > MAX_PROMPT_CHARS) return@forEach
            val key = name.lowercase()
            val mode = portable.mode.enumOr(SystemPromptMode.PREPEND)
            val current = existing[key]
            val local = if (current != null) {
                current.copy(prompt = portable.prompt, mode = mode, updatedAt = maxOf(current.updatedAt, portable.updatedAt)).also { dao.update(it) }
            } else {
                val id = portable.id.takeIf(SAFE_ID::matches)?.takeIf { dao.get(it) == null } ?: UUID.randomUUID().toString()
                SystemPromptProfileEntity(id, name, portable.prompt, mode, portable.createdAt, portable.updatedAt).also {
                    dao.insert(it)
                    existing[key] = it
                }
            }
            mappings[portable.id] = local.id
        }
    }

    private suspend fun restoreMemories(values: List<PortableMemorySettings>) {
        values.take(500).forEach { portable ->
            val clean = portable.content.trim().replace(Regex("\\s+"), " ").take(2_000)
            if (clean.isBlank()) return@forEach
            val normalized = clean.lowercase().take(512)
            val now = System.currentTimeMillis()
            val existing = database.memoryDao().byNormalizedKey(normalized)
            database.memoryDao().upsert(
                MemoryEntity(
                    id = existing?.id ?: portable.id.takeIf(SAFE_ID::matches) ?: UUID.randomUUID().toString(),
                    normalizedKey = normalized,
                    content = clean,
                    category = portable.category.take(40).ifBlank { "general" },
                    sourceConversationId = null,
                    enabled = portable.enabled,
                    createdAt = existing?.createdAt ?: portable.createdAt.takeIf { it > 0 } ?: now,
                    updatedAt = maxOf(existing?.updatedAt ?: 0L, portable.updatedAt.takeIf { it > 0 } ?: now),
                ),
            )
        }
    }

    private suspend fun restoreAutomation(value: PortableAutomationSettings) {
        database.automationSettingsDao().upsert(
            AutomationSettingsEntity(
                titleMode = value.titleMode.enumOr(AuxiliaryMode.LOCAL),
                titleProviderId = value.titleProviderId,
                titleModelId = value.titleModelId,
                compressionMode = value.compressionMode.enumOr(AuxiliaryMode.LOCAL),
                compressionProviderId = value.compressionProviderId,
                compressionModelId = value.compressionModelId,
                packageApprovalMode = value.packageApprovalMode.enumOr(PackageApprovalMode.ALWAYS_ASK),
                approvalProviderId = value.approvalProviderId,
                approvalModelId = value.approvalModelId,
                packageRestrictionsEnabled = value.packageRestrictionsEnabled,
                trustedPythonPackages = value.trustedPythonPackages.take(MAX_TRUST_LIST_CHARS),
                trustedUbuntuPackages = value.trustedUbuntuPackages.take(MAX_TRUST_LIST_CHARS),
                memoryEnabled = value.memoryEnabled,
                memoryAutoSave = value.memoryAutoSave,
            ),
        )
    }

    private fun restorePreferences(
        value: PortablePreferenceSettings,
        promptIds: Map<String, String>,
    ) {
        preferences.setThemeMode(value.themeMode.enumOr(ThemeMode.SYSTEM))
        preferences.setAppLanguage(value.appLanguage.enumOr(AppLanguage.SYSTEM))
        preferences.setPalette(value.palette.enumOr(ColorPalette.XYLUNE))
        preferences.setAmoled(value.amoled)
        preferences.setMatchLauncherIconToPalette(value.matchLauncherIconToPalette)
        preferences.setChromeBlurStrength(value.chromeBlurStrength)
        preferences.setChromeEdgeSoftness(value.chromeEdgeSoftness)
        preferences.setChromeOverlayOpacity(value.chromeOverlayOpacity)
        preferences.setGeneratedRepairMaxAttempts(value.generatedRepairMaxAttempts)
        preferences.setLessEmojiEnabled(value.lessEmojiEnabled)
        preferences.setAutomaticUpdateChecks(value.automaticUpdateChecks)
        val defaults = value.newChatDefaults
        preferences.setNewChatDefaults(
            NewChatDefaults(
                selectedProviderId = defaults.selectedProviderId,
                selectedModelId = defaults.selectedModelId,
                contextPairs = defaults.contextPairs,
                contextTokenLimit = defaults.contextTokenLimit,
                workingTokenLimit = defaults.workingTokenLimit,
                maxOutputTokens = defaults.maxOutputTokens,
                systemPromptProfileId = defaults.systemPromptProfileId?.let(promptIds::get),
                reasoningVisibility = defaults.reasoningVisibility.enumOr(ReasoningVisibility.SHOW_WHILE_WORKING),
                thinkingEnabled = defaults.thinkingEnabled,
                thinkingEffort = defaults.thinkingEffort.enumOr(ThinkingEffort.MEDIUM),
                webSearchEnabled = defaults.webSearchEnabled,
                agentPythonEnabled = defaults.agentPythonEnabled,
                agentUbuntuEnabled = defaults.agentUbuntuEnabled,
                deepResearchEnabled = defaults.deepResearchEnabled,
                hybridTokenCountingEnabled = defaults.hybridTokenCountingEnabled,
            ),
        )
        val developer = value.developerSettings
        preferences.setDeveloperSettings(
            DeveloperSettings(
                enabled = developer.enabled,
                toolDiagnosticsEnabled = developer.toolDiagnosticsEnabled,
                performanceOverlayEnabled = developer.performanceOverlayEnabled,
                diagnosticProfilerEnabled = developer.diagnosticProfilerEnabled,
                detailedPerformanceOverlay = developer.detailedPerformanceOverlay,
                performanceUpdateIntervalMs = developer.performanceUpdateIntervalMs,
                performanceOverlayPosition = developer.performanceOverlayPosition.enumOr(PerformanceOverlayPosition.TOP_END),
                performanceOverlayBackgroundOpacity = developer.performanceOverlayBackgroundOpacity,
                performanceOverlayTextOpacity = developer.performanceOverlayTextOpacity,
                performanceOverlayScale = developer.performanceOverlayScale,
                blurBoundaryDebugEnabled = developer.blurBoundaryDebugEnabled,
                blurBoundaryDebugThicknessDp = developer.blurBoundaryDebugThicknessDp,
            ),
        )
        val distribution = value.selectedLinuxDistribution.uppercase().takeIf { it in SUPPORTED_DISTRIBUTIONS }
        if (distribution != null) {
            context.getSharedPreferences(LINUX_PREFERENCES, Context.MODE_PRIVATE).edit(commit = true) {
                putString(KEY_DISTRIBUTION, distribution)
            }
        }
    }

    private inline fun <reified T : Enum<T>> String.enumOr(fallback: T): T =
        runCatching { enumValueOf<T>(this) }.getOrDefault(fallback)

    private companion object {
        const val LINUX_PREFERENCES = "xylune_linux_runtime"
        const val KEY_DISTRIBUTION = "selected_distribution"
        const val MAX_PROVIDERS = 100
        const val MAX_MODELS_PER_PROVIDER = 500
        const val MAX_PROJECTS = 500
        const val MAX_PROMPT_PROFILES = 500
        const val MAX_PROMPT_CHARS = 256_000
        const val MAX_TRUST_LIST_CHARS = 256_000
        val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,239}$")
        val SUPPORTED_DISTRIBUTIONS = setOf("UBUNTU", "DEBIAN", "ALPINE")
    }
}
