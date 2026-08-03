# Xylune 0.16.13

This release repairs the chat/header geometry, makes Deep Research state explicit and per-response, improves source previews, and removes two failure modes in long agent runs and table-link interaction.

## Header and blur

- The reverse-layout chat title uses the actual physical top reserve. It is expanded at the oldest/start of a chat and compact near the latest messages.
- Title and model-selector movement use sub-pixel translation rather than rounded offsets.
- Settings and other menu pages retain a single moving title and use an 88 dp top blur region; Chat remains intentionally shorter at 64 dp.

## Deep Research

- Research visuals are tied to the immutable generation snapshot, not the chat's current toggle.
- Xylune no longer guesses Plan/Read/Verify states from search counts.
- Models receive an explicit machine-readable research-state protocol with task-specific steps, factual progress, blocked state, synthesis state, and final report state.
- When the tool budget is reached, Xylune requests a final no-tools synthesis instead of immediately inserting a “too many tool calls” failure.

## Links and sources

- Source and file pills are shorter and lower-profile.
- Link, source, file, and search-result previews appear anchored to the tapped element, show metadata and destination, and require an explicit Open action.
- Markdown smart selection is disabled for the rendered link surface, preventing Android 16's invalid SmartSelect animation rectangle crash in tables.
