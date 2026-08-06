package app.xylune.chat.settings

enum class WebSearchRoute(
    val title: String,
    val description: String,
) {
    AUTO(
        "Automatic",
        "Use the model provider's native server-side search when supported, otherwise use the selected Xylune search engine.",
    ),
    NATIVE_ONLY(
        "Provider native only",
        "Require the selected provider/model to perform search itself. Unsupported models fail instead of silently switching engines.",
    ),
    SEARCH_ENGINE(
        "Xylune search engine",
        "Always use the selected search engine through Xylune's client-side web_search tool.",
    ),
}

enum class WebSearchEngine(
    val title: String,
    val description: String,
    val requiresApiKey: Boolean,
) {
    DUCKDUCKGO(
        "DuckDuckGo",
        "No API key. Uses DuckDuckGo's lightweight HTML results endpoint.",
        false,
    ),
    BRAVE(
        "Brave Search",
        "Uses the official Brave Search API.",
        true,
    ),
    TAVILY(
        "Tavily",
        "Search API optimized for AI research and concise result content.",
        true,
    ),
    SERPER(
        "Serper",
        "Google-result API supplied by Serper.",
        true,
    ),
    SEARXNG(
        "SearXNG",
        "Uses a user-supplied public HTTPS SearXNG instance with JSON output enabled.",
        false,
    ),
}

data class WebSearchSettings(
    val route: WebSearchRoute = WebSearchRoute.AUTO,
    val engine: WebSearchEngine = WebSearchEngine.DUCKDUCKGO,
    val maxResults: Int = 8,
    val pageFetchEnabled: Boolean = true,
    val searxngEndpoint: String = "",
) {
    fun normalized() = copy(
        maxResults = maxResults.coerceIn(3, 20),
        searxngEndpoint = searxngEndpoint.trim().trimEnd('/'),
    )

    val activeLabel: String
        get() = when (route) {
            WebSearchRoute.AUTO -> "Auto · ${engine.title} fallback"
            WebSearchRoute.NATIVE_ONLY -> "Native only"
            WebSearchRoute.SEARCH_ENGINE -> engine.title
        }
}
