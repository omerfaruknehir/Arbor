# Arbor 0.16.26

This build returns to the 0.16.19 UI and blur baseline, then adds only focused chat-streaming fixes:

- Increased the empty space above chat messages by 16 dp.
- Replaced per-token animated auto-scroll with a lightweight explicit follow lock.
- Scrolling away during generation releases follow mode; reaching the true bottom after the gesture settles enables it again.
- While follow mode is released, growth of the active response is compensated so the visible text remains anchored instead of moving upward.
- Standardized streaming text, reasoning, tool, code, widget, and result appearance on one 180 ms fade-only motion specification.
- Removed size-changing default visibility transitions from working/tool expansion paths.
