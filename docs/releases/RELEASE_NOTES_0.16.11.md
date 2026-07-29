# Arbor 0.16.11

- Fixed IME/keyboard composer geometry so the gradual blur and tint follow the visible composer instead of remaining pinned behind the keyboard.
- Anchored bottom blur to the measured composer edge, including keyboard-open, attachment, and expanded-composer layouts.
- Kept empty-chat welcome content inside the usable area above the keyboard and composer.
- Moved floating controls outside the blurred message layer so the go-to-latest button remains sharp.
- Delayed and shortened bottom chrome blur activation to avoid blurring too far into the conversation.
- Replaced reverse-list nested-scroll header behaviour with deterministic list-position collapse, preventing stale or inverted chat headers.
- Made the selected-model control persist while the large header collapses.
- Reset chat-local menus and list position cleanly when switching conversations.
- Added animated page transitions across Chat, Search, Settings, Local Code Execution, and Terminal.
- Added gesture-progress predictive back previews for top-level pages and nested Settings pages; cancelling the gesture restores the current page smoothly.
- Preserved Android/Samsung's native back-to-home preview by leaving the Chat root unhandled.
- Enabled Material predictive-back behaviour for the conversation drawer and made Local Code Execution/Terminal back buttons return consistently to Settings.
