# Arbor 0.18.4

## Strong-blur reconstruction repair

- Replaces the two-pass quarter-resolution blur with a fixed four-pass half-resolution chain.
- Every nonzero blur strength uses the same topology: full capture -> 1/2 scale -> four equal Gaussian passes -> full-resolution reconstruction.
- Each half-resolution pass uses `requestedRadius / 4`, preserving the requested combined full-resolution sigma without exposing a coarse quarter-resolution grid above roughly 20%.
- No radius, scroll, animation, navigation, or thermal threshold changes the number of levels or passes.

## Edge-softness boundary repair

- Removes the `DECAL` RenderEffect from the completed panel layer. That effect blurred every side, including the physical top of the top panel, which created a faded strip between the screen top and the panel.
- Uses one cached vertical alpha brush with `BlendMode.DstIn` only across the panel/content boundary.
- The top panel remains fully covered from the physical screen top through the solid portion of the panel and feathers only around its lower boundary.
- The bottom panel feathers only around its upper boundary and remains fully covered through the physical screen bottom.
- The transition remains centered on the nominal content-facing edge: half inside and half outside.
- Blur, tint, and highlight are combined before the single directional alpha mask, preserving identical geometry.
- Exact zero softness still bypasses the mask and keeps the normal rounded corners; nonzero softness remains flat as requested.
- Capture overscan now explicitly includes the outward feather half-span so the mask never exposes an uncaptured or stale backdrop region.

## Compatibility

- Package, application IDs, Room schemas, settings keys, conversations, credentials, workspaces, and debug signing compatibility are preserved.
- The 0.17.26 drawer/navigation/recomposition isolation and normal Android adaptive-refresh/DVFS behavior remain unchanged.
