# Arbor 0.20.29

## Widget compiler as an AI tool

- Adds a native `compile_widget` function to every tool-capable model request.
- Widget drafts remain inside tool-call arguments instead of appearing as broken user-facing fences.
- The compiler returns trusted structured diagnostics with phase, JSON path, and corrective message.
- The active model can revise the complete candidate and call the compiler repeatedly in the same response.
- A widget fence is emitted only after a successful compiler call, using the exact tested source unchanged.
- Existing post-generation compilation remains as a defensive fallback for models without native tool support or models that violate the protocol.

## Tool activity UX

- Adds dedicated “Preparing widget compile” and “Compiling Home widget” activity states.
- Hides raw candidate JSON from the normal activity summary while preserving it in the provider tool protocol.
- Treats compiler output as trusted Arbor runtime data rather than untrusted external content.

## Version

- versionName: `0.20.29`
- versionCode: `155`
- generated-content validator: `2.3.0`
