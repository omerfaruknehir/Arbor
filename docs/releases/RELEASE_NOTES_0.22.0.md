# Arbor 0.22.0

Version code: `164`

This release ships the adaptive Home-widget renderer and substantially upgrades Arbor's cross-chat memory management.

## Memory management

- Replaces the flat newest-first memory injection with relevance-aware selection based on the active conversation.
- Enforces strict memory item and character budgets so a large memory library cannot crowd chat history out of the model context.
- Preserves a small recent-memory fallback when no strong topical match exists.
- Adds punctuation-, case-, and whitespace-insensitive duplicate detection while avoiding unsafe merges of negated preferences.
- Adds native memory search and edit tools, and expands memory listing with query, disabled-item, and result-limit controls.
- Makes memory saves report whether an item was created, updated, or merged.
- Adds search, inline editing, enable-all, disable-all, and delete-disabled controls to Memory settings.
- Keeps disabled memories out of ordinary model context and search results unless explicitly requested.

## Home widgets

- Replaces equal-share row and column sizing with intrinsic, content-aware allocation.
- Allows readable adaptive fitting down to an 11sp compact-widget floor instead of rejecting every layout below 13sp.
- Makes compact list and metric nodes use their available space efficiently.
- Tests compact, standard, and expanded launcher canvases instead of the artificial 84dp compiler canvas.
- Reduces launcher chrome so generated content receives more usable height.
- Accepts common generated style aliases such as `fontWeight` and `size`, then stores canonical Arbor schema.
- Adds regression coverage for six-row prayer lists and genuine three-row prayer layouts.

## Verification

The release workflow runs third-party license verification, offline license generation, release unit tests, release lint, optimized APK and AAB assembly, instrumentation APK assembly, and APK signature verification before publishing the GitHub release.
