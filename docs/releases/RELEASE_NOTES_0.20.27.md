# Xylune 0.20.27

## Widget-aware AI

- Xylune now injects a concrete Home-widget capability manifest on every request instead of only naming the available fence.
- Full widget schema selection uses recent conversation context, so follow-ups such as “make it cleaner” retain the widget capability.
- Widget intent detection now covers broader English and Turkish Home-screen language.
- The model receives explicit node, action, data-source, permission, fallback-data, and glanceability guidance.

## Widget experience

- Widget cards now provide an interactive local preview with reset and clear simulation feedback for external actions.
- Permission setup shows progress, ready/required states, grouped network-origin approval, safer explanations, and a precise list of what remains.
- The add flow uses clearer success and failure messages and distinguishes configured pinning from generic launcher widgets.
- Installed widgets use a dedicated refresh affordance, richer local/live/error status, pill-style actions, and support button, toggle, choice, and list actions.
- Launcher rendering no longer duplicates clickable controls inside the non-interactive bitmap.
