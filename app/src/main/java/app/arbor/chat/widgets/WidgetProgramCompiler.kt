package app.arbor.chat.widgets

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class WidgetCompileIssue(
    val phase: String,
    val path: String,
    val message: String,
)

internal data class WidgetCompileResult(
    val compiledSource: String,
    val issues: List<WidgetCompileIssue>,
)

private data class ActionCompileResult(
    val issues: List<WidgetCompileIssue>,
    val states: List<Pair<String, Map<String, String>>>,
)

/**
 * Compiles an arbor-widget/1 definition into Arbor's typed runtime and executes a
 * bounded preflight before the definition can be shown as usable content.
 */
internal object WidgetProgramCompiler {
    suspend fun compile(context: Context, source: String): WidgetCompileResult {
        val definition = ArborProgramParser.parse(source, ArborProgramSurface.WIDGET).getOrElse { error ->
            return WidgetCompileResult(
                source,
                listOf(WidgetCompileIssue("compile", "/", error.message ?: "Widget parser failed")),
            )
        }

        val issues = mutableListOf<WidgetCompileIssue>()
        issues += staticIssues(definition)

        val dataPreflight = WidgetDataRuntime.preflightHttpSources(definition)
        issues += dataPreflight.issues.map { issue ->
            WidgetCompileIssue("network_compile", "/dataSources/${issue.sourceId}", issue.message)
        }

        val actionCompilation = executeActions(definition, dataPreflight.state)
        issues += actionCompilation.issues

        val statesToRender = buildList {
            add("initial" to dataPreflight.state)
            actionCompilation.states.take(MAX_RENDERED_ACTION_STATES).forEach(::add)
        }
        issues += renderIssues(context, definition, statesToRender)

        return WidgetCompileResult(source, issues.distinct())
    }

    private fun staticIssues(definition: ArborProgramDefinition): List<WidgetCompileIssue> {
        val issues = mutableListOf<WidgetCompileIssue>()
        val visibleActions = launcherActionCount(definition.ui)
        if (visibleActions > 4) {
            issues += WidgetCompileIssue(
                "layout_compile",
                "/ui",
                "The launcher can expose at most four actions, but this widget defines $visibleActions. Keep only the four most useful actions.",
            )
        }
        definition.dataSources.forEachIndexed { index, source ->
            if (source.type == "http_json") {
                source.bindings.forEachIndexed { bindingIndex, binding ->
                    if (binding.fallback.isBlank()) {
                        issues += WidgetCompileIssue(
                            "data_compile",
                            "/dataSources/$index/bindings/$bindingIndex/fallback",
                            "Every live HTTP binding needs a useful offline fallback so the widget is never blank.",
                        )
                    }
                }
            }
        }

        fun walk(node: ArborProgramNode, path: String) {
            if (node.style.fontSize in 1..12) {
                issues += WidgetCompileIssue(
                    "layout_compile",
                    "$path/style/fontSize",
                    "Widget text below 13sp is not readable on a launcher. Use at least 15sp for normal text and 28sp for primary metrics.",
                )
            }
            if (node.type == "list" && node.items.size > 6) {
                issues += WidgetCompileIssue(
                    "layout_compile",
                    "$path/items",
                    "Launcher widgets can show at most six readable list rows. Reduce or summarize the list.",
                )
            }
            if (node.type == "choice" && node.options.size > 4) {
                issues += WidgetCompileIssue(
                    "layout_compile",
                    "$path/options",
                    "A Home-screen choice should have at most four short options.",
                )
            }
            node.children.forEachIndexed { index, child -> walk(child, "$path/children/$index") }
        }
        walk(definition.ui, "/ui")
        return issues
    }

    private fun executeActions(
        definition: ArborProgramDefinition,
        initialState: Map<String, String>,
    ): ActionCompileResult {
        val issues = mutableListOf<WidgetCompileIssue>()
        val states = mutableListOf<Pair<String, Map<String, String>>>()
        definition.actions.forEach { (id, _) ->
            runCatching {
                val transition = ArborProgramRuntime.apply(id, definition, initialState)
                require(transition.state.size <= 64) { "Action creates too many state values" }
                require(transition.state.values.all { it.length <= 1_000 }) { "Action creates an oversized state value" }
                transition.state.forEach { (key, value) ->
                    require(key.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}"))) { "Action creates invalid state key $key" }
                    require(value.length <= 1_000) { "Action creates an oversized value for $key" }
                }
                states += "action '$id'" to transition.state
            }.exceptionOrNull()?.let { error ->
                issues += WidgetCompileIssue("runtime_compile", "/actions/$id", error.message ?: "Action execution failed")
            }
        }
        return ActionCompileResult(issues, states.distinctBy { it.second })
    }

    private suspend fun renderIssues(
        context: Context,
        definition: ArborProgramDefinition,
        states: List<Pair<String, Map<String, String>>>,
    ): List<WidgetCompileIssue> = withContext(Dispatchers.Default) {
        val metrics = context.resources.displayMetrics
        val viewports = listOf(
            Triple("standard", 320, 84),
            Triple("expanded", 420, 160),
        )
        val issues = mutableListOf<WidgetCompileIssue>()

        states.forEachIndexed { stateIndex, (stateLabel, state) ->
            val stateViewports = if (stateIndex == 0) viewports else viewports.take(1)
            stateViewports.forEach viewport@ { (viewportLabel, widthDp, heightDp) ->
                val label = if (stateIndex == 0) viewportLabel else "$viewportLabel after $stateLabel"
                val result = runCatching {
                    WidgetCanvasRenderer.renderWithDiagnostics(
                        definition = definition,
                        state = state,
                        widthPx = (widthDp * metrics.density).toInt(),
                        heightPx = (heightDp * metrics.density).toInt(),
                        dark = false,
                        suppressActionControls = true,
                        density = metrics.density,
                        scaledDensity = metrics.scaledDensity,
                    )
                }.getOrElse { error ->
                    issues += WidgetCompileIssue(
                        "render_compile",
                        "/ui",
                        "$label launcher render failed: ${error.message ?: error::class.java.simpleName}",
                    )
                    return@viewport
                }
                result.bitmap.recycle()
                if (result.renderedNodes == 0) {
                    issues += WidgetCompileIssue(
                        "render_compile",
                        "/ui",
                        "The $label launcher render has no visible content after action controls are moved to the native action row.",
                    )
                }
                if (result.clippedTextCount > 0) {
                    issues += WidgetCompileIssue(
                        "layout_compile",
                        "/ui",
                        "The $label launcher render clips ${result.clippedTextCount} text item(s): ${result.clippedSamples.take(3).joinToString()}. Shorten labels or simplify the layout.",
                    )
                }
                if (result.crampedTextCount > 0) {
                    issues += WidgetCompileIssue(
                        "layout_compile",
                        "/ui",
                        "The $label launcher render gives ${result.crampedTextCount} text item(s) too little vertical space. Reduce the number of rows or use weights to prioritize the main value.",
                    )
                }
                if (result.minimumTextSp > 0f && result.minimumTextSp < 13f) {
                    issues += WidgetCompileIssue(
                        "layout_compile",
                        "/ui",
                        "The $label launcher render shrinks text to ${"%.1f".format(result.minimumTextSp)}sp. Keep all visible text at 13sp or larger.",
                    )
                }
            }
        }
        issues
    }

    private fun launcherActionCount(node: ArborProgramNode): Int = when (node.type) {
        "button", "toggle" -> if (node.action.isNotBlank()) 1 else 0
        "choice" -> node.options.count { it.action.isNotBlank() }
        "list" -> node.items.count { it.action.isNotBlank() }
        else -> 0
    } + node.children.sumOf(::launcherActionCount)

    private const val MAX_RENDERED_ACTION_STATES = 6
}
