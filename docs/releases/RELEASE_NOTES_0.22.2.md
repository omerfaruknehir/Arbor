# Xylune 0.22.2

- Give the Android IME first ownership of predictive Back while the keyboard is visible.
- Prevent drawer and page navigation from consuming the keyboard's Back gesture.
- Replace Search's oversized collapsing header with a compact, pinned, IME-aware search field and clear empty states.
- Remove competing nested-scroll ownership that made fully collapsed chat and Settings titles snap back to expanded.
- Keep a collapsed Settings title stable through transient pre-measure and IME-resize states.
- Parse Google authorization result data before classifying the account picker as canceled.
