[English Version Below](#english-version)

# 技能、工具與提示詞 (OpenRing)

此專案目前開放了 **工具 (Tools)** (Gemini 函式呼叫)，並為 **技能 (Skills)** (外掛引擎) 預留了位置。

## 您目前可以管理的內容

### 工具 (函式呼叫)

- **定義 (schemas)**: `app/src/main/java/com/openring/agent/ToolSchemas.kt`
  - 工具名稱、描述、JSON schema 參數 (包含 `required`)
- **實作 (執行時期行為)**: `app/src/main/java/com/openring/agent/ToolDispatcher.kt`
  - 將工具呼叫對應至裝置動作 (無障礙服務、intents 等)

### 提示詞 / 系統指令

- **協調器**: `app/src/main/java/com/openring/agent/ReActCoordinator.kt`
  - 目前發送：
    - `userText` 作為 `user` 內容
    - 工具結果作為 `functionResponse`
  - **尚未配置專屬的系統提示詞**

## 技能 (外掛引擎) – 狀態

- `call_skill` 工具已存在於 schema (`ToolSchemas.kt`)，但執行時期**尚未實作**。
- 目前行為：`ToolDispatcher.dispatch("call_skill", ...)` 回傳 `PERMISSION_DENIED`。

## 「道德鎖定」(防護欄) – 雙層機制

### 1) 開發時期防護欄 (Cursor rules)

這些規則管理 **AI 程式設計行為**：

- `.cursor/rules/morality-guardrails.mdc`
- `.cursor/rules/end-of-task-build.mdc`

### 2) App 執行時期道德鎖定 (執行階段權限)

這是一個 **App 內建切換開關**，用於控制是否允許未來的 AI/工具操作修改敏感設定：

- 儲存層: `app/src/main/java/com/openring/settings/MoralityStore.kt`
- UI 層: `app/src/main/java/com/openring/ui/screens/SkillsScreen.kt`

## 下一個實作里程碑 (建議)

為了讓技能「可安裝/可編輯/可管理」，需實作：

- 技能包格式 (例如 ZIP)
- `manifest.json` schema 驗證
- 用於已安裝技能的本地端儲存
- 權限 / 啟用-停用切換開關
- QuickJS 執行時期執行 + `call_skill` 串接

## 技能包範本

此儲存庫在 `docs/skill-templates/` 下包含幾個範例技能外掛包。

每個範本資料夾包含：
- `manifest.json`
- `script.js`

若要建立可安裝的 ZIP 檔，請將這些檔案打包在 ZIP 根目錄 (ZIP 應直接包含 `manifest.json` 和 `script.js`，而非嵌套於額外的目錄中)。

---
<a id="english-version"></a>

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
