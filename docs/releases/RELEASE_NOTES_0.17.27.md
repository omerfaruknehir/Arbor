# Xylune 0.17.27

- Replaces 0.17.26's progressively cropped three-pass blur with a fixed-extent, panel-local dual-Kawase-style renderer.
- Records Compose content once per frame and reuses the same source layer for normal rendering and both glass panels.
- Uses fixed overscan, two or three bounded downsample/upsample levels, bilinear reconstruction, and final-only rounded cropping.
- Preserves underlying color variation with restrained saturation, contrast, brightness, tint, edge softness, and edge highlight.
- Keeps blur quality fixed during scrolling, drawer motion, navigation, streaming, keyboard changes, and window-size changes.
- Retains the 0.17.26 drawer, kept-alive navigation, and profiler-overlay recomposition isolation.
- Separates display refresh, Choreographer callbacks, rendered frames, and presented-frame availability in the developer profiler.
- Adds capture/replay, effect rebuild, processed megapixel, pyramid-level, allocation, GC, and frame-stage diagnostics.
- Adds deterministic renderer geometry tests and a debug moving-backdrop visual stress scene.
- Does not force refresh rate, display mode, performance clocks, wake locks, or motion-time quality reduction.
- Maps overlay opacity absolutely: 0% is transparent and 100% is fully opaque instead of multiplying the theme tint's pre-existing alpha.
- Keeps the complete 68 dp edge-softness range but centers it on the nominal rounded panel boundary, splitting the transition evenly inside and outside.
- Extends the fixed panel capture support by the outward feather half so soft edges sample current backdrop content without clipping, stale pixels, or inward-only erosion.
