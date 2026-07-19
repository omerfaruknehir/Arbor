# Arbor 0.16.32

- Streams message text directly into the existing editable buffer instead of waiting for Markdown rendering.
- Parses and styles completed Markdown blocks once; only the final complete response receives a whole-message reconciliation pass.
- Stops repainting the entire TextView for each fade frame and avoids redundant color/layout setter calls per token.
- Smooths line-height growth with a render-layer translation while follow-lock is active.
- Keeps detached-generation viewport compensation and true-bottom re-lock behavior from 0.16.31.
