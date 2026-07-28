# Arbor 0.19.14

This release fixes the remaining native Gaussian scroll flicker by recording one complete frame and replaying that same immutable source normally and through the panel filter, instead of attaching RenderEffect directly to the scrolling root layer.

The temporary top/bottom height settings are removed. Chat uses a fixed 120 dp top panel, Settings and other utility screens use 100 dp, and the composer blur now follows the measured composer height so multiline prompts expand the bottom panel automatically.

Edge Smoothness and Thinking snap markers use Material-sized dots constrained inside the track caps. Streaming responses now provide subtle, rate-limited chunk haptics and a clearer completion pulse without vibrating for every token.
