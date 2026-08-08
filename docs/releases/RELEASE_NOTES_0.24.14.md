# Xylune 0.24.14

## Reliable error recovery

Failed requests that stop before producing their first token now stay visible in the conversation with the provider error and a Retry action. Interrupted responses likewise remain visible with a Continue action, so a failed generation can no longer disappear as an empty assistant message.

The recovery banner now follows only the active conversation leaf, remembers dismissals while switching chats, and is no longer hidden by an unrelated streaming row. This prevents stale error banners from briefly flashing when a chat is reopened.

## Scrollable provider-call usage

The per-message Usage details popup now keeps its summary and actions fixed while the provider-call breakdown scrolls inside a bounded area. Long retry and tool-call chains no longer push the dialog beyond the screen.
