package app.arbor.chat.settings

import android.content.Context
import androidx.core.content.edit
import app.arbor.chat.data.ConversationEntity
import app.arbor.chat.data.ReasoningVisibility
import app.arbor.chat.data.ThinkingEffort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ColorPalette { ARBOR, SYSTEM, GRAPHITE }

const val ARBOR_CORE_PROMPT_REVISION = "0.16.16"

val DEFAULT_ARBOR_SYSTEM_PROMPT = """
You are Arbor, a capable assistant running inside a native Android BYOK workspace.

Be accurate, direct, and practical. Do not pretend to have used a tool, opened a file, checked the web, executed code, or created an artifact until Arbor returns the corresponding result. Distinguish verified facts from estimates and assumptions. For date-sensitive or current claims, use web search when it is enabled; otherwise state that you cannot verify freshness.

Use the user's language unless they request another. Preserve technical precision, explain consequential assumptions, and avoid unnecessary filler. Prefer concise structure for simple questions and fuller analysis for complex work.

Arbor may provide uploaded files, image/OCR content, web search and page fetching, persistent local code execution, an optional Linux tooling layer, generated files, native charts and diagrams, interactive chat UI, and Android Home-screen widgets. The runtime context supplied with each request is authoritative: use only capabilities marked enabled, follow their tool protocol exactly, and never infer access to disabled capabilities.

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
    val systemPrompt: String = DEFAULT_ARBOR_SYSTEM_PROMPT,
    val systemPromptProfileId: String? = null,
    val reasoningVisibility: ReasoningVisibility = ReasoningVisibility.SHOW_WHILE_WORKING,
    val thinkingEnabled: Boolean = true,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    val webSearchEnabled: Boolean = true,
    val agentPythonEnabled: Boolean = true,
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
        // The built-in Arbor core prompt is versioned with the app and is never
        // copied from editable or legacy per-chat text.
        systemPrompt = DEFAULT_ARBOR_SYSTEM_PROMPT,
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
            systemPrompt = DEFAULT_ARBOR_SYSTEM_PROMPT,
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
    private val preferences = context.getSharedPreferences("arbor_app_settings", Context.MODE_PRIVATE)
    private val _amoled = MutableStateFlow(preferences.getBoolean(KEY_AMOLED, false))
    private val _palette = MutableStateFlow(enumValue(KEY_PALETTE, ColorPalette.ARBOR))
    private val _themeMode = MutableStateFlow(enumValue(KEY_THEME_MODE, ThemeMode.SYSTEM))
    private val _chromeBlurEnabled = MutableStateFlow(preferences.getBoolean(KEY_CHROME_BLUR_ENABLED, true))
    private val _chromeBlurStrength = MutableStateFlow(preferences.getFloat(KEY_CHROME_BLUR_STRENGTH, 0.7f).coerceIn(0f, 1f))
    private val _newChatDefaults = MutableStateFlow(readNewChatDefaults())

    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()
    val palette: StateFlow<ColorPalette> = _palette.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    val chromeBlurEnabled: StateFlow<Boolean> = _chromeBlurEnabled.asStateFlow()
    val chromeBlurStrength: StateFlow<Float> = _chromeBlurStrength.asStateFlow()
    val newChatDefaults: StateFlow<NewChatDefaults> = _newChatDefaults.asStateFlow()
    val hasNewChatDefaults: Boolean get() = preferences.getBoolean(KEY_DEFAULTS_INITIALIZED, false)

    fun setAmoled(enabled: Boolean) {
        _amoled.value = enabled
        preferences.edit { putBoolean(KEY_AMOLED, enabled) }
    }

    fun setPalette(value: ColorPalette) {
        _palette.value = value
        preferences.edit { putString(KEY_PALETTE, value.name) }
    }

    fun setThemeMode(value: ThemeMode) {
        _themeMode.value = value
        preferences.edit { putString(KEY_THEME_MODE, value.name) }
    }

    fun setChromeBlurEnabled(enabled: Boolean) {
        _chromeBlurEnabled.value = enabled
        preferences.edit { putBoolean(KEY_CHROME_BLUR_ENABLED, enabled) }
    }

    fun setChromeBlurStrength(value: Float) {
        val normalized = value.coerceIn(0f, 1f)
        _chromeBlurStrength.value = normalized
        preferences.edit { putFloat(KEY_CHROME_BLUR_STRENGTH, normalized) }
    }

    fun setNewChatDefaults(value: NewChatDefaults) {
        val normalized = value.copy(
            contextPairs = value.contextPairs.coerceIn(1, 500),
            contextTokenLimit = value.contextTokenLimit.coerceIn(1_024, 2_000_000),
            workingTokenLimit = value.workingTokenLimit.coerceIn(0, 2_000_000),
            maxOutputTokens = value.maxOutputTokens.coerceIn(1, 384_000),
            systemPrompt = DEFAULT_ARBOR_SYSTEM_PROMPT,
        )
        _newChatDefaults.value = normalized
        preferences.edit {
            putString(KEY_DEFAULT_PROVIDER, normalized.selectedProviderId)
            putString(KEY_DEFAULT_MODEL, normalized.selectedModelId)
            putInt(KEY_DEFAULT_PAIRS, normalized.contextPairs)
            putInt(KEY_DEFAULT_CONTEXT_TOKENS, normalized.contextTokenLimit)
            putInt(KEY_DEFAULT_WORKING_TOKENS, normalized.workingTokenLimit)
            putInt(KEY_DEFAULT_OUTPUT_TOKENS, normalized.maxOutputTokens)
            // Old releases persisted an editable copy of Arbor's built-in prompt.
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

    private fun readNewChatDefaults() = NewChatDefaults(
        selectedProviderId = preferences.getString(KEY_DEFAULT_PROVIDER, null) ?: "deepseek",
        selectedModelId = preferences.getString(KEY_DEFAULT_MODEL, null) ?: "deepseek-v4-flash",
        contextPairs = preferences.getInt(KEY_DEFAULT_PAIRS, 24),
        contextTokenLimit = preferences.getInt(KEY_DEFAULT_CONTEXT_TOKENS, 64_000),
        workingTokenLimit = preferences.getInt(KEY_DEFAULT_WORKING_TOKENS, 16_000),
        maxOutputTokens = preferences.getInt(KEY_DEFAULT_OUTPUT_TOKENS, 8_192),
        systemPrompt = DEFAULT_ARBOR_SYSTEM_PROMPT,
        systemPromptProfileId = preferences.getString(KEY_DEFAULT_SYSTEM_PROMPT_PROFILE, null),
        reasoningVisibility = enumValue(KEY_DEFAULT_REASONING_VISIBILITY, ReasoningVisibility.SHOW_WHILE_WORKING),
        thinkingEnabled = preferences.getBoolean(KEY_DEFAULT_THINKING_ENABLED, true),
        thinkingEffort = enumValue(KEY_DEFAULT_THINKING_EFFORT, ThinkingEffort.MEDIUM),
        webSearchEnabled = preferences.getBoolean(KEY_DEFAULT_WEB, true),
        agentPythonEnabled = preferences.getBoolean(KEY_DEFAULT_PYTHON, true),
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
        const val KEY_CHROME_BLUR_ENABLED = "chrome_blur_enabled"
        const val KEY_CHROME_BLUR_STRENGTH = "chrome_blur_strength"
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
    }
}
