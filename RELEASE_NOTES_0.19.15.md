# Arbor 0.19.15

This release replaces the complete Arbor slider stack. The new control is not a wrapper around Material Slider: it has one pointer tracker, one magnetic state machine, one visual spring, and one Canvas renderer, so no second implementation can override release animation or draw duplicate markers.

Continuous sliders remain fully continuous. Thinking and stepped sliders move continuously during drag and settle through a damped spring only after release. Edge Smoothness keeps its two hard-edge anchors with hysteresis and does not capture any value in the real softness range. Every Arbor slider now shares the same Material-style in-track ticks, rounded track, thumb, haptics, accessibility behavior, RTL mapping, and drawer-gesture priority.
