# Xylune 0.24.5

## Thinking display

- Recognizes visible reasoning delivered through OpenAI-compatible `reasoning`, `reasoning_content`, `thinking`, `analysis`, and textual `reasoning_details` fields.
- Routes streamed `<thinking>...</thinking>` and `<think>...</think>` content into the Working card instead of exposing the tags in the final answer.
- Handles opening and closing tags split across arbitrary provider chunks without delaying ordinary answer text.

## Updates

- Adds **Check automatically** under **About Xylune → Updates**.
- The option is enabled by default and checks the build's embedded GitHub repository at most once per day when Xylune starts.
- Manual checks remain available, and the preference is included in portable settings backups.

## Website and legal pages

- Applies the selected Material palette to backgrounds, surfaces, ordinary text, bold text, outlines, and navigation.
- Uses app-like expanded and collapsed page titles while keeping Privacy Policy and Terms scrolling unrestricted.
- Keeps theme controls compact and matches website branding to Xylune's launcher-icon variants.
