# Arbor 0.16.32

- Removed the streaming Markdown `conflate()` stage that visibly batched token states.
- Appends incoming Markdown source directly to the existing editable TextView buffer on the UI frame where it arrives.
- Styles only newly completed Markdown blocks; the active unfinished block remains append-only and is never reparsed for every token.
- Performs one full Markdown reconciliation only when streaming finishes.
- Gives each appended batch its own alpha span so later tokens do not restart or collapse earlier fades into two or three visible frames.
- Removes expired fade spans independently while one shared frame callback drives redraws.
- Smooths newly wrapped response lines with render-layer translation, avoiding the one-frame upward jump caused by LazyColumn remeasurement.
- Keeps one persistent follow controller and removes the competing Go-to-latest animation.
