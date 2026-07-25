# Arbor 0.17.12

- Keeps the 0.17.8 three-axis glass-blur character.
- Raises the real shader sample count from 9 to 15 samples per pass.
- Preserves the known-correct top blur range.
- Derives the bottom blur range from the measured composer overlay height instead of assuming 208 dp.
- Paints blur tint overlays in the scrolling source coordinate space, using the exact same top and bottom ranges as the blur masks.
- Preserves live Python/shell output, Running states, and deferred popup dismissal from later releases.
