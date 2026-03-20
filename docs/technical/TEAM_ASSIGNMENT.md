[English Version Below](#english-version)

# OpenRing 團隊分工與啟動 Prompt

> 純手機端架構 — 僅需 Android 開發者

---

## 分工總覽

| 角色 | 負責範圍 | 參考文件 |
|------|----------|----------|
| **A. Android 開發者** | 全 App：核心、儲存、排程、UI | PROJECT_PLAN, SCRIPT_FORMAT, PRODUCT_BACKLOG |

**說明：** 純手機端架構下，無需後端、前端、WebSocket。所有功能由單一 Android App 完成。

---

## A. Android 開發者

### 負責範圍
- **核心層**：AccessibilityService、ViewTreeParser、ActionExecutor、IntentRouter、SensitiveFilter、OverlayService
- **業務層**：ScriptStore（Room）、ScriptExecutor、Scheduler（WorkManager）
- **UI 層**：腳本列表、腳本編輯器、排程設定、執行歷史

### 參考文件
1. `docs/PROJECT_PLAN.md` — 全文（架構、模組、資料模型、里程碑）
2. `docs/SCRIPT_FORMAT.md` — 腳本 JSON 格式、動作類型、排程格式
3. `docs/PRODUCT_BACKLOG.md` — Epic 1、2、3 全部 User Story

### 啟動 Prompt

```text
你是 OpenRing 的 Android 開發者。OpenRing 是「純手機端」架構，所有功能在手機上完成，不需 Web 控制台、不需後端。

請根據以下文件實作：

1. 閱讀 docs/PROJECT_PLAN.md — 系統架構、模組拆解、目錄結構
2. 閱讀 docs/SCRIPT_FORMAT.md — 腳本 JSON 格式、動作類型
3. 閱讀 docs/PRODUCT_BACKLOG.md — Epic 1~6 的 User Story（包含 Chat-Driven OS / Gemini / QuickJS Skills）

請依序實作：

【Phase 1 MVP：ReAct 閉環 + JSON 節點 + QuickJS Skill + Human takeover】
- AccessibilityService 骨架，可啟用並取得基本節點
- ViewTreeParser：將 AccessibilityNodeInfo 轉為語意化 JSON，並實作 SensitiveFilter（password/金鑰遮蔽）
- ActionExecutor / Gesture tools：支援 click/swipe/back/home + extract_text 等工具
- OverlayService + Working Bubble：執行時可視化狀態（內心獨白/step）
- Chat UI（最小可用）：發起任務並展示最終回覆
- ReAct Loop Coordinator：sense -> think/tool -> act -> finish 閉環跑通
- Gemini Function Calling Dispatcher：tool calls 可執行並把結果回填給 Gemini（包含 call_skill）
- QuickJS Runtime：初始化、timeout、可序列化 return value
- Skill manifest + 單一 Skill 安裝（zip）與執行（run(input)）
- Human takeover：連續 NODE_NOT_FOUND 時紅色震動 + 使用者一次 tap hint 後可繼續

【Phase 2 擴充：Marketplace 與體驗完善】
- Working Bubble UX polish
- Skill permission UI / 授權記憶
- Skill marketplace 基礎：技能清單、啟用/停用、zip 匯入（以及可選的 GitHub 匯入）
- 保留既有 ScriptStore / ScriptExecutor / Scheduler 作為本地替代流程或工具後端

【Phase 3 進階：VLM fallback】
- VLM screenshot fallback：連續失敗後改用視覺定位策略
- 錯誤追蹤與重試策略強化

技術棧：Kotlin，minSdk 26，targetSdk 34。UI 可用 Jetpack Compose 或 XML。參考 Android AccessibilityService、GestureDescription、WorkManager、Room 官方文件。
```

---

## 快速參考：誰看哪個文件

| 文件 | A (Android) |
|------|-------------|
| PROJECT_PLAN.md | ✓ 全文 |
| SCRIPT_FORMAT.md | ✓ 全文 |
| PRODUCT_BACKLOG.md | ✓ Epic 1, 2, 3 |

---

## 備註

- `docs/PROTOCOL.md` 為舊版 WebSocket 協定，純手機端架構下**不再使用**，可保留作參考或刪除。
- 若未來擴充「電腦遠端控制」功能，可再引入後端與 WebSocket。

---
<a id="english-version"></a>

# OpenRing Team Assignment and Startup Prompt

> Mobile-only Architecture — Only requires an Android Developer

---

## Assignment Overview

| Role | Responsibility Scope | Reference Documents |
|------|----------|----------|
| **A. Android Developer** | Entire App: Core, Storage, Scheduling, UI | PROJECT_PLAN, SCRIPT_FORMAT, PRODUCT_BACKLOG |

**Note:** Under the mobile-only architecture, there is no need for a backend, frontend, or WebSocket. All functions are completed by a single Android App.

---

## A. Android Developer

### Responsibility Scope
- **Core Layer**: AccessibilityService, ViewTreeParser, ActionExecutor, IntentRouter, SensitiveFilter, OverlayService
- **Business Layer**: ScriptStore (Room), ScriptExecutor, Scheduler (WorkManager)
- **UI Layer**: Script List, Script Editor, Schedule Settings, Execution History

### Reference Documents
1. `docs/PROJECT_PLAN.md` — Full document (Architecture, Modules, Data Models, Milestones)
2. `docs/SCRIPT_FORMAT.md` — Script JSON Format, Action Types, Schedule Format
3. `docs/PRODUCT_BACKLOG.md` — All User Stories for Epic 1, 2, 3

### Startup Prompt

```text
You are the Android Developer for OpenRing. OpenRing is a "mobile-only" architecture where all functions are completed on the mobile device, requiring no Web Console and no Backend.

Please implement according to the following documents:

1. Read docs/PROJECT_PLAN.md — System Architecture, Module Breakdown, Directory Structure
2. Read docs/SCRIPT_FORMAT.md — Script JSON Format, Action Types
3. Read docs/PRODUCT_BACKLOG.md — User Stories for Epic 1~6 (including Chat-Driven OS / Gemini / QuickJS Skills)

Please implement in the following order:

[Phase 1 MVP: ReAct Loop + JSON Nodes + QuickJS Skill + Human takeover]
- AccessibilityService skeleton, enableable and capable of retrieving basic nodes
- ViewTreeParser: Convert AccessibilityNodeInfo into semantic JSON, and implement SensitiveFilter (password/key masking)
- ActionExecutor / Gesture tools: Support click/swipe/back/home + extract_text and other tools
- OverlayService + Working Bubble: Visualized state during execution (inner monologue/step)
- Chat UI (Minimum Viable): Initiate tasks and display the final response
- ReAct Loop Coordinator: Successfully run the sense -> think/tool -> act -> finish loop
- Gemini Function Calling Dispatcher: Tool calls can be executed and results populated back to Gemini (including call_skill)
- QuickJS Runtime: Initialization, timeout, serializable return value
- Skill manifest + Single Skill installation (zip) and execution (run(input))
- Human takeover: Red vibration upon continuous NODE_NOT_FOUND + user can continue after a single tap hint

[Phase 2 Expansion: Marketplace and Experience Polish]
- Working Bubble UX polish
- Skill permission UI / Authorization memory
- Skill marketplace basics: Skill list, enable/disable, zip import (and optional GitHub import)
- Retain existing ScriptStore / ScriptExecutor / Scheduler as a local fallback process or tool backend

[Phase 3 Advanced: VLM fallback]
- VLM screenshot fallback: Switch to visual positioning strategy after continuous failures
- Error tracking and retry strategy enhancement

Tech Stack: Kotlin, minSdk 26, targetSdk 34. UI can use Jetpack Compose or XML. Reference official Android AccessibilityService, GestureDescription, WorkManager, and Room documentation.
```

---

## Quick Reference: Who reads which document

| Document | A (Android) |
|------|-------------|
| PROJECT_PLAN.md | ✓ Full Doc |
| SCRIPT_FORMAT.md | ✓ Full Doc |
| PRODUCT_BACKLOG.md | ✓ Epic 1, 2, 3 |

---

## Notes

- `docs/PROTOCOL.md` is the legacy WebSocket protocol, which is **no longer used** under the mobile-only architecture. It can be kept for reference or deleted.
- If the "PC remote control" feature is expanded in the future, backend and WebSocket can be reintroduced.
