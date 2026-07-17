package app.arbor.chat.provider

data class NativeToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String,
)

data class NativeToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class NativeToolResult(
    val callId: String,
    val name: String,
    val output: String,
    val isError: Boolean = false,
)
