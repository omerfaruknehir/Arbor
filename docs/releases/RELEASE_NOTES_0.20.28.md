# Arbor 0.20.28

## Compiled AI widgets

- Home-widget definitions are now withheld until Arbor compiles them into its typed declarative runtime.
- The compiler validates the schema/capability graph, executes every action, preflights live public HTTP JSON sources, verifies binding paths, and renders initial and post-action states at representative launcher sizes.
- Compiler diagnostics are sent back through the bounded auxiliary-model repair loop; only a passing replacement is shown to the user.
- Manual widget-source edits use the same compile gate.

## HTTP reliability

- Safely follow up to five HTTPS redirects while rechecking exact declared origins and blocking private/local addresses at every hop.
- Seed representative location and folder state during compilation so location-dependent APIs are not incorrectly tested with blank coordinates.
- Reject deterministic HTTP 4xx responses, invalid JSON, and missing live binding paths before display, while retaining honest fallbacks for transient network failures.
- Add `{{urlencode:key}}` for safe query-parameter interpolation and return sanitized server response details to the repair model.

## Widget readability

- Replace fixed pseudo-density rendering with the device's actual density and font scale.
- Compile compact and expanded launcher layouts and reject clipped, cramped, empty, or sub-13sp output.
- Increase default content, metric, title, subtitle, status, and action typography; enlarge padding and touch targets; and render post-action states to catch conditional-layout failures.
