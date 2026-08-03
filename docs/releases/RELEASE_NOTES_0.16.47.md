# Xylune 0.16.47

## Table rendering

Markdown tables no longer introduce stray whitespace at the boundaries between a table and adjacent paragraphs. The parser now distinguishes structural separators from escaped pipes and pipes inside inline code, preventing phantom columns and malformed spacing.

## Streaming tool calls

OpenAI-compatible and Anthropic providers now expose tool calls while their names and JSON arguments are still streaming. Xylune creates one provisional tool card immediately, updates its code/query/command/URL/path preview incrementally, and reuses that card when execution starts. Gemini calls appear as soon as Gemini emits the function-call part.

Execution still waits for a complete, valid final call. Live code previews skip linting until the call is complete to avoid expensive per-fragment parsing.
