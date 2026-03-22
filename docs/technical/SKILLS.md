[English Version Below](#english-version)

# 技能、工具與提示詞 (OpenRing)

此專案透過 **工具 (Tools)**（Gemini 函式呼叫）驅動自動化，並以 **技能 (Skills)**（QuickJS 外掛）擴充可重複、確定性的邏輯。

## 您目前可以管理的內容

### 工具 (函式呼叫)

- **定義 (schemas)**: `app/src/main/java/com/openring/agent/ToolSchemas.kt`
  - 工具名稱、描述、JSON schema 參數 (包含 `required`)
  - 已啟用的 Skill 會額外註冊為 `skill_<manifest.name>`（與 `manifest.json` 的 `name` 一致時最清楚）
- **實作 (執行時期行為)**: `app/src/main/java/com/openring/agent/ToolDispatcher.kt`
  - 將工具呼叫對應至裝置動作 (無障礙服務、intents 等)
  - `get_view_tree` / `get_cached_scan`：送進 Gemini 前可由 `ReActCoordinator` 搭配 `UiTreeCompact` **壓縮**；亦可改呼叫 **`summarize_view_tree`** 只取精簡摘要
  - `describe_screen`：螢幕截圖 + Gemini 視覺（需 API Key）
  - `call_skill` / `skill_*`：讀取已安裝技能目錄下的 `script.js`，於 QuickJS 執行 `run(input)`
  - `install_skill`：從**白名單 URL** 下載 ZIP 並安裝（見 `SkillAllowedSourcesStore`）

### 提示詞 / 系統指令

- **協調器**: `app/src/main/java/com/openring/agent/ReActCoordinator.kt`
  - 發送使用者訊息、工具結果；每輪會重新建立工具列表（含動態 Skill）
- 系統提示詞可由 `AiPromptStore` / 設定畫面配置（與 Skills 分開）

## 技能 (外掛引擎) – 目前狀態

| 能力 | 說明 |
|------|------|
| 執行 | `call_skill` 已實作：依 **已啟用** 的已安裝技能執行 `script.js`（`SkillQuickJsExecutor`） |
| 動態工具名 | 啟用的技能若具備可讀的 `manifest.json`，會向模型暴露 `skill_<name>`，參數來自 `inputSchema` |
| 安裝 | **本機 ZIP**（Skills 畫面「匯入 ZIP」）、**URL**（須符合白名單；AI 的 `install_skill` 亦同）、**內建** `threads`（`DefaultSkillBootstrap` 自 assets 複製） |
| 啟用/停用 | `SkillEnabledStore`；未啟用則 `call_skill` 回傳 `SKILL_DISABLED` |

### 尚未實作或僅文件層級

- `manifest.json` 的 **`permissions`**：執行時未強制；QuickJS 內無對應網路/儲存等 bridge（見產品 backlog）。
- **`outputSchema`**：安裝時可檢查型別為 JSON object，但**執行後不驗證**回傳是否符合 schema。

## 「道德鎖定」(防護欄) – 雙層機制

### 1) 開發時期防護欄 (Cursor rules)

- `.cursor/rules/morality-guardrails.mdc`
- `.cursor/rules/end-of-task-build.mdc`

### 2) App 執行時期道德鎖定 (執行階段權限)

- 儲存層: `app/src/main/java/com/openring/settings/MoralityStore.kt`
- UI 層: `app/src/main/java/com/openring/ui/screens/MoralityEditScreen.kt`（與 Skills 清單分開）

## 技能包格式與範本

- 範例與格式說明：`docs/skill-templates/`
- ZIP 根目錄應含 `manifest.json` 與 `script.js`（可與 `docs/skill-templates/README.md` 對照）

---

<a id="english-version"></a>

# Skills, Tools, and Prompts (OpenRing)

This project uses **Tools** (Gemini function calling) for automation and **Skills** (QuickJS plugins) for deterministic, reusable logic.

## What you can manage today

### Tools (function calling)

- **Definition (schemas)**: `app/src/main/java/com/openring/agent/ToolSchemas.kt`
  - Tool names, descriptions, JSON schema parameters (incl. `required`)
  - Enabled Skills also register as `skill_<manifest.name>` (keep `name` aligned with the install folder id)
- **Implementation (runtime)**: `app/src/main/java/com/openring/agent/ToolDispatcher.kt`
  - Maps tool calls to device actions (Accessibility, intents, etc.)
  - `get_view_tree` / `get_cached_scan`: may be **compacted** for Gemini via `UiTreeCompact` in `ReActCoordinator`; **`summarize_view_tree`** returns a compact summary only
  - `describe_screen`: screenshot + Gemini vision (requires API key)
  - `call_skill` / `skill_*`: load `script.js` from installed skill folders and run `run(input)` in QuickJS
  - `install_skill`: download a ZIP from an **allowlisted** URL (`SkillAllowedSourcesStore`)

### Prompt / system instruction

- **Coordinator**: `app/src/main/java/com/openring/agent/ReActCoordinator.kt`
  - Sends user messages and tool results; rebuilds the tool list each run (including dynamic Skills)
- System prompt is configured via `AiPromptStore` / settings (separate from Skills)

## Skills (plugin engine) – current status

| Capability | Notes |
|------------|--------|
| Execution | `call_skill` is implemented for **enabled** installed skills via `script.js` (`SkillQuickJsExecutor`) |
| Dynamic tool names | Enabled skills with a readable `manifest.json` expose `skill_<name>` with parameters from `inputSchema` |
| Installation | **Local ZIP** (Skills screen), **URL** (must match allowlist; same for AI `install_skill`), **bundled** `threads` (copied from assets in `DefaultSkillBootstrap`) |
| Enable/disable | `SkillEnabledStore`; disabled skills return `SKILL_DISABLED` |

### Not implemented or documentation-only

- **`permissions` in manifest**: not enforced at runtime; no network/storage bridge inside QuickJS yet (see product backlog).
- **`outputSchema`**: type shape may be validated on install, but return values are **not** validated after execution.

## “Morality Lock” (guardrails) – two layers

### 1) Development-time guardrails (Cursor rules)

- `.cursor/rules/morality-guardrails.mdc`
- `.cursor/rules/end-of-task-build.mdc`

### 2) App-time Morality Lock

- Store: `app/src/main/java/com/openring/settings/MoralityStore.kt`
- UI: `app/src/main/java/com/openring/ui/screens/MoralityEditScreen.kt` (separate from the Skills list)

## Skill package format and templates

- Examples: `docs/skill-templates/`
- ZIP root should contain `manifest.json` and `script.js` (see `docs/skill-templates/README.md`)
