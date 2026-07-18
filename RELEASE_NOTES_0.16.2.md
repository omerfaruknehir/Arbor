# Arbor 0.16.2

Hotfix release.

- Fixes secondary screens rendering blank because the translucent collapsing top-bar background consumed the full Scaffold height.
- Retains the corrected Room 12→13 migration using `DEFAULT NULL` for `systemPromptProfileId`.
- Preserves existing encrypted chats and settings.
