# Arbor 0.11.1

This maintenance release tightens context accounting, cost reporting, release engineering, and regression coverage.

## Fixed

- Added an independent Working-history token budget for completed reasoning and tool traces.
- Preserved complete interrupted, streaming, and errored Working state for resume/steer even when the historical Working budget is zero.
- Ensured Working history remains inside the conversation's overall context ceiling after attachment and system-prompt accounting.
- Stopped treating unknown or dynamic model pricing as free. Messages now distinguish known cost from unavailable cost, and conversation totals identify partial cost.
- Added a Room 8→9 migration for Working limits and cost-confidence fields.
- Reused a shared JSON decoder in the chat renderer.
- Replaced deprecated directional icons with auto-mirrored variants and fixed app-owned Android lint findings.

## Engineering

- Added optional environment/Gradle-property release signing without committing credentials.
- Added GitHub Actions jobs for unit tests, lint, debug artifacts, Android emulator smoke testing, and manually triggered signed releases.
- Added JVM regression tests for Working-budget selection and unknown pricing.
- Added an instrumentation smoke test which starts the application and opens the encrypted Room database.

## Still intentionally out of scope

Exact provider-specific preflight tokenizers, native provider function-call adapters, full Mermaid/Office rendering, local image captioning, Bedrock/Azure signing adapters, and a production keystore remain separate projects. A real-device acceptance pass is still required before public release.
