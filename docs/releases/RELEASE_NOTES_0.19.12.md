# Arbor 0.19.12

This release makes slider snapping visually smooth after release by animating the thumb locally and committing only the final snapped value, avoiding per-frame preference writes. Magnetic pull is stronger, and explicit Edge Smoothness and Thinking anchors use the same small Material tick colors and in-track placement as ordinary discrete sliders. The Thinking popup now dismisses when tapping outside it.

Blur panel boundaries are locked to physical pixels and receive a stable one-pixel mask antialias, reducing edge flicker while scrolling. The normal white boundary highlight is removed. Developer Settings can optionally draw bright-red top and bottom blur-boundary guides with adjustable 1–8 dp thickness for diagnostics.
