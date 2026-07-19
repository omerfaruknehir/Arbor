# Arbor 0.16.22

- Ported Agora-style direct-content edge blur: blur radius is fixed while content scrolls through it.
- Replaced the old 17-tap kernel with an effective 33-tap separable Gaussian using paired bilinear samples.
- Replaced predictive/full-page navigation with a single-destination transition; outgoing and incoming pages are never composed together.
- Retained per-destination saveable state while eliminating interrupted-transition page overlap.
