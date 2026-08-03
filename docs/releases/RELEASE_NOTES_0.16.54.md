# Xylune 0.16.54

- Reworked streaming chat behavior after auditing Agora's actual Compose implementation.
- Auto-follow now distinguishes a real finger drag from Xylune's own programmatic scrolling, reattaches on immediate send, and catches up at a capped smooth velocity instead of jumping to the last item.
- The measured composer remains the bottom boundary for streamed content.
- Streaming Markdown is conflated and parsed serially on `Dispatchers.Default`; the UI no longer parses every database token update.
- Tables flush at 250 ms, render as proper Markwon/GFM tables while within a safe live budget, and switch to a bounded aligned table preview only when oversized.
- Markwon syntax parsing is performed off the main thread before spans are applied.
- Child bring-into-view requests are swallowed inside rich messages so tables, text selection, and working cards cannot unexpectedly relocate the chat.
- Horizontal scrolling uses a less extreme touch threshold, preserving vertical scrolling without making sideways table/code navigation unnecessarily difficult.
