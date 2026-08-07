# Xylune 0.24.12

## Images workspace

Image-generation models now live in a dedicated **Images** catalog and workspace instead of being mixed into normal chat models. Selecting an image model opens image-specific controls; selecting a chat model returns to the normal conversation UI without losing history.

The Images workspace distinguishes **Generate**, **Generate + edit**, and **Edit** models, shows model-specific reference-image limits, supports photo-library and camera references, validates unsupported attachments before sending, and provides clear Generate/Edit, Queue, and Stop actions.

## Image editing

Qwen Image 2.x generation-and-editing models accept up to three reference images where supported, while edit-only and generation-only Qwen variants expose the correct workflow instead of generic image/vision controls.

OpenAI GPT Image models use the native Images generation and editing endpoints. GPT Image 2 is included as the current bundled OpenAI image model, and OpenAI editing can use up to sixteen reference images.

## Live generation previews

Providers that expose real intermediate image frames can now show them while rendering. OpenAI GPT Image generation and editing request up to three native partial images; Xylune replaces the in-progress preview as newer provider frames arrive and crossfades between them before saving only the final image.

Providers that do not expose progressive frames, including the current Qwen Image API, show an explicit generation placeholder until the final image arrives instead of fabricating intermediate detail.

## Release-note reliability

Release publication can no longer fall back to the complete `CHANGELOG.md`. Xylune uses the exact version-specific release-note file when present, otherwise extracts only that version's changelog section, and refuses publication if neither exists. Already-published same-version releases can synchronize their description without recreating their tag or assets.
