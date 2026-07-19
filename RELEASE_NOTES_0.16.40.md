# Arbor 0.16.40

- Removes the post-layout latest-message translation and deferred height compensation that caused the streamed response to jump up, snap down, then animate again.
- Leaves message growth to the LazyColumn anchor and the single persistent follow controller; the same path is used whether follow-lock is enabled or released.
- Uses one incremental Markwon pipeline during streaming and after completion. Completed blocks remain committed and only the mutable Markdown block is rendered again.
- Finishing a response no longer briefly exposes the raw Markdown tail before the final styled delta is applied.
- Streaming glyph fades use independent, frame-driven cohorts, coalesce only within a two-frame window, and respect Android's animation-duration scale.
- Markdown tail replacement waits for the active fade duration at 1x, 5x, or 10x instead of using an unscaled timeout that cut slowed fades short.
