[English Version Below](#english-version)

# OpenRing Skill Plugins（QuickJS）

## 繁體中文摘要

- OpenRing Skill 的執行核心仍是 `manifest.json + script.js`（QuickJS deterministic runtime）。
- 現在可選擇在 ZIP 內加入 `SKILL.md`，提供 OpenClaw-style 的「何時用、怎麼用」指引。
- 安裝後若 skill 啟用，系統會讀取 `SKILL.md`（去除 frontmatter 後內容）注入到模型 system guidance。
- `name` 會正規化成 canonical `skillId`，動態工具名為 `skill_<skillId>`。
- `outputSchema` 目前仍為文件契約，執行期僅強制回傳必須是 JSON object。

---

<a id="english-version"></a>

# OpenRing Skill Plugins (QuickJS)

OpenRing Skill Plugins empower the **chat agent** (Gemini with function calling / ReAct inside the app) with **deterministic logic, custom integrations, and data processing** capabilities. On-device GGUF chat does not invoke the same tool loop; Skills are used when the **Gemini** path calls `call_skill` / `skill_*`. Since the agent's context window is limited and LLMs can hallucinate logic, Skills provide local sandboxed code (running on QuickJS) that the Agent can call reliably.

This directory contains templates to help developers and users build Skills for the OpenRing ecosystem.

---

## Host runtime behavior (what OpenRing enforces today)

| Manifest field | Enforced at install | Enforced at execution |
|----------------|--------------------|------------------------|
| `name` | Yes (required, non-empty; normalized into canonical `skillId`) | Dynamic tool name is `skill_<skillId>` |
| `description` | Optional (recommended) | Exposed to the model for `skill_<skillId>` |
| `inputSchema` | If present, must be a JSON object | Passed as Gemini function parameters for `skill_<skillId>` |
| `outputSchema` | If present, must be a JSON object | **Not validated** (documentation / future use) |
| `permissions` | Stored as-is | **Not enforced** — no network/storage APIs inside QuickJS yet (roadmap) |

Skills must define **`function run(input)`** in `script.js` (see `SkillQuickJsExecutor`); `export` prefixes are tolerated.
`call_skill.input` must be a JSON object, and `run(input)` must return a JSON object.

---

## Why Build a Skill?

1. **Deterministic Processing**: Need complex regex extraction or specific math? The LLM might make mistakes, but a JS script won't.
2. **Data Transformation**: Convert massive raw inputs (like raw HTML or JSON) into small, clean payloads before feeding them back to Gemini, saving tokens.
3. **Local Tooling (roadmap)**: Network requests or storage under explicit user permission would require host APIs not present in the current QuickJS sandbox.

---

## How to Build and Package a Skill

An OpenRing Skill is a `.zip` file containing at least two files:

```text
my_awesome_skill.zip
├── manifest.json   # Describes the tool to the Gemini Agent (name, description, inputSchema)
├── script.js       # JavaScript executed by QuickJS
└── SKILL.md        # Optional OpenClaw-style guidance for when/how the model should invoke this skill
```

### 1. `manifest.json`

Minimum for installation:

- **`name`** (string, required): normalized install id keeps only alphanumeric + `_`; this canonical `skillId` is used by `call_skill` and `skill_<skillId>`.
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

OpenRing currently scans ZIP entries and accepts any structure that contains both `manifest.json` and `script.js` somewhere in the archive.
If `SKILL.md` is present, OpenRing installs it and injects its instruction body into model system guidance for enabled skills.

### 3.5 Optional `SKILL.md` (OpenClaw-style instructions)

Use `SKILL.md` to teach the model *when to use* your skill and what arguments to pass.
This is guidance text only; runtime execution still comes from `script.js`.

Recommended structure:

```markdown
---
name: my_skill_name
description: One-line purpose of this skill.
---

# My Skill

Use this skill when:
- Condition A
- Condition B

Input contract:
- query: string

Output contract:
- result: string

Do not use this skill when:
- Condition X
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
- **`crypto_price_fetcher`**: Deterministic placeholder; real networking requires host bridge APIs.
- **`text_uppercase`**: Minimal pure transform example.
- **`duolingo_word_match_guard`**: Deterministically resolve a Duolingo word-match target and block ambiguous clicks.

### Template I/O examples

#### `threads`

Input:

```json
{
  "text": "Today I shipped OpenRing skill improvements.",
  "includeLink": false
}
```

Output:

```json
{
  "postText": "Today I shipped OpenRing skill improvements.",
  "includeLink": false
}
```

#### `text_uppercase`

Input:

```json
{
  "text": "OpenRing"
}
```

Output:

```json
{
  "text": "OPENRING"
}
```

#### `html_metadata_extractor`

Input:

```json
{
  "htmlString": "<html><head><title>OpenRing</title><meta name=\"description\" content=\"Automation\" /></head></html>"
}
```

Output:

```json
{
  "title": "OpenRing",
  "description": "Automation",
  "image": ""
}
```

#### `json_reformatter`

Input:

```json
{
  "data": {
    "a": 1,
    "b": [1, 2, 3]
  },
  "style": "pretty"
}
```

Output:

```json
{
  "json": "{\n  \"a\": 1,\n  \"b\": [\n    1,\n    2,\n    3\n  ]\n}\n"
}
```

#### `markdown_to_blocks`

Input:

```json
{
  "markdown": "# Title\nHello world\n- item 1\n- item 2"
}
```

Output:

```json
{
  "blocks": [
    { "type": "h1", "content": "Title" },
    { "type": "paragraph", "content": "Hello world" },
    { "type": "list", "content": "- item 1\n- item 2" }
  ]
}
```

#### `crypto_price_fetcher`

Input:

```json
{
  "symbols": ["BTC", "ETH"]
}
```

Current output (placeholder):

```json
{
  "prices": {
    "BTC": 0,
    "ETH": 0
  }
}
```

The current sandbox has no native network bridge, so this template intentionally returns deterministic placeholder values.

#### `duolingo_word_match_guard`

Package URL (local build artifact):

```text
file:///Applications/Projects/OpenRing/build/skill-packages/duolingo_word_match_guard.zip
```

Input:

```json
{
  "target": "apple",
  "choices": ["apple", "banana", "orange", "蘋果"],
  "allowContainsFallback": false
}
```

Output:

```json
{
  "status": "ok",
  "selected": "apple",
  "confidence": 1,
  "reason": "unique exact normalized match",
  "matchedIndices": [0]
}
```

Ambiguous example:

```json
{
  "status": "ambiguous",
  "selected": "",
  "confidence": 0,
  "reason": "multiple exact matches; do not click blindly",
  "matchedIndices": [0, 3]
}
```

---

## How to Distribute

1. Build your ZIP file.
2. Upload it (e.g. GitHub Releases, static file host).
3. Users add your URL prefix to **Allowed Sources** for URL-based installs, or share the ZIP for local import.
