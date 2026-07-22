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
import app.arbor.chat.generated.GeneratedContentCapabilityRegistry
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
            You are running inside Arbor for Android. Arbor exposes provider-native structured functions for the enabled web, Python, Linux, and file-delivery capabilities. Use those functions directly and call at most one side-effecting function at a time. Never print function-call JSON, XML, an `arbor-tool` fence, or any other text-encoded tool command. Stop the conversational answer when making a function call; Arbor executes it, records it in Working, and returns a structured provider tool result so you can continue. Never claim a tool ran until Arbor returns its result. If a needed function is not exposed, state that it is unavailable instead of encoding a request in ordinary text.
            """.trimIndent()
        } else {
            """
            Arbor has not exposed executable functions for this request because the selected model/provider is not configured for native function calling or no enabled tool is available. Do not emit `arbor-tool` fences, function-call JSON, or pretend to search, fetch, execute Python/Linux, or send a file. State the limitation when the task requires one of those capabilities.
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
        val latestUserIntent = newestFirst.firstOrNull { it.role == MessageRole.USER }?.content.orEmpty().take(8_000)
        val generatedContentInstructions = GeneratedContentCapabilityRegistry.promptForRequest(latestUserIntent)
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

            User attachments are mirrored under the workspace's `incoming/` directory. Distro Python may inspect and transform those private copies even when the selected API model has no native file or image input. Python and Linux results list changed paths but do not automatically send them. To return one at the correct point in the answer, call the native `send_file` function after its creating tool finishes. If `send_file` is not exposed, state that file delivery is unavailable; never encode a file-send request in text. Arbor inserts a native file card at that exact timeline position after a successful call. Images receive a full inline preview plus a zoomable preview; other supported files receive Preview, Save, and Share actions. Never claim a file was sent until the `send_file` result confirms it.

            If Python needs packages which are not installed, request them in a fenced `python-requirements` block with one package requirement per line. Arbor installs them with pip into `/workspace/.arbor-venv` inside the selected distribution after approval. Arbor asks the user before installing anything; never claim installation until a later system event confirms it.

            Arbor can also provide a user-selected Ubuntu, Debian, or Alpine tooling layer. When the native `linux_exec` function is exposed and the selected distribution is installed, call it with a non-interactive command such as `file incoming/example.bin && rg -n TODO .`. If it is not exposed, report that Linux execution is unavailable; never encode the command as a textual tool request.
            The chat workspace is `/workspace` inside the selected distribution, including `incoming/`. Python and Linux commands run as root (uid 0) inside PRoot. This is a compatibility/tooling layer, not a security boundary; Android still confines the app. Python has a 45-second default deadline and Linux commands have a 60-second default; a request may set `timeoutSeconds`, up to 600 for Python or 900 for Linux. If a result says it timed out, report the exact elapsed time and ask before retrying with a longer deadline—never silently repeat it. Never use apt, dpkg, apk, pip, or another package manager through `linux_exec`. Request packages in a visible fenced `linux-packages` block, one package per line, and wait for Arbor to report the user's configured approval decision and completed installation.

            Every Python or Linux tool call is persisted under `.arbor/runs/<run-id>/` before execution. If an existing run fails, inspect only necessary line ranges with `workspace_read`, then use SHA-guarded `apply_patch` and `rerun_script`. Preserve correct code and do not resend the complete script unless its file is missing, the user explicitly requests a rewrite, or more than roughly 60% genuinely needs replacement. Do not rerun the same deterministic failure repeatedly without changing its source. Patches and reruns remain part of the same Working activity. Ask before extending a long timeout under the timeout policy above.

            $generatedContentInstructions
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
                    // Only user-supplied attachments are provider inputs. Files created
                    // and sent by the assistant remain disk-backed chat artifacts and are
                    // represented by their tool/timeline metadata; feeding them back as
                    // inline base64 would duplicate large generated files in memory.
                    attachments = if (message.role == MessageRole.USER) attachmentsByMessage[message.nodeId].orEmpty() else emptyList(),
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
