# Xylune 0.19.13

This release further stabilizes native Gaussian panel rendering during scrolling by forcing a complete offscreen source capture and widening the physical-pixel mask transition. It adds a persistent 96–320 dp bottom-panel height control, applies it live to the composer, and provides an exact-height native-blur preview while the slider moves.

Slider magnets use a wider, stronger well while retaining enough visible distance for a real damped spring settle after release. Drawer releases now carry a bounded fraction of finger velocity into the settle spring, and the collapsed chat model tag sits closer to the title without changing its expanded position.
