# Xylune 0.23.14

## Back motion: half slide, half fade

Page transitions now travel only half the viewport while crossfading between the source and destination. This keeps the motion visible and complete without making the entire screen feel as though it is being dragged away.

The outgoing page remains composed until it reaches zero opacity, and the destination reaches full opacity before the source is retired. Predictive Back, button Back, toolbar Back, forward navigation, and cancelled gestures use the same transition model.
