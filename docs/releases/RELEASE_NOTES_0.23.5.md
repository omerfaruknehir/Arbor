# Xylune 0.23.5

This release repairs predictive Back, makes Xylune's built-in instructions inspectable, and narrows the legal documents to the app's real local/direct role.

## Predictive Back

- Active pages remain fully opaque throughout the gesture; the whole Compose tree is no longer faded through intermediate surfaces.
- Page travel is reduced and a small scale response replaces the visually unstable endpoint fade.
- A new Back gesture can take ownership while the previous short page animation is still settling instead of falling through or doing nothing.

## Inspectable core prompt

**Settings → Custom instructions** now shows the exact bundled Xylune core prompt as selectable, read-only text with its revision. Xylune also explains that date, tool, research, memory, attachment, and generated-content instructions are assembled dynamically for each request and are not user-editable.

## Privacy and third-party AI roles

- The bilingual privacy policy now states that Xylune can be used worldwide and does not operate a chat relay or central backup server.
- The maintainer's data role is limited to information actually submitted to maintainer-controlled support, security, project, or OAuth-administration channels.
- Ordinary volunteer support has no promised response time; mandatory privacy-request deadlines remain preserved where they legally apply.
- New bilingual Terms of Use state that users select independent AI providers and that the Xylune maintainer does not create, train, host, pre-review, or endorse individual model outputs.
- Liability and warranty limitations apply only to the maximum extent allowed by law and do not waive mandatory consumer rights.

## Release files

The release continues the 0.23.4 invariant: exactly six assets in the same canonical order—APK, AAB, source ZIP, source tarball, release manifest, then SHA-256 list.
