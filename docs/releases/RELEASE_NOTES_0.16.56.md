# Xylune 0.16.56

- Fixed a streaming-state capture bug which left an active rich-message renderer permanently stuck on its first visible snapshot. Tables now switch from partial Markdown into the native live table renderer as soon as the separator row arrives, and all content appended below a table appears without reopening the chat.
- Kept the incremental Markdown parser serial, conflated, and off the main thread by observing updated Compose state rather than restarting a parser for every token.
- Increased auto-follow catch-up speed while retaining proportional easing near the bottom, so ordinary streaming remains smooth and large table/tool insertions no longer take several seconds to catch up.
