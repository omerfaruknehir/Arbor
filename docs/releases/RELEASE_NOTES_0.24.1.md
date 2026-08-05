# Xylune 0.24.1

## Capability messages only when something is unavailable

Images no longer show an OCR badge merely because local OCR data already exists. Xylune shows the compatibility badge and fallback controls only when the selected model cannot read the original image. Models with native image input receive the image normally without an unnecessary OCR notice.

Tool calling follows the same rule: no positive or redundant compatibility banner is shown for capable models. If web search, Python, Linux, or deep research is enabled for a model without function calling, Xylune shows one direct warning that those tools cannot run.

## Reliable model selection from the expanded title

The model pill now stays above the transparent hit region owned by the large chat app bar. It opens the model catalog consistently whether the conversation title is expanded or collapsed.

## Rewritten privacy and usage terms

The English and Turkish privacy documents now explain each data path separately: device-only data, direct provider requests, cloud backups, documentation visits, and information deliberately sent to the maintainer. They explicitly cover Google API Limited Use, legal bases, international processing, retention, privacy rights, and the boundary between official builds and forks.

The Terms of Use now make provider billing, AI output, local execution, backups, open-source licensing, support expectations, warranties, and liability boundaries easier to understand while preserving non-waivable consumer, privacy, product, and safety rights.

The Pages workflow no longer attempts repository bootstrap with a workflow token. GitHub Pages must be enabled once by the repository owner with **GitHub Actions** selected as the source; subsequent legal-site deployments run through the normal Pages workflow.

Build metadata: `versionName 0.24.1`, `versionCode 190`.
