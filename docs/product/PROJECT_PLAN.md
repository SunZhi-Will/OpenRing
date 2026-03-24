[English Version Below](#english-version)

# OpenRing 專案規劃書

> 版本：v0.4 | 更新日期：2026-03-22  
> **架構：純手機端，無 Web 控制台、無 OpenRing 自建後端**

---

## 1. 專案總覽

### 1.1 產品定位

| 項目 | 說明 |
|------|------|
| **產品名稱** | OpenRing |
| **產品類型** | 開源 Android 端跨 App 自動化 Agent |
| **核心價值** | 以「語意化 UI 節點」取代「絕對座標」，實現可移植、可維護的 RPA 工作流 |
| **架構原則** | **純手機端** — 所有功能在手機上完成，不需電腦、不需後台伺服器 |
| **目標平台** | Android 8.0+ (API 26+) |

### 1.2 成功指標 (Success Metrics)

| 指標 | 目標值 | 衡量方式 |
|------|--------|----------|
| 腳本可移植性 | 同一腳本在 2 款以上機型正常執行 | 實測覆蓋率 |
| 任務成功率 | ≥ 95% | 排程任務成功數 / 總觸發數 |
| 離線可用 | 無網路時仍可編輯、排程（AI 聊天與技能取資料需網路） | 功能驗證 |
| 社群採用 | GitHub Stars / 活躍貢獻者 | 開源指標 |

---

## 2. 專案範圍與邊界

### 2.1 In Scope (MVP)

- Android AccessibilityService 核心：View Tree 解析、手勢模擬
- Chat-Driven OS：主控對話室、Working Bubble 狀態顯示
- ReAct Loop Coordinator：sense/think/tool/act/finalize 閉環
- Gemini Function Calling dispatcher：tool 結果結構化回填；大型 UI 樹可經 `UiTreeCompact` 壓縮；`summarize_view_tree` 提供精簡摘要
- 可選本機 GGUF（`LocalLlmEngine` + 型錄下載）：純文字對話、串流輸出（無內建工具迴圈）
- QuickJS Skill Plugin Engine：單一 Skill 安裝與執行
- BYOK：Gemini API Key 管理、敏感資料遮蔽
- Human takeover：連續找不到節點時的紅色震動提示與一次 tap hint
- **App 內腳本編輯器**：在手機上建立、編輯工作流
- **App 內腳本儲存**：本地 Room / DataStore
- **App 內排程**：WorkManager 或 AlarmManager 定時觸發
- 敏感節點過濾、執行狀態懸浮窗
- 腳本匯出/匯入（JSON 檔案，可選）

### 2.2 Out of Scope (MVP)

- Web 控制台
- 後端 API、WebSocket
- Webhook 遠端觸發
- 多裝置管理
- iOS 支援
- OpenRing 自建雲端推理／後台託管模型（使用者自備 BYOK 呼叫 Google API 不在此列）

---

## 3. 系統架構（純手機端）

```
┌─────────────────────────────────────────────────────────────────┐
│                     OpenRing Android App                         │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                      UI 層 (Activity / Fragment)              │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐ │ │
│  │  │ 腳本列表      │  │ 腳本編輯器    │  │ 排程設定 / 執行歷史  │ │ │
│  │  └──────────────┘  └──────────────┘  └────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                              │                                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                      業務邏輯層                              │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐ │ │
│  │  │ ScriptStore   │  │ Scheduler     │  │ ScriptExecutor     │ │ │
│  │  │ (本地儲存)    │  │ (WorkManager) │  │ (執行引擎)          │ │ │
│  │  └──────────────┘  └──────────────┘  └────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                              │                                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                      核心層 (AccessibilityService)           │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐ │ │
│  │  │ ViewTreeParser│  │ ActionExecutor│  │ IntentRouter        │ │ │
│  │  └──────────────┘  └──────────────┘  └────────────────────┘ │ │
│  │  ┌──────────────────────────────────────────────────────────┐│ │
│  │  │ SensitiveFilter │ OverlayService (懸浮窗)                  ││ │
│  │  └──────────────────────────────────────────────────────────┘│ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. 模組拆解與規格

### 4.1 核心層（AccessibilityService）

| 模組 | 功能描述 | 輸入 | 輸出 | 依賴 |
|------|----------|------|------|------|
| **ViewTreeParser** | 解析當前畫面為結構化 JSON | AccessibilityEvent | JSON (節點樹) | AccessibilityService |
| **ActionExecutor** | 執行點擊、滑動、長按、返回、Home | 動作指令 + 節點 ID | 執行結果 | AccessibilityService, GestureDescription |
| **IntentRouter** | 喚醒 App、Deep Link 跳轉 | Package/URI | 跳轉結果 | Android Intent |
| **SensitiveFilter** | 過濾 password、金鑰輸入框 | 原始節點樹 | 脫敏後節點樹 | 無 |
| **OverlayService** | 執行時顯示鸚鵡圖示 | 執行狀態 | 懸浮窗 UI | WindowManager |

### 4.2 業務邏輯層

| 模組 | 功能描述 | 輸入 | 輸出 | 依賴 |
|------|----------|------|------|------|
| **ScriptStore** | 腳本 CRUD，本地儲存 | 腳本 JSON | 儲存/讀取結果 | Room / DataStore |
| **Scheduler** | 定時觸發腳本 | 腳本 ID + Cron 表達式 | 到時觸發 | WorkManager |
| **ScriptExecutor** | 依 steps 依序執行，呼叫 ActionExecutor | 腳本 steps | 執行結果、變數 | 核心層 |

### 4.3 UI 層

| 畫面 | 功能描述 | 元件 |
|------|----------|------|
| **腳本列表** | 顯示所有腳本、新增、刪除、執行按鈕 | RecyclerView / LazyColumn |
| **腳本編輯器** | 區塊式步驟編輯、參數設定、儲存 | 列表 + 表單 |
| **排程設定** | 設定定時（每日/每小時/自訂）、啟用/停用 | 表單 |
| **執行歷史** | 近期執行記錄、成功/失敗、錯誤訊息 | 列表 |
| **主控聊天（Chat）** | 對話驅動任務、模型鏈、本機串流／Gemini ReAct | Compose |
| **設定** | API Key、模型鏈、本機模型下載、掃描／道德鎖定等 | Compose |
| **技能（Skills）** | 匯入 ZIP／URL、啟用技能、白名單來源 | Compose |

### 4.4 Agent 與本機模型層

| 模組 | 功能描述 | 主要檔案 |
|------|----------|----------|
| **ReActCoordinator** | Gemini 多輪、工具呼叫、結果回填、可選 UI 壓縮 | `agent/ReActCoordinator.kt` |
| **ToolDispatcher / ToolSchemas** | 工具註冊與執行（含 `summarize_view_tree`、`describe_screen`） | `agent/ToolDispatcher.kt`, `ToolSchemas.kt` |
| **UiTreeCompact** | 指紋與可點擊摘要，縮小送模組之 payload | `agent/UiTreeCompact.kt` |
| **LocalLlmEngine** | GGUF 載入與串流／非串流推論 | `localmodel/LocalLlmEngine.kt` |
| **LocalModelCatalog** | 公開 GGUF 下載 URL 與檔名 | `localmodel/LocalModelCatalog.kt` |

---

## 5. 資料模型 (Data Model)

### 5.1 腳本 (Script)

```json
{
  "id": "uuid",
  "name": "ADA 收益巡邏",
  "version": 1,
  "steps": [
    {
      "type": "launch_app",
      "params": { "package": "com.example.wallet" }
    },
    {
      "type": "wait",
      "params": { "ms": 2000 }
    },
    {
      "type": "find_and_click",
      "params": { "text": "ADA", "match": "contains" }
    },
    {
      "type": "extract_text",
      "params": { "nodeId": "price_label", "variable": "adaPrice" }
    }
  ],
  "schedule": {
    "enabled": true,
    "type": "daily",
    "hour": 9,
    "minute": 0
  }
}
```

### 5.2 節點樹 (View Tree JSON)

```json
{
  "root": {
    "id": "node_0",
    "className": "android.widget.TextView",
    "text": "ADA",
    "contentDesc": null,
    "clickable": true,
    "bounds": { "left": 100, "top": 200, "right": 200, "bottom": 250 },
    "children": []
  }
}
```

### 5.3 動作類型 (Action Types)

| type | params | 說明 |
|------|--------|------|
| `launch_app` | `package`, `uri?` | 喚醒 App，可選 Deep Link |
| `wait` | `ms` | 等待毫秒 |
| `find_and_click` | `text`, `match`, `nodeId?` | 依文字或 nodeId 點擊 |
| `click_node` | `nodeId` | 依 nodeId 點擊 |
| `swipe` | `direction`, `distance?` | 滑動 |
| `long_press` | `nodeId?`, `text?` | 長按 |
| `back` | - | 返回鍵 |
| `home` | - | Home 鍵 |
| `extract_text` | `nodeId`, `variable` | 提取文字存入變數 |

---

## 6. 里程碑與時程規劃

### Phase 0：專案初始化 (約 3 天)

| 任務 | 產出 |
|------|------|
| Android 專案建立 | Kotlin + Gradle，minSdk 26 |
| 模組結構、依賴 | 分層清晰 |
| 腳本格式文件 | `docs/SCRIPT_FORMAT.md` |

### Phase 1：核心層 (2–3 週)

| 週次 | 里程碑 | 交付物 |
|------|--------|--------|
| W1 | AccessibilityService 骨架 + ViewTreeParser | 可取得語意化 View Tree JSON（含 sensitive filter） |
| W2 | ActionExecutor + Gesture tools + OverlayService | click/swipe/back/home 等可執行工具集合 |
| W2 | Chat UI（最小可用）+ Working Bubble | 聊天發起任務，執行時可看到狀態 |
| W3 | ReAct Loop Coordinator + Gemini Function Calling | tool calls 可被 dispatcher 執行並回填結果 |
| W4 | QuickJS Runtime + 單一 Skill 安裝/執行 + Human takeover | 可 `call_skill` 並處理 `NODE_NOT_FOUND` hint 流程 |

### Phase 2：擴充層（1–2 週）

| 任務 | 產出 |
|------|------|
| Working Bubble UX polish | 狀態文字/錯誤提示更清晰、UX 可接受 |
| Skill Marketplace 基礎入口 | zip 匯入/技能清單/權限請求（最小版） |
| ScriptStore / ScriptExecutor / Scheduler（維持現有） | 保留既有本地腳本能力作為工具後端或替代流程 |

### Phase 3：進階層（1–2 週）

| 週次 | 里程碑 | 交付物 |
|------|--------|--------|
| W1 | Human takeover 強化（容錯與閾值調整） | 盲區突破成功率提升 |
| W2 | VLM screenshot fallback（Phase 3） | 連續失敗時能切換視覺定位策略 |
| W3 | 啟動/穩定性優化 | 失敗重試策略與錯誤追蹤更完整 |

### Phase 4：整合與發布 (約 1 週)

| 任務 | 產出 |
|------|------|
| 端到端測試 | 完整流程通過 |
| 文件與 README | 安裝、使用教學 |
| v0.1.0 發布 | GitHub Release、APK |

---

## 7. 風險與緩解

| 風險 | 影響 | 機率 | 緩解策略 |
|------|------|------|----------|
| 部分 App 限制 AccessibilityService | 高 | 中 | 文件說明支援範圍；提供除錯模式 |
| 廠商 ROM 差異導致手勢失敗 | 高 | 中 | 多機型測試；fallback 策略 |
| 手機上編輯腳本體驗較差 | 中 | 中 | 精簡步驟、常用預設、匯入 JSON |
| 背景執行被系統殺掉 | 中 | 中 | WorkManager、電池優化白名單說明 |

---

## 8. 依賴與前置條件

### 8.1 技術依賴

- **Android**：Kotlin 2.3.10, minSdk 26, targetSdk 34, compileSdk 36, Build-Tools 36.0.0
- **儲存**：Room 或 DataStore
- **排程**：WorkManager
- **UI**：Jetpack Compose 或 XML（擇一）

### 8.2 外部依賴

- **可選**：使用者自備 **Google Gemini API（BYOK）**，用於雲端對話、`describe_screen` 視覺與完整工具迴圈。
- **可選**：`LocalModelCatalog` 所列之 **HTTPS GGUF** 下載（例如 Hugging Face 公開連結）。
- 核心自動化能力**不要求**任何 OpenRing 付費服務。

---

## 9. 交付清單 (Definition of Done)

- [ ] 程式碼通過格式化（Lint gate 目前因工具穩定性暫時關閉，待恢復後重新納入）
- [ ] 關鍵路徑有單元測試（目前為目標項，現況以手動驗證 + build 為主）
- [ ] 敏感節點過濾通過人工驗證
- [ ] 執行時有明顯狀態指示
- [ ] README 含安裝與基本使用說明
- [ ] 無已知 P0/P1 Bug

### 9.1 文件同步策略

- 每次更新建置版本（Kotlin / SDK / build tools）時，同步更新 `README.md`、`README.zh-TW.md`、`docs/technical/CI_CD.md`。
- 若 CI 的品質閘門變更（例如 lint/test 啟閉），需同步更新 `docs/technical/CI_CD.md` 與本章 DoD 說明。
- 當產品能力（Agent/tool/permission flow）變更時，同步更新 `docs/technical/AI_AGENT.md` 與 `docs/technical/SCRIPT_FORMAT.md`。

---

## 10. 附錄

### A. 目錄結構

```
OpenRing/
├── app/                    # Android App 主模組
│   ├── src/main/
│   │   ├── java/.../       # Kotlin 原始碼
│   │   │   ├── core/       # AccessibilityService, Parser, Executor, ScreenCapture, 播放音訊／MediaProjection
│   │   │   ├── agent/      # ReAct, ToolDispatcher, ToolSchemas
│   │   │   ├── localmodel/ # GGUF 型錄、LocalLlmEngine
│   │   │   ├── data/       # ScriptStore, Room, ChatRepository
│   │   │   ├── domain/     # ScriptExecutor, Scheduler
│   │   │   └── ui/         # Jetpack Compose（含設定內「權限設定」、聊天）
│   │   └── res/
│   └── build.gradle.kts
├── docs/
│   ├── product/            # PRD, PROJECT_PLAN, BACKLOG
│   └── technical/          # SCRIPT_FORMAT, AI_AGENT, SKILLS, CI_CD …
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

### B. 參考資源

- [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [GestureDescription](https://developer.android.com/reference/android/accessibilityservice/GestureDescription)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Room](https://developer.android.com/training/data-storage/room)

---

*本規劃書為活文件，隨專案推進更新。*

---
<a id="english-version"></a>

# OpenRing Project Plan

> Version: v0.4 | Updated: 2026-03-22  
> **Architecture: Pure Mobile Client, No Web Console, No OpenRing-Hosted Backend**

---

## 1. Project Overview

### 1.1 Product Positioning

| Item | Description |
|------|-------------|
| **Product Name** | OpenRing |
| **Product Type** | Open-source cross-App automation Agent for Android |
| **Core Value** | Replaces "absolute coordinates" with "semantic UI nodes" to achieve portable and maintainable RPA workflows |
| **Architecture Principle** | **Pure Mobile Client** — All functions are completed on the phone, no PC or backend server required |
| **Target Platform** | Android 8.0+ (API 26+) |

### 1.2 Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Script Portability | The same script executes normally on 2+ different device models | Actual test coverage |
| Task Success Rate | ≥ 95% | Successful scheduled tasks / Total triggers |
| Offline Availability | Can still edit and schedule without network (AI chat and skill data retrieval require network) | Feature verification |
| Community Adoption | GitHub Stars / Active contributors | Open-source metrics |

---

## 2. Project Scope & Boundaries

### 2.1 In Scope (MVP)

- Android AccessibilityService core: View Tree parsing, gesture simulation
- Chat-Driven OS: Main control chat room, Working Bubble status display
- ReAct Loop Coordinator: sense/think/tool/act/finalize closed loop
- Gemini Function Calling dispatcher: Structured backfilling of tool results; large UI trees may be compacted via `UiTreeCompact`; `summarize_view_tree` returns a compact summary
- Optional on-device GGUF (`LocalLlmEngine` + catalog downloads): text-only chat with streaming (no built-in tool loop in the local path)
- QuickJS Skill Plugin Engine: Single Skill installation and execution
- BYOK: Gemini API Key management, sensitive data masking
- Human takeover: Red vibration prompt and single tap hint when nodes cannot be found continuously
- **In-App Script Editor**: Create and edit workflows on the phone
- **In-App Script Storage**: Local Room / DataStore
- **In-App Scheduling**: WorkManager or AlarmManager timer triggers
- Sensitive node filtering, execution status overlay window
- Script export/import (JSON files, optional)

### 2.2 Out of Scope (MVP)

- Web Console
- Backend APIs, WebSocket
- Remote webhook triggers
- Multi-device management
- iOS support
- OpenRing-hosted cloud inference or managed models (BYOK calls to Google APIs are not “hosted backend” in this sense)

---

## 3. System Architecture (Pure Mobile)

```
┌─────────────────────────────────────────────────────────────────┐
│                     OpenRing Android App                         │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                      UI Layer (Activity / Fragment)          │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐ │ │
│  │  │ Script List   │  │ Script Editor │  │ Schedule / History   │ │ │
│  │  └──────────────┘  └──────────────┘  └────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                              │                                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                      Business Logic Layer                    │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐ │ │
│  │  │ ScriptStore   │  │ Scheduler     │  │ ScriptExecutor     │ │ │
│  │  │ (Local Data)  │  │ (WorkManager) │  │ (Execution Engine)  │ │ │
│  │  └──────────────┘  └──────────────┘  └────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                              │                                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                      Core Layer (AccessibilityService)       │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐ │ │
│  │  │ ViewTreeParser│  │ ActionExecutor│  │ IntentRouter        │ │ │
│  │  └──────────────┘  └──────────────┘  └────────────────────┘ │ │
│  │  ┌──────────────────────────────────────────────────────────┐│ │
│  │  │ SensitiveFilter │ OverlayService (Floating Window)       ││ │
│  │  └──────────────────────────────────────────────────────────┘│ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Module Breakdown & Specifications

### 4.1 Core Layer (AccessibilityService)

| Module | Description | Input | Output | Dependencies |
|--------|-------------|-------|--------|--------------|
| **ViewTreeParser** | Parses the current screen into structured JSON | AccessibilityEvent | JSON (Node Tree) | AccessibilityService |
| **ActionExecutor** | Executes click, swipe, long press, back, home | Action command + Node ID | Execution result | AccessibilityService, GestureDescription |
| **IntentRouter** | Wakes up Apps, Deep Link routing | Package/URI | Routing result | Android Intent |
| **SensitiveFilter** | Filters password and key input fields | Raw node tree | Masked node tree | None |
| **OverlayService** | Displays parrot icon during execution | Execution status | Overlay UI | WindowManager |

### 4.2 Business Logic Layer

| Module | Description | Input | Output | Dependencies |
|--------|-------------|-------|--------|--------------|
| **ScriptStore** | Script CRUD, local storage | Script JSON | Save/Read results | Room / DataStore |
| **Scheduler** | Triggers scripts on a timer | Script ID + Cron expression | Trigger at time | WorkManager |
| **ScriptExecutor** | Executes steps sequentially, calls ActionExecutor | Script steps | Execution result, variables | Core Layer |

### 4.3 UI Layer

| Screen | Description | Components |
|--------|-------------|------------|
| **Script List** | Shows all scripts, add, delete, execute buttons | RecyclerView / LazyColumn |
| **Script Editor** | Block-based step editing, parameter setup, save | List + Form |
| **Schedule Settings**| Sets timer (daily/hourly/custom), enable/disable| Form |
| **Execution History**| Recent execution logs, success/failure, errors | List |
| **Chat** | Chat-driven tasks, model chain, local streaming / Gemini ReAct | Compose |
| **Settings** | API keys, model chain, on-device model downloads, scan/morality, etc. | Compose |
| **Skills** | ZIP/URL install, enable skills, URL allowlists | Compose |

### 4.4 Agent & On-Device Model Layer

| Module | Description | Key paths |
|--------|-------------|-----------|
| **ReActCoordinator** | Multi-turn Gemini, tool calls, optional UI compaction | `agent/ReActCoordinator.kt` |
| **ToolDispatcher / ToolSchemas** | Tool registration and execution (incl. `summarize_view_tree`, `describe_screen`) | `agent/ToolDispatcher.kt`, `ToolSchemas.kt` |
| **UiTreeCompact** | Fingerprints and clickable summaries to shrink payloads | `agent/UiTreeCompact.kt` |
| **LocalLlmEngine** | GGUF load and streaming / non-streaming inference | `localmodel/LocalLlmEngine.kt` |
| **LocalModelCatalog** | Public GGUF download URLs and filenames | `localmodel/LocalModelCatalog.kt` |

---

## 5. Data Model

### 5.1 Script

```json
{
  "id": "uuid",
  "name": "ADA Yield Patrol",
  "version": 1,
  "steps": [
    {
      "type": "launch_app",
      "params": { "package": "com.example.wallet" }
    },
    {
      "type": "wait",
      "params": { "ms": 2000 }
    },
    {
      "type": "find_and_click",
      "params": { "text": "ADA", "match": "contains" }
    },
    {
      "type": "extract_text",
      "params": { "nodeId": "price_label", "variable": "adaPrice" }
    }
  ],
  "schedule": {
    "enabled": true,
    "type": "daily",
    "hour": 9,
    "minute": 0
  }
}
```

### 5.2 Node Tree (View Tree JSON)

```json
{
  "root": {
    "id": "node_0",
    "className": "android.widget.TextView",
    "text": "ADA",
    "contentDesc": null,
    "clickable": true,
    "bounds": { "left": 100, "top": 200, "right": 200, "bottom": 250 },
    "children": []
  }
}
```

### 5.3 Action Types

| type | params | Description |
|------|--------|-------------|
| `launch_app` | `package`, `uri?` | Wake up App, optional Deep Link |
| `wait` | `ms` | Wait in milliseconds |
| `find_and_click` | `text`, `match`, `nodeId?` | Click based on text or nodeId |
| `click_node` | `nodeId` | Click based on nodeId |
| `swipe` | `direction`, `distance?` | Swipe |
| `long_press` | `nodeId?`, `text?` | Long press |
| `back` | - | Back button |
| `home` | - | Home button |
| `extract_text` | `nodeId`, `variable` | Extract text and save to variable |

---

## 6. Milestones & Timeline

### Phase 0: Project Initialization (~3 Days)

| Task | Output |
|------|--------|
| Android project setup | Kotlin + Gradle, minSdk 26 |
| Module structure, dependencies | Clear layering |
| Script format documentation | `docs/SCRIPT_FORMAT.md` |

### Phase 1: Core Layer (2–3 Weeks)

| Week | Milestone | Deliverables |
|------|-----------|--------------|
| W1 | AccessibilityService skeleton + ViewTreeParser | Obtains semantic View Tree JSON (with sensitive filter) |
| W2 | ActionExecutor + Gesture tools + OverlayService | Executable tool set: click/swipe/back/home, etc. |
| W2 | Chat UI (MVP) + Working Bubble | Initiate tasks via chat, view status during execution |
| W3 | ReAct Loop Coordinator + Gemini Function Calling | tool calls can be executed by dispatcher and backfill results |
| W4 | QuickJS Runtime + Single Skill installation/execution + Human takeover | Can `call_skill` and handle `NODE_NOT_FOUND` hint flow |

### Phase 2: Extension Layer (1–2 Weeks)

| Task | Output |
|------|--------|
| Working Bubble UX polish | Clearer status text/error prompts, acceptable UX |
| Skill Marketplace basic portal | Zip import/skill list/permission request (minimal version) |
| ScriptStore / ScriptExecutor / Scheduler (keep current) | Retain existing local scripting capabilities as a tool backend or alternative flow |

### Phase 3: Advanced Layer (1–2 Weeks)

| Week | Milestone | Deliverables |
|------|-----------|--------------|
| W1 | Human takeover enhancement (fault tolerance and threshold adjustment) | Increased success rate of breaking through blind spots |
| W2 | VLM screenshot fallback (Phase 3) | Switch to visual positioning strategy upon continuous failure |
| W3 | Startup/stability optimization | More complete failure retry strategies and error tracking |

### Phase 4: Integration & Release (~1 Week)

| Task | Output |
|------|--------|
| End-to-end testing | Complete flows pass |
| Documentation and README | Installation and usage guides |
| v0.1.0 Release | GitHub Release, APK |

---

## 7. Risks & Mitigation

| Risk | Impact | Probability | Mitigation Strategy |
|------|--------|-------------|---------------------|
| Some Apps restrict AccessibilityService | High | Medium | Document supported scope; provide debug mode |
| Manufacturer ROM differences cause gesture failures | High | Medium | Multi-device testing; fallback strategies |
| Poor experience editing scripts on mobile | Medium | Medium | Simplify steps, common defaults, JSON imports |
| Background execution killed by the system | Medium | Medium | WorkManager, battery optimization whitelist instructions |

---

## 8. Dependencies & Prerequisites

### 8.1 Technical Dependencies

- **Android**: Kotlin 2.3.10, minSdk 26, targetSdk 34, compileSdk 36, Build-Tools 36.0.0
- **Storage**: Room or DataStore
- **Scheduling**: WorkManager
- **UI**: Jetpack Compose or XML (choose one)

### 8.2 External Dependencies

- **Optional**: Google Gemini API (user-supplied key, BYOK) for cloud chat, vision (`describe_screen`), and full tool loop.
- **Optional**: Hugging Face–hosted GGUF URLs listed in `LocalModelCatalog` for on-device download (HTTPS).
- Core automation does not require any paid OpenRing service.

---

## 9. Definition of Done

- [ ] Code passes formatting (lint gate is temporarily disabled due to tooling stability issues; to be re-enabled)
- [ ] Critical paths have unit tests (target state; current baseline is build + manual validation)
- [ ] Sensitive node filtering passes manual verification
- [ ] Obvious status indicators during execution
- [ ] README includes installation and basic usage instructions
- [ ] No known P0/P1 bugs

### 9.1 Documentation Sync Policy

- When build versions change (Kotlin / SDK / build tools), update `README.md`, `README.zh-TW.md`, and `docs/technical/CI_CD.md` together.
- When CI quality gates change (e.g., lint/test enabled or disabled), update `docs/technical/CI_CD.md` and this DoD section in the same change.
- When product capability changes (agent/tool/permission flows), update `docs/technical/AI_AGENT.md` and `docs/technical/SCRIPT_FORMAT.md` together.

---

## 10. Appendix

### A. Directory Structure

```
OpenRing/
├── app/                    # Main Android App module
│   ├── src/main/
│   │   ├── java/.../       # Kotlin source code
│   │   │   ├── core/       # AccessibilityService, Parser, Executor, ScreenCapture, playback audio / MediaProjection
│   │   │   ├── agent/      # ReAct, ToolDispatcher, ToolSchemas
│   │   │   ├── localmodel/ # GGUF catalog, LocalLlmEngine
│   │   │   ├── data/       # ScriptStore, Room, ChatRepository
│   │   │   ├── domain/     # ScriptExecutor, Scheduler
│   │   │   └── ui/         # Jetpack Compose (incl. Settings → Permission settings, Chat)
│   │   └── res/
│   └── build.gradle.kts
├── docs/
│   ├── product/            # PRD, PROJECT_PLAN, BACKLOG
│   └── technical/          # SCRIPT_FORMAT, AI_AGENT, SKILLS, CI_CD, …
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

### B. References

- [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [GestureDescription](https://developer.android.com/reference/android/accessibilityservice/GestureDescription)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Room](https://developer.android.com/training/data-storage/room)

---

*This plan is a living document and will be updated as the project progresses.*