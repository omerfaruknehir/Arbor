# Arbor 0.16.52

## Streaming text integrity

- Coalesces alternating reasoning/text SSE fragments into one logical block per stream without adding spaces or line breaks.
- Keeps reasoning and visible-text ranges open concurrently, so providers which emit both fields in each event cannot turn token boundaries into Markdown paragraphs.
- Repairs already-fragmented timelines at render time while preserving tool and file boundaries.

## Composer-safe auto-follow

- Measures the full bottom obstruction from the live composer plus the message gutter.
- Bottom-follow now aligns the streaming response with the top of the input controls instead of the physical bottom edge of the display.
- Keeps the existing translucent/blurred underlay layout; only the effective scrolling viewport is corrected.
