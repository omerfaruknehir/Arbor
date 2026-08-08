# Xylune 0.24.15

## Reliable error recovery

Failed requests that stop before producing their first token stay visible in the conversation with the provider error and a Retry action. Interrupted responses likewise remain visible with a Continue action, so a failed generation cannot disappear as an empty assistant message.

Recovery banners are limited to the active conversation leaf and their dismissal is tracked per conversation across chat switches. Reopening a chat therefore does not briefly flash an already-dismissed stale error, while a genuinely new error revision can still surface normally.

## Scrollable provider-call usage

The per-message Usage details popup keeps its summary and actions fixed while the provider-call breakdown scrolls inside a bounded area. Long retry and tool-call chains no longer push the dialog beyond the screen.
