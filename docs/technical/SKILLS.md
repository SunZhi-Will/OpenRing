[English Version Below](#english-version)

# Skills Runtime（OpenRing）

## 繁體中文摘要

OpenRing 目前採用雙層 Skill 模型：

1. **Runtime Skill（必要）**：`manifest.json + script.js`，在裝置上由 QuickJS 執行 deterministic 邏輯。
2. **Instruction Skill（可選）**：`SKILL.md`，提供 OpenClaw-style 指引，告訴模型何時/如何呼叫 skill。

重點規則：

- 安裝 id 採 canonical `skillId`（由 `manifest.name` 正規化），工具名為 `skill_<skillId>`。
- `call_skill.input` 若存在必須是 JSON object。
- `run(input)` 回傳必須是 JSON object。
- ZIP 內可選擇攜帶 `SKILL.md`；啟用後會注入 system guidance。

目前與 OpenClaw 尚未完全等價：

- 尚未支援 OpenClaw 的多來源技能優先序載入。
- 尚未支援 `metadata.openclaw.*` gating。
- 尚未具備 ClawHub 式 registry/lifecycle。

---

<a id="english-version"></a>

# Skills Runtime (OpenRing)

OpenRing uses **Gemini function calling** for tool orchestration and **QuickJS Skills** for deterministic local logic.

This document focuses on the **actual host runtime contract** implemented in:

- `app/src/main/java/com/openring/skills/SkillInstall.kt`
- `app/src/main/java/com/openring/skills/SkillQuickJsExecutor.kt`
- `app/src/main/java/com/openring/agent/ToolSchemas.kt`
- `app/src/main/java/com/openring/agent/ToolDispatcher.kt`

---

## Runtime Overview

OpenRing now supports a **dual-layer skill model**:

1. **Runtime Skill (required)**: `manifest.json + script.js` for deterministic execution in QuickJS.
2. **Instruction Skill (optional)**: `SKILL.md` for OpenClaw-style model guidance about when/how to use the skill.

### Install paths

Skills can be installed from:

1. Local ZIP import in `SkillsScreen`
2. URL install (`install_skill` tool or Skills UI), only when URL matches `SkillAllowedSourcesStore` allowlist
3. Built-in template catalog metadata in `SkillsScreen` (download-on-install; scripts are fetched only after user clicks install)

### Install identity (`skillId`) is canonical

At install time, manifest `name` is normalized to the canonical installed id:

- keep only `[A-Za-z0-9_]`
- if empty after normalization, fallback to `skill`

That canonical id becomes:

- install folder name under `filesDir/skills/<skillId>`
- id in `InstalledSkillStore` and `SkillEnabledStore`
- dynamic tool name suffix (`skill_<skillId>`)
- required `call_skill.skill` value

### Dynamic tool registration

Enabled skills with a readable `manifest.json` are exposed to Gemini as dynamic tools:

- name: `skill_<skillId>`
- description: `manifest.description` (fallback: `Skill: <manifestName>`)
- parameters: `manifest.inputSchema` (fallback: empty object schema)

### Execution contract

Execution path:

- `call_skill` (explicit id) OR `skill_<skillId>` (dynamic tool)
- host resolves installed/enabled/canonical skill
- host reads `script.js`
- QuickJS evaluates `run(input)`

Hard requirements:

- `script.js` must define `run(input)` (sync function)
- `call_skill.input` must be a JSON object when provided
- `run(input)` return value must serialize and parse into a **JSON object**

---

## Manifest Contract (`manifest.json`)

### Required and validated

- `name`: required, non-empty string

### Optional but type-checked on install

- `inputSchema`: if present, must be a JSON object
- `outputSchema`: if present, must be a JSON object

### Optional, currently documentation-only

- `permissions`: stored as-is; not enforced in QuickJS runtime today

### Execution-time behavior notes

- `inputSchema` is used for Gemini function parameters on dynamic `skill_<skillId>` tools.
- `outputSchema` is not schema-validated against runtime output yet.
- Runtime only enforces that output is a JSON object shape.

---

## ZIP Packaging Rules

Current installer scans all ZIP entries and accepts package if it finds:

- one `manifest.json` entry
- one `script.js` entry
- optional `SKILL.md` entry

The files do **not** need to be at ZIP root for current installer logic.

If `SKILL.md` exists, it is installed under the skill directory and used as prompt guidance.

---

## OpenClaw-Style `SKILL.md` Compatibility

### What is supported

- Optional `SKILL.md` in skill ZIP.
- For enabled skills, OpenRing reads `SKILL.md`, strips YAML frontmatter, and injects the markdown body into system guidance.
- Guidance is surfaced as:
  - `### skill_<skillId> (<manifestName>)`
  - followed by clipped instruction body.

### What is not yet fully equivalent to OpenClaw

- No multi-location precedence loader (`/skills`, `~/.openclaw/skills`, bundled) yet.
- No load-time gating from `metadata.openclaw.*` fields yet.
- No standalone skill registry/installer lifecycle like ClawHub.

This compatibility layer is designed to reduce skill author friction while keeping OpenRing’s on-device QuickJS runtime model.

---

## Tool-Level Contracts

### `call_skill`

Arguments:

```json
{
  "skill": "threads",
  "input": {
    "text": "hello"
  }
}
```

Rules:

- `skill` is required
- `input` is optional; defaults to `{}`
- if `input` exists and is not a JSON object, returns `INVALID_ARGUMENT`

### `skill_<skillId>` dynamic tool

Rules:

- Arguments are passed directly as skill `input`
- Tool name must match the installed canonical `skillId`

---

## Common Error Codes (Skills)

Install path:

- `URL_NOT_ALLOWED`
- `INVALID_PACKAGE`
- `INVALID_MANIFEST`
- `INSTALL_FAILED`

Execution path:

- `SKILL_NOT_INSTALLED`
- `SKILL_DISABLED`
- `SKILL_NOT_FOUND`
- `INVALID_SKILL`
- `READ_FAILED`
- `SKILL_RUNTIME_ERROR`
- `INVALID_ARGUMENT`

---

## Security and Guardrails

- QuickJS Skill runtime is pure JS sandbox (no DOM/Node APIs).
- `permissions` in manifest is not enforced yet.
- URL install is restricted by user-managed allowlist.
- Skill enable/disable is user-controlled through `SkillEnabledStore` and the Skills UI.

---

## Related Docs

- `docs/skill-templates/README.md` for authoring templates and examples
- `docs/skill-templates/duolingo_word_match_guard/` for a deterministic external skill example targeting Duolingo word-match tasks
- `docs/technical/AI_AGENT.md` for end-to-end tool orchestration context (includes `describe_ambient_audio` and in-app **Permission settings**)
