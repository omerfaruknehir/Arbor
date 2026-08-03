# Xylune 0.20.31

- Recognizes the double-pipe DSML tool-call form emitted by DeepSeek V4-compatible endpoints, including `<||DSML||...>` markers.
- Accepts gateway-formatted DSML markers with whitespace and either ASCII or full-width pipe characters without exposing protocol text in chat.
- Keeps split streaming markers buffered until the complete native tool request is available, then routes it through Xylune's normal validation and execution path.
- Adds regression coverage using the exact `compile_widget` marker shape observed on-device.
