# Arbor 0.16.51

## Chat viewport rewrite

- Replaced the reverse-layout/frozen-snapshot scroll stack with one chronological keyed list and one explicit follow state.
- Removed continuous viewport correction, per-card frame compensation, competing auto-scroll jobs, and detached message copies.
- Bottom following now reacts to measured layout overflow and scrolls only the newly added height.
- User scrolling detaches only after real consumed movement; no-op overscroll at the bottom does nothing.
- Manual Working-card expansion grows downward. Large manually collapsed cards are centred once after their animation.

## Incremental streaming rendering

- Added an append-only Markdown block parser. Completed paragraphs, code fences, tables, lists, and other completed regions retain stable identities; only the unfinished tail is reparsed.
- One application-wide Markwon instance is reused across message blocks instead of rebuilding parsers while scrolling through history.
- Streaming code fences are recognized immediately and remain non-executable until closed.
- Completed UI blocks no longer re-render when later tokens arrive.

## Timeline and freeze prevention

- Streaming text and reasoning no longer duplicate the full growing answer inside `timelineJson`.
- Timeline JSON now stores compact source ranges for text/reasoning segments and changes only at actual timeline boundaries such as tools or a text/reasoning transition.
- Ordinary stream flushes append only the new text/reasoning delta, avoiding repeated full-string binding plus full JSON serialization, decoding, and event reconstruction.
- Deep Research state extraction is skipped entirely for ordinary responses.

## Code display

- Removed code linting and compiler-style diagnostics from chat blocks, local editors, and agent tool execution.
- Code remains syntax-coloured and selectable.

## Compatibility

- Existing content-based timeline entries still render normally.
- New compact timeline entries are materialized from the message content/reasoning fields at display time.
