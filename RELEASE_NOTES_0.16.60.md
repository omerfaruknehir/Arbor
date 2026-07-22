# Arbor 0.16.60

- Stabilize the chat viewport through every Working-card expansion and collapse instead of correcting only after a manual collapse.
- Manual expansion pins the card header so the card grows downward; manual collapse keeps the header fixed and still centers very large cards after the animation.
- Automatic card changes pin the latest-message bottom while following, preserve downstream content when the card is above the detached viewport, and avoid touching the list when the card is below it.
- Suspend the nonlinear streaming follower while a Working-card mutation owns the viewport, preventing the two scroll controllers from fighting each other.
- Add regression tests for the Working-card anchor strategy.

