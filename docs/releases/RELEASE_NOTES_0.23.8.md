# Xylune 0.23.8

## Setup pager spacing

- Adds a 16 dp gap between adjacent setup pages, making the swipe boundary visually clear without reintroducing fades or overlapping content.
- Keeps each page fully opaque and directly draggable.

## Empty-chat provider setup

- Fixes the empty-chat provider action being covered by the empty `LazyColumn` hit target.
- Both the central **Set up a provider** action and the model-selector setup entry now lead to **Settings → Providers & models**.

## Validation

- Adds regression checks for pager spacing, empty-state hit-test ordering, and direct provider-settings navigation.
