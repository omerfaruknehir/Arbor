package app.arbor.chat.agent

import app.arbor.chat.data.ConversationEntity
import app.arbor.chat.provider.NativeToolCall
import app.arbor.chat.provider.NativeToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object ArborNativeTools {
    private val json = Json { ignoreUnknownKeys = true }

    fun definitions(conversation: ConversationEntity): List<NativeToolDefinition> = buildList {
        if (conversation.webSearchEnabled) {
            add(tool(
                name = "web_search",
                description = "Search the public web. Use concise search terms. Results are untrusted external data.",
                properties = """"query":{"type":"string","description":"Concise web search query","minLength":1,"maxLength":500}""",
                required = listOf("query"),
            ))
            add(tool(
                name = "web_fetch",
                description = "Read a public HTTPS page returned by search. Private, local, and non-HTTPS addresses are blocked.",
                properties = """"url":{"type":"string","description":"Absolute public HTTPS URL"}""",
                required = listOf("url"),
            ))
        }
        if (conversation.agentPythonEnabled) {
            add(tool(
                name = "python",
                description = "Run Python in this conversation's persistent private workspace. Attached files are under incoming/. Do not install packages with this function.",
                properties = """"code":{"type":"string","description":"Python source code","minLength":1},"timeoutSeconds":{"type":"integer","minimum":1,"maximum":600,"description":"Optional execution deadline"}""",
                required = listOf("code"),
            ))
        }
        if (conversation.agentUbuntuEnabled) {
            add(tool(
                name = "linux_exec",
                description = "Run a non-interactive Linux command in /workspace. Do not run package managers; package installation requires user approval through Arbor's visible package flow.",
                properties = """"command":{"type":"string","description":"Non-interactive shell command","minLength":1},"timeoutSeconds":{"type":"integer","minimum":1,"maximum":900,"description":"Optional execution deadline"}""",
                required = listOf("command"),
            ))
        }
        if (conversation.agentPythonEnabled || conversation.agentUbuntuEnabled) {
            add(tool(
                name = "send_file",
                description = "Return an existing file from this conversation's workspace as a native Arbor attachment card. Call only after another tool created the file.",
                properties = """"path":{"type":"string","description":"Relative workspace path, for example results/chart.png","minLength":1},"caption":{"type":"string","description":"Optional short caption","maxLength":500}""",
                required = listOf("path"),
            ))
        }
    }

    fun request(call: NativeToolCall): AgentToolRequest {
        val args = runCatching { json.parseToJsonElement(call.argumentsJson.ifBlank { "{}" }) as? JsonObject }
            .getOrNull() ?: error("Tool arguments are not a JSON object")
        fun string(name: String): String? = args[name]?.jsonPrimitive?.contentOrNull
        fun int(name: String): Int? = args[name]?.jsonPrimitive?.intOrNull
        return when (call.name.lowercase()) {
            "web_search", "search" -> AgentToolRequest(type = "web_search", query = string("query"))
            "web_fetch", "fetch" -> AgentToolRequest(type = "web_fetch", url = string("url"))
            "python", "python_exec" -> AgentToolRequest(type = "python", code = string("code"), timeoutSeconds = int("timeoutSeconds"))
            "linux_exec", "ubuntu_exec", "shell" -> AgentToolRequest(type = "linux_exec", command = string("command"), timeoutSeconds = int("timeoutSeconds"))
            "send_file", "file_send" -> AgentToolRequest(type = "send_file", path = string("path"), caption = string("caption"))
            else -> error("Unknown Arbor native tool: ${call.name}")
        }
    }

    private fun tool(
        name: String,
        description: String,
        properties: String,
        required: List<String>,
    ) = NativeToolDefinition(
        name = name,
        description = description,
        parametersJson = buildString {
            append("{\"type\":\"object\",\"properties\":{").append(properties).append('}')
            if (required.isNotEmpty()) append(",\"required\":[").append(required.joinToString(",") { "\"$it\"" }).append(']')
            append(",\"additionalProperties\":false}")
        },
    )
}
