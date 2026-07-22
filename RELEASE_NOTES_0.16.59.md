# Arbor 0.16.59

- Added a streaming scroll-anchor guard. Room/Paging refreshes can no longer reset the visible list to item 0 and then make the nonlinear follower race back down.
- Preserved stable message keys across transient Paging refresh gaps.
- Restored pull-to-open for the conversation drawer with a 56 dp edge zone and a low 10 dp horizontal trigger.
- Kept the drawer gesture edge-only while closed so vertical chat/table scrolling is not captured; native pull-to-close remains enabled while open.
