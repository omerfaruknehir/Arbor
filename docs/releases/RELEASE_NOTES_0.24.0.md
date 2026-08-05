# Xylune 0.24.0

## A model catalog and setup flow that scale

Xylune's model selection has been rebuilt for providers with hundreds of models. The shared model catalog now supports instant search, provider and capability filters, favorites, recents, and selected-model prioritization. The same picker is used consistently in chat defaults, automation, package review, and provider management.

OpenRouter discovery now imports and persists the provider's model metadata instead of guessing from model names. Context and output limits, input and output modalities, pricing, supported request parameters, and reasoning metadata are refreshed automatically and can also be refreshed manually. Thinking controls follow each model's supported effort levels, default state, and mandatory-reasoning behavior, including OpenRouter's unified reasoning request format.

Settings are reorganized into **Setup & connections**, **Chat behavior**, **Intelligence**, **Tools & safety**, **Personalization**, and **About**. Provider setup exposes model refresh in the correct place, duplicate policy controls have been removed, and large provider catalogs remain usable rather than rendering an enormous undifferentiated list.

First-run setup is now a focused three-step path: welcome or restore, provider connection, and ready. Appearance and local execution remain available after setup instead of blocking the path to a working chat. Restored settings can bypass setup when they already contain a usable configuration.

## Local execution with clear ownership

Bundled Python is now managed independently from the optional Linux runtime. Python uses its embedded runtime and per-conversation workspace directly; it no longer silently depends on installing or starting Ubuntu. The Local execution screen separates overview, Python, and Linux management, with clearer state, package, repair, reset, and removal actions.

Stored Python files use the same execution path as inline Python, arguments are delivered through `sys.argv`, exit codes are reported accurately, paths and argument counts are validated, and stopping a run reaches the embedded runner directly. The UI and generated tool context now state the real isolation and package-compatibility boundaries instead of presenting the Linux container as the owner of all execution.

The Room database is migrated to schema 16, and portable backups preserve the expanded model and reasoning metadata. Queued or resumed generations also retain the selected model's reasoning policy.

Build metadata: `versionName 0.24.0`, `versionCode 189`.
