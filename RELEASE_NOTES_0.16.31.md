# Arbor 0.16.31

- Replaced snap-to-bottom streaming with one frame-paced auto-follow controller.
- Freezes the active streaming message after the user detaches, preserving the viewport exactly until the true bottom is reached again.
- Coalesces streaming text updates before Markdown parsing.
- Removed per-token TextView span animators and uses a shared render-layer fade with ModulateAlpha for text, tools, code, and reasoning blocks.
- Keeps the predictive-navigation fixes from 0.16.30.
