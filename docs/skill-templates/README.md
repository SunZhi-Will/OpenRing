# OpenRing Skill Plugin Templates (QuickJS)

OpenRing Skill plugins are installed by downloading a ZIP package. The installer expects each package ZIP to contain:

1. `manifest.json`
2. `script.js`

This folder provides a few small, copy-and-modify templates so teammates can build new skills without guessing the package format.

## How to package a skill (ZIP layout)

For each template directory, create a ZIP that includes `manifest.json` and `script.js` at the ZIP root.

Expected ZIP structure:
```text
your-skill.zip
└── manifest.json
└── script.js
```

## `manifest.json` (recommended fields)

The current host installer only requires `name` to derive `skillId`, but this template uses the richer format described in `docs/PRD.md`.

Minimal requirement:
```json
{ "name": "your_skill_id" }
```

Recommended shape:
```json
{
  "name": "your_skill_id",
  "description": "What this skill does and when it should be used",
  "inputSchema": { "type": "object", "properties": {}, "required": [] },
  "outputSchema": { "type": "object", "properties": {} },
  "permissions": { "network": { "required": true } }
}
```

## `script.js` interface

Each skill module should export a single entry function:

```js
export function run(input) {
  return { /* output object */ };
}
```

Return value should be a JSON-serializable object.

## Templates in this folder

1. `crypto_price_fetcher`: Demonstrates a `network` permission requirement and a stable output shape.
2. `text_uppercase`: Demonstrates a pure transform skill with no permissions.
3. `json_reformatter`: Demonstrates schema-driven formatting and deterministic outputs.

