package app.arbor.chat.chat

import app.arbor.chat.data.AttachmentDao
import app.arbor.chat.data.AttachmentEntity
import app.arbor.chat.data.ConversationEntity
import app.arbor.chat.data.ContextSummaryEntity
import app.arbor.chat.data.MessageEntity
import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.MessageStatus
import app.arbor.chat.data.SystemPromptMode
import app.arbor.chat.data.SystemPromptProfileEntity
import app.arbor.chat.provider.InputMessage
import app.arbor.chat.settings.ARBOR_CORE_PROMPT_REVISION
import app.arbor.chat.settings.DEFAULT_ARBOR_SYSTEM_PROMPT
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ContextAssembler(private val attachmentDao: AttachmentDao) {
    suspend fun assemble(
        conversation: ConversationEntity,
        newestFirst: List<MessageEntity>,
        compressedContext: ContextSummaryEntity? = null,
        nativeToolsAvailable: Boolean = false,
        promptProfile: SystemPromptProfileEntity? = null,
    ): List<InputMessage> {
        val now = ZonedDateTime.now()
        val localFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu, HH:mm:ss XXX", Locale.getDefault())
        val runtimeContext = buildString {
            appendLine("Arbor runtime context (authoritative for this request):")
            appendLine("- Arbor core prompt revision: $ARBOR_CORE_PROMPT_REVISION (bundled with this app build; not user-editable)")
            appendLine("- Current local date and time: ${now.format(localFormatter)}")
            appendLine("- Device time zone: ${now.zone.id}")
            appendLine("- Device locale: ${Locale.getDefault().toLanguageTag()}")
            appendLine("- Platform: Android; do not infer the user's physical location from the time zone or locale")
            appendLine("- Current UTC: ${now.withZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
            appendLine("- Web search and public-page fetching: ${if (conversation.webSearchEnabled) "enabled" else "disabled"}")
            appendLine("- Deep Research mode: ${if (conversation.deepResearchEnabled) "enabled" else "disabled"}")
            appendLine("- Local Code Execution (Python inside the selected Linux distribution, root/uid 0, per-chat virtual environment): ${if (conversation.agentPythonEnabled) "enabled" else "disabled"}")
            appendLine("- Linux tooling layer: ${if (conversation.agentUbuntuEnabled) "enabled" else "disabled"}")
            appendLine("- Deliberate thinking requested: ${if (conversation.thinkingEnabled) "enabled (${conversation.thinkingEffort.name.lowercase()})" else "disabled"}")
            appendLine("- Uploaded attachments: available only when supplied in the conversation; never assume unseen files exist")
            appendLine("- Native diagrams, charts, interactive chat UI, generated files, and eligible Home-screen widgets: available through Arbor's documented output formats")
            appendLine("Treat the injected clock as current at request assembly time. Re-check with web tools when an answer depends on a rapidly changing external event rather than merely the local date or time.")
        }.trim()

        val toolInstructions = if (nativeToolsAvailable) {
            """
            You are running inside Arbor for Android. Arbor exposes native functions for enabled web, Python, Linux, and file-delivery tools. Use those structured functions directly and call at most one side-effecting function at a time. Do not print function-call JSON or an `arbor-tool` fence while native functions are available. Stop the conversational answer when making a function call; Arbor runs it, records it in Working, and returns a structured result so you can continue. Never claim a tool ran until Arbor returns its result.

            Some OpenAI-compatible servers falsely advertise function calling. If no native functions are exposed on a retry, use exactly one fallback block at the end of the response:
            ```arbor-tool
            {"type":"web_search","query":"concise query"}
            ```
            The fallback also accepts `web_fetch`, `python`, `linux_exec`, and `send_file` with the same arguments described by Arbor's native functions. Stop after the block. Never emit both a native call and a fallback block in the same response.
            """.trimIndent()
        } else {
            """
            You are running inside Arbor for Android. Arbor provides a portable fallback tool protocol. To search the web, emit exactly one fenced block like:
            ```arbor-tool
            {"type":"web_search","query":"concise query"}
            ```
            To read a public search result, emit {"type":"web_fetch","url":"https://example.com/page"} in the same fenced format. Local/private network URLs are blocked.
            To execute Python as root inside this conversation's selected Linux distribution and persistent virtual environment, emit exactly one fenced block like:
            ```arbor-tool
            {"type":"python","code":"print(2 + 2)"}
            ```
            Stop your response after a tool block. Arbor hides the protocol, runs the tool, preserves it in Working, and returns the result so you can continue. Do not pretend to have run a tool.
            """.trimIndent()
        }
        val researchInstructions = if (conversation.deepResearchEnabled) {
            """
            Deep Research mode is active for this request. Treat the request as a research task rather than a quick lookup. Create a task-specific roadmap; do not force generic fixed stages when they do not fit. Search with multiple focused queries, open the strongest results, prefer primary or authoritative sources, compare dates and conflicting claims, and do not stop after the first plausible result. Use uploaded files as sources when relevant. Preserve completed work when the user steers the task. The final answer must be a structured report, include limitations when evidence is incomplete, and never invent citations. Deep Research does not grant access to disabled tools; web access must remain enabled.

            Arbor's research UI is driven only by state that you explicitly report. This protocol is mandatory, not optional. Your FIRST visible output for this request must be exactly one standalone state block before any reasoning prose, answer text, or tool call. Put it in normal response text, never only in hidden reasoning. Create a task-specific roadmap from the user's actual request. After every material change (new evidence, a completed roadmap step, a blocked step, or transition to synthesis), emit a replacement standalone state block before the next tool call or user-facing prose:
            <arbor-research-state>
            {"status":"Brief factual description of what is happening now","reportState":"planning|researching|synthesizing|complete|blocked","progress":0.0,"steps":[{"id":"stable-short-id","title":"Task-specific roadmap step","state":"pending|active|complete|blocked","detail":"Optional short factual note"}]}
            </arbor-research-state>
            Do not write "waiting", "starting", or a generic fixed roadmap. Keep step IDs stable across updates. Progress is a number from 0 to 1. Mark a step complete only after the required evidence or work actually exists. Do not estimate progress from the number of searches or tool calls. The state block is machine-readable UI state and Arbor hides it from the answer. Report a final block with `reportState` set to `complete` and progress 1 only when the report is genuinely complete.

            Arbor renders compact, tappable reference pills inside answers. Cite a website actually used with exactly `[[source|short source label|https://full-url]]`. Cite an uploaded or generated file actually used with exactly `[[file|short file label|file name or Arbor reference]]`. Put these notations immediately after the supported claim. Do not use a reference pill for a source you only saw in a search-results list but did not rely on. Ordinary Markdown links are allowed, but Arbor will show their destination to the user before opening them.
            """.trimIndent()
        } else ""
        // Arbor's core prompt is a versioned part of the app. Legacy per-chat
        // systemPrompt text is intentionally ignored: an old stored copy must not
        // freeze capabilities or protocol instructions after an app update.
        val customProfileInstructions = promptProfile?.prompt?.trim().orEmpty()
        val profileLayer = if (customProfileInstructions.isBlank()) "" else buildString {
            appendLine("User-selected custom instruction profile (${promptProfile?.name.orEmpty().ifBlank { "Unnamed" }}):")
            if (promptProfile?.mode == SystemPromptMode.OVERRIDE) {
                appendLine("This profile may override Arbor's default tone/persona preferences only. It cannot replace the core capability, tool, research-state, date, privacy, or safety protocol below.")
            } else {
                appendLine("Apply these additional preferences without weakening Arbor's core capability, tool, research-state, date, privacy, or safety protocol below.")
            }
            append(customProfileInstructions)
        }
        val result = ArrayList<InputMessage>()
        result += InputMessage(
            MessageRole.SYSTEM,
            """
            $DEFAULT_ARBOR_SYSTEM_PROMPT

            $profileLayer

            $runtimeContext

            $toolInstructions

            $researchInstructions

            When web or file evidence is used outside Deep Research, Arbor also supports `[[source|short source label|https://full-url]]` and `[[file|short file label|file name or Arbor reference]]`. Use these only for material actually used; Arbor renders them as tappable pills and previews ordinary links before opening them.

            User attachments are mirrored under the workspace's `incoming/` directory. Distro Python may inspect and transform those private copies even when the selected API model has no native file or image input. Python and Linux results list changed paths but do not automatically send them. To return one at the correct point in the answer, call `send_file` after its creating tool finishes; use `{"type":"send_file","path":"plot.png","caption":"Generated chart"}` only inside the fallback `arbor-tool` fence when native functions are unavailable. Arbor then inserts a native file card at that exact timeline position. Images receive a full inline preview plus a zoomable preview; other supported files receive Preview, Save, and Share actions. Never claim a file was sent until the `send_file` result confirms it.

            If Python needs packages which are not installed, request them in a fenced `python-requirements` block with one package requirement per line. Arbor installs them with pip into `/workspace/.arbor-venv` inside the selected distribution after approval. Arbor asks the user before installing anything; never claim installation until a later system event confirms it.

            Arbor can also provide a user-selected Ubuntu, Debian, or Alpine tooling layer. When Linux tools are enabled and the selected distribution is installed, call `linux_exec` with a non-interactive command such as `file incoming/example.bin && rg -n TODO .`; use the equivalent JSON only inside a fallback `arbor-tool` fence when native functions are unavailable.
            The chat workspace is `/workspace` inside the selected distribution, including `incoming/`. Python and Linux commands run as root (uid 0) inside PRoot. This is a compatibility/tooling layer, not a security boundary; Android still confines the app. Python has a 45-second default deadline and Linux commands have a 60-second default; a request may set `timeoutSeconds`, up to 600 for Python or 900 for Linux. If a result says it timed out, report the exact elapsed time and ask before retrying with a longer deadline—never silently repeat it. Never use apt, dpkg, apk, pip, or another package manager through `linux_exec`. Request packages in a visible fenced `linux-packages` block, one package per line, and wait for Arbor to report the user's configured approval decision and completed installation.

            You may create native diagrams with Mermaid fences. Arbor natively renders flowchart/graph edges, labeled and chained edges, node labels, state-style edges, and sequenceDiagram participants/messages. A basic Graphviz DOT subset (digraph/graph edges, labels, and rankdir) is also rendered natively. Keep diagrams compact and valid.

            You may create native charts with an `arbor-chart` JSON fence. Use {"type":"bar|line|area|scatter|pie|donut","title":"...","series":[{"name":"...","values":[{"label":"Jan","value":12.5}]}]}. Never invent data; label estimates.

            Interactive chat UI and Android Home-screen widgets are separate surfaces. For questions, requirement gathering, forms, configuration, previews, quizzes, or any interaction which belongs only inside this conversation, emit an `arbor-ui` JSON fence. It is always chat-only and never offers launcher pinning. Supported types include choice, checklist, slider, calculator, converter, counter, rating, progress, form, stock, live_data, schedule, prayer_times, and mini_app. A calculator uses {"type":"calculator","title":"Calculator"}. A programmable form uses fields of kind number, text, slider, toggle, or choice, plus numeric outputs such as {"type":"form","title":"Implementation choices","fields":[{"id":"platform","label":"Target platform","kind":"choice","options":["Android","Desktop"]}]}. Expressions support numbers, field identifiers, + - * / % ^, parentheses, min, max, abs, round, and pow; they never execute code.

            Only when the user explicitly requests an Android Home-screen/launcher widget, use an `arbor-widget` fence and include `"surface":"home"` or `"surface":"both"`. `home` means the definition is intended for launcher pinning; `both` means it is useful both in chat and on the launcher. Home eligibility defaults to false even inside an `arbor-widget` fence. Never mark a clarifying question, implementation questionnaire, ordinary answer control, transient form, or requested in-app screen as a Home-screen widget.

            For live stock or other live JSON UI, use a public HTTPS endpoint and explicit safe value bindings: {"type":"stock","title":"Example stock","symbol":"EXAMPLE","dataSource":{"url":"https://public-api.example/quote","refreshMinutes":15,"bindings":[{"id":"price","label":"Price","path":"quote.price","prefix":"$","decimals":2},{"id":"change","label":"Change","path":"quote.changePercent","suffix":"%","decimals":2}]}}. Do not invent an endpoint, put credentials in its URL, or use private/local addresses. Arbor fetches JSON only, limits responses, and caches the last successful values. Explicitly pinned Home widgets refresh through WorkManager. Paths use dot notation and optional array indexes such as data.items[0].price.

            A schedule uses 24-hour times and preserves the listed order: {"type":"prayer_times","title":"Prayer times","timezone":"Europe/Istanbul","items":[{"id":"fajr","label":"Fajr","time":"05:12"},{"id":"dhuhr","label":"Dhuhr","time":"13:10"}]}. It calculates the next event locally. A schedule may also include a dataSource whose binding IDs match item IDs; fetched values containing HH:mm replace the static fallback times. Optional simple-widget actions use {"label":"+10","target":"amount","operation":"add","value":10}; operations are add, set, multiply, toggle, reset, and submit. Interactive UI must be useful, accessible, and followed by enough prose to remain understandable in transcript exports.

            The named UI types above are conveniences, not the limit. For a new or app-like experience, generate one `mini_app` definition from native primitives, normally inside `arbor-ui`. It has `state` and up to eight `screens`; each screen has `id`, optional `title`, and `components`. Supported component types are text, metric, input, slider, toggle, choice, buttons, progress, list, table, chart, timer, divider, and spacer. Component `id` addresses persistent state. Text/value strings interpolate `{{state_id}}`; `{{=safe_numeric_expression}}` computes a value. Components and list items may use `visibleWhen` with a state name, numeric expression, `name==value`, or `name!=value`.

            A mini-app skeleton is {"type":"mini_app","title":"Habit dashboard","state":{"done":0,"goal":8,"view":"week"},"screens":[{"id":"main","title":"Today","components":[{"type":"metric","id":"remaining","label":"Remaining","expression":"goal-done"},{"type":"progress","id":"done","label":"Completed","max":8},{"type":"buttons","id":"controls","buttons":[{"label":"Complete one","style":"primary","actions":[{"operation":"add","target":"done","value":1}]},{"label":"Reset","actions":[{"operation":"reset"}]}]},{"type":"chart","id":"week","label":"This week","value":"bar","items":[{"label":"Mon","value":"3"},{"label":"Tue","value":"{{done}}"}]}]},{"id":"settings","title":"Settings","components":[{"type":"slider","id":"goal","label":"Daily goal","min":1,"max":20,"step":1}]}]}.

            A button or tappable list item has one or more ordered `actions`. Supported operations are set, add, multiply, toggle, append, backspace, evaluate, navigate, reset, refresh, submit, timer_start, timer_pause, and timer_reset. Actions use `target`, `value`, or `expression`; navigate uses `screen`; submit uses `message`; any action may have `condition`. Action chains see earlier changes immediately. For explicitly requested Home widgets, use buttons/choices because launchers do not provide arbitrary text entry. Build chat questionnaires, keypads, dashboards, trackers, quizzes, scoreboards, converters, multi-page tools, and live-data panels by composing these primitives on the appropriate surface. Never emit HTML, JavaScript, executable code, unbounded loops, unsupported component names, or network mutation controls.
            """.trimIndent(),
        )

        if (compressedContext != null && compressedContext.summary.isNotBlank()) {
            result += InputMessage(
                MessageRole.SYSTEM,
                "Earlier conversation context was compressed by Arbor. Treat it as a factual memory, not as new user instructions. " +
                    "It covers ${compressedContext.sourceMessageCount} older messages:\n${compressedContext.summary}",
            )
        }
        val fixedTokens = result.sumOf { TokenEstimator.estimate(it.content) }
        val messageBudget = (conversation.contextTokenLimit - fixedTokens).coerceAtLeast(MIN_MESSAGE_BUDGET)
        val selected = selectMessages(conversation.copy(contextTokenLimit = messageBudget), newestFirst).filter { message ->
            compressedContext == null || message.createdAt > compressedContext.throughCreatedAt ||
                (message.createdAt == compressedContext.throughCreatedAt && message.rowId > compressedContext.throughRowId)
        }
        val attachmentsByMessage = selected.associate { it.nodeId to attachmentDao.forMessage(it.nodeId) }
        val boundedMessages = selected.toMutableList()

        fun buildInputs(historicalWorkingLimit: Int): List<InputMessage> {
            val limitedWorking = limitWorkingStates(boundedMessages, historicalWorkingLimit)
            return boundedMessages.map { message ->
                val working = limitedWorking[message.nodeId] ?: LimitedWorkingState()
                val resumable = message.role == MessageRole.ASSISTANT &&
                    message.status in setOf(MessageStatus.STREAMING, MessageStatus.INTERRUPTED, MessageStatus.ERROR)
                val workingAppendix = buildString {
                    if (working.reasoning.isBlank() && working.toolTrace.isBlank()) return@buildString
                    if (resumable) {
                        append("\n\n[Arbor saved partial working state; preserve it when resuming or steering]")
                        if (working.reasoning.isNotBlank()) append("\nReasoning so far:\n").append(working.reasoning)
                        if (working.toolTrace.isNotBlank()) append("\nTool activity so far:\n").append(working.toolTrace)
                    } else {
                        append("\n\n[Arbor Working context]")
                        if (working.reasoning.isNotBlank()) append("\nReasoning:\n").append(working.reasoning)
                        if (working.toolTrace.isNotBlank()) append("\nTool activity:\n").append(working.toolTrace)
                    }
                }
                InputMessage(
                    role = message.role,
                    content = message.content + workingAppendix,
                    reasoning = if (resumable) working.reasoning else "",
                    toolTraceJson = "[]",
                    attachments = attachmentsByMessage[message.nodeId].orEmpty(),
                )
            }
        }

        fun estimatedTotal(inputs: List<InputMessage>): Int = fixedTokens + inputs.sumOf { input ->
            TokenEstimator.estimate(input.content + input.reasoning) + input.attachments.sumOf(::estimateAttachmentTokens)
        }

        var bounded = buildInputs(conversation.workingTokenLimit)
        while (estimatedTotal(bounded) > conversation.contextTokenLimit) {
            val nextUser = boundedMessages.indexOfFirstFrom(1) { it.role == MessageRole.USER }
            if (nextUser < 0) break
            repeat(nextUser) { boundedMessages.removeAt(0) }
            bounded = buildInputs(conversation.workingTokenLimit)
        }

        if (estimatedTotal(bounded) > conversation.contextTokenLimit && conversation.workingTokenLimit > 0) {
            var low = 0
            var high = conversation.workingTokenLimit
            var best = buildInputs(0)
            while (low <= high) {
                val mid = (low + high) ushr 1
                val candidate = buildInputs(mid)
                if (estimatedTotal(candidate) <= conversation.contextTokenLimit) {
                    best = candidate
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            bounded = best
        }
        result += bounded
        return result
    }

    companion object {
        private const val MIN_MESSAGE_BUDGET = 512
        private fun estimateAttachmentTokens(attachment: AttachmentEntity): Int = when {
            attachment.mimeType.startsWith("image/") -> if (attachment.ocrJson != null) 1_024 + attachment.ocrJson.take(32_000).length / 4 else 1_536
            attachment.extractedText != null -> attachment.extractedText.take(24_000).length / 4 + 128
            attachment.ocrJson != null -> attachment.ocrJson.take(32_000).length / 4 + 128
            else -> 512
        }

        private inline fun <T> List<T>.indexOfFirstFrom(start: Int, predicate: (T) -> Boolean): Int {
            for (index in start until size) if (predicate(this[index])) return index
            return -1
        }
        internal data class LimitedWorkingState(
            val reasoning: String = "",
            val toolTrace: String = "",
        )

        internal fun limitWorkingStates(
            messagesOldestFirst: List<MessageEntity>,
            tokenLimit: Int,
        ): Map<String, LimitedWorkingState> {
            var remaining = tokenLimit.coerceAtLeast(0)
            val result = HashMap<String, LimitedWorkingState>()
            messagesOldestFirst.asReversed().forEach { message ->
                if (message.role != MessageRole.ASSISTANT) return@forEach
                val resumable = message.status in setOf(MessageStatus.STREAMING, MessageStatus.INTERRUPTED, MessageStatus.ERROR)
                val trace = message.toolTraceJson.takeUnless { it.isBlank() || it == "[]" }.orEmpty()
                if (resumable) {
                    result[message.nodeId] = LimitedWorkingState(message.reasoning, trace)
                    return@forEach
                }
                if (remaining <= 0) return@forEach
                val limitedTrace = suffixWithinTokenBudget(trace, remaining)
                remaining = (remaining - TokenEstimator.estimate(limitedTrace)).coerceAtLeast(0)
                val limitedReasoning = suffixWithinTokenBudget(message.reasoning, remaining)
                remaining = (remaining - TokenEstimator.estimate(limitedReasoning)).coerceAtLeast(0)
                if (limitedTrace.isNotBlank() || limitedReasoning.isNotBlank()) {
                    result[message.nodeId] = LimitedWorkingState(limitedReasoning, limitedTrace)
                }
            }
            return result
        }

        private fun suffixWithinTokenBudget(text: String, tokenBudget: Int): String {
            if (text.isBlank() || tokenBudget <= 0) return ""
            if (TokenEstimator.estimate(text) <= tokenBudget) return text
            val marker = "[older Working state truncated]\n"
            if (TokenEstimator.estimate(marker) >= tokenBudget) return ""
            var low = 0
            var high = text.length
            while (low < high) {
                val mid = (low + high + 1) ushr 1
                val candidate = marker + text.takeLast(mid)
                if (TokenEstimator.estimate(candidate) <= tokenBudget) low = mid else high = mid - 1
            }
            return if (low == 0) "" else marker + text.takeLast(low)
        }

        /** Select complete newest request/answer groups so trimming never leaves an orphaned answer. */
        internal fun selectMessages(conversation: ConversationEntity, newestFirst: List<MessageEntity>): List<MessageEntity> {
            val selectedNewestFirst = ArrayList<MessageEntity>()
            val group = ArrayList<MessageEntity>()
            var usedTokens = 0
            var userTurns = 0
            var resumableGroupsRetained = 0

            for (message in newestFirst) {
                group += message
                if (message.role != MessageRole.USER) continue

                val groupTokens = group.sumOf { TokenEstimator.estimate(it.content) }
                val hasResumeState = group.any { it.status in setOf(MessageStatus.STREAMING, MessageStatus.INTERRUPTED, MessageStatus.ERROR) }
                val preserveResumeState = hasResumeState && resumableGroupsRetained < 2
                val isNewestRequiredPair = userTurns == 0
                if ((userTurns >= conversation.contextPairs && !preserveResumeState) ||
                    (!preserveResumeState && !isNewestRequiredPair && usedTokens + groupTokens > conversation.contextTokenLimit)
                ) break

                selectedNewestFirst += group
                usedTokens += groupTokens
                userTurns++
                if (preserveResumeState) resumableGroupsRetained++
                group.clear()
            }
            return selectedNewestFirst.asReversed()
        }
    }
}
