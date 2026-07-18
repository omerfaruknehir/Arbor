# Arbor 0.16.15

- Reduced full-screen navigation transition work and shortened page motion to improve menu responsiveness.
- Reworked streaming auto-follow so token updates no longer repeatedly snap the chat to the bottom.
- Added a short fade-in for newly appended response text.
- Increased the chat top blur region slightly and animated header/blur progress near the conversation start.
- Enabled native text selection in assistant responses while preserving safe link previews.
- Added horizontally scrollable Markdown tables with content-aware widths.
- Hardened Ubuntu stdout/stderr reader threads against Android stream-close interruption crashes.
