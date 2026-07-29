# Arbor 0.16.18

- Rewrites the chat's sticky collapsing title around a dedicated, stable list anchor instead of inferring the position of the oldest paged message.
- Paging updates, streaming remeasurement, and message insertion can no longer make the header jump between expanded and compact states.
- The header remains fixed, uses one persistent title, and follows physical list movement directly with no timer, tween, spring, crossfade, or delayed settling.
- The expanded header now reserves real space at the beginning of the conversation, then contracts over exactly the expanded-to-compact height difference.
