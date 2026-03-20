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
