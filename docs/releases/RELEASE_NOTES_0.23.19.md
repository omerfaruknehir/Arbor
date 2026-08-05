# Xylune 0.23.19

## Cleaner response style and settings

Xylune now includes a global **Less emoji** preference under **Settings → Response style**. It is enabled by default and applies to both existing and new chats from their next model response. The preference suppresses decorative emoji in routine answers, headings, lists, and status text while still allowing meaningful emoji or emoji explicitly requested by the user.

The preference is stored persistently and included in portable settings backups and restoration, with older backups defaulting safely to the enabled state. The bundled prompt revision is updated so every provider receives the new response-style layer consistently.

Settings are reorganized into clearer **AI & chat**, **Capabilities**, **App & data**, and **About** groups. Model defaults, response style, custom instructions, memory, automation, local tools, appearance, privacy, and backup controls are now separated by purpose instead of appearing in one crowded list.

Build metadata: `versionName 0.23.19`, `versionCode 188`.
