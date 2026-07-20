# Arbor 0.16.53

## Streaming layout integrity

- Fixes the remaining apparent “newline between tokens” bug. The underlying text was contiguous, but the live-tail fade wrapper was measuring at wrap-content width and forcing the Markdown view into a narrow column.
- Streaming blocks now occupy the complete response width and pass the parent width constraints into Android text rendering.

## Table freeze protection

- Live Markdown tables no longer invoke Markwon, table splitting, or table-span layout while rows are arriving.
- Streaming tables use a bounded selectable source preview, update at a reduced cadence, and catch up in larger batches.
- Small tables render normally once complete. Very large completed tables remain on the bounded lightweight renderer to prevent a post-stream UI lock; the full source remains available through message copy.

## Scrolling and gestures

- Auto-follow now moves with a slower frame-paced, speed-limited easing curve rather than an immediate per-layout correction.
- Initial and manual “go to latest” positioning account for the measured composer before locking the list position.
- Horizontal tables, code and diagrams require a more deliberate sideways drag, allowing normal diagonal gestures to continue scrolling the chat vertically.
- The left drawer no longer installs an aggressive full-screen swipe recognizer; use the menu button to open it, and the scrim, Back, or a selection to close it.
