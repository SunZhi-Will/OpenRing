# OpenRing 專案規劃書

> 版本：v0.3 | 更新日期：2026-03-19  
> **架構：純手機端，無 Web 控制台、無後端**

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
- Gemini Function Calling dispatcher：tool 結果結構化回填
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
- LLM 輔助節點選擇

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

- **Android**：Kotlin 1.9+, minSdk 26, targetSdk 34
- **儲存**：Room 或 DataStore
- **排程**：WorkManager
- **UI**：Jetpack Compose 或 XML（擇一）

### 8.2 外部依賴

- 無需網路（純本地）
- 無需第三方付費服務

---

## 9. 交付清單 (Definition of Done)

- [ ] 程式碼通過 Lint / 格式化
- [ ] 關鍵路徑有單元測試
- [ ] 敏感節點過濾通過人工驗證
- [ ] 執行時有明顯狀態指示
- [ ] README 含安裝與基本使用說明
- [ ] 無已知 P0/P1 Bug

---

## 10. 附錄

### A. 目錄結構

```
OpenRing/
├── app/                    # Android App 主模組
│   ├── src/main/
│   │   ├── java/.../       # Kotlin 原始碼
│   │   │   ├── core/       # AccessibilityService, Parser, Executor
│   │   │   ├── data/       # ScriptStore, Room
│   │   │   ├── domain/     # ScriptExecutor, Scheduler
│   │   │   └── ui/         # Activity, Fragment, ViewModel
│   │   └── res/
│   └── build.gradle.kts
├── docs/
│   ├── PROJECT_PLAN.md
│   ├── PRODUCT_BACKLOG.md
│   └── SCRIPT_FORMAT.md
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
