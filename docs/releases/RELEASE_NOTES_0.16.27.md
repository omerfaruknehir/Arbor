# Xylune 0.16.27

This build keeps the restored 0.16.19 UI/blur baseline and the focused 0.16.26 streaming fixes, then makes agent execution native-only:

- Removed the legacy `xylune-tool` fenced-text parser and execution path.
- OpenAI-compatible, Anthropic, and Gemini providers use their native structured function/tool-call protocols.
- Tool results are returned through each provider's native tool-result message format.
- Providers which reject native tool schemas now fail explicitly instead of silently retrying with text-encoded commands.
- Models without native function calling receive a clear no-tools system instruction and cannot execute text that resembles a tool call.
- Package-install approval blocks remain visible user-review UI requests; they are not executed as agent tool calls.
