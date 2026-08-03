# Xylune 0.22.1

Xylune 0.22.1 improves memory management and repairs navigation state on phones.

## Memory management

- Gives memory text the full card width and moves controls into a separate metadata row.
- Adds enabled/disabled filtering, category filtering, sorting, selection, and bulk enable/disable/delete.
- Adds confirmation before destructive single-item, selected-item, or disabled-item cleanup.
- Shows whether a memory was saved manually or from a chat, plus its update time.
- Prevents a small memory library from being injected wholesale into unrelated chats.
- Keeps a small set of profile/preference memories available as baseline context and prioritizes relevant or same-chat memories.
- Merges exact duplicates even when their categories differ, while fuzzy duplicate matching remains category-scoped.

## Navigation and scrolling

- New Settings destinations start at the top; Back restores the prior page position.
- Settings and chat titles are derived from the actual restored scroll position, preventing expanded titles over scrolled content.
- Opening Settings from the drawer starts at the Settings root instead of reviving a stale nested page.
- Chat switching restores each chat's own saved position without inheriting another chat's app-bar state.

## Predictive Back

- An open drawer now owns the complete predictive-back gesture.
- Back progress directly closes the drawer, cancellation restores it, and page navigation cannot steal the gesture halfway through.
