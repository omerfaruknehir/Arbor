# Xylune 0.24.7

## Search configuration

Search routing is now explicit and configurable:

- **Automatic** uses provider-native search when supported and the selected Xylune engine otherwise.
- **Provider native only** refuses unsupported provider/model combinations instead of silently changing backends.
- **Xylune search engine** always uses the selected engine.

Available client-side engines are DuckDuckGo, Brave Search, Tavily, Serper, and a configurable public SearXNG instance. Search API keys remain in Android encrypted preferences. Result count and page fetching are configurable.

## Accurate activity labels

The work timeline now names the actual backend, including provider-native search and Google Search grounding. Completed native calls are marked complete rather than remaining as “Prepared web search”.

## Markdown links

Markdown hyperlinks are displayed as their literal Markdown source instead of producing non-working clickable spans.
