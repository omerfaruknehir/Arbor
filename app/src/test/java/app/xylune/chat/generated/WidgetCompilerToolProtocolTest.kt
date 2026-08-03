package app.xylune.chat.generated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCompilerToolProtocolTest {
    @Test
    fun failedCompileReturnsStructuredRetryInstruction() {
        val result = WidgetCompilerToolProtocol.result(
            source = "{}",
            compilation = GeneratedCompilationResult(
                compiledSource = "{}",
                errors = listOf(GeneratedValidationError("schema", "/schema", "Expected xylune-widget/1")),
            ),
        )

        assertFalse(result.success)
        assertEquals(WidgetCompilerToolProtocol.RESULT_SCHEMA, result.schema)
        assertEquals("/schema", result.diagnostics.single().path)
        assertTrue(result.instruction.contains("call compile_widget again"))
        assertNull(result.compiledSource)
    }

    @Test
    fun itemShapeFailureExplainsChildrenVersusRecords() {
        val result = WidgetCompilerToolProtocol.result(
            source = "{}",
            compilation = GeneratedCompilationResult(
                compiledSource = "{}",
                errors = listOf(
                    GeneratedValidationError(
                        "schema",
                        "/ui/children/1/items/0",
                        "/ui/children/1/items/0 has unknown fields: text, type",
                    ),
                ),
            ),
        )

        assertTrue(result.instruction.contains("data records, never UI nodes"))
        assertTrue(result.instruction.contains("move nested nodes"))
        assertTrue(result.instruction.contains("label"))
    }

    @Test
    fun successfulCompileRequiresExactTestedSource() {
        val source = """{"schema":"xylune-widget/1"}"""
        val result = WidgetCompilerToolProtocol.result(
            source = source,
            compilation = GeneratedCompilationResult(source, emptyList()),
        )

        assertTrue(result.success)
        assertTrue(result.diagnostics.isEmpty())
        assertTrue(result.instruction.contains("exactly the source argument"))
        assertEquals(64, result.sourceSha256.length)
    }

    @Test
    fun normalizedCompileReturnsReplacementSource() {
        val result = WidgetCompilerToolProtocol.result(
            source = "draft",
            compilation = GeneratedCompilationResult("compiled", emptyList()),
        )

        assertTrue(result.success)
        assertEquals("compiled", result.compiledSource)
        assertTrue(result.instruction.contains("compiledSource"))
    }
}
