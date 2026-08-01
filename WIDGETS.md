# Arbor snippets and programmable widgets

Contract family: `arbor-generated-content/2`
Validator: `2.2.0`

`GeneratedContentCapabilityRegistry` is the prompt-time and validation-time authority. This document is the human-readable skill specification supplied with the app.

## The hard boundary

Arbor has two generated-program surfaces. They are intentionally incompatible.

| Surface | Fence | Schema | Lives where | External capabilities |
|---|---|---|---|---|
| Snippet | `arbor-snippet` | `arbor-snippet/1` | Inside one chat message | None |
| Widget | `arbor-widget` | `arbor-widget/1` | Android Home screen | Explicit per-widget grants |

A **snippet** is a chat interaction: a quiz, short questionnaire, calculator, checklist, configuration form, simple question, or other temporary interactive answer.

A **widget** is an Android launcher program. Arbor shows an installation card inside chat, but the program itself runs outside the app after the user reviews its manifest and pins it.

The removed `arbor-ui`, `ui`, `arbor-form`, `widget`, category-based widget roots, and `mini_app` schema are not parsed or migrated.

## One component language, not widget categories

Do not emit roots such as `type: "stock"`, `type: "prayer_times"`, `type: "calculator"`, or `type: "quiz"`. Those were brittle categories.

Compose the experience from general nodes instead:

- layout: `column`, `row`, `stack`, `spacer`, `divider`
- content: `text`, `metric`, `list`, `chart`, `progress`
- interaction: `button`, `toggle`, `choice`, `slider`, `input`

`input` is snippet-only because Android launchers cannot host a keyboard. Home widgets may display sliders and choices, but launcher interaction is exposed through at most four visible button/toggle actions.

Every node supports the relevant subset of:

```json
{
  "type": "text",
  "id": "optional_stable_id",
  "text": "Hello {{name}}",
  "label": "Optional label",
  "value": "state_key_or_template",
  "action": "action_group_id",
  "visibleWhen": "count > 0",
  "children": [],
  "options": [],
  "items": [],
  "min": 0,
  "max": 100,
  "step": 1,
  "decimals": 2,
  "style": {
    "foreground": "primary",
    "background": "surface_variant",
    "emphasis": "strong",
    "align": "start",
    "padding": 12,
    "gap": 8,
    "cornerRadius": 16,
    "fontSize": 18,
    "weight": 1
  }
}
```

Theme color tokens are `primary`, `secondary`, `tertiary`, `surface`, `surface_variant`, `on_surface`, `error`, and `transparent`. `#RRGGBB` and `#AARRGGBB` are also accepted.

## State, templates, conditions, and actions

State is a flat map of at most 64 primitive values. `{{name}}` inserts a state value. `{{=count*2}}` evaluates Arbor's numeric expression language. `{{urlencode:name}}` percent-encodes a state value for an HTTP query parameter.

Expressions allow numbers, state identifiers, `+ - * / % ^`, parentheses, and `min`, `max`, `abs`, `round`, and `pow`. There are no loops, imports, user-defined functions, or object access.

`visibleWhen` accepts a truthy state key, numeric expression, `name == value`, or `name != value`.

Actions are named groups containing ordered operations:

- `set`, `add`, `multiply`, `toggle`, `append`, `backspace`, `evaluate`
- `reset`
- `submit` for sending an explicit snippet result back into the chat
- `refresh` for requesting one widget data source
- `write_folder` for replacing the file of a declared `folder_text` source after a `read_write` grant
- `open_app` for opening Arbor from a launcher widget

Later operations see state changes made by earlier operations in the same group.

## Snippet skill

Use a snippet only when the interaction belongs in the answer itself. Snippets cannot request network, background work, location, folders, notifications, contacts, camera, microphone, or other Android permissions.

```arbor-snippet
{
  "schema": "arbor-snippet/1",
  "id": "prime_quiz",
  "title": "Prime-number check",
  "state": {"answer": "", "checked": false},
  "ui": {
    "type": "column",
    "style": {"gap": 10},
    "children": [
      {"type": "text", "text": "Which number is prime?", "style": {"emphasis": "strong"}},
      {
        "type": "choice",
        "value": "answer",
        "options": [
          {"label": "9", "value": "9"},
          {"label": "11", "value": "11"},
          {"label": "15", "value": "15"}
        ]
      },
      {"type": "button", "label": "Check", "action": "check"},
      {"type": "text", "text": "Correct", "visibleWhen": "checked == true"}
    ]
  },
  "actions": {
    "check": [
      {"op": "set", "target": "checked", "value": true},
      {"op": "submit", "message": "Quiz answer: {{answer}}"}
    ]
  }
}
```

## Widget skill

A widget requires a stable `id`, a general UI tree, named actions, an explicit capability manifest, optional data sources, and optional scheduled refresh.


### Compile before display

Arbor does not present a raw `arbor-widget` block as a usable widget. It first compiles the definition into the typed widget runtime and runs a bounded preflight:

1. Parse and validate the complete schema and capability graph.
2. Seed representative location/folder values, then execute public HTTP JSON sources in dependency order.
3. Follow safe declared HTTPS redirects, reject deterministic HTTP 4xx responses, invalid JSON, and missing binding paths, while allowing transient outages only when honest fallbacks exist.
4. Execute every action group against the compiled state.
5. Render the initial state and representative post-action states at standard and expanded launcher sizes.
6. Reject clipped, cramped, empty, or undersized layouts and feed the exact compiler diagnostics back to the auxiliary model.

Only a candidate that passes this cycle is shown with its install/preview card. The compiler is declarative: it does not build or execute JavaScript, bytecode, shell code, or downloaded code.

### Capability manifest

Each capability has a user-facing `reason`. Arbor rejects a data source unless its matching capability is present, then asks the user to grant it for that pinned widget instance.

- `network`: exact HTTPS origins only, for example `https://api.open-meteo.com`. No wildcards, path grants, embedded credentials, or private/local IPs. HTTPS redirects are followed for at most five hops only when every destination origin is explicitly declared.
- `location`: `approximate` or `precise`; the Android runtime permission must still exist when the widget refreshes.
- `folder`: `read` or `read_write`; the user chooses one Storage Access Framework document tree. Relative paths cannot escape it.
- `background_refresh`: permits WorkManager scheduling. The interval is 15–1440 minutes and Android may defer execution.

A widget that requests no capabilities remains fully local.

### Data sources

- `http_json`: GET-only HTTPS JSON, maximum 1 MB. Bindings copy explicit JSON paths into state. Every binding needs a useful fallback, and query values containing text should use `{{urlencode:key}}`.
- `location`: binds `latitude`, `longitude`, `accuracy`, or `updatedAt` into state.
- `folder_text`: reads one relative UTF-8 file from the selected tree and binds `text`, `size`, or `lineCount`.

Location and folder sources run before HTTP sources, so their state can safely parameterize an allowed URL.

```arbor-widget
{
  "schema": "arbor-widget/1",
  "id": "local_weather",
  "title": "Weather",
  "description": "Live temperature for the current area",
  "state": {"latitude": 0, "longitude": 0, "temperature": "—"},
  "ui": {
    "type": "column",
    "style": {"gap": 8},
    "children": [
      {"type": "metric", "label": "Temperature", "value": "{{temperature}} °C"},
      {"type": "button", "label": "Refresh", "action": "refresh_weather"}
    ]
  },
  "actions": {
    "refresh_weather": [{"op": "refresh", "source": "weather"}]
  },
  "capabilities": [
    {"type": "location", "accuracy": "approximate", "reason": "Use the device area for local weather."},
    {"type": "network", "origins": ["https://api.open-meteo.com"], "reason": "Download current weather from Open-Meteo."},
    {"type": "background_refresh", "reason": "Keep the launcher value current."}
  ],
  "dataSources": [
    {
      "id": "location",
      "type": "location",
      "bindings": [
        {"state": "latitude", "path": "latitude"},
        {"state": "longitude", "path": "longitude"}
      ]
    },
    {
      "id": "weather",
      "type": "http_json",
      "url": "https://api.open-meteo.com/v1/forecast?latitude={{latitude}}&longitude={{longitude}}&current=temperature_2m",
      "bindings": [
        {"state": "temperature", "path": "current.temperature_2m", "fallback": "—"}
      ]
    }
  ],
  "refreshMinutes": 30
}
```

## Security and privacy invariants

Generated programs never execute HTML, JavaScript, JSX, WebViews, downloaded bytecode, reflection, shell commands, Python, Linux commands, arbitrary Android intents, or hidden permissions.

Network grants expose the device IP address to only the listed origin. Location and folder data can enter a granted network request only when the same widget declares and receives both capabilities. Home-screen content is visible to anyone who can view the unlocked launcher. Removing a widget deletes its private program state and cancels its scheduled work.


## Model capability delivery

Arbor injects an always-on compact widget manifest into the system context and selects the full schema from up to sixteen recent conversation messages. This preserves capability awareness across follow-ups and multilingual requests instead of relying only on the latest user sentence.

Generated widgets should be glanceable, honest about unavailable live data, usable when resized, and limited to a small set of meaningful launcher actions. The compiler enforces readable launcher typography (normally 15sp+, supporting text 13sp+, primary metrics around 28–32sp), bounded rows/actions, and representative resize checks before display. The chat install card provides an interactive local preview, grant progress, grouped origin approval, clearer launcher feedback, and per-instance permission explanations. The launcher widget uses a dedicated refresh affordance and avoids drawing duplicate action controls into the bitmap.
