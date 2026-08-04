# Xylune 0.23.16

## Chat title collapse hotfix

This release removes the 0.23.15 feedback loop between the message-list position and the changing top-bar inset. Finger scrolling and flings are again handled by one Material nested-scroll owner, so the title no longer jitters, jumps, or reacts to unrelated message-height changes.

Programmatic boundaries are synchronized explicitly: opening a non-empty chat at its latest message, Send, search navigation, and Go to latest all select the correct compact or expanded state even when a short conversation has no physical scroll distance.
