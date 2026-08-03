package app.xylune.chat.widgets

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
 * Compiles an xylune-widget/1 definition into Xylune's typed runtime and executes a
 * bounded preflight before the definition can be shown as usable content.
 */
internal object WidgetProgramCompiler {
    suspend fun compile(context: Context, source: String): WidgetCompileResult {
        val definition = XyluneProgramParser.parse(source, XyluneProgramSurface.WIDGET).getOrElse { error ->
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

    private fun staticIssues(definition: XyluneProgramDefinition): List<WidgetCompileIssue> {
        val issues = mutableListOf<WidgetCompileIssue>()
        val visibleActions = launcherActionIds(definition.ui).size
        if (visibleActions > 4) {
            issues += WidgetCompileIssue(
                "layout_compile",
                "/ui",
                "The launcher can expose at most four distinct actions, but this widget defines $visibleActions. Keep only the four most useful actions.",
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

        fun walk(node: XyluneProgramNode, path: String) {
            if (node.style.fontSize in 1..10) {
                issues += WidgetCompileIssue(
                    "layout_compile",
                    "$path/style/fontSize",
                    "Widget text below 11sp is not readable. Use 11–12sp only for compact supporting text, 13–16sp for ordinary text, and larger type for primary metrics.",
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
        definition: XyluneProgramDefinition,
        initialState: Map<String, String>,
    ): ActionCompileResult {
        val issues = mutableListOf<WidgetCompileIssue>()
        val states = mutableListOf<Pair<String, Map<String, String>>>()
        definition.actions.forEach { (id, _) ->
            runCatching {
                val transition = XyluneProgramRuntime.apply(id, definition, initialState)
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
        definition: XyluneProgramDefinition,
        states: List<Pair<String, Map<String, String>>>,
    ): List<WidgetCompileIssue> = withContext(Dispatchers.Default) {
        val metrics = context.resources.displayMetrics
        val viewports = listOf(
            Triple("compact", 240, 96),
            Triple("standard", 320, 122),
            Triple("expanded", 420, 190),
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
                        "The $label launcher render still clips ${result.clippedTextCount} text block(s) after adaptive fitting: ${result.clippedSamples.take(3).joinToString()}. Shorten the affected labels or simplify that row.",
                    )
                }
                if (result.crampedTextCount > 0) {
                    issues += WidgetCompileIssue(
                        "layout_compile",
                        "/ui",
                        "The $label launcher render still cannot fit ${result.crampedTextCount} text block(s) after intrinsic sizing and adaptive type fitting. Reduce nonessential content or use fewer vertical groups.",
                    )
                }
                if (result.minimumTextSp > 0f && result.minimumTextSp < 11f) {
                    issues += WidgetCompileIssue(
                        "layout_compile",
                        "/ui",
                        "The $label launcher render would require text below 11sp.",
                    )
                }
            }
        }
        issues
    }

    private fun launcherActionIds(node: XyluneProgramNode): Set<String> = buildSet {
        if (node.type in setOf("button", "toggle", "input") && node.action.isNotBlank()) add(node.action)
        if (node.type == "choice") {
            node.action.takeIf(String::isNotBlank)?.let(::add)
            node.options.mapNotNull { it.action.takeIf(String::isNotBlank) }.forEach(::add)
        }
        if (node.type == "list") node.items.mapNotNull { it.action.takeIf(String::isNotBlank) }.forEach(::add)
        node.children.flatMap(::launcherActionIds).forEach(::add)
    }

    private const val MAX_RENDERED_ACTION_STATES = 6
}
