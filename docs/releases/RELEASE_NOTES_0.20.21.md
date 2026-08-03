# Xylune 0.20.21

## Setup is a real standalone flow

Setup is no longer exposed as a destination inside Settings. It remains a dedicated first-run and interrupted-setup surface. Provider and Linux configuration are temporary subflows and return directly to setup.

## Exact setup viewport restoration

Xylune now journals the setup pager page, fractional swipe offset, and the vertical scroll offset of every setup page. Launcher-icon changes synchronously flush that viewport state alongside chats, drafts, files, and other UI state, then restore the same setup position after relaunch.

## Swipe-linked progress

The segmented progress header is driven by the pager's continuous position. Dragging between pages fills the next segment proportionally, and button-driven page animations update the indicator frame by frame instead of jumping after settlement.
