package app.arbor.chat.provider

import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ProviderKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCredentialPolicyTest {
    private val anthropic = ProviderEntity("anthropic", "Anthropic", ProviderKind.ANTHROPIC, "https://api.anthropic.com/v1")

    @Test fun remoteProviderRequiresKey() {
        assertFalse(ProviderCredentialPolicy.isUsable(anthropic, ""))
        assertTrue(ProviderCredentialPolicy.isUsable(anthropic, "configured"))
    }

    @Test fun disabledProviderIsNeverUsable() {
        assertFalse(ProviderCredentialPolicy.isUsable(anthropic.copy(enabled = false), "configured"))
    }

    @Test fun localOllamaMayBeKeyless() {
        val ollama = ProviderEntity("ollama", "Ollama", ProviderKind.OPENAI_COMPATIBLE, "http://127.0.0.1:11434/v1", registered = true, apiKeyRequired = false)
        assertTrue(ProviderCredentialPolicy.isUsable(ollama, ""))
    }

    @Test fun unregisteredKeylessTemplateDoesNotLeakIntoSelectors() {
        val template = ProviderEntity("ollama", "Ollama", ProviderKind.OPENAI_COMPATIBLE, "http://127.0.0.1:11434/v1", apiKeyRequired = false)
        assertFalse(ProviderCredentialPolicy.isUsable(template, ""))
    }
}
