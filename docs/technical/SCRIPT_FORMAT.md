[English Version Below](#english-version)

# OpenRing 腳本格式定義

> 純手機端架構 — 腳本儲存於本地，格式供編輯器與執行引擎使用

---

## 1. 腳本結構 (Script)

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

---

## 2. 動作類型 (Action Types)

| type | params | 說明 |
|------|--------|------|
| `launch_app` | `package`, `uri?` | 喚醒 App，可選 Deep Link |
| `wait` | `ms` | 等待毫秒 |
| `find_and_click` | `text`, `match`, `nodeId?` | 依文字或 nodeId 點擊 |
| `click_node` | `nodeId` | 依 nodeId 點擊 |
| `swipe` | `direction`, `distance?` | 滑動，direction: up/down/left/right |
| `long_press` | `nodeId?`, `text?` | 長按 |
| `back` | - | 返回鍵 |
| `home` | - | Home 鍵 |
| `extract_text` | `nodeId`, `variable` | 提取文字存入變數 |

---

## 3. 排程格式 (Schedule)

| type | 額外 params | 說明 |
|------|-------------|------|
| `disabled` | - | 不排程 |
| `daily` | `hour`, `minute` | 每日固定時間 |
| `hourly` | `minute` | 每小時固定分 |
| `interval` | `minutes` | 每 N 分鐘 |

---

## 4. 節點樹 (View Tree) — 執行時內部使用

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

---

## 5. 錯誤碼

| code | 說明 |
|------|------|
| `NODE_NOT_FOUND` | 找不到指定節點 |
| `ACTION_FAILED` | 動作執行失敗 |
| `APP_NOT_INSTALLED` | 目標 App 未安裝 |
| `PERMISSION_DENIED` | 權限不足 |
| `TIMEOUT` | 操作逾時 |
| `UNKNOWN` | 未知錯誤 |

---

## 延伸閱讀

- 執行期**權限**與對話 **Agent 工具**（與腳本引擎分開）：[README.md](../../README.md)、[AI_AGENT.md](AI_AGENT.md)。

---

<a id="english-version"></a>

# OpenRing Script Format Definition

> Pure mobile architecture — Scripts are stored locally, and the format is for use by the editor and execution engine.

---

## 1. Script Structure

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

---

## 2. Action Types

| type | params | Description |
|------|--------|-------------|
| `launch_app` | `package`, `uri?` | Launch an App, with optional Deep Link |
| `wait` | `ms` | Wait for milliseconds |
| `find_and_click` | `text`, `match`, `nodeId?` | Click based on text or nodeId |
| `click_node` | `nodeId` | Click based on nodeId |
| `swipe` | `direction`, `distance?` | Swipe, direction: up/down/left/right |
| `long_press` | `nodeId?`, `text?` | Long press |
| `back` | - | Back button |
| `home` | - | Home button |
| `extract_text` | `nodeId`, `variable` | Extract text and save it to a variable |

---

## 3. Schedule Format

| type | Additional params | Description |
|------|-------------------|-------------|
| `disabled` | - | Do not schedule |
| `daily` | `hour`, `minute` | Fixed daily time |
| `hourly` | `minute` | Fixed minute every hour |
| `interval` | `minutes` | Every N minutes |

---

## 4. View Tree — For internal execution use

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

---

## 5. Error Codes

| code | Description |
|------|-------------|
| `NODE_NOT_FOUND` | Specified node not found |
| `ACTION_FAILED` | Action execution failed |
| `APP_NOT_INSTALLED` | Target App is not installed |
| `PERMISSION_DENIED` | Insufficient permissions |
| `TIMEOUT` | Operation timed out |
| `UNKNOWN` | Unknown error |

---

## See also

- Runtime **permissions** and chat **agent tools** (separate from script engine): [README.md](../../README.md), [AI_AGENT.md](AI_AGENT.md).
