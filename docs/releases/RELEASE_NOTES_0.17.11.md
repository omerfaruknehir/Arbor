# Arbor 0.17.11

- Reverts the backdrop blur and panel-mask renderer to the exact 0.17.8 implementation after the 0.17.9 and 0.17.10 blur experiments produced worse visual results on-device.
- Retains all later live Python/shell output, Running-state UI, popup gesture handling, and other post-0.17.8 fixes.
- No database or migration changes.
