# Xylune 0.20.35

This release repairs repeated **Response paused** loops after a provider returns an output-length finish reason.

## Fixed

- Xylune now automatically requests up to three continuation segments when each segment makes progress.
- A zero-progress length loop is stopped and reported as a provider error instead of offering an endless Continue cycle.
- Manual Resume uses the limits currently shown in Chat configuration, so raising the output ceiling now affects an already-paused response.
- DeepSeek prefix completion receives the exact saved assistant prefix. Internal reasoning/tool metadata is no longer appended after the visible answer and mistaken for text to continue.

The provider/model and prompt identity remain pinned to the original request; only user-adjustable runtime limits are refreshed on Resume.
