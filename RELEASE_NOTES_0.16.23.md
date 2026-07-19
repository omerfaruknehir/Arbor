# Arbor 0.16.23

- Reverted the unproven 33-tap blur kernel to the previously working 17-tap kernel.
- Fixed transparent blur output at large radii by clamping every AGSL sample to the source layer bounds.
- Added a non-zero frosted chrome tint at the expanded scroll position so top and bottom chrome never become fully transparent.
