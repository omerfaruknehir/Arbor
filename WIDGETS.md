# Arbor generated-content contract

Contract family: `arbor-generated-content/1` (the runtime appends a deterministic schema-shape fingerprint so the exposed version changes with the contract)  
Validator: `1.0.0`

`GeneratedContentCapabilityRegistry` in application code is authoritative. Model prompt summaries, validation, exact examples, fence aliases, limits, component/action sets, and repair schema excerpts are derived from that registry. This file explains the same contract for humans; consistency tests require all registry examples to validate and every mini-app component exposed by the renderer to be registered by the parser.

Supported native generated fences:

- `arbor-ui` (`ui`, `arbor-form` aliases): native chat-only declarative interaction.
- `arbor-widget` (`widget` alias): Home eligibility requires `"surface":"home"` or `"surface":"both"` and explicit pinning review.
- `arbor-chart` (`chart`, `bar-chart`, `barchart`, `line-chart`, `pie-chart` aliases): JSON with `type` (`bar`, `line`, `area`, `scatter`, `pie`, or `donut`), optional `title`, and one to eight series of at most 80 finite labelled points.
- `mermaid` (`graph`, `diagram`, `dot`, `graphviz` aliases): bounded native flowchart, sequence, and basic DOT text only.

Ordinary Markdown tables and code fences are not generated mini-apps. Arbor never accepts HTML, JavaScript, JSX, WebViews, downloaded bytecode, or arbitrary executable generated UI as a fallback.

# Native mini-app schema

Arbor has two deliberately separate interactive surfaces:

- `arbor-ui` renders native Compose inside the conversation only. Use it for questions, requirement gathering, forms, previews, configuration, quizzes, and other chat interaction.
- `arbor-widget` may offer Android Home-screen pinning only when the JSON also contains `"surface":"home"` or `"surface":"both"`. Use it only when the user explicitly asks for a launcher widget.

Home eligibility defaults to false. An `arbor-ui` fence never offers pinning even if malformed content asks for it. No HTML, JavaScript, WebView, downloaded bytecode, or generated source is evaluated.

Every definition has an expandable local security review which lists requested capabilities, expected benefits, risks, and cautions in a Flatpak-style summary. Live network data is never fetched merely because a model emitted a definition: the user must consent on first use. A separately configured model may provide a second security opinion, but that opinion is advisory; Arbor's local validator and capability restrictions remain authoritative. Home-screen installation has its own final review and pin confirmation.

## Structure

Use `type: "mini_app"` for a composed experience:

```json
{
  "type": "mini_app",
  "title": "Study dashboard",
  "description": "A small multi-page study tool",
  "state": {
    "correct": 0,
    "attempted": 0,
    "subject": "Math"
  },
  "screens": [
    {
      "id": "dashboard",
      "title": "Dashboard",
      "components": []
    }
  ]
}
```

Put that JSON in an `arbor-ui` fence by default. Add `"surface":"both"` and use an `arbor-widget` fence only for an explicitly requested Home-screen version.

Limits are enforced before rendering: 48 KB source, 48 state values, eight screens, 32 components per screen, 24 list/chart items, 16 buttons per button group, and eight actions per button/item.

## Components

- `text`: `text` supports templates.
- `metric`: large formatted value from `value`, state `id`, or numeric `expression`; supports `prefix`, `suffix`, and `decimals`.
- `input`: editable in chat; `value: "number"` requests a numeric keyboard. Home-screen rendering is read-only, so provide choices or a keypad when Home interaction matters.
- `slider`: state-backed control using `min`, `max`, and `step`. Home-screen rendering provides bounded minus/plus controls.
- `toggle`: state-backed switch and Home-screen toggle button.
- `choice`: state-backed options from `options`.
- `buttons`: one or more generated buttons from `buttons`.
- `progress`: value from its state `id`, `value`, or `expression`, with `min` and `max`.
- `list` and `table`: rows from `items`; rows may be tappable by giving them `actions`.
- `chart`: bar, line, area, scatter, pie, or donut from `items`. Each item has `label` and a numeric/template `value`.
- `timer`: state `id` contains seconds. `value: "countdown"` counts down; other values count up in chat.
- `divider` and `spacer`: visual organization.

Any component or list item may use `visibleWhen`. It accepts a truthy state name, a numeric expression, `name==value`, or `name!=value`.

## Templates and expressions

`{{subject}}` inserts a state value. `{{=attempted-correct}}` evaluates the restricted numeric expression language. Numeric expressions support state identifiers, numbers, `+ - * / % ^`, parentheses, `min`, `max`, `abs`, `round`, and `pow`.

## Actions

Buttons and tappable list items use an ordered `actions` array. Each action observes changes made by earlier actions in the same chain.

- `set`: assign rendered `value` to `target`.
- `add` and `multiply`: update numeric `target` from `value` or `expression`.
- `toggle`: invert a boolean/numeric `target`.
- `append` and `backspace`: build safe keypad/input state.
- `evaluate`: evaluate `expression`, or the current target text, into `target`.
- `navigate`: switch to `screen`.
- `reset`: restore initial state.
- `refresh`: request the configured live data source.
- `submit`: return rendered `message` to the conversation; Home-screen use saves the result visibly.
- `timer_start`, `timer_pause`, and `timer_reset`: control timer state.

An optional `condition` uses the same rules as `visibleWhen`.

## Live data

A mini-app may include the same HTTPS JSON `dataSource` used by live widgets:

```json
{
  "dataSource": {
    "url": "https://public.example/api/data",
    "refreshMinutes": 15,
    "bindings": [
      {"id": "price", "label": "Price", "path": "quote.price", "decimals": 2}
    ]
  }
}
```

Binding IDs become state values and can feed any component, expression, chart, condition, or action. After explicit first-use consent, Arbor allows public HTTPS JSON only, blocks redirects and private/local addresses, limits responses to 1 MB, caches the last successful result, and uses WorkManager for pinned-widget refresh.

## Boundaries

This is a bounded native UI/state language, not a way to install arbitrary generated Android code. It intentionally has no loops, imports, scripts, reflection, shell access, WebView, hidden network requests, or arbitrary Android intents. Python and Ubuntu tools remain separate, explicit agent tools with their existing policies.
