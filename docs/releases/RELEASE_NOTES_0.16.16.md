# Arbor 0.16.16

- Makes Arbor's built-in core system prompt immutable, versioned with the app, and automatically updated by app releases; legacy editable prompt copies are ignored and removed from defaults.
- Keeps reusable custom instruction profiles as a separate tone/workflow layer that cannot replace Arbor's capability and tool protocol.
- Adds an explicit model-only Deep Research planning pass so the model creates and reports a task-specific roadmap before normal research begins.
- Stops showing a guessed or indefinitely waiting roadmap card when no valid model state exists.
- Retains the 0.16.15 navigation, streaming, text-selection, horizontal-table, top-blur, and Ubuntu stream-shutdown fixes.
