[English Version Below](#english-version)

# OpenRing WebSocket 通訊協定

> **已棄用** — 純手機端架構下不再使用。保留供未來「電腦遠端控制」擴充參考。  
> 腳本格式請參考 [SCRIPT_FORMAT.md](SCRIPT_FORMAT.md)。

---

## 1. 連線建立

### 1.1 手機端 → 後端

**連線 URL：** `ws://{api_host}/ws?deviceId={id}&token={optional}`

**首次註冊訊息：**

```json
{
  "type": "register",
  "payload": {
    "deviceId": "android-xxx",
    "deviceName": "Pixel 7",
    "androidVersion": 34,
    "appVersion": "0.1.0"
  }
}
```

### 1.2 後端 → 手機端

**註冊確認：**

```json
{
  "type": "registered",
  "payload": {
    "sessionId": "uuid"
  }
}
```

---

## 2. 心跳保活

| 方向 | 類型 | 說明 |
|------|------|------|
| 雙向 | `ping` | 發送方請求 |
| 雙向 | `pong` | 接收方回應 |

**建議間隔：** 30 秒

---

## 3. 腳本執行

### 3.1 後端 → 手機端：下發執行指令

```json
{
  "type": "execute",
  "payload": {
    "scriptId": "uuid",
    "requestId": "uuid",
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
      }
    ]
  }
}
```

### 3.2 手機端 → 後端：回報執行結果

**單步成功：**

```json
{
  "type": "step_result",
  "payload": {
    "requestId": "uuid",
    "stepIndex": 0,
    "status": "ok",
    "data": null
  }
}
```

**單步失敗：**

```json
{
  "type": "step_result",
  "payload": {
    "requestId": "uuid",
    "stepIndex": 1,
    "status": "error",
    "error": "Node not found: text=ADA"
  }
}
```

**腳本完成：**

```json
{
  "type": "script_complete",
  "payload": {
    "requestId": "uuid",
    "status": "success",
    "variables": {
      "adaPrice": "0.52"
    }
  }
}
```

---

## 4. 動作類型 (Action Types)

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
| `get_view_tree` | - | 取得當前畫面樹（除錯用） |

---

## 5. 除錯與監控

### 5.1 取得畫面樹 (手機端 → 後端)

```json
{
  "type": "view_tree",
  "payload": {
    "tree": { "root": { ... } },
    "timestamp": 1710000000000
  }
}
```

### 5.2 日誌推送 (手機端 → 後端)

```json
{
  "type": "log",
  "payload": {
    "level": "info",
    "message": "Clicked node: ADA",
    "timestamp": 1710000000000
  }
}
```

---

## 6. 錯誤碼

| code | 說明 |
|------|------|
| `NODE_NOT_FOUND` | 找不到指定節點 |
| `ACTION_FAILED` | 動作執行失敗 |
| `APP_NOT_INSTALLED` | 目標 App 未安裝 |
| `PERMISSION_DENIED` | 權限不足 |
| `TIMEOUT` | 操作逾時 |
| `UNKNOWN` | 未知錯誤 |

---

## 7. 版本相容

- 協定版本於連線時透過 `X-Protocol-Version: 1` 傳遞
- 主版本不相容時，後端回傳 `upgrade_required` 並關閉連線

---
<a id="english-version"></a>

# OpenRing WebSocket Protocol

> **DEPRECATED** — No longer used in the pure mobile-side architecture. Kept for future reference for "PC remote control" expansion.  
> For script format, please refer to [SCRIPT_FORMAT.md](SCRIPT_FORMAT.md).

---

## 1. Connection Establishment

### 1.1 Mobile Client → Backend

**Connection URL:** `ws://{api_host}/ws?deviceId={id}&token={optional}`

**Initial Registration Message:**

```json
{
  "type": "register",
  "payload": {
    "deviceId": "android-xxx",
    "deviceName": "Pixel 7",
    "androidVersion": 34,
    "appVersion": "0.1.0"
  }
}
```

### 1.2 Backend → Mobile Client

**Registration Confirmation:**

```json
{
  "type": "registered",
  "payload": {
    "sessionId": "uuid"
  }
}
```

---

## 2. Heartbeat Keep-Alive

| Direction | Type | Description |
|-----------|------|-------------|
| Bidirectional | `ping` | Sender Request |
| Bidirectional | `pong` | Receiver Response |

**Recommended Interval:** 30 seconds

---

## 3. Script Execution

### 3.1 Backend → Mobile Client: Dispatch Execution Command

```json
{
  "type": "execute",
  "payload": {
    "scriptId": "uuid",
    "requestId": "uuid",
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
      }
    ]
  }
}
```

### 3.2 Mobile Client → Backend: Report Execution Result

**Single Step Success:**

```json
{
  "type": "step_result",
  "payload": {
    "requestId": "uuid",
    "stepIndex": 0,
    "status": "ok",
    "data": null
  }
}
```

**Single Step Failure:**

```json
{
  "type": "step_result",
  "payload": {
    "requestId": "uuid",
    "stepIndex": 1,
    "status": "error",
    "error": "Node not found: text=ADA"
  }
}
```

**Script Complete:**

```json
{
  "type": "script_complete",
  "payload": {
    "requestId": "uuid",
    "status": "success",
    "variables": {
      "adaPrice": "0.52"
    }
  }
}
```

---

## 4. Action Types

| type | params | Description |
|------|--------|-------------|
| `launch_app` | `package`, `uri?` | Launch App, optional Deep Link |
| `wait` | `ms` | Wait in milliseconds |
| `find_and_click` | `text`, `match`, `nodeId?` | Click by text or nodeId |
| `click_node` | `nodeId` | Click by nodeId |
| `swipe` | `direction`, `distance?` | Swipe, direction: up/down/left/right |
| `long_press` | `nodeId?`, `text?` | Long press |
| `back` | - | Back button |
| `home` | - | Home button |
| `extract_text` | `nodeId`, `variable` | Extract text and store in variable |
| `get_view_tree` | - | Get current view tree (for debugging) |

---

## 5. Debugging and Monitoring

### 5.1 Get View Tree (Mobile Client → Backend)

```json
{
  "type": "view_tree",
  "payload": {
    "tree": { "root": { ... } },
    "timestamp": 1710000000000
  }
}
```

### 5.2 Log Push (Mobile Client → Backend)

```json
{
  "type": "log",
  "payload": {
    "level": "info",
    "message": "Clicked node: ADA",
    "timestamp": 1710000000000
  }
}
```

---

## 6. Error Codes

| code | Description |
|------|-------------|
| `NODE_NOT_FOUND` | Specified node not found |
| `ACTION_FAILED` | Action execution failed |
| `APP_NOT_INSTALLED` | Target App is not installed |
| `PERMISSION_DENIED` | Insufficient permissions |
| `TIMEOUT` | Operation timed out |
| `UNKNOWN` | Unknown error |

---

## 7. Version Compatibility

- Protocol version is passed via `X-Protocol-Version: 1` upon connection
- When the major version is incompatible, backend returns `upgrade_required` and closes the connection
