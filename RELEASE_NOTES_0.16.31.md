# Arbor 0.16.31

- Replaced token-by-token chat snapping with one persistent, velocity-limited auto-scroll controller.
- Holds a single LazyList scroll mutation during catch-up instead of reacquiring the scroll mutex every frame.
- Preserves manual follow-lock release and automatic re-lock only at the true bottom.
- Moved streaming Markdown parsing off the main thread and limited it to two parser workers.
- Added a stateful append-only Markdown renderer: completed block prefixes are parsed once and only the unfinished tail is reparsed while generation continues.
- Mutates the existing TextView editable buffer in place instead of replacing the full message for every token.
- Added an incremental top-level fence scanner so complete Markdown/code blocks are promoted once rather than rescanning the whole response.
- Tool and thinking plain text now append directly to their existing buffers.
- Replaced per-token ValueAnimator recreation with one frame-driven alpha span and non-stacking tail fades.
- Performs one complete Markdown reconciliation when streaming ends to preserve exact CommonMark output.
