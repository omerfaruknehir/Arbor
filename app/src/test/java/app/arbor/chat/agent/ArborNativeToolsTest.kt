package app.arbor.chat.agent

import app.arbor.chat.data.ConversationEntity
import app.arbor.chat.provider.NativeToolCall
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArborNativeToolsTest {
    @Test
    fun exposesOnlyEnabledToolsAndValidSchemas() {
        val definitions = ArborNativeTools.definitions(conversation(web = true, python = false, linux = true))

        assertEquals(
            listOf("compile_widget", "web_search", "web_fetch", "workspace_read", "apply_patch", "rerun_script", "linux_exec", "send_file"),
            definitions.map { it.name },
        )
        definitions.forEach { definition ->
            val schema = Json.parseToJsonElement(definition.parametersJson).jsonObject
            assertEquals("object", schema["type"]?.toString()?.trim('"'))
            assertTrue(schema.containsKey("properties"))
            assertEquals("false", schema["additionalProperties"].toString())
        }
        assertFalse(definitions.any { it.name == "python" })
    }

    @Test
    fun convertsStructuredCallsToExistingExecutionRequests() {
        val request = ArborNativeTools.request(
            NativeToolCall("call-1", "python", """{"code":"print(42)","timeoutSeconds":30}"""),
        )

        assertEquals("python", request.type)
        assertEquals("print(42)", request.code)
        assertEquals(30, request.timeoutSeconds)
    }

    @Test fun patchAndRerunCallsNeverRequireCompleteSource() {
        val patch = ArborNativeTools.request(NativeToolCall("call-2", "apply_patch", """{"path":".arbor/runs/run-12345678/main.py","unifiedDiff":"@@ -1 +1 @@\\n-a\\n+b","expectedSha256":"${"a".repeat(64)}"}"""))
        val rerun = ArborNativeTools.request(NativeToolCall("call-3", "rerun_script", """{"runId":"run-12345678"}"""))
        assertEquals(null, patch.code)
        assertEquals(null, rerun.code)
        assertEquals("run-12345678", rerun.runId)
    }


    @Test
    fun convertsWidgetCompilerCallToInternalSourceRequest() {
        val source = """{"schema":"arbor-widget/1","id":"counter"}"""
        val request = ArborNativeTools.request(
            NativeToolCall("call-widget", "compile_widget", """{"source":${Json.encodeToString(source)}}"""),
        )

        assertEquals("compile_widget", request.type)
        assertEquals(source, request.source)
        assertEquals(null, request.code)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsUnknownToolNames() {
        ArborNativeTools.request(NativeToolCall("call-1", "delete_everything", "{}"))
    }

    private fun conversation(web: Boolean, python: Boolean, linux: Boolean) = ConversationEntity(
        id = "c",
        title = "test",
        createdAt = 0,
        updatedAt = 0,
        webSearchEnabled = web,
        agentPythonEnabled = python,
        agentUbuntuEnabled = linux,
    )
}
