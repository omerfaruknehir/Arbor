# Arbor 0.16.58

- Removed the generation-time `scrollToItem(lastIndex)` snap which reran whenever a streamed tool, file, table, or result changed the paging item count.
- The nonlinear frame-paced follower is now the sole owner of programmatic movement during streaming.
- Initial chat opening and the explicit Go to latest action retain their intentional one-time positioning.
