# Xylune 0.16.48

## Stable card motion

Manual expansion pins the card header in place for the full animation, so the card always grows downward. Manually collapsing a card that occupied most of the viewport centers the collapsed card after the shrink completes. Automatic working-card state changes are deferred while a precomposed card is outside the viewport, preventing invisible below-view animations from moving the visible conversation.

## Bottom-scroll stability

A drag at the true latest position no longer detaches and reattaches the live paging snapshot when the list consumes no scroll distance. This removes the flash seen when trying to scroll farther down while already at the bottom.

## Streaming tables

Incomplete final table rows are normalized into the existing table structure while streaming instead of switching repeatedly between plain Markdown and table blocks. Table width is locked for the streaming lifetime and resolved to its final content width when the response completes, reducing TextView recreation and reflow flicker without changing final rendering.
