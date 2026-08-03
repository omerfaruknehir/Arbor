# Xylune 0.16.46

- Preserve the exact detached viewport through every frame of working-card expansion/collapse and through newly inserted Python/tool blocks.
- Let manual drag or fling take priority, then adopt the resulting visible message as the new stable anchor.
- Replace large 512-character streaming commits with smaller 96-character transport batches and adaptive token-sized visible micro-batches.
- Keep Markdown rendering frame-limited to 30 Hz and reuse the code-fence regex, improving perceived smoothness without token-by-token parsing.
- Smoothly drain any remaining text after the response completes, eliminating the final large chunk.
- No intentional visual design changes.
