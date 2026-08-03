# Xylune 0.17.4

This release repairs gradual interface blur on devices where the 0.17.2/0.17.3 single-pass Poisson AGSL shader produced no visible blur.

## Fixed

- Restored the previously working two-pass RuntimeShader/RenderEffect chain.
- Rotated the Gaussian axes by 22.5 degrees and kept them orthogonal, removing the old screen-aligned grid/cross pattern without weakening the blur.
- Retained the 36 dp maximum blur radius, expanded top/composer fade regions, and radius quantization.
- Kept all 0.17.3 Back/drawer gesture behavior and 0.17.2 chat scrolling optimizations.

## Compatibility

- Package and application IDs are unchanged.
- Room schema remains version 13.
- Existing conversations, credentials, settings, workspaces, and debug-signing compatibility are preserved.
