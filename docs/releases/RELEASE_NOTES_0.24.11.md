# Xylune 0.24.11

## Qwen Cloud image and model metadata

- Correct sparse Alibaba Cloud Model Studio metadata for Qwen, GLM, Kimi, DeepSeek, and MiniMax families so Xylune exposes the capabilities each model actually supports.
- Route Qwen-Image through Alibaba's native multimodal-generation API instead of Chat Completions, including Qwen-Image 2.x generation and editing with up to three local reference images.
- Add provider-managed thinking controls where effort levels are not exposed, and restore GLM 5.2's documented reasoning-effort scale, Function Calling, and current context/output limits.
- Restrict Alibaba native web search to model IDs that actually support it; unsupported hosted and regional models use Xylune's client-side search path.
- Repair previously cached Qwen Cloud model rows after updating so corrected capabilities appear without requiring a manual catalog refresh.

## Reliability

- Preserve explicit reasoning-effort selections instead of replacing them with the model default.
- Keep Alibaba-specific request behavior isolated from custom OpenAI-compatible endpoints when the Qwen Cloud preset is repointed.
