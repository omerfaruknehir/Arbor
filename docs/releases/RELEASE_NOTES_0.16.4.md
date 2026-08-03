# Xylune 0.16.4

- Added a shared automatic syntax-highlighting and lint-decoration pipeline for code shown throughout the app.
- Code diagnostics now color and underline the affected token in fenced code blocks, tool inputs, generated-source recovery views, diagram source fallbacks, and terminal history.
- Python, shell, terminal, and provider-header JSON editors now highlight syntax and lint diagnostics while typing.
- Existing distro-backed Python and shell lint results are reused when available; lightweight static lint is used immediately while deeper linting is running.
