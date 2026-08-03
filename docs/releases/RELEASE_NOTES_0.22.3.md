# Xylune 0.22.3

- Correct app-bar scroll physics so content tracks the finger one-to-one while the title collapses or expands.
- Restore Material nested-scroll consumption for chat and Settings.
- Remove continuous list-offset-to-app-bar synchronization that added app-bar inset movement on top of the list's own scroll.
- Restore saved title collapse once when reopening a chat or Settings page, then leave live movement to a single scroll owner.
