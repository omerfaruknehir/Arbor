# Arbor 0.17.26

## Motion-path optimization without blur-quality reduction

### Chat scrolling

- Preserve the exact Arbor 0.17.18 adaptive AGSL shader, sample positions, three-pass order, full-resolution input, masks, edge-softness curve, and tint geometry.
- Replace three full-screen filtered passes with progressively cropped full-resolution pass layers for only the visible top and bottom glass regions.
- Record the Compose source once and replay it into the required blur dependency regions instead of filtering the entire viewport.
- Include the complete remaining vertical support for every pass, so the crop does not truncate any sample used by the 0.17.18 kernel.

### Interactive drawer

- Separate high-frequency drawer offset from the low-frequency visible/closed state read by `ArborApp`.
- Apply drag progress inside draw/layer state so opening and closing the drawer no longer recomposes the application and chat trees on every pointer frame.

### Navigation

- Remove navigation-transition state from kept-alive page composition.
- Stabilize the screen-content composable passed to the navigation host.
- Keep transition transforms in parent render layers, preventing the parked Chat page from recomposing during Settings navigation animations.

### Power and compatibility

- No forced refresh rate, sustained-performance mode, clock request, motion-time blur reduction, downsampling, or sample-count reduction.
- Package IDs, Room data, conversations, providers, credentials, workspaces, settings, and existing debug signing compatibility are preserved.
