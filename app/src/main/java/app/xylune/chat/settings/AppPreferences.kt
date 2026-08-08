package app.xylune.chat.settings

import android.content.Context
import androidx.core.content.edit
import app.xylune.chat.data.ConversationEntity
import app.xylune.chat.data.ReasoningVisibility
import app.xylune.chat.data.ThinkingEffort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** First physical anchor: 0% softness with a fully rounded panel. */
internal const val CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT = 0f

/**
 * Second physical anchor: still 0% softness, but with a fully flat panel.
 *
 * The value is an internal control position, not a displayed softness value.
 * Keeping the anchors separated gives the user a continuous, non-snapping
 * rounded-to-flat geometry lane while both endpoints remain semantically 0%.
 */
internal const val CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT = 0.20f

/** Persistence remains continuous; snapping is performed only by the slider on release. */
internal fun snapChromeEdgeSoftness(value: Float): Float = value.coerceIn(0f, 1f)

/**
 * 0..FLAT is reserved for the rounded-to-flat geometry transition. The actual
 * edge feather starts at the second anchor and then spans the rest of the slider.
 */
internal fun effectiveChromeEdgeSoftness(value: Float): Float {
    val normalized = snapChromeEdgeSoftness(value)
    if (normalized <= CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT) return 0f
    return ((normalized - CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT) /
        (1f - CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT)).coerceIn(0f, 1f)
}

/** Percentage shown to the user. Both geometry anchors intentionally read 0%. */
internal fun displayedChromeEdgeSoftness(value: Float): Float =
    effectiveChromeEdgeSoftness(value)

/** Maps a semantic 0..100% feather value into the post-flat control lane. */
internal fun chromeEdgeControlPositionForSoftness(softness: Float): Float {
    val normalized = softness.coerceIn(0f, 1f)
    if (normalized <= 0f) return CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT
    return CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT +
        normalized * (1f - CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT)
}

/** Smooth rounded-to-flat geometry transition between the two snap anchors. */
internal fun chromeEdgeCornerTransition(value: Float): Float {
    val x = (snapChromeEdgeSoftness(value) / CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT)
        .coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

enum class ColorPalette { XYLUNE, SYSTEM, GRAPHITE, OCEAN, VIOLET, SUNSET }

enum class PerformanceOverlayPosition { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

data class DeveloperSettings(
    val enabled: Boolean = false,
    val toolDiagnosticsEnabled: Boolean = false,
    val performanceOverlayEnabled: Boolean = false,
    val diagnosticProfilerEnabled: Boolean = false,
    val detailedPerformanceOverlay: Boolean = true,
    val performanceUpdateIntervalMs: Int = 500,
    val performanceOverlayPosition: PerformanceOverlayPosition = PerformanceOverlayPosition.TOP_END,
    val performanceOverlayBackgroundOpacity: Float = 0.86f,
    val performanceOverlayTextOpacity: Float = 1f,
    val performanceOverlayScale: Float = 1f,
    val blurBoundaryDebugEnabled: Boolean = false,
    val blurBoundaryDebugThicknessDp: Float = 3f,
) {
    fun normalized() = copy(
        performanceUpdateIntervalMs = performanceUpdateIntervalMs.coerceIn(250, 2_000),
        performanceOverlayBackgroundOpacity = performanceOverlayBackgroundOpacity.coerceIn(0f, 1f),
        performanceOverlayTextOpacity = performanceOverlayTextOpacity.coerceIn(0f, 1f),
        performanceOverlayScale = performanceOverlayScale.coerceIn(0.60f, 2.00f),
        blurBoundaryDebugThicknessDp = blurBoundaryDebugThicknessDp.coerceIn(1f, 8f),
    )
}

const val XYLUNE_CORE_PROMPT_REVISION = "0.24.0"

val DEFAULT_XYLUNE_SYSTEM_PROMPT = """
You are Xylune, a capable assistant running inside a native Android BYOK workspace.

Be accurate, direct, and practical. Do not pretend to have used a tool, opened a file, checked the web, executed code, or created an artifact until Xylune returns the corresponding result. Distinguish verified facts from estimates and assumptions. For date-sensitive or current claims, use web search when it is enabled; otherwise state that you cannot verify freshness.

Use the user's language unless they request another. Preserve technical precision, explain consequential assumptions, and avoid unnecessary filler. Prefer concise structure for simple questions and fuller analysis for complex work.

Xylune may provide uploaded files, image/OCR content, web search and page fetching, persistent local code execution, an optional Linux tooling layer, generated files, native charts and diagrams, interactive chat UI, and Android Home-screen widgets. The runtime context supplied with each request is authoritative: use only capabilities marked enabled, follow their tool protocol exactly, and never infer access to disabled capabilities.

When creating files or structured outputs, make them usable and complete. When research is requested, verify sources, compare conflicting evidence, cite the material actually used, and report limitations rather than inventing support.
""".trimIndent()
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class NewChatDefaults(
    val selectedProviderId: String = "deepseek",
    val selectedModelId: String = "deepseek-v4-flash",
    val contextPairs: Int = 24,
    val contextTokenLimit: Int = 64_000,
    val workingTokenLimit: Int = 16_000,
    val maxOutputTokens: Int = 8_192,
    val systemPrompt: String = DEFAULT_XYLUNE_SYSTEM_PROMPT,
    val systemPromptProfileId: String? = null,
    val reasoningVisibility: ReasoningVisibility = ReasoningVisibility.SHOW_WHILE_WORKING,
    val thinkingEnabled: Boolean = true,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    val webSearchEnabled: Boolean = true,
    val agentPythonEnabled: Boolean = false,
    val agentUbuntuEnabled: Boolean = false,
    val deepResearchEnabled: Boolean = false,
    val hybridTokenCountingEnabled: Boolean = false,
) {
    fun applyTo(conversation: ConversationEntity): ConversationEntity = conversation.copy(
        selectedProviderId = selectedProviderId,
        selectedModelId = selectedModelId,
        contextPairs = contextPairs,
        contextTokenLimit = contextTokenLimit,
        workingTokenLimit = workingTokenLimit,
        maxOutputTokens = maxOutputTokens,
        // The built-in Xylune core prompt is versioned with the app and is never
        // copied from editable or legacy per-chat text.
        systemPrompt = DEFAULT_XYLUNE_SYSTEM_PROMPT,
        systemPromptProfileId = systemPromptProfileId,
        reasoningVisibility = reasoningVisibility,
        thinkingEnabled = thinkingEnabled,
        thinkingEffort = thinkingEffort,
        webSearchEnabled = webSearchEnabled,
        agentPythonEnabled = agentPythonEnabled,
        agentUbuntuEnabled = agentUbuntuEnabled,
        deepResearchEnabled = deepResearchEnabled,
        hybridTokenCountingEnabled = hybridTokenCountingEnabled,
    )

    companion object {
        fun from(conversation: ConversationEntity) = NewChatDefaults(
            selectedProviderId = conversation.selectedProviderId,
            selectedModelId = conversation.selectedModelId,
            contextPairs = conversation.contextPairs,
            contextTokenLimit = conversation.contextTokenLimit,
            workingTokenLimit = conversation.workingTokenLimit,
            maxOutputTokens = conversation.maxOutputTokens,
            systemPrompt = DEFAULT_XYLUNE_SYSTEM_PROMPT,
            systemPromptProfileId = conversation.systemPromptProfileId,
            reasoningVisibility = conversation.reasoningVisibility,
            thinkingEnabled = conversation.thinkingEnabled,
            thinkingEffort = conversation.thinkingEffort,
            webSearchEnabled = conversation.webSearchEnabled,
            agentPythonEnabled = conversation.agentPythonEnabled,
            agentUbuntuEnabled = conversation.agentUbuntuEnabled,
            deepResearchEnabled = conversation.deepResearchEnabled,
            hybridTokenCountingEnabled = conversation.hybridTokenCountingEnabled,
        )
    }
}

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(XYLUNE_APP_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
    private val _amoled = MutableStateFlow(preferences.getBoolean(KEY_AMOLED, false))
    private val _palette = MutableStateFlow(enumValue(KEY_PALETTE, ColorPalette.XYLUNE))
    private val _themeMode = MutableStateFlow(enumValue(KEY_THEME_MODE, ThemeMode.SYSTEM))
    private val _appLanguage = MutableStateFlow(enumValue(KEY_APP_LANGUAGE, AppLanguage.SYSTEM))
    private val _matchLauncherIconToPalette = MutableStateFlow(
        preferences.getBoolean(KEY_MATCH_LAUNCHER_ICON_TO_PALETTE, false),
    )
    private val _chromeBlurStrength = MutableStateFlow(readChromeBlurStrength())
    private val _chromeEdgeSoftness = MutableStateFlow(readChromeEdgeSoftness())
    private val _chromeOverlayOpacity = MutableStateFlow(preferences.getFloat(KEY_CHROME_OVERLAY_OPACITY, 1f).coerceIn(0f, 1f))
    private val _lessEmojiEnabled = MutableStateFlow(preferences.getBoolean(KEY_LESS_EMOJI_ENABLED, true))
    private val _automaticUpdateChecks = MutableStateFlow(preferences.getBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, true))
    private val _webSearchSettings = MutableStateFlow(readWebSearchSettings())
    private val _newChatDefaults = MutableStateFlow(readNewChatDefaults())
    private val _generatedRepairMaxAttempts = MutableStateFlow(preferences.getInt(KEY_GENERATED_REPAIR_ATTEMPTS, 3).coerceIn(1, 5))
    private val _developerSettings = MutableStateFlow(readDeveloperSettings())
    private val _favoriteModels = MutableStateFlow(
        preferences.getStringSet(KEY_FAVORITE_MODELS, emptySet()).orEmpty().toSet(),
    )
    private val _recentModels = MutableStateFlow(
        preferences.getString(KEY_RECENT_MODELS, "").orEmpty().lineSequence()
            .map(String::trim).filter(String::isNotBlank).distinct().take(MAX_RECENT_MODELS).toList(),
    )

    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()
    val palette: StateFlow<ColorPalette> = _palette.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()
    val matchLauncherIconToPalette: StateFlow<Boolean> = _matchLauncherIconToPalette.asStateFlow()
    val chromeBlurStrength: StateFlow<Float> = _chromeBlurStrength.asStateFlow()
    val chromeEdgeSoftness: StateFlow<Float> = _chromeEdgeSoftness.asStateFlow()
    val chromeOverlayOpacity: StateFlow<Float> = _chromeOverlayOpacity.asStateFlow()
    val lessEmojiEnabled: StateFlow<Boolean> = _lessEmojiEnabled.asStateFlow()
    val automaticUpdateChecks: StateFlow<Boolean> = _automaticUpdateChecks.asStateFlow()
    val webSearchSettings: StateFlow<WebSearchSettings> = _webSearchSettings.asStateFlow()
    val newChatDefaults: StateFlow<NewChatDefaults> = _newChatDefaults.asStateFlow()
    val generatedRepairMaxAttempts: StateFlow<Int> = _generatedRepairMaxAttempts.asStateFlow()
    val developerSettings: StateFlow<DeveloperSettings> = _developerSettings.asStateFlow()
    val favoriteModels: StateFlow<Set<String>> = _favoriteModels.asStateFlow()
    val recentModels: StateFlow<List<String>> = _recentModels.asStateFlow()
    val hasNewChatDefaults: Boolean get() = preferences.getBoolean(KEY_DEFAULTS_INITIALIZED, false)

    private fun readChromeBlurStrength():  Float {
        val saved = preferences.getFloat(KEY_CHROME_BLUR_STRENGTH, 0.7f).coerceIn(0f, 1f)
        if (!preferences.contains(KEY_CHROME_BLUR_ENABLED)) return saved
        val migrated = if (preferences.getBoolean(KEY_CHROME_BLUR_ENABLED, true)) saved else 0f
        preferences.edit {
            remove(KEY_CHROME_BLUR_ENABLED)
            putFloat(KEY_CHROME_BLUR_STRENGTH, migrated)
        }
        return migrated
    }

    private fun readChromeEdgeSoftness(): Float {
        if (preferences.contains(KEY_CHROME_EDGE_SOFTNESS)) {
            val saved = preferences.getFloat(KEY_CHROME_EDGE_SOFTNESS, DEFAULT_CHROME_EDGE_SOFTNESS)
                .coerceIn(0f, 1f)
            if (preferences.getInt(KEY_CHROME_EDGE_CONTROL_REVISION, 1) >= CHROME_EDGE_CONTROL_REVISION) {
                return snapChromeEdgeSoftness(saved)
            }

            // 0.19.8 and older stored the whole slider as actual softness:
            // zero meant rounded, every nonzero value meant flat + feather.
            // Preserve that visual meaning while moving the feather range after
            // the new second 0% anchor.
            val migrated = if (saved <= 0f) {
                CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT
            } else {
                chromeEdgeControlPositionForSoftness(saved)
            }
            preferences.edit {
                putFloat(KEY_CHROME_EDGE_SOFTNESS, migrated)
                putInt(KEY_CHROME_EDGE_CONTROL_REVISION, CHROME_EDGE_CONTROL_REVISION)
            }
            return migrated
        }
        val migrated = if (preferences.getBoolean(KEY_CHROME_GRADUAL_ENABLED, true)) {
            DEFAULT_CHROME_EDGE_SOFTNESS
        } else {
            CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT
        }
        preferences.edit {
            remove(KEY_CHROME_GRADUAL_ENABLED)
            putFloat(KEY_CHROME_EDGE_SOFTNESS, migrated)
            putInt(KEY_CHROME_EDGE_CONTROL_REVISION, CHROME_EDGE_CONTROL_REVISION)
        }
        return migrated
    }

    fun setAmoled(enabled: Boolean) {
        _amoled.value = enabled
        preferences.edit { putBoolean(KEY_AMOLED, enabled) }
    }

    fun setPalette(value: ColorPalette) {
        _palette.value = value
        preferences.edit { putString(KEY_PALETTE, value.name) }
    }

    fun setMatchLauncherIconToPalette(enabled: Boolean) {
        _matchLauncherIconToPalette.value = enabled
        preferences.edit { putBoolean(KEY_MATCH_LAUNCHER_ICON_TO_PALETTE, enabled) }
    }

    fun setThemeMode(value: ThemeMode) {
        _themeMode.value = value
        preferences.edit { putString(KEY_THEME_MODE, value.name) }
    }

    fun setAppLanguage(value: AppLanguage) {
        _appLanguage.value = value
        preferences.edit { putString(KEY_APP_LANGUAGE, value.name) }
    }


    fun setChromeBlurStrength(value: Float) {
        val normalized = value.coerceIn(0f, 1f)
        _chromeBlurStrength.value = normalized
        preferences.edit { putFloat(KEY_CHROME_BLUR_STRENGTH, normalized) }
    }

    fun setChromeEdgeSoftness(value: Float) {
        val normalized = snapChromeEdgeSoftness(value)
        _chromeEdgeSoftness.value = normalized
        preferences.edit { putFloat(KEY_CHROME_EDGE_SOFTNESS, normalized) }
    }

    fun setChromeOverlayOpacity(value: Float) {
        val normalized = value.coerceIn(0f, 1f)
        _chromeOverlayOpacity.value = normalized
        preferences.edit { putFloat(KEY_CHROME_OVERLAY_OPACITY, normalized) }
    }

    fun setLessEmojiEnabled(enabled: Boolean) {
        _lessEmojiEnabled.value = enabled
        preferences.edit { putBoolean(KEY_LESS_EMOJI_ENABLED, enabled) }
    }

    fun setAutomaticUpdateChecks(enabled: Boolean) {
        _automaticUpdateChecks.value = enabled
        preferences.edit { putBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, enabled) }
    }

    fun setWebSearchSettings(value: WebSearchSettings) {
        val normalized = value.normalized()
        _webSearchSettings.value = normalized
        preferences.edit {
            putString(KEY_WEB_SEARCH_ROUTE, normalized.route.name)
            putString(KEY_WEB_SEARCH_ENGINE, normalized.engine.name)
            putInt(KEY_WEB_SEARCH_MAX_RESULTS, normalized.maxResults)
            putBoolean(KEY_WEB_FETCH_ENABLED, normalized.pageFetchEnabled)
            putString(KEY_SEARXNG_ENDPOINT, normalized.searxngEndpoint)
        }
    }

    fun updateWebSearchSettings(transform: (WebSearchSettings) -> WebSearchSettings) =
        setWebSearchSettings(transform(_webSearchSettings.value))

    fun setGeneratedRepairMaxAttempts(value: Int) {
        val normalized = value.coerceIn(1, 5)
        _generatedRepairMaxAttempts.value = normalized
        preferences.edit { putInt(KEY_GENERATED_REPAIR_ATTEMPTS, normalized) }
    }

    fun toggleFavoriteModel(providerId: String, modelId: String) {
        val key = modelPreferenceKey(providerId, modelId)
        val updated = _favoriteModels.value.toMutableSet().apply {
            if (!add(key)) remove(key)
        }.toSet()
        _favoriteModels.value = updated
        preferences.edit { putStringSet(KEY_FAVORITE_MODELS, updated) }
    }

    fun recordRecentModel(providerId: String, modelId: String) {
        val key = modelPreferenceKey(providerId, modelId)
        val updated = (listOf(key) + _recentModels.value.filterNot { it == key }).take(MAX_RECENT_MODELS)
        _recentModels.value = updated
        preferences.edit { putString(KEY_RECENT_MODELS, updated.joinToString("\n")) }
    }

    fun setDeveloperSettings(value: DeveloperSettings) {
        val normalized = value.normalized()
        _developerSettings.value = normalized
        preferences.edit {
            putBoolean(KEY_DEVELOPER_ENABLED, normalized.enabled)
            putBoolean(KEY_TOOL_DIAGNOSTICS_ENABLED, normalized.toolDiagnosticsEnabled)
            putBoolean(KEY_PERFORMANCE_OVERLAY_ENABLED, normalized.performanceOverlayEnabled)
            putBoolean(KEY_DIAGNOSTIC_PROFILER_ENABLED, normalized.diagnosticProfilerEnabled)
            putBoolean(KEY_PERFORMANCE_OVERLAY_DETAILED, normalized.detailedPerformanceOverlay)
            putInt(KEY_PERFORMANCE_UPDATE_INTERVAL_MS, normalized.performanceUpdateIntervalMs)
            putString(KEY_PERFORMANCE_OVERLAY_POSITION, normalized.performanceOverlayPosition.name)
            putFloat(KEY_PERFORMANCE_OVERLAY_BACKGROUND_OPACITY, normalized.performanceOverlayBackgroundOpacity)
            putFloat(KEY_PERFORMANCE_OVERLAY_TEXT_OPACITY, normalized.performanceOverlayTextOpacity)
            putFloat(KEY_PERFORMANCE_OVERLAY_SCALE, normalized.performanceOverlayScale)
            putBoolean(KEY_BLUR_BOUNDARY_DEBUG_ENABLED, normalized.blurBoundaryDebugEnabled)
            putFloat(KEY_BLUR_BOUNDARY_DEBUG_THICKNESS_DP, normalized.blurBoundaryDebugThicknessDp)
        }
    }

    fun updateDeveloperSettings(transform: (DeveloperSettings) -> DeveloperSettings) =
        setDeveloperSettings(transform(_developerSettings.value))

    fun setNewChatDefaults(value: NewChatDefaults) {
        val normalized = value.copy(
            contextPairs = value.contextPairs.coerceIn(1, 500),
            contextTokenLimit = value.contextTokenLimit.coerceIn(1_024, 2_000_000),
            workingTokenLimit = value.workingTokenLimit.coerceIn(0, 2_000_000),
            maxOutputTokens = value.maxOutputTokens.coerceIn(1, 384_000),
            systemPrompt = DEFAULT_XYLUNE_SYSTEM_PROMPT,
        )
        _newChatDefaults.value = normalized
        preferences.edit {
            putString(KEY_DEFAULT_PROVIDER, normalized.selectedProviderId)
            putString(KEY_DEFAULT_MODEL, normalized.selectedModelId)
            putInt(KEY_DEFAULT_PAIRS, normalized.contextPairs)
            putInt(KEY_DEFAULT_CONTEXT_TOKENS, normalized.contextTokenLimit)
            putInt(KEY_DEFAULT_WORKING_TOKENS, normalized.workingTokenLimit)
            putInt(KEY_DEFAULT_OUTPUT_TOKENS, normalized.maxOutputTokens)
            // Old releases persisted an editable copy of Xylune's built-in prompt.
            // Remove it so app updates always supply the current core prompt.
            remove(KEY_DEFAULT_SYSTEM_PROMPT)
            putString(KEY_DEFAULT_SYSTEM_PROMPT_PROFILE, normalized.systemPromptProfileId)
            putString(KEY_DEFAULT_REASONING_VISIBILITY, normalized.reasoningVisibility.name)
            putBoolean(KEY_DEFAULT_THINKING_ENABLED, normalized.thinkingEnabled)
            putString(KEY_DEFAULT_THINKING_EFFORT, normalized.thinkingEffort.name)
            putBoolean(KEY_DEFAULT_WEB, normalized.webSearchEnabled)
            putBoolean(KEY_DEFAULT_PYTHON, normalized.agentPythonEnabled)
            putBoolean(KEY_DEFAULT_LINUX, normalized.agentUbuntuEnabled)
            putBoolean(KEY_DEFAULT_DEEP_RESEARCH, normalized.deepResearchEnabled)
            putBoolean(KEY_DEFAULT_HYBRID_COUNTING, normalized.hybridTokenCountingEnabled)
            putBoolean(KEY_DEFAULTS_INITIALIZED, true)
        }
    }

    fun updateNewChatDefaults(transform: (NewChatDefaults) -> NewChatDefaults) =
        setNewChatDefaults(transform(_newChatDefaults.value))

    private fun readWebSearchSettings() = WebSearchSettings(
        route = enumValue(KEY_WEB_SEARCH_ROUTE, WebSearchRoute.AUTO),
        engine = enumValue(KEY_WEB_SEARCH_ENGINE, WebSearchEngine.DUCKDUCKGO),
        maxResults = preferences.getInt(KEY_WEB_SEARCH_MAX_RESULTS, 8),
        pageFetchEnabled = preferences.getBoolean(KEY_WEB_FETCH_ENABLED, true),
        searxngEndpoint = preferences.getString(KEY_SEARXNG_ENDPOINT, "").orEmpty(),
    ).normalized()

    private fun readDeveloperSettings() = DeveloperSettings(
        enabled = preferences.getBoolean(KEY_DEVELOPER_ENABLED, false),
        toolDiagnosticsEnabled = preferences.getBoolean(KEY_TOOL_DIAGNOSTICS_ENABLED, false),
        performanceOverlayEnabled = preferences.getBoolean(KEY_PERFORMANCE_OVERLAY_ENABLED, false),
        diagnosticProfilerEnabled = preferences.getBoolean(KEY_DIAGNOSTIC_PROFILER_ENABLED, false),
        detailedPerformanceOverlay = preferences.getBoolean(KEY_PERFORMANCE_OVERLAY_DETAILED, true),
        performanceUpdateIntervalMs = preferences.getInt(KEY_PERFORMANCE_UPDATE_INTERVAL_MS, 500),
        performanceOverlayPosition = enumValue(KEY_PERFORMANCE_OVERLAY_POSITION, PerformanceOverlayPosition.TOP_END),
        performanceOverlayBackgroundOpacity = preferences.getFloat(KEY_PERFORMANCE_OVERLAY_BACKGROUND_OPACITY, 0.86f),
        performanceOverlayTextOpacity = preferences.getFloat(KEY_PERFORMANCE_OVERLAY_TEXT_OPACITY, 1f),
        performanceOverlayScale = preferences.getFloat(KEY_PERFORMANCE_OVERLAY_SCALE, 1f),
        blurBoundaryDebugEnabled = preferences.getBoolean(KEY_BLUR_BOUNDARY_DEBUG_ENABLED, false),
        blurBoundaryDebugThicknessDp = preferences.getFloat(KEY_BLUR_BOUNDARY_DEBUG_THICKNESS_DP, 3f),
    ).normalized()

    private fun readNewChatDefaults() = NewChatDefaults(
        selectedProviderId = preferences.getString(KEY_DEFAULT_PROVIDER, null) ?: "deepseek",
        selectedModelId = preferences.getString(KEY_DEFAULT_MODEL, null) ?: "deepseek-v4-flash",
        contextPairs = preferences.getInt(KEY_DEFAULT_PAIRS, 24),
        contextTokenLimit = preferences.getInt(KEY_DEFAULT_CONTEXT_TOKENS, 64_000),
        workingTokenLimit = preferences.getInt(KEY_DEFAULT_WORKING_TOKENS, 16_000),
        maxOutputTokens = preferences.getInt(KEY_DEFAULT_OUTPUT_TOKENS, 8_192),
        systemPrompt = DEFAULT_XYLUNE_SYSTEM_PROMPT,
        systemPromptProfileId = preferences.getString(KEY_DEFAULT_SYSTEM_PROMPT_PROFILE, null),
        reasoningVisibility = enumValue(KEY_DEFAULT_REASONING_VISIBILITY, ReasoningVisibility.SHOW_WHILE_WORKING),
        thinkingEnabled = preferences.getBoolean(KEY_DEFAULT_THINKING_ENABLED, true),
        thinkingEffort = enumValue(KEY_DEFAULT_THINKING_EFFORT, ThinkingEffort.MEDIUM),
        webSearchEnabled = preferences.getBoolean(KEY_DEFAULT_WEB, true),
        agentPythonEnabled = preferences.getBoolean(KEY_DEFAULT_PYTHON, false),
        agentUbuntuEnabled = preferences.getBoolean(KEY_DEFAULT_LINUX, false),
        deepResearchEnabled = preferences.getBoolean(KEY_DEFAULT_DEEP_RESEARCH, false),
        hybridTokenCountingEnabled = preferences.getBoolean(KEY_DEFAULT_HYBRID_COUNTING, false),
    )

    private inline fun <reified T : Enum<T>> enumValue(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(preferences.getString(key, null) ?: fallback.name) }.getOrDefault(fallback)

    private companion object {
        const val KEY_AMOLED = "amoled_black"
        const val KEY_PALETTE = "color_palette"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_MATCH_LAUNCHER_ICON_TO_PALETTE = "match_launcher_icon_to_palette"
        const val KEY_CHROME_BLUR_ENABLED = "chrome_blur_enabled" // legacy migration only
        const val KEY_CHROME_GRADUAL_ENABLED = "chrome_gradual_enabled" // legacy migration only
        const val KEY_CHROME_BLUR_STRENGTH = "chrome_blur_strength"
        const val KEY_CHROME_EDGE_SOFTNESS = "chrome_edge_softness"
        const val KEY_CHROME_EDGE_CONTROL_REVISION = "chrome_edge_control_revision"
        const val KEY_CHROME_OVERLAY_OPACITY = "chrome_overlay_opacity"
        const val KEY_LESS_EMOJI_ENABLED = "less_emoji_enabled"
        const val KEY_AUTOMATIC_UPDATE_CHECKS = "automatic_update_checks"
        const val KEY_WEB_SEARCH_ROUTE = "web_search_route"
        const val KEY_WEB_SEARCH_ENGINE = "web_search_engine"
        const val KEY_WEB_SEARCH_MAX_RESULTS = "web_search_max_results"
        const val KEY_WEB_FETCH_ENABLED = "web_fetch_enabled"
        const val KEY_SEARXNG_ENDPOINT = "searxng_endpoint"
        const val CHROME_EDGE_CONTROL_REVISION = 2
        const val DEFAULT_CHROME_EDGE_SOFTNESS = 0.6f // 50% semantic feather after the flat 0% anchor.
        const val KEY_DEFAULT_PROVIDER = "new_chat_provider"
        const val KEY_DEFAULT_MODEL = "new_chat_model"
        const val KEY_DEFAULT_PAIRS = "new_chat_context_pairs"
        const val KEY_DEFAULT_CONTEXT_TOKENS = "new_chat_context_tokens"
        const val KEY_DEFAULT_WORKING_TOKENS = "new_chat_working_tokens"
        const val KEY_DEFAULT_OUTPUT_TOKENS = "new_chat_output_tokens"
        const val KEY_DEFAULT_SYSTEM_PROMPT = "new_chat_system_prompt"
        const val KEY_DEFAULT_SYSTEM_PROMPT_PROFILE = "new_chat_system_prompt_profile"
        const val KEY_DEFAULT_REASONING_VISIBILITY = "new_chat_reasoning_visibility"
        const val KEY_DEFAULT_THINKING_ENABLED = "new_chat_thinking_enabled"
        const val KEY_DEFAULT_THINKING_EFFORT = "new_chat_thinking_effort"
        const val KEY_DEFAULT_WEB = "new_chat_web"
        const val KEY_DEFAULT_PYTHON = "new_chat_python"
        const val KEY_DEFAULT_LINUX = "new_chat_linux"
        const val KEY_DEFAULT_DEEP_RESEARCH = "new_chat_deep_research"
        const val KEY_DEFAULT_HYBRID_COUNTING = "new_chat_hybrid_counting"
        const val KEY_DEFAULTS_INITIALIZED = "new_chat_defaults_initialized"
        const val KEY_GENERATED_REPAIR_ATTEMPTS = "generated_repair_max_attempts"
        const val KEY_DEVELOPER_ENABLED = "developer_settings_enabled"
        const val KEY_TOOL_DIAGNOSTICS_ENABLED = "tool_diagnostics_enabled"
        const val KEY_PERFORMANCE_OVERLAY_ENABLED = "performance_overlay_enabled"
        const val KEY_DIAGNOSTIC_PROFILER_ENABLED = "diagnostic_profiler_enabled"
        const val KEY_PERFORMANCE_OVERLAY_DETAILED = "performance_overlay_detailed"
        const val KEY_PERFORMANCE_UPDATE_INTERVAL_MS = "performance_update_interval_ms"
        const val KEY_PERFORMANCE_OVERLAY_POSITION = "performance_overlay_position"
        const val KEY_PERFORMANCE_OVERLAY_BACKGROUND_OPACITY = "performance_overlay_background_opacity"
        const val KEY_PERFORMANCE_OVERLAY_TEXT_OPACITY = "performance_overlay_text_opacity"
        const val KEY_PERFORMANCE_OVERLAY_SCALE = "performance_overlay_scale"
        const val KEY_BLUR_BOUNDARY_DEBUG_ENABLED = "blur_boundary_debug_enabled"
        const val KEY_BLUR_BOUNDARY_DEBUG_THICKNESS_DP = "blur_boundary_debug_thickness_dp"
        const val KEY_FAVORITE_MODELS = "favorite_models"
        const val KEY_RECENT_MODELS = "recent_models"
        const val MAX_RECENT_MODELS = 12
    }
}

fun modelPreferenceKey(providerId: String, modelId: String): String = "$providerId::$modelId"
