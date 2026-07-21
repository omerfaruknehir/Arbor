# Arbor 0.16.57

Auto-scroll no longer moves at one fixed catch-up speed. It now accelerates exponentially according to both how far the live tail is behind and how long it has remained off-screen.

- Small corrections remain eased and controlled.
- Large streamed insertions can accelerate up to 48,000 px/s once measurable.
- Off-screen tail seeking can accelerate up to 72,000 px/s.
- The transition from fast seeking to final alignment is continuous rather than an abrupt snap.
