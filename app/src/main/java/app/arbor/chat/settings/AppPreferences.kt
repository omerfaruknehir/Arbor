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
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class NewChatDefaults(
    val selectedProviderId: String = "deepseek",
    val selectedModelId: String = "deepseek-v4-flash",
    val contextPairs: Int = 24,
    val contextTokenLimit: Int = 64_000,
    val workingTokenLimit: Int = 16_000,
    val maxOutputTokens: Int = 8_192,
    val systemPrompt: String = "",
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
        systemPrompt = systemPrompt,
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
            systemPrompt = conversation.systemPrompt,
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
    private val _newChatDefaults = MutableStateFlow(readNewChatDefaults())

    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()
    val palette: StateFlow<ColorPalette> = _palette.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
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

    fun setNewChatDefaults(value: NewChatDefaults) {
        val normalized = value.copy(
            contextPairs = value.contextPairs.coerceIn(1, 500),
            contextTokenLimit = value.contextTokenLimit.coerceIn(1_024, 2_000_000),
            workingTokenLimit = value.workingTokenLimit.coerceIn(0, 2_000_000),
            maxOutputTokens = value.maxOutputTokens.coerceIn(1, 384_000),
        )
        _newChatDefaults.value = normalized
        preferences.edit {
            putString(KEY_DEFAULT_PROVIDER, normalized.selectedProviderId)
            putString(KEY_DEFAULT_MODEL, normalized.selectedModelId)
            putInt(KEY_DEFAULT_PAIRS, normalized.contextPairs)
            putInt(KEY_DEFAULT_CONTEXT_TOKENS, normalized.contextTokenLimit)
            putInt(KEY_DEFAULT_WORKING_TOKENS, normalized.workingTokenLimit)
            putInt(KEY_DEFAULT_OUTPUT_TOKENS, normalized.maxOutputTokens)
            putString(KEY_DEFAULT_SYSTEM_PROMPT, normalized.systemPrompt)
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
        systemPrompt = preferences.getString(KEY_DEFAULT_SYSTEM_PROMPT, null).orEmpty(),
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
        const val KEY_DEFAULT_PROVIDER = "new_chat_provider"
        const val KEY_DEFAULT_MODEL = "new_chat_model"
        const val KEY_DEFAULT_PAIRS = "new_chat_context_pairs"
        const val KEY_DEFAULT_CONTEXT_TOKENS = "new_chat_context_tokens"
        const val KEY_DEFAULT_WORKING_TOKENS = "new_chat_working_tokens"
        const val KEY_DEFAULT_OUTPUT_TOKENS = "new_chat_output_tokens"
        const val KEY_DEFAULT_SYSTEM_PROMPT = "new_chat_system_prompt"
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
