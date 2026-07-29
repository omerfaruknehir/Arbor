# Offline license catalog

This directory is the source of truth for Arbor's in-app **Licenses & notices**
screen. The build reads these local files, validates them, and generates
`licenses/catalog.json` inside the APK. It never downloads legal text or
metadata.

## Layout

- `components/*.json` — one user-facing component record per bundled library,
  runtime, or native component.
- `icons/*` — small local artwork shown by the generated UI.
- `texts/*` — complete license or vendor-notice documents.

Every component must define:

- a stable `id`, display `name`, `version`, `category`, and useful
  `description`;
- its official `projectUrl`;
- a relative local `icon`;
- all directly declared Gradle `coordinates` it represents (an empty array is
  valid for bundled native or Python components);
- at least one license record with a display `name`, optional SPDX expression,
  and relative local `file`.

`./gradlew :app:generateOfflineLicenseCatalog` validates the schema, rejects
duplicate IDs or escaping/missing paths, and checks that every dependency in
the app's `implementation` configuration is represented. The generated output
is deterministic and lives under `app/build/generated/offlineLicenses/`.

When adding a runtime dependency, add or update its component record and place
the exact upstream license text in `texts/`. Do not replace a proprietary
vendor agreement with an open-source SPDX label.
