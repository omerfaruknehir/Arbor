# Xylune 0.20.34

- Fixes the remaining DeepSeek DSML leak after Xylune exhausted the tool-execution budget and entered its tools-disabled final synthesis turn.
- Keeps a separate protocol-firewall allowlist after callable tools are removed, so stale `compile_widget`, `web_search`, and `web_fetch` DSML can still be recognized without making those tools callable again.
- Quarantines the complete finalization response until it is classified; protocol text is rolled back before it can reach the message or title.
- Retries once with an explicit no-tools finalization instruction. Repeated protocol output is rejected cleanly and is never shown or executed.
- Adds an end-to-end transport regression test reproducing the prayer-time `web_fetch` DSML sequence from the device screenshot.
