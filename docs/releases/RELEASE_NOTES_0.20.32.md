# Xylune 0.20.32

- Implements the recommended client-side mitigation for DeepSeek's model-side plain-text tool-call failure: detect the failed format, roll back provisional assistant output, and retry once with a strict correction prompt.
- Adds a preventive DeepSeek system reminder requiring structured `tool_calls` instead of DSML, function names, or JSON serialized into assistant content.
- Preserves normal streaming during the first attempt; leaked preamble or protocol text is atomically removed before the transparent retry.
- If the corrected retry still fails, Xylune recovers only exact allowed trailing calls with valid JSON and routes them through the normal native-tool validator.
- Accounts for retried token usage and adds regression tests for DSML, trailing plain-text calls, code-fence false positives, and correction-prompt injection.
