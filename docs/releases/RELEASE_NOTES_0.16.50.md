# Arbor 0.16.50

## Branch-path repair

Some conversations could retain several sibling assistant retries with `supersededAt` still clear. Paging then rendered those alternatives one after another, so a single prompt appeared to have several separate Working messages with the same inline branch counter.

Before a conversation is displayed, Arbor now reconstructs the selected ancestry from `activeLeafNodeId` and repairs the active flags transactionally. Nodes on the selected path are restored; stray active siblings are returned to branch history. No message text, Working trace, tool output, or retry is deleted.

The repair also runs when opening a conversation from the drawer or search, and branch activation uses the same cycle-safe path builder. Normal text still separates consecutive Working runs inside one response, as intended; only leaked sibling responses are removed from the active chat.
