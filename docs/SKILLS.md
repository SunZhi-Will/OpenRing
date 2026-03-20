# Skills, Tools, and Prompts (OpenRing)

This project currently exposes **Tools** (Gemini function calling) and has a placeholder for **Skills** (plugin engine).

## What you can manage today

### Tools (function calling)

- **Definition (schemas)**: `app/src/main/java/com/openring/agent/ToolSchemas.kt`
  - Tool name, description, JSON schema parameters (incl. `required`)
- **Implementation (runtime behavior)**: `app/src/main/java/com/openring/agent/ToolDispatcher.kt`
  - Maps tool calls to device actions (Accessibility, intents, etc.)

### Prompt / System instruction

- **Coordinator**: `app/src/main/java/com/openring/agent/ReActCoordinator.kt`
  - Currently sends:
    - `userText` as a `user` content
    - tool results as `functionResponse`
  - **No dedicated system prompt is configured yet**

## Skills (plugin engine) – status

- The tool `call_skill` exists in schema (`ToolSchemas.kt`), but the runtime is **not implemented yet**.
- Current behavior: `ToolDispatcher.dispatch("call_skill", ...)` returns `PERMISSION_DENIED`.

## “Morality Lock” (guardrails) – two layers

### 1) Development-time guardrails (Cursor rules)

These rules govern **AI coding behavior**:

- `.cursor/rules/morality-guardrails.mdc`
- `.cursor/rules/end-of-task-build.mdc`

### 2) App-time Morality Lock (runtime permission)

This is an **in-app toggle** to control whether future AI/tool operations are allowed to modify sensitive settings:

- Store: `app/src/main/java/com/openring/settings/MoralityStore.kt`
- UI: `app/src/main/java/com/openring/ui/screens/SkillsScreen.kt`

## Next implementation milestone (recommended)

To make Skills “installable/editable/manageable”, implement:

- Skill package format (e.g. ZIP)
- `manifest.json` schema validation
- Local storage for installed skills
- Permissions / enable-disable toggles
- QuickJS runtime execution + `call_skill` wiring

## Skill package templates

This repository includes a few example Skill plugin packages under `docs/skill-templates/`.

Each template folder contains:
- `manifest.json`
- `script.js`

To create an installable ZIP, package those files at the ZIP root (ZIP should contain `manifest.json` and `script.js`, not nested in extra directories).

