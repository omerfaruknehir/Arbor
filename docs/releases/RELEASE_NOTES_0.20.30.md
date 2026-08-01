# Arbor 0.20.30

- Clarifies the widget DSL at prompt time and in `WIDGETS.md`: UI nodes belong in `children`; list/chart `items` and choice `options` are bounded data records with exact fields.
- Adds targeted compiler repair guidance for common `items`/`options` shape mistakes, including the previously opaque `unknown fields: text, type` failure.
- Adapts DeepSeek DSML tool-call serialization into Arbor's normal validated native-tool path and suppresses raw protocol markup, including malformed or unapproved calls.
- Reserves the Share action width while the chat header collapses so long titles cannot overlap Share or More.
- Adds regression coverage for compiler guidance, DSML streaming, and collapsed-header geometry.
