# Arbor 0.16.20

- Removed token-by-token animated scrolling. Streaming now follows the latest response without launching a new scroll animation for every update.
- Added explicit generation scroll lock: dragging away detaches immediately, detached text is position-compensated while the response grows, and reaching the exact bottom re-enables follow mode.
- Increased the empty breathing room above chat messages.
- Preserved chat/list and expandable working/tool state across ordinary and predictive navigation transitions instead of recreating it during animations.
- Moved predictive-back progress reads into `graphicsLayer`, preventing full screen-tree recomposition on every gesture frame.
- Standardized thinking, tools, generated outputs, recovery cards, and streaming indicators on the same fade-only motion system; removed large subtree size animations.
- Throttled streaming Markdown rendering and token fades, cached Markwon, and skipped unchanged Android `TextView` updates.
- Replaced the two-pass seventeen-tap Gaussian edge blur with a single-pass nine-sample radial tent blur. It remains a real gradual backdrop blur, while substantially reducing GPU texture work during scrolling.
