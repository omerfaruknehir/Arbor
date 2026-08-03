package app.xylune.chat.provider

import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiOAuthMultiAccountWiringTest {
    @Test
    fun oauthSessionsAndRequestsAreKeyedByProviderId() {
        val manager = java.io.File("src/main/java/app/xylune/chat/provider/OpenAiOAuthManager.kt").readText()
        val provider = java.io.File("src/main/java/app/xylune/chat/provider/OpenAiOAuthProvider.kt").readText()
        val secureStore = java.io.File("src/main/java/app/xylune/chat/security/SecureStore.kt").readText()
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()

        assertTrue(manager.contains("private val sessions"))
        assertTrue(manager.contains("accountStates"))
        assertTrue(manager.contains("validSession(providerId"))
        assertTrue(provider.contains("validSession(request.provider.id)"))
        assertTrue(provider.contains("modelInfo(request.provider.id"))
        assertTrue(secureStore.contains("openai_oauth_sessions_v2"))
        assertTrue(secureStore.contains("Map<String, OpenAiOAuthSecrets>"))
        assertTrue(settings.contains("Add ChatGPT provider"))
        assertTrue(settings.contains("Each provider keeps its OAuth session"))
    }

    @Test
    fun freshLoginPromptIsRequestedForAccountSwitching() {
        val manager = java.io.File("src/main/java/app/xylune/chat/provider/OpenAiOAuthManager.kt").readText()
        assertTrue(manager.contains(".appendQueryParameter(\"prompt\", \"login\")"))
    }
}
