# Xylune 0.16.45

- Freeze the complete loaded message window after the user detaches from the bottom, preventing streamed tool/Python insertions from moving the viewport.
- Continue synchronizing terminal message status while detached, so working cards can finish and collapse without exposing growing content.
- Anchor card-height animations by stable item key and screen offset rather than paging index.
- Replace 20 Hz timer-based text commits with frame-aligned 30 Hz adaptive streaming updates.
- Remove nested alpha animations from active working cards; each new event now owns a single render-layer fade.
