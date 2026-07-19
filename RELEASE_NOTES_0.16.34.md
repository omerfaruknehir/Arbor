# Arbor 0.16.34

- Fixes `performMeasureAndLayout called during measure layout` while a streamed message changes height.
- Removes direct `LazyListState.dispatchRawDelta()` calls from `onSizeChanged`.
- Coalesces message-height changes and applies compensation from the next frame, after layout completes.
- Defers compensation until an active user scroll/fling has settled.
- Keeps the persistent follow controller alive by applying compensation outside the LazyList scroll mutex.
- Stops writing Compose snapshot state for every streamed message-height measurement.
