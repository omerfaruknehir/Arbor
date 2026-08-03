# Xylune 0.20.33

- Quarantines DeepSeek tool-turn content and reasoning until Xylune can classify the completed response, preventing raw DSML from ever being committed to chat.
- Parses DSML emitted through either `content` or `reasoning_content`, including HTML-escaped brackets, Unicode pipe glyphs, zero-width format characters, and whitespace-separated fences.
- Detects unparseable DSML-shaped output and retries with the strict structured-tool correction prompt; after the retry, unsafe protocol text is rejected rather than displayed.
- Expands DeepSeek detection across provider id, provider name, base URL, model id, and model display name.
- Adds regression tests reproducing the on-device prayer-time `web_fetch` failure in the reasoning channel and escaped Unicode fence variants.
