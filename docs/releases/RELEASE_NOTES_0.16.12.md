# Xylune 0.16.12

- Rebuilt the chat header around one persistent title and one persistent model selector. The title is large only at the beginning of a conversation, then physically moves and scales into the header without fading or duplicating.
- Corrected reverse-layout scroll calculations so header collapse is top-relative while composer blur remains bottom-relative.
- Kept the model selector available throughout the conversation instead of hiding it away from the chat start.
- Shortened the top blurred region to 64 dp in Chat, Search, Settings, and nested settings pages without reducing the configured blur radius.
- Confined gradient tint drawing to the blur region so it no longer darkens the rest of the page.
- Added a staged Deep Research roadmap with Plan, Discover, Read, Verify, and Synthesize progress.
- Replaced verbose web-search tool dumps with compact query and source-site cards. Source details and URLs are shown before opening.
- Added tappable website and file reference pills in answers. Ordinary links also show a destination preview before leaving Xylune.
- Added model prompting for grounded website/file reference notation and research-stage reporting.
- Fixed an uncaught `InterruptedIOException` when Ubuntu process output streams are intentionally closed during cancellation, timeout, or teardown.
