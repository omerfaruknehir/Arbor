# Xylune 0.24.13

## Improved Images workspace

The dedicated Images workspace now follows Xylune's normal conversation layout more closely, including the collapsing conversation header and translucent composer. Reference images use compact previews with Photos and Camera actions, while Generate/Edit, Queue, and Stop controls stay focused on image workflows without exposing normal chat-only Thinking, Search, or Tools controls.

## Gemini image generation

Xylune now detects current Gemini image-generation model families from the Gemini model catalog, including `*-image`, `*-image-*`, and Imagen-style model IDs. Gemini image requests explicitly ask for text and image output, and returned inline image data is decoded and saved as generated attachments.

Gemini image generation remains truthful about progress: Xylune shows final-image output unless the provider actually exposes progressive frames.

## Better usage accounting

Gemini candidate tokens and thinking tokens are now counted together in billable output totals so thinking-enabled requests are not undercounted.

Chat configuration now includes conversation-level provider-call usage totals for input, cached input, non-cached input, output/billed tokens, total tokens, known cost, and unpriced calls.

Assistant messages also expose a Usage details view with aggregate and per-provider-call token, cost, status, and finish-reason information.

## Message actions

User and assistant messages now have a compact overflow menu. Messages can be shared through Android's share sheet, including local attachments when present.
