# Xylune 0.18.1

## Blur and edge geometry repair

- Replaced the separate Compose vertical tint fringe with a panel-local AGSL tint layer.
- Blur and tint now interpolate the exact same rounded-panel signed-distance function.
- Edge softness is centered on the nominal rounded edge for both blur and overlay.
- Removed corner and straight-edge geometry divergence between softened blur and tint.
- Invalid transparent Kawase taps now fall back to the valid center sample instead of contributing transparent black.
- The final blur sample is un-premultiplied before saturation, contrast, brightness, and highlight adjustments, preventing dark/black backdrop contamination.
- Preserved the fixed three-level pyramid, continuous blur controls, 0.17.26 drawer/navigation optimizations, settings keys, Room schemas, package identity, and debug signing compatibility.

Final visual confirmation still requires installation on the Galaxy S23+.
