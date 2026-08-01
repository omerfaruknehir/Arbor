package app.arbor.chat.generated

import android.content.Context
import app.arbor.chat.widgets.WidgetProgramCompiler

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
        val validation = GeneratedContentCapabilityRegistry.validate(type, source)
        if (!validation.valid) return GeneratedCompilationResult(source, validation.errors)
        if (type != GeneratedBlockType.HOME_WIDGET) return GeneratedCompilationResult(source, emptyList())

        val result = WidgetProgramCompiler.compile(context, source)
        return GeneratedCompilationResult(
            compiledSource = result.compiledSource,
            errors = result.issues.map { issue ->
                GeneratedValidationError(issue.phase, issue.path, issue.message)
            },
        )
    }
}
