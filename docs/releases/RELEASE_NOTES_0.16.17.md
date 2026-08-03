# Xylune 0.16.17

- Replaces the chat title overlay with one real collapsing header.
- At the oldest/top of a conversation the header is stable and expanded.
- Scrolling toward newer messages contracts the same title into the compact toolbar with direct, linear scroll-linked movement and no opacity crossfade, tween, spring, or delayed settling.
- The header container now physically changes height and clips its contents, preventing the title or model selector from floating over messages.
- The header stays pinned; only list scrolling is animated, and the title simply follows that scroll.
- The model selector remains available in both states.
