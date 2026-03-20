# OpenRing 產品需求文件（PRD）

版本：v0.3（文件草案精煉版）
更新日期：2026-03-19

> 說明：本 PRD 以「純手機端 Android App」為前提；Gemini 僅作為雲端大語言模型（device -> Google API），“無 Web 控制台、無自建後端”。

---

## 1. 產品概述與核心願景

### 1.1 產品名稱
OpenRing

### 1.2 核心願景
將科幻級 AI 助理實作於 Android 終端：OpenRing 捨棄傳統僵化腳本（RPA），改採「Chat-Driven」自然語言介面，並以雲端大語言模型（Gemini）作為大腦，具備「讀取畫面、模擬手勢、自主思考」的閉環能力（ReAct loop）。

進一步導入「動態技能擴充生態系」：類似電腦版軟體生態（如“龍蝦”那種強插件擴充精神），任何人都能用 JS 開發 Skill Plugins，並在手機端一鍵安裝、即時執行。

### 1.3 核心定位
基於 Android `AccessibilityService`、Gemini Function Calling 與輕量 JS 沙盒引擎（QuickJS）的「無腳本、高擴充、BYOK 自動化外掛平台」。

---

## 2. 目標用戶與痛點分析

### 2.1 目標用戶
1. 極客玩家
2. Web3 / 加密貨幣投資者
3. 需要跨 App 自動化獲取資訊的工作者
4. 開源社群開發者（希望能快速貢獻插件）

### 2.2 痛點
1. 反爬蟲高牆：傳統爬蟲難以突破 Threads、各大交易所 App 的沙盒/反爬機制。
2. 腳本易碎：按鍵精靈依賴絕對座標，UI 一改版即失效；也難以處理“需要邏輯思考”的突發狀況。
3. 擴充門檻高：一般手機自動化 App 難以像電腦軟體那樣，讓社群能隨時用 Python/JS 擴充能力。

---

## 3. OpenRing Architecture（The OpenRing Architecture）

系統由四大支柱構成，形成「思考與行動」閉環：

### 3.1 The Body（感知與執行）
- `AccessibilityService` 攔截當前畫面結構
- 解析出語意化節點 JSON（semantic UI nodes）
- 根據 AI 指令執行手勢與系統級操作（click / swipe / back / home / app jump）

### 3.2 The Brain（決策中樞）
- 接收目標任務與畫面資料
- 路徑規劃、容錯判斷、技能調度（skill dispatch）
- 透過 Function Calling 將“工具呼叫”具體化為可執行動作

### 3.3 The OS（對話介面與狀態展示）
- 主控對話室（Chat-Driven OS）
- 懸浮窗 Working Bubble：顯示 AI 執行狀態（含“內心獨白/狀態文字”）
- 人類接管機制：當模型連續判定無法找到目標節點，懸浮窗轉紅色並震動，提示使用者手動點擊一次以提供 hint

### 3.4 The Skills（動態技能引擎）
- Android 內建 QuickJS 引擎
- Gemini 透過 `call_skill` 指令觸發本地 JS 沙盒執行
- 將 return value 回填給 Gemini，形成“資料取得/計算/轉換”的能力擴充

---

## 4. MVP 核心功能模組規格（MVP）

> MVP 以「可用閉環」為交付標準：可在聊天中理解意圖 -> 讀取畫面 -> 透過工具呼叫執行手勢 -> 必要時呼叫 Skill -> 回覆結果並完成任務。

### 4.1 Chat-Driven OS（對話驅動介面）
1. 主控對話室：文字輸入（MVP 可先不強制語音）
2. Working Bubble：
   - 執行階段顯示透明懸浮窗
   - 顯示 AI 狀態文字（例如：`正在開啟交易所`、`正在呼叫計算技能`）
3. Human takeover：
   - 當連續 N 次嘗試仍找不到節點（或 tool result 顯示 `NODE_NOT_FOUND`），bubble 顯示紅色並震動
   - 使用者完成一次手動點擊（tap）以提供 hint
   - hint 被轉成語意節點或 nodeId/文本線索，回饋給模型繼續

### 4.2 UI Parsing 與 Gesture Engine（語意化節點擷取）
1. `AccessibilityNodeInfo` -> semantic node JSON
2. 節點包含（至少）：
   - `id`（節點識別）
   - `text` / `contentDesc`（可視文字與描述）
   - `bounds`（僅用於定位輔助；Gemini 不依賴絕對座標）
   - `clickable` / `className` 等必要元資訊
3. 手勢執行：
   - click / long_press
   - swipe（up/down/left/right + distance）
   - back / home
   - app jump（package + optional deep link）

### 4.3 Skill Plugin Engine（QuickJS 沙盒與 Function Bridging）
MVP 功能：
1. `manifest.json` 定義技能名稱、描述、參數介面（OpenAPI-like，偏輕量）
2. `script.js` 在 QuickJS 中執行
3. Gemini 發出 `call_skill`：
   - OpenRing 在本地沙盒執行對應技能
   - 將 return value 透過 Function result 回填 Gemini

Skill 標準格式（建議）：
```json
{
  "name": "crypto_price_fetcher",
  "description": "Fetch realtime price and minimal chain status",
  "inputSchema": {
    "type": "object",
    "properties": {
      "symbols": { "type": "array", "items": { "type": "string" } }
    },
    "required": ["symbols"]
  },
  "outputSchema": {
    "type": "object",
    "properties": {
      "prices": { "type": "object" }
    }
  },
  "permissions": {
    "network": { "required": true }
  }
}
```

`script.js`（概念）：
```javascript
// Exports a single function used by the host.
export function run(input) {
  // ... fetch/compute ...
  return { prices: { BTC: 63000 } };
}
```

### 4.4 BYOK 與 Security（BYOK + 隱私安全）
1. BYOK 金鑰管理：
   - 使用者在 App 內填入 Gemini API Key（device to Google）
2. 密碼框遮蔽：
   - 底層攔截器識別 `password` 類型節點輸入內容，回傳替換為 `***`
   - 禁止把任何密碼/金鑰等敏感欄位送進模型
3. 沙盒權限控管：
   - 安裝第三方 Skill 時，使用者需勾選授權（例如：允許此技能發送網路請求）
   - 未授權時，Skill 執行應失敗並回傳安全錯誤（由 host 決定訊息內容）

### 4.5 Gemini Tool Interface（Function Calling）
> 技術欄位與工具名稱保持英文，以確保可被工程實作與除錯使用。

**Tool list（minimum）**
- `get_view_tree`: returns the latest semantic UI node tree (password fields are masked)
- `find_and_click`: find a node by `text` + `match` and perform a click
- `click_node`: click by `nodeId`
- `swipe`: perform swipe gesture
- `back`: global back action
- `home`: global home action
- `extract_text`: extract text by `nodeId` into a structured result
- `call_skill`: execute a locally installed Skill Plugin (QuickJS)

**Common tool result shape（minimum）**
```json
{
  "ok": true,
  "code": null,
  "message": null,
  "data": {}
}
```

**Common error codes**
- `NODE_NOT_FOUND`
- `ACTION_FAILED`
- `PERMISSION_DENIED`
- `TIMEOUT`

#### 4.5.1 Tool I/O examples（MVP 可直接照此實作）

**Tool: `get_view_tree`**

Input:
```json
{}
```

Output（data 節錄）:
```json
{
  "ok": true,
  "code": null,
  "message": null,
  "data": {
    "timestampMs": 1710000000000,
    "root": {
      "id": "node_0",
      "className": "android.widget.FrameLayout",
      "text": null,
      "contentDesc": null,
      "clickable": false,
      "bounds": { "left": 0, "top": 0, "right": 1080, "bottom": 2400 },
      "children": [
        {
          "id": "node_12",
          "className": "android.widget.TextView",
          "text": "登入",
          "contentDesc": null,
          "clickable": true,
          "bounds": { "left": 120, "top": 1800, "right": 960, "bottom": 1950 },
          "children": []
        }
      ]
    }
  }
}
```

敏感資訊遮蔽規則（minimum）：
- 若 node 為 password input（或 host 判定為敏感輸入框），`text` 必須回傳 `***` 或 `null`（兩者擇一，但需一致）。

---

**Tool: `find_and_click`**

Input:
```json
{ "text": "登入", "match": "exact" }
```

Output（成功）:
```json
{
  "ok": true,
  "code": null,
  "message": null,
  "data": { "clickedNodeId": "node_12" }
}
```

Output（失敗：找不到）:
```json
{
  "ok": false,
  "code": "NODE_NOT_FOUND",
  "message": "Node not found: text=登入 match=exact",
  "data": { "query": { "text": "登入", "match": "exact" } }
}
```

---

**Tool: `click_node`**

Input:
```json
{ "nodeId": "node_12" }
```

Output:
```json
{
  "ok": true,
  "code": null,
  "message": null,
  "data": { "clickedNodeId": "node_12" }
}
```

---

**Tool: `swipe`**

Input:
```json
{ "direction": "up", "distance": 600 }
```

Output:
```json
{
  "ok": true,
  "code": null,
  "message": null,
  "data": { "direction": "up", "distance": 600 }
}
```

---

**Tool: `back` / `home`**

Input:
```json
{}
```

Output:
```json
{ "ok": true, "code": null, "message": null, "data": {} }
```

---

**Tool: `extract_text`**

Input:
```json
{ "nodeId": "node_99" }
```

Output:
```json
{
  "ok": true,
  "code": null,
  "message": null,
  "data": { "nodeId": "node_99", "text": "BTC 63,000" }
}
```

---

**Tool: `call_skill`**

Input:
```json
{
  "skill": "crypto_price_fetcher",
  "input": { "symbols": ["BTC", "ETH", "ADA"] }
}
```

Output（成功）:
```json
{
  "ok": true,
  "code": null,
  "message": null,
  "data": {
    "skill": "crypto_price_fetcher",
    "output": {
      "prices": { "BTC": 63000, "ETH": 3400, "ADA": 0.52 }
    }
  }
}
```

Output（失敗：未授權 network）:
```json
{
  "ok": false,
  "code": "PERMISSION_DENIED",
  "message": "Skill permission denied: network",
  "data": { "skill": "crypto_price_fetcher", "permission": "network" }
}
```

#### 4.5.2 Retry / guardrails（避免無限循環）
MVP 建議：
- `NODE_NOT_FOUND`：最多重試 3 次（可搭配 `get_view_tree` refresh 與一次 `swipe` 探索）。
- 若同一目標連續 3 次 `NODE_NOT_FOUND`，觸發 Human takeover（見 4.1/4.6）。
- 任務最大 tool rounds：例如 12 回合；超過則回覆使用者「需要接管或改寫目標」。

### 4.6 Human takeover：tap hint data contract（MVP）
目標：把「使用者的一次 tap」轉成模型可理解的線索，協助下一輪定位。

**Host event（from device to Brain）**
```json
{
  "type": "human_hint",
  "timestampMs": 1710000000000,
  "tap": { "x": 540, "y": 1880 },
  "context": {
    "appPackage": "com.example.app",
    "screen": { "width": 1080, "height": 2400 }
  },
  "candidates": [
    {
      "nodeId": "node_12",
      "text": "登入",
      "contentDesc": null,
      "className": "android.widget.TextView",
      "clickable": true,
      "bounds": { "left": 120, "top": 1800, "right": 960, "bottom": 1950 },
      "distancePx": 40
    }
  ]
}
```

**候選節點挑選規則（minimum）**
- 以 tap 點為中心，找出 bounds 包含 tap 的 node；若沒有，取最近的 clickable node（例如距離前 3 名）。
- `candidates` 至少提供 1 個；最多 5 個，避免 token 浪費。
- `text` 若為敏感遮蔽欄位，保持遮蔽（`***`/`null`）。

**Brain 行為（minimum）**
- 下一回合優先使用 `click_node(nodeId)`（若可用），比 `find_and_click` 更穩。
- 若 `click_node` 仍失敗（例如 node stale），Brain 需先 `get_view_tree` refresh 再決策。

---

## 5. User Flow（典型實戰）

1. 使用者在聊天室輸入任務（例如：根據投資組合與風險偏好，產出全天候動態配置建議）
2. Gemini 判斷需要即時資料：
   - 觸發 `call_skill(crypto_price_fetcher)`
3. OpenRing 啟動 JS 引擎執行技能：
   - 取得價格與必要狀態
   - 回填給 Gemini
4. Gemini 深度分析並產出報告
5. OpenRing 透過 UI 工具完成跨 App 輸出（若 MVP 後續納入：以既有 gesture/action set 實作 Intent/跳轉）
6. 任務完成後：
   - bubble 顯示結束狀態
   - 回到主對話等待下一指令

---

## 6. Roadmap（迭代藍圖）

### Phase 1（MVP）
- 核心 ReAct loop 跑通（sense -> think/tool -> act -> finish）
- 完成 Accessibility 節點 JSON 化 + sensitive filter
- 實作基本 QuickJS runtime + 單一 Skill 安裝與執行

### Phase 2（擴充）
- 完善 Working Bubble（UX polish + 狀態清晰化）
- 建立 Skill marketplace 的基本入口（安裝、更新、權限）

### Phase 3（進階）
- 導入 VLM（視覺語言模型）支援：
  - 遇到無法解析 DOM tree 的特殊 App 或遊戲
  - 改為每秒截圖送 Gemini 做純視覺定位

---

## 7. Non-Functional Requirements

1. Security：
   - password / key-like input 必須被攔截與遮蔽
   - Skill sandbox 必須有明確 permission gating
2. Reliability：
   - tool execution 必須有可追蹤錯誤（例如 `NODE_NOT_FOUND`, `ACTION_FAILED`）
3. Extensibility：
   - Function Calling 工具集合可擴充（新增 skill/tool 不破壞舊版本）

