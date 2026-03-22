# OpenRing Skill Plugins (QuickJS)

OpenRing Skill Plugins empower the Gemini Agent inside the app with **deterministic logic, custom integrations, and data processing** capabilities. Since the agent's context window is limited and LLMs can hallucinate logic, Skills provide local sandboxed code (running on QuickJS) that the Agent can call reliably.

This directory contains templates to help developers and users build Skills for the OpenRing ecosystem.

---

## Host runtime behavior (what OpenRing enforces today)

| Manifest field | Enforced at install | Enforced at execution |
|----------------|--------------------|------------------------|
| `name` | Yes (required, non-empty; drives install folder id) | Used for `skill_<name>` tool registration |
| `description` | Optional (recommended) | Exposed to the model for `skill_<name>` |
| `inputSchema` | If present, must be a JSON object | Passed as Gemini function parameters for `skill_<name>` |
| `outputSchema` | If present, must be a JSON object | **Not validated** (documentation / future use) |
| `permissions` | Stored as-is | **Not enforced** — no network/storage APIs inside QuickJS yet (roadmap) |

Skills must define **`function run(input)`** in `script.js` (see `SkillQuickJsExecutor`); `export` prefixes are tolerated.

---

## Why Build a Skill?

1. **Deterministic Processing**: Need complex regex extraction or specific math? The LLM might make mistakes, but a JS script won't.
2. **Data Transformation**: Convert massive raw inputs (like raw HTML or JSON) into small, clean payloads before feeding them back to Gemini, saving tokens.
3. **Local Tooling (roadmap)**: Network requests or storage under explicit user permission would require host APIs not present in the current QuickJS sandbox.

---

## How to Build and Package a Skill

An OpenRing Skill is a `.zip` file containing two files at its root:

```text
my_awesome_skill.zip
├── manifest.json   # Describes the tool to the Gemini Agent (name, description, inputSchema)
└── script.js       # JavaScript executed by QuickJS
```

### 1. `manifest.json`

Minimum for installation:

- **`name`** (string, required): becomes the install id (alphanumeric + `_`). Keep it stable and match what you document for `call_skill`.
- **`description`** (string, recommended): when the model should use this skill.
- **`inputSchema`** (object, optional): JSON Schema–style object for Gemini `skill_<name>` parameters. If omitted, the host uses an empty object schema.

Optional / future:

- **`outputSchema`**: documented contract only; **not checked** when `run` returns.
- **`permissions`**: documented intent only; **not enforced** by the host today.

Example:

```json
{
  "name": "my_skill_name",
  "description": "Tell the AI EXACTLY when and why to use this skill.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": { "type": "string" }
    },
    "required": ["query"]
  },
  "outputSchema": {
    "type": "object",
    "properties": {
      "result": { "type": "string" }
    }
  },
  "permissions": {
    "network": { "required": false }
  }
}
```

### 2. `script.js`

Your script runs inside [QuickJS](https://bellard.org/quickjs/) on the Android device.

- Define **`function run(input)`** (or `export function run` — the host strips `export` for compatibility).
- Pure ES2020-style JS: **no** DOM (`window`, `document`), **no** Node (`require`).
- Return a JSON-serializable object (what you intend for `outputSchema`, even though the host does not validate it yet).

```javascript
export function run(input) {
  const query = input && input.query ? String(input.query) : "";
  return { result: query.toUpperCase() };
}
```

### 3. Zip it

```bash
# Inside your skill folder
zip -j my_awesome_skill.zip manifest.json script.js
```

### 4. Install on device

- **Skills screen → Import ZIP**: pick the `.zip` from local storage (no URL allowlist required).
- **From URL**: add the URL prefix to **Allowed Sources** in the same screen, then use **Install from URL** (or ask the AI to call `install_skill` with that URL).
- Uploading to GitHub Releases or any `https` host works as long as the URL matches an allowlisted prefix.

---

## Included Templates

Browse the folders in this directory:

- **`html_metadata_extractor`**: Parse raw HTML with regex (no DOM in QuickJS).
- **`markdown_to_blocks`**: Parse Markdown into structured JSON blocks.
- **`threads`**: Prepare a Threads post payload (also bundled as a default skill in the app assets).
- **`json_reformatter`**: Minify or pretty-print JSON.
- **`crypto_price_fetcher`**: Placeholder for network-backed fetching; **requires host network APIs** not available in the current sandbox.
- **`text_uppercase`**: Minimal pure transform example.

---

## How to Distribute

1. Build your ZIP file.
2. Upload it (e.g. GitHub Releases, static file host).
3. Users add your URL prefix to **Allowed Sources** for URL-based installs, or share the ZIP for local import.
