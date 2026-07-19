# Arbor 0.16.30

- Replaced predictive navigation's preview/AnimatedContent hand-off with stable page slots.
- A predictive destination is composed exactly once and promoted in place on commit.
- Fixed committed back gestures visibly returning the source page to center before removal.
- Fixed rapid navigation crash: `Key HOME was used multiple times`.
- Treats cancellation after predictive progress completion as a committed back, never a rollback.
- Interrupted ordinary transitions settle on the latest requested destination without leaving overlapping pages.
