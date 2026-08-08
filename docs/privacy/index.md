---
layout: default
lang: en
alternate_en: /privacy/
alternate_tr: /tr/privacy/
title: Privacy Policy
heading: Privacy policy
browser_title: Xylune Privacy Policy — Android BYOK AI Chat App
description: Privacy policy for Xylune, the open-source Android BYOK AI chat app. Learn what stays on-device, what providers receive, and how cloud backups work.
---

# Xylune Privacy Policy

**Effective date: August 5, 2026**

This is a factual privacy notice, not a contract or a request for consent.

Xylune is downloadable open-source software maintained by **Ömer Faruk Nehir in Türkiye**. Xylune is not a hosted service. The official app has no Xylune account, advertising, analytics, telemetry, or developer-operated backend. Through the app, the maintainer does not receive, collect, store, or have technical access to users' conversations, API keys, files, backups, or connected-account data.

## 1. Data on the device

Depending on the features used, Xylune stores chats, settings, attachments, workspaces, provider configuration, and credentials in app-private storage on the user's device. Credentials use encrypted app-private storage backed by Android Keystore where supported. Credentials and OAuth sessions are excluded from portable Xylune archives. An archive is encrypted only when the user gives it a password.

The official app does not automatically send the maintainer crash reports, diagnostics, or usage events. A user may delete device data in Xylune, clear Xylune's Android app data, or uninstall the app. The maintainer cannot remotely access, recover, export, or delete device-only data.

## 2. User-selected third parties

When a user selects an AI provider, search service, website, local server, cloud-storage provider, or other endpoint, Xylune communicates directly from the device with that endpoint. The selected provider may receive the prompts, conversation context, files, tool inputs, account information, and network data needed for the requested action.

The provider independently determines its processing, security, retention, model-training practices, international transfers, billing, and deletion controls under its own terms and privacy policy. The Xylune maintainer does not receive a relayed copy, does not control a provider's copy, and cannot access, retrieve, correct, or delete it for the user.

## 3. Backups, OAuth, and Google API data

When enabled by the user, backup and restore traffic goes directly between the device and the selected Google Drive, Microsoft OneDrive, Dropbox, WebDAV/Nextcloud, S3-compatible, or Android document-storage destination. A backup may contain the content selected in Xylune. Account labels and authorization sessions remain on the device. Disconnecting an account removes local authorization but may not delete an existing provider backup.

Xylune uses Google Drive's restricted app-data area only for backup operations requested by the user. Its use and transfer of Google user data follows the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including Limited Use. Xylune does not use Google user data for advertising, profiling, credit decisions, or AI-model training.

## 4. GitHub and deliberate submissions

[GitHub](https://docs.github.com/site-policy/privacy-policies/github-privacy-statement)—not the Xylune maintainer—operates the public repository, Issues, pull requests, accounts, hosting, cookies, and platform logs. A public GitHub post and its public profile information can be seen by the maintainer and by anyone else. GitHub is not a private support or privacy-request channel. Do not post credentials, confidential material, or personal data in an issue.

The only personal data the maintainer may receive is information a person deliberately publishes or sends, plus limited administration or security information an OAuth provider may make available to an application owner. Such information may be used only to respond to the submission, maintain or secure the project, administer OAuth, comply with law, or establish, exercise, or defend legal claims. It is not sold, used for advertising, or used to train AI models.

Where applicable, the legal basis is the requested action, legitimate interests in maintaining and defending the project, compliance with law, or consent when specifically requested. Information may be disclosed to necessary project collaborators, professional advisers, authorities when legally required, or a disclosed project successor. A chosen communication service or GitHub may process it outside Türkiye under that service's safeguards. It is retained only as long as reasonably necessary for those purposes or legal claims.

## 5. Deletion, rights, and contact

Rights and deletion requests must be directed to the party that actually controls the information:

- for device data, use Xylune or Android controls;
- for AI, cloud, or other provider data, use that provider's controls;
- for GitHub account or platform data, use GitHub's controls; and
- for information deliberately sent privately to the maintainer, use the private contact method shown on the relevant OAuth consent screen.

The maintainer cannot act on information never received or controlled. For a deliberate private submission the maintainer actually controls, applicable KVKK, GDPR, or other mandatory rights remain available. Reasonable identity and scope verification may be required. Do not use a public GitHub issue for a privacy request. Practical deletion steps are described on the [Xylune data deletion page](https://omerfaruknehir.github.io/Xylune/data-deletion/).

## 6. Security, children, and changes

Xylune uses Android app isolation, scoped provider permissions, and encrypted credential storage where supported, but no system is completely secure. Users remain responsible for device security, provider permissions, archive passwords, and independent copies of important data.

Xylune is not directed to children. Any required guardian consent and provider age rules still apply. This notice may be updated if the app's data paths, operator, or legal duties change; the effective date and public repository history show the current version.
