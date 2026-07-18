# Arbor 0.16.14

This release includes all 0.16.13 header, Deep Research, source-preview, and table-link fixes, plus an Android 16 native-diagram crash fix.

## Native diagram crash

- Mermaid node labels are parsed with a small delimiter scanner instead of a delimiter-heavy regular expression.
- Android ICU no longer needs to compile the pattern that caused `PatternSyntaxException` while rendering flowcharts.
- Graphviz bracket expressions use explicit closing-delimiter escapes.
- Malformed generated diagrams degrade safely instead of crashing the app.
