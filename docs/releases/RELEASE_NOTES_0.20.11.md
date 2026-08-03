# Xylune 0.20.11

## Performance overlay

- Added an **Overlay scale** slider from 60% to 200%.
- Replaced the vague single “Likely” diagnosis with a ranked cause profile:
- primary cause
- optional secondary cause
- confidence percentage
- severity
- concrete evidence from the sampled interval
- Added explicit sibling pointer sharing without consuming pointer changes. Scroll, tap, drawer-edge, and stylus input can begin directly over the overlay and continue to the content underneath.

## Compatibility

- Developer settings remain available in the optimized release build.
- Existing chats, credentials, providers, OAuth sessions, workspaces, Linux environments, and app data remain compatible.
