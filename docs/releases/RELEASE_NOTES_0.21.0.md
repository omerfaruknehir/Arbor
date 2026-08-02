# Arbor 0.21.0

This release repairs long-running agent work and adds first-class local memory while relaxing arbitrary generated-widget UI limits.

## Long-running work and Continue

- Streaming model requests no longer fail merely because a reasoning model emits no SSE bytes for two or three minutes.
- Manual Continue waits for the previous WorkManager instance to finish cancelling before marking the message as streaming, eliminating the cancellation race which made Continue appear to do nothing.
- Worker replacement cancellation no longer overwrites a newly resumed message with `INTERRUPTED`.
- Automatic output continuation rises from three to twelve segments.
- Normal tool workflows can use up to 64 tool rounds and Deep Research up to 128, replacing the previous 8/24-round ceiling which prematurely finalized widget-building sessions.

## Memory

- Adds encrypted cross-chat memories stored in the SQLCipher Room database.
- Adds native `memory_save`, `memory_list`, and `memory_forget` tools with visible Working events.
- Injects enabled memories as bounded user-owned reference data, never as instructions.
- Adds Memory settings with global enable, automatic-memory policy, manual add, per-item enable/disable, and delete controls.
- Includes memories and memory policy in app-settings backups.
- Automatic memory excludes transient details and requires explicit consent for sensitive information.

## Generated widgets

- Raises arbitrary schema ceilings while retaining bounded resource and security limits.
- Accepts input-style nodes on Home widgets as state readouts/actions instead of rejecting the entire program.
- Removes the six-row renderer truncation.
- Adds bounded multiline text wrapping and ellipsis instead of flattening every line into one clipped row.
- Exposes input actions in the Home-widget action strip.

## Verification

Third-party license verification, offline license generation, release unit tests, release lint, and the optimized release APK build passed before publication.
