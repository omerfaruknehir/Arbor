package app.arbor.chat.generated

import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class WidgetCompilerToolResult(
    val schema: String = WidgetCompilerToolProtocol.RESULT_SCHEMA,
    val success: Boolean,
    val contractVersion: String,
    val sourceSha256: String,
    val compiledSource: String? = null,
    val diagnostics: List<GeneratedValidationError> = emptyList(),
    val omittedDiagnosticCount: Int = 0,
    val instruction: String,
)

/** Converts compiler output into a bounded, model-readable native-tool result. */
object WidgetCompilerToolProtocol {
    const val RESULT_SCHEMA = "arbor-widget-compiler-result/1"
    private const val MAX_DIAGNOSTICS = 24

    fun result(source: String, compilation: GeneratedCompilationResult): WidgetCompilerToolResult {
        val visible = compilation.errors.take(MAX_DIAGNOSTICS)
        val omitted = (compilation.errors.size - visible.size).coerceAtLeast(0)
        val rewritten = compilation.compiledSource.takeIf { compilation.valid && it != source }
        return WidgetCompilerToolResult(
            success = compilation.valid,
            contractVersion = GeneratedContentCapabilityRegistry.CONTRACT_VERSION,
            sourceSha256 = sha256(source),
            compiledSource = rewritten,
            diagnostics = visible,
            omittedDiagnosticCount = omitted,
            instruction = when {
                compilation.valid && rewritten != null ->
                    "Compilation passed. Emit exactly compiledSource, unchanged, inside one arbor-widget fence. Do not regenerate or edit it after this successful call."
                compilation.valid ->
                    "Compilation passed. Emit exactly the source argument from this successful call, unchanged, inside one arbor-widget fence. Do not regenerate or edit it after this successful call."
                else ->
                    "Compilation failed. Do not emit an arbor-widget block yet. Fix every diagnostic in the complete candidate and call compile_widget again."
            },
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
