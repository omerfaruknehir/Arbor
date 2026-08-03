package app.xylune.chat.generated

import android.content.Context
import app.xylune.chat.widgets.WidgetProgramCompiler
import app.xylune.chat.widgets.WidgetSourceNormalizer

/** Result of turning generated source into a tested runtime artifact. */
data class GeneratedCompilationResult(
    val compiledSource: String,
    val errors: List<GeneratedValidationError>,
) {
    val valid: Boolean get() = errors.isEmpty()
}

/**
 * Compiles generated content before it is exposed as usable UI.
 *
 * Widgets receive the full pipeline: typed parse, action execution, live-data preflight,
 * and representative launcher rendering. Other generated surfaces currently use their
 * authoritative parser/validator as their compiler.
 */
class GeneratedBlockCompiler(private val context: Context) {
    suspend fun compile(type: GeneratedBlockType, source: String): GeneratedCompilationResult {
        val canonicalSource = if (type == GeneratedBlockType.HOME_WIDGET) {
            WidgetSourceNormalizer.normalize(source)
        } else {
            source
        }
        val validation = GeneratedContentCapabilityRegistry.validate(type, canonicalSource)
        if (!validation.valid) return GeneratedCompilationResult(canonicalSource, validation.errors)
        if (type != GeneratedBlockType.HOME_WIDGET) return GeneratedCompilationResult(canonicalSource, emptyList())

        val result = WidgetProgramCompiler.compile(context, canonicalSource)
        return GeneratedCompilationResult(
            compiledSource = result.compiledSource,
            errors = result.issues.map { issue ->
                GeneratedValidationError(issue.phase, issue.path, issue.message)
            },
        )
    }
}
