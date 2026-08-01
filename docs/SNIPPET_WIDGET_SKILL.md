# AI skill: Arbor snippets and widgets

Treat `GeneratedContentCapabilityRegistry` as authoritative. Its compact widget manifest is injected on every request, and the full schema is injected whenever recent conversation context indicates a widget task.

## Decision rule

1. Use `arbor-snippet` for an interaction that belongs inside the current chat answer.
2. Use `arbor-widget` when the user asks for an Android Home-screen widget, launcher surface, persistent dashboard, glanceable tracker, or similar outside-app experience.
3. Follow-up language such as “make it cleaner” or “add live updates” inherits the widget context from recent turns; do not forget the capability merely because the latest sentence does not repeat the word “widget”.
4. When the request is satisfiable, generate the fenced program. Do not merely explain that widgets are possible, and do not claim Arbor lacks the capability.
5. Never convert a snippet into a widget merely because it is interactive. Never use removed fences or category-specific root types.

## Compile-before-display contract

A Home-widget response is not immediately exposed to the user. Arbor compiles it into the typed declarative runtime, preflights declared public HTTP JSON sources (including safe redirects and binding paths), executes every action, and renders initial/post-action states at representative launcher sizes. Compiler diagnostics are returned to the repair model, which must emit a complete replacement. Only a passing candidate is displayed.

For HTTP query values, use `{{urlencode:key}}`. Give every HTTP binding a useful fallback. Prefer the final JSON URL; if a redirect is unavoidable, declare every HTTPS destination origin. A deterministic 4xx response, incompatible JSON, missing binding, clipped text, empty canvas, or undersized typography is a compile failure, not something to hide.

## Widget design quality

- Lead with one glanceable primary value or status, normally around 28–32sp.
- Use at least 15sp for ordinary content and 13sp for supporting labels. Keep supporting labels short and make the default state honest; use `—`, `Not updated`, or another explicit fallback instead of invented live values.
- Use two to four high-value launcher actions. Avoid duplicating decorative controls or exposing actions that do nothing.
- Prefer a fully local widget. Add exact network, location, folder, and background-refresh grants only when the user’s requested behavior requires them.
- Match every data source to its capability and explain the reason in plain language.
- Design for both the compact compiled viewport and expanded resize state: important content first, secondary details later, no keyboard-dependent input.
- Use general nodes and named actions to compose the requested experience. Widgets are programmable surfaces, not a fixed template catalogue.
