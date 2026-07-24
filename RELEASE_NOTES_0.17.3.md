# Arbor 0.17.3

This maintenance release fixes gesture ownership on secondary pages.

- On Chat, a deliberate rightward pull still opens the conversation drawer.
- On Settings, Search, Sandbox, and Terminal, the drawer drag recognizer is absent, so Android Back works from either edge.
- The hamburger/menu button still opens the drawer from those pages.
- During drawer close, page Back becomes active as soon as the drawer is no longer visible; there is no unhandled interval that can exit the Activity.
- No Room migration or data reset is required.
