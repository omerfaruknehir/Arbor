# Arbor 0.16.33

- Removed the post-layout message translation that caused an up/down flash.
- Compensates newest-message height changes synchronously in the list scroll state.
- Keeps detached streaming content fixed while the latest response grows.
- Uses the same incremental Markwon rendering path during and after streaming.
- Re-renders only the active Markdown block; completed blocks stay committed.
- Limits streaming text fade to one persistent suffix span to avoid per-token draw-span accumulation.
