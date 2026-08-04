# Xylune 0.23.15

## Chat title scroll synchronization

The chat header is now a direct projection of the message list's real scroll position. It collapses and expands consistently during finger scrolling, flings, automatic following after Send, streaming updates, search navigation, restored chat positions, and programmatic viewport corrections.

This also removes the separate nested-scroll owner and ignores stale saved app-bar offsets, preventing the expanded title from remaining over newly positioned messages.
