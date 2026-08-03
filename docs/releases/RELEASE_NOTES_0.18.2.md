# Xylune 0.18.2

## Stable artifact-free glass renderer

- Replaced the artifact-prone AGSL resample, mask, and composite shaders.
- Blur now uses a fixed three-level panel-local pyramid with one low-resolution platform blur using `CLAMP` edge treatment.
- Blur and overlay are recorded into one premultiplied rounded-panel layer.
- Edge softness is applied exactly once to that combined layer using `DECAL`, so blur, tint, corners, and softened geometry cannot diverge.
- Removed double masking, rotated sampling taps, transparent-black fallback colors, and RGB un-premultiplication.
- Overlay opacity remains absolute; 100% is opaque throughout the panel body, with only the configured edge-softness band transitioning.
- Blur strength and edge softness remain fixed during scrolling, drawer motion, streaming, and navigation.
- Preserved the 0.17.26 drawer/navigation/recomposition isolation and all stored appearance preference keys.
