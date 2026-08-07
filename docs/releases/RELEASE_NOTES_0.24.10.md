# Xylune 0.24.10

## Visible search activity

Provider-native and Xylune-managed searches now keep their query visible in the work timeline. Completed search steps remain expanded and show every returned result in a horizontally swipeable set of cards with title, domain, snippet, preview, and open controls.

Provider citations are emitted through Xylune's source notation, allowing native-search results to appear in the response source pills and bottom Sources bar as well as the search activity card.

## Useful stream errors

A failed or interrupted stream now shows the actual error summary instead of only **Request failed**. The notice can be dismissed, opened for complete diagnostics, copied, retried, or continued. The details view also identifies the provider and model used for the failed request.


## Qwen Cloud

Qwen Cloud is now a first-class provider backed by Alibaba Cloud Model Studio's OpenAI-compatible API. The preset uses the still-supported Singapore international endpoint by default; users can paste the API Host from their Model Studio workspace to select another region or the newer workspace-specific endpoint.

Bundled defaults include Qwen3.7 Max, Qwen3.7 Plus, and Qwen3.6 Flash. Model discovery remains available, while bundled capability metadata is retained for the main Qwen models.

Xylune sends Qwen's native `enable_thinking` and `max_completion_tokens` parameters for Chat Completions. When provider-native web search is selected, supported Qwen models use Model Studio's Responses API with `web_search` and `web_extractor`; returned `action.sources` links feed Xylune's inline citations, search result cards, and bottom Sources bar.
