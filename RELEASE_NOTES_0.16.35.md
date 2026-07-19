# Arbor 0.16.35

- Removed all message-height scroll compensation from layout callbacks and deferred frame jobs. Streaming growth now uses only the LazyColumn's own anchoring plus the persistent follow controller, eliminating the up/down flash and the measure/layout crash class.
- Each appended streaming range now owns an independent fade clock. New tokens no longer inherit an almost-finished older fade.
- Markdown tail replacement waits for the active glyph fade to finish, preventing parser/layout work from starving the animation.
- SHOW_WHILE_WORKING sections now collapse as soon as the active reasoning/tool segment finishes. Completed tools no longer remain expanded until the assistant message ends.
