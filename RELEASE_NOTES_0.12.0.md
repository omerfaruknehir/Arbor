# Arbor 0.12.0

Arbor 0.12.0 separates settings by scope and upgrades provider integration.

## Settings scopes

- **Chat** controls affect only the active conversation.
- **Global** contains the persistent profile used when creating a new chat, automation policies, package approval, appearance, and privacy controls.
- **Providers** contains credentials, endpoints, model discovery, model metadata, and pricing.
- Changing a chat option persists it to that conversation and records it as the last selected default for future chats. Editing Global defaults does not rewrite existing chats.

## Simpler model controls

- Web, Python, and Linux are independent on/off permissions.
- Thinking has an on/off switch and a four-level effort control.
- Thinking and tool settings are captured in queued request snapshots so navigation, retries, steering, and process recovery cannot silently change them.

## Provider and attachment upgrades

- OpenAI-compatible, Anthropic, and Gemini APIs use native structured tool calls, including streamed arguments and provider-specific continuation state.
- Arbor keeps the fenced tool protocol as a fallback for compatible endpoints which do not implement native calls correctly.
- DOCX, PPTX, and XLSX files receive bounded local text extraction; this is not a visual Office renderer.

## Compatibility

The database migrates automatically from schema 9 to 10. The debug package retains the existing `app.arbor.chat.debug` application ID and increases the version code to 19.
