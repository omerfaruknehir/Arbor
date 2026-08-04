# Xylune 0.23.18

## Real first-token streaming

The DeepSeek-compatible transport no longer buffers the complete answer until the HTTP stream ends. DSML tool syntax remains hidden and validated incrementally, while ordinary text and reasoning are emitted as soon as they are provably not part of a split tool marker.

The DSML filter also no longer hides a fixed 256-character window. It keeps only a genuinely ambiguous marker suffix, so normal prose can appear from the first provider delta.

Generation work is requested as expedited, automatic context compression never performs a hidden auxiliary-model request before the selected model, and Deep Research initialization is folded into the first visible request instead of using a preliminary invisible generation.
