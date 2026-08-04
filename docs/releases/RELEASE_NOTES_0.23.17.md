# Xylune 0.23.17

## Smoother live responses

The visible response now receives each in-process provider chunk immediately through a transient preview store. Room remains the durable source of truth and is still written in efficient batches, but its PagingSource invalidation cadence no longer determines what the user sees.

Ordinary prose is revealed in small display-paced steps instead of dumping as many as 96 characters every 50 ms. The adaptive catch-up logic now respects its maximum step, and chat auto-follow has explicit per-frame movement caps so a delayed frame cannot produce a large scroll jump.
