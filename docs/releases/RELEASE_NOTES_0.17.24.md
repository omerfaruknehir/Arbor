# Arbor 0.17.24

## Full-quality glass without brute-force performance policy

- Restores the 0.17.8 three-direction, nine-tap glass kernel at full input resolution.
- Removes the visibly coarse 0.5x blur input introduced in 0.17.23.
- Records the Compose source once per invalidated frame and replays that display list for normal content and active blur strips.
- Reduces vertical strip support from a conservative 3x radius to the exact 1.8304333x chained-kernel footprint.
- Keeps filtering restricted to the top and bottom strips; no full-screen blur pass returns.
- Does not force 120 Hz, sustained-performance mode, higher clocks, or any battery-draining frame-rate policy.

## Profiler corrections

- Isolates profiler snapshot collection to the overlay leaf so updates no longer recompose the Arbor application root.
- Separates source traversal, layer replay, capture-update rate, effect rebuild rate, and filtered megapixels.
- Stops labeling a healthy 2.5 ms GPU stage as the cause merely because total frame duration missed an 8.33 ms deadline.
- Reports frame pacing / scheduling stalls when no measured stage independently consumes enough of the frame budget.
- Adds regression coverage using the Galaxy S23+ capture values reported for 0.17.23.
