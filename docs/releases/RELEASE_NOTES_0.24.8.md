# Xylune 0.24.8

## Source pills and previews

Xylune now accepts compact website citations such as `[[PNA|https://www.pna.gov.ph/index.php/articles/1281231]]`. They render as tappable source pills at the correct inline claim position. The earlier `[[source|label|URL]]` and `[[file|label|target]]` forms remain supported.

Completed answers automatically receive a deduplicated **Sources** section in first-use order. Tapping an inline or bottom source pill opens an anchored preview containing the page title, domain, description, full destination, and an explicit **Open** button.

Ordinary model-written Markdown hyperlinks remain visible as literal Markdown rather than becoming unreliable clickable spans.

## AI citation instructions

Xylune now explicitly tells models to cite only pages they opened or materially used, place each source immediately after the supported claim, use compact source notation, avoid invented citations, and rely on Xylune to generate the bottom Sources section.
