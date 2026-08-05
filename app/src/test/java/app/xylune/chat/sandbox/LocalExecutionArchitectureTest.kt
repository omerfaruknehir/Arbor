package app.xylune.chat.sandbox

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalExecutionArchitectureTest {
    @Test
    fun `bundled Python is independent from optional Linux`() {
        val viewModel = File("src/main/java/app/xylune/chat/ui/ChatViewModel.kt").readText()
        val agentTools = File("src/main/java/app/xylune/chat/agent/AgentTools.kt").readText()
        val context = File("src/main/java/app/xylune/chat/chat/ContextAssembler.kt").readText()

        assertTrue(viewModel.contains("container.pythonSandbox.execute"))
        assertTrue(viewModel.contains("container.pythonSandbox.preflight"))
        assertTrue(viewModel.contains("container.pythonSandbox.install"))
        assertFalse(viewModel.contains("container.ubuntuRuntime.executePython"))
        assertFalse(viewModel.contains("container.ubuntuRuntime.preflightPythonPackages"))
        assertTrue(agentTools.contains("python.executeFile"))
        assertTrue(context.contains("no Linux install required"))
    }

    @Test
    fun `fresh chats do not silently enable local execution`() {
        val preferences = File("src/main/java/app/xylune/chat/settings/AppPreferences.kt").readText()

        assertTrue(preferences.contains("val agentPythonEnabled: Boolean = false"))
        assertTrue(preferences.contains("getBoolean(KEY_DEFAULT_PYTHON, false)"))
    }
}
