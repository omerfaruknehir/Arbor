# Arbor 0.19.5

Arbor 0.19.5 repairs the image-generation UX introduced in 0.19.4. Official OpenAI GPT Image models are always merged into the normal model catalog and shown in the same picker as chat models. Provider presets now choose the request transport automatically; only genuinely custom OpenAI-compatible endpoints expose a compact Chat/Image generation selector.

Reasoning now uses the same Markdown renderer as assistant messages, including headings, emphasis, lists, links, inline and fenced code, tables, blockquotes, LaTeX, and streaming updates. Reasoning is strictly display-only: code fences, widgets, generated-content blocks, scripts, tools, and package requests cannot execute from hidden thinking.

The release preserves `app.arbor.chat.debug`, Room schema and migrations, user data, credentials, workspaces, attachments, and the existing debug signer.
