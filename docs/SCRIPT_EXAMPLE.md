# OpenRing 腳本範例與步驟說明

---

## 步驟類型說明

| 類型 | 用途 | 常用參數 |
|------|------|----------|
| **launch_app** | 開啟指定 App | `package`: App 的套件名（如 `com.instagram.barcelona`） |
| **wait** | 等待畫面載入 | `ms`: 毫秒數（如 `2000` = 2 秒） |
| **find_and_click** | 依畫面上的「文字」找到並點擊 | `text`: 要找的文字、`match`: `exact` 完全符合 或 `contains` 包含 |
| **click_node** | 依節點 ID 點擊（進階） | `nodeId`: 節點 ID（需先知道） |
| **swipe** | 滑動畫面 | `direction`: `up`/`down`/`left`/`right`、`distance`: 滑動距離 |
| **long_press** | 長按 | `text`: 要長按的元素文字 |
| **back** | 按返回鍵 | 無參數 |
| **home** | 按 Home 鍵回桌面 | 無參數 |
| **extract_text** | 從畫面上擷取文字（進階） | `nodeId`: 節點 ID、`variable`: 存到變數名 |

---

## 如何查 App 的 Package 名稱？

1. 開啟目標 App
2. 在電腦執行：`adb shell dumpsys window \| grep mCurrentFocus`
3. 輸出中 `com.xxx.xxx` 即為 package 名稱

常見 App：
- **Threads**: `com.instagram.barcelona`
- **Instagram**: `com.instagram.android`
- **LINE**: `jp.naver.line.android`
- **Chrome**: `com.android.chrome`

---

## 範例：開啟 Threads 並抓取貼文數

假設流程：開啟 Threads → 等待載入 → 點擊個人檔案 → 抓取追蹤數

```
步驟 1: launch_app
  package: com.instagram.barcelona
  uri: (留空)

步驟 2: wait
  ms: 3000
  (等待 App 載入約 3 秒)

步驟 3: find_and_click
  text: 個人檔案  (或畫面上實際顯示的按鈕文字)
  match: contains

步驟 4: wait
  ms: 2000

步驟 5: find_and_click
  text: 追蹤者  (或 "followers")
  match: contains
  (點擊後可進入追蹤者列表，或擷取數字)
```

**注意**：`extract_text` 需要知道 `nodeId`，目前需透過除錯取得。實務上可先用 `find_and_click` 完成大部分操作。

---

## 範例：開啟 Threads 並滑動瀏覽

```
步驟 1: launch_app
  package: com.instagram.barcelona

步驟 2: wait
  ms: 3000

步驟 3: swipe
  direction: up
  distance: 500
  (向上滑動，類似刷動態)

步驟 4: wait
  ms: 2000

步驟 5: swipe
  direction: up
  distance: 500
  (再滑一次)
```

---

## 撰寫技巧

1. **每個操作後加 wait**：畫面切換需要時間，建議 1–3 秒
2. **文字要精準**：`find_and_click` 的 `text` 要與畫面上顯示的完全一致（或使用 `contains` 部分符合）
3. **先手動操作一遍**：記下要點的文字、順序，再轉成步驟
4. **從簡單開始**：先試 `launch_app` + `wait` + `find_and_click` 能否成功
