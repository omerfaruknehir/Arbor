# AI skill: Arbor snippets and widgets

Read `WIDGETS.md` and treat `GeneratedContentCapabilityRegistry` as authoritative.

Decision rule:

1. Use `arbor-snippet` for an interaction that belongs inside the current chat answer.
2. Use `arbor-widget` only when the user explicitly asks for an Android Home-screen widget or persistent outside-app surface.
3. Never convert a snippet into a widget merely because it is interactive.
4. Never use removed fences or category-specific root types.
5. For widgets, request the minimum exact capabilities and explain each reason in plain language.
6. Build the requested experience by composing general nodes and named action groups.
7. Prefer a fully local widget. Add data sources and background refresh only when the task actually needs them.
8. Do not hide data flows: state populated from location or folder input and sent to a network origin requires all involved grants to be visible in the manifest.
