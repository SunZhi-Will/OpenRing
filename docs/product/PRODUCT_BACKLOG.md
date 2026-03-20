[English Version Below](#english-version)

# OpenRing 產品待辦清單 (Product Backlog)

> 純手機端架構 | 依優先級排序

---

## Epic 1：核心能力（AccessibilityService）

### US-1.1 啟用無障礙服務
**作為** 使用者  
**我想要** 在設定中啟用 OpenRing 無障礙服務  
**以便** 讓 OpenRing 能讀取畫面與執行手勢  

**驗收條件：**
- [ ] 安裝 APK 後可於系統設定中找到 OpenRing
- [ ] 啟用後可取得當前畫面的基本節點資訊
- [ ] 首次啟用有引導說明

**優先級：** P0 | **預估：** 2 SP

---

### US-1.2 解析畫面為結構化 JSON
**作為** 系統  
**我想要** 將 AccessibilityNodeInfo 轉換為可序列化的 JSON  
**以便** 腳本執行時能依節點定位並操作  

**驗收條件：**
- [ ] 輸出包含 className、text、contentDesc、clickable、bounds
- [ ] 支援巢狀 children 結構
- [ ] password 類型節點回傳空值

**優先級：** P0 | **預估：** 5 SP

---

### US-1.3 執行點擊動作
**作為** 腳本執行引擎  
**我想要** 對指定節點執行點擊  
**以便** 模擬使用者點擊按鈕或連結  

**驗收條件：**
- [ ] 可依 nodeId 或 text 定位節點
- [ ] 點擊成功回傳 result: ok
- [ ] 節點不存在時回傳明確錯誤

**優先級：** P0 | **預估：** 3 SP

---

### US-1.4 執行滑動動作
**作為** 腳本執行引擎  
**我想要** 執行滑動指令（上/下/左/右）  
**以便** 模擬捲動或切換頁面  

**驗收條件：**
- [ ] 支援方向與距離參數
- [ ] 滑動流暢、無明顯卡頓

**優先級：** P0 | **預估：** 3 SP

---

### US-1.5 執行返回與 Home
**作為** 腳本執行引擎  
**我想要** 執行全域返回鍵與 Home 鍵  
**以便** 快速退回上一頁或桌面  

**驗收條件：**
- [ ] 返回鍵可退回上一 Activity
- [ ] Home 鍵可回到桌面

**優先級：** P0 | **預估：** 2 SP

---

### US-1.6 喚醒目標 App
**作為** 腳本執行引擎  
**我想要** 透過 Package Name 或 Deep Link 喚醒指定 App  
**以便** 腳本可直接跳轉到目標頁面  

**驗收條件：**
- [ ] 支援 package 喚醒
- [ ] 支援 custom scheme Deep Link
- [ ] App 未安裝時回傳錯誤

**優先級：** P1 | **預估：** 3 SP

---

### US-1.7 執行時顯示狀態指示
**作為** 使用者  
**我想要** 在腳本執行時看到明顯的懸浮圖示  
**以便** 清楚知道目前是 OpenRing 在控制手機  

**驗收條件：**
- [ ] 執行中顯示鸚鵡圖示
- [ ] 可點擊收起/展開
- [ ] 不阻擋主要操作區域

**優先級：** P0 | **預估：** 2 SP

---

### US-1.8 敏感節點過濾
**作為** 系統  
**我想要** 對 password 與金鑰輸入框一律回傳空值  
**以便** 不將敏感資訊外洩  

**驗收條件：**
- [ ] password 類型節點 text 為空
- [ ] 已知金鑰輸入框（如助記詞）過濾
- [ ] 有單元測試覆蓋

**優先級：** P0 | **預估：** 2 SP

---

## Epic 2：腳本儲存與執行（App 內）

### US-2.1 本地儲存腳本
**作為** 使用者  
**我想要** 在手機上儲存我的腳本  
**以便** 可重複使用與排程執行  

**驗收條件：**
- [ ] 支援腳本 CRUD（新增、讀取、更新、刪除）
- [ ] 腳本含 name、steps、schedule
- [ ] 可列出所有腳本

**優先級：** P0 | **預估：** 5 SP

---

### US-2.2 腳本執行引擎
**作為** 系統  
**我想要** 依 steps 依序執行動作  
**以便** 完成自動化工作流  

**驗收條件：**
- [ ] 支援 launch_app、wait、find_and_click、click_node、swipe、back、home、extract_text
- [ ] 單步失敗時可選中止或繼續
- [ ] 執行結果可寫入執行歷史

**優先級：** P0 | **預估：** 5 SP

---

### US-2.3 定時觸發腳本
**作為** 使用者  
**我想要** 設定定時讓腳本自動執行  
**以便** 無需手動觸發即可自動巡邏  

**驗收條件：**
- [ ] 支援每日、每小時、自訂時間
- [ ] 使用 WorkManager 或 AlarmManager 觸發
- [ ] 可啟用/停用排程

**優先級：** P0 | **預估：** 5 SP

---

### US-2.4 手動立即執行
**作為** 使用者  
**我想要** 在腳本列表點擊「執行」按鈕  
**以便** 立即跑一次腳本  

**驗收條件：**
- [ ] 腳本列表每筆有執行按鈕
- [ ] 點擊後開始執行，顯示懸浮窗
- [ ] 執行完成有提示

**優先級：** P0 | **預估：** 2 SP

---

## Epic 3：App 內 UI

### US-3.1 腳本列表畫面
**作為** 使用者  
**我想要** 看到所有已儲存的腳本  
**以便** 管理與執行  

**驗收條件：**
- [ ] 列表顯示腳本名稱、排程狀態
- [ ] 可新增、刪除腳本
- [ ] 點擊進入編輯

**優先級：** P0 | **預估：** 3 SP

---

### US-3.2 腳本編輯器（App 內）
**作為** 使用者  
**我想要** 在手機上用區塊方式編輯工作流  
**以便** 不需電腦即可建立自動化腳本  

**驗收條件：**
- [ ] 可新增/刪除/排序步驟
- [ ] 支援 launch_app、wait、find_and_click、extract_text、swipe、back、home
- [ ] 可設定每步參數（文字、package、毫秒等）
- [ ] 儲存後可執行

**優先級：** P0 | **預估：** 8 SP

---

### US-3.3 排程設定畫面
**作為** 使用者  
**我想要** 在腳本編輯頁設定定時  
**以便** 腳本能自動執行  

**驗收條件：**
- [ ] 可選每日、每小時、自訂
- [ ] 顯示下次執行時間（若已排程）
- [ ] 開關啟用/停用

**優先級：** P0 | **預估：** 3 SP

---

### US-3.4 執行歷史
**作為** 使用者  
**我想要** 看到近期腳本執行結果  
**以便** 除錯與確認是否成功  

**驗收條件：**
- [ ] 列表顯示時間、腳本名、狀態（成功/失敗）
- [ ] 失敗時可展開看錯誤詳情

**優先級：** P1 | **預估：** 4 SP

---

### US-3.5 腳本匯出/匯入（JSON）
**作為** 使用者  
**我想要** 匯出腳本為 JSON 檔案，或從檔案匯入  
**以便** 備份或在不同裝置間搬移  

**驗收條件：**
- [ ] 可匯出單一腳本或全部
- [ ] 可從檔案選擇器匯入 JSON
- [ ] 格式與 SCRIPT_FORMAT.md 一致

**優先級：** P2 | **預估：** 3 SP

---

## Epic 4：Chat-Driven OS 與 ReAct 循環（Gemini）

### US-4.1 主控對話室（Chat-Driven OS）
**作為** 使用者  
**我想要** 在手機上進行自然語言對話並發起任務  
**以便** 讓 OpenRing 依指令執行跨 App 動作（MVP 以可驗收閉環為目標）  

**驗收條件：**
- [ ] Chat UI 可輸入文字並顯示模型回覆
- [ ] 執行期間能顯示 Working Bubble（見 US-4.2）
- [ ] 任務可在失敗時顯示可追蹤錯誤訊息（見 US-4.5）

**優先級：** P0 | **預估：** 3 SP

---

### US-4.2 Working Bubble 狀態顯示（內心獨白/狀態）
**作為** 使用者  
**我想要** 在自動化執行時看到半透明懸浮窗與 AI 狀態文字  
**以便** 掌控正在發生的步驟（例如：正在呼叫計算技能）  

**驗收條件：**
- [ ] 執行中顯示 Working Bubble（半透明）
- [ ] 能顯示來自 Brain 的狀態事件（status / step）
- [ ] 任務結束後可自動收起或淡出

**優先級：** P0 | **預估：** 2 SP

---

### US-4.3 Human takeover（紅色震動 + 一次 tap hint）
**作為** 使用者  
**我想要** 在 Gemini 連續找不到目標節點時，能被提醒並提供一次手動協助  
**以便** 突破無法解析的盲區並讓流程繼續  

**驗收條件：**
- [ ] 當 tool 連續回傳 `NODE_NOT_FOUND` 達閾值（例如 N=3），bubble 變紅且震動
- [ ] 顯示提示「請點一次以提供 hint」
- [ ] 使用者完成一次 tap 後，系統將 tap 對應的語意節點/文本回饋給 ReAct loop
- [ ] 使用者 hint 必須被記錄到執行歷史（含時間、任務 id）

**優先級：** P0 | **預估：** 5 SP

---

### US-4.4 ReAct Loop Coordinator（閉環跑通）
**作為** 系統  
**我想要** 在一次任務流程中執行 `sense -> think/tool -> act -> finish` 閉環  
**以便** 讓 Gemini 把“意圖”轉換為可執行動作集合  

**驗收條件：**
- [ ] 可輸入目標指令並取得 view tree（工具）
- [ ] 支援工具回合（tool results）直到任務完成或達到最大回合數
- [ ] 執行過程能回報每回合的狀態到 Working Bubble
- [ ] 任務完成後，能生成一份可展示給使用者的摘要回覆

**優先級：** P0 | **預估：** 8 SP

---

### US-4.5 Gemini Function Calling Dispatcher（工具集合與結果回填）
**作為** 系統  
**我想要** 定義 Function Calling 工具集合，並把 Gemini 的 tool calls 映射到本地手勢/資料工具  
**以便** 把 AI 指令轉成可驗收的 device actions  

**驗收條件：**
- [ ] 至少支援以下 tool：`get_view_tree`、`find_and_click`、`click_node`、`swipe`、`back`、`home`、`extract_text`
- [ ] tool result 回傳包含明確錯誤碼（例如 `NODE_NOT_FOUND`、`ACTION_FAILED`）
- [ ] dispatcher 可將工具結果以結構化 JSON 回填給 Gemini
- [ ] 支援 `call_skill` 工具（見 Epic 5）

**優先級：** P0 | **預估：** 8 SP

---

### US-4.6 BYOK（Gemini API Key 管理）與敏感資料保護
**作為** 使用者  
**我想要** 在 App 內填入 Gemini API Key，並確保敏感欄位不會被上傳模型  
**以便** 讓使用者掌控金鑰與隱私安全  

**驗收條件：**
- [ ] Key 管理頁面可新增/修改/刪除 API key
- [ ] API key 以安全方式儲存（例如 Android Keystore/Encrypted storage；實作細節由工程決定）
- [ ] 任何 `password` 類型節點內容在 view tree 中需被遮蔽成空值/`***`（與 US-1.8 一致）
- [ ] 執行 request 組裝時不包含任何敏感欄位（包含：password 內容、API key）

**優先級：** P0 | **預估：** 5 SP

---

## Epic 5：Skill Plugin Engine（QuickJS）

### US-5.1 QuickJS Runtime（初始化與執行）
**作為** 系統  
**我想要** 在本地初始化 QuickJS 引擎並能執行 JS 技能程式  
**以便** 讓 Gemini 透過 `call_skill` 取得計算/資料結果  

**驗收條件：**
- [ ] QuickJS runtime 可初始化且可重複執行
- [ ] 能限制執行時間（timeout）並在逾時回傳可追蹤錯誤
- [ ] JS 執行結果可序列化回 host（JSON-friendly）

**優先級：** P0 | **預估：** 8 SP

---

### US-5.2 Skill manifest 規範與 schema 驗證
**作為** 技能開發者  
**我想要** 用一致的 `manifest.json` 描述技能輸入輸出與權限  
**以便** Gemini 與 host 能正確理解並安全執行  

**驗收條件：**
- [ ] 支援讀取 `manifest.json`（包含 name/description/inputSchema/outputSchema/permissions）
- [ ] host 可驗證 schema（至少：必填欄位、型別）
- [ ] manifest 欄位缺失時回傳明確錯誤（skill_invalid_manifest）

**優先級：** P0 | **預估：** 5 SP

---

### US-5.3 Skill call 介面（輸入/輸出橋接）
**作為** 系統  
**我想要** 將 Gemini 傳入的 tool arguments 轉成 JS `run(input)` 的輸入  
**以便** 把 return value 回填給 Gemini 作為後續推理依據  

**驗收條件：**
- [ ] JS 技能以標準入口導出（例如 `export function run(input)` 或固定函數名）
- [ ] host 將 return value 轉為 JSON 並回傳給 Gemini
- [ ] JS error / throw 能映射為結構化錯誤（skill_runtime_error）

**優先級：** P0 | **預估：** 5 SP

---

### US-5.4 單一 Skill 安裝（MVP：zip 匯入）
**作為** 使用者  
**我想要** 在不重新編譯 APK 的情況下，安裝一個 Skill 包並立即可用  
**以便** 快速驗證插件能力  

**驗收條件：**
- [ ] 可從檔案選擇器匯入 `.zip`（MVP）
- [ ] zip 內包含 `manifest.json` 與 `script.js`（或約定路徑）
- [ ] 安裝完成後在技能清單中可見並可啟用/停用

**優先級：** P0 | **預估：** 5 SP

---

### US-5.5 Skill sandbox 權限控管（network/storage）
**作為** 使用者  
**我想要** 在執行第三方 Skill 前先確認權限  
**以便** 防止惡意腳本竊取本地資料或濫用網路  

**驗收條件：**
- [ ] manifest 的 `permissions` 能在 UI 顯示並要求授權
- [ ] 未授權時，技能執行失敗且回傳安全錯誤
- [ ] 授權狀態可被保存並可在後續管理頁面修改

**優先級：** P0 | **預估：** 5 SP

---

## Epic 6：Skill Marketplace 與 VLM 進階（後續階段）

### US-6.1 GitHub 技能匯入（Marketplace 基礎入口）
**作為** 使用者  
**我想要** 透過 GitHub 連結匯入/更新 Skill  
**以便** 讓社群能力能快速擴散  

**驗收條件：**
- [ ] 可貼上 GitHub URL（或 repo/zip release URL）並下載匯入
- [ ] 更新同名技能時可提示版本差異（MVP 可簡化）

**優先級：** P1 | **預估：** 5 SP

---

### US-6.2 Permission 管理與技能清單（Marketplace 管理）
**作為** 使用者  
**我想要** 管理已安裝技能的權限與狀態  
**以便** 控制插件帶來的風險  

**驗收條件：**
- [ ] 技能清單顯示：名稱、描述、版本（若可得）、已授權權限
- [ ] 支援單技能啟用/停用

**優先級：** P1 | **預估：** 3 SP

---

### US-6.3 VLM screenshot fallback（Phase 3）
**作為** 系統  
**我想要** 在特定 App 無法解析 DOM tree 時，改用截圖 + 視覺模型定位  
**以便** 提升覆蓋範圍與容錯能力  

**驗收條件：**
- [ ] 觸發條件：連續 N 次 tool 操作失敗且 view tree 可用節點極低
- [ ] 對應模式：每秒截圖（或可配置頻率）並把必要資訊送 Gemini/VLM（由後續決定）

**優先級：** P2 | **預估：** 8 SP

---

## 優先級說明

- **P0**：MVP 必須，無則產品不可用
- **P1**：MVP 重要，可延後一版
- **P2**：Nice to have

## 預估說明

- **SP**：Story Point，1 SP ≈ 0.5–1 人天（依團隊速度調整）

---
<a id="english-version"></a>

# OpenRing Product Backlog

> Mobile-Only Architecture | Ordered by Priority

---

## Epic 1: Core Capabilities (AccessibilityService)

### US-1.1 Enable Accessibility Service
**As a** user  
**I want to** enable the OpenRing accessibility service in settings  
**so that** OpenRing can read the screen and execute gestures  

**Acceptance Criteria:**
- [ ] After installing the APK, OpenRing can be found in the system settings
- [ ] After enabling, basic node information of the current screen can be obtained
- [ ] There is a guided tutorial on first enable

**Priority:** P0 | **Estimate:** 2 SP

---

### US-1.2 Parse Screen into Structured JSON
**As the** system  
**I want to** convert AccessibilityNodeInfo into serializable JSON  
**so that** scripts can locate and interact with nodes during execution  

**Acceptance Criteria:**
- [ ] Output includes className, text, contentDesc, clickable, bounds
- [ ] Supports nested children structure
- [ ] Password type nodes return empty values

**Priority:** P0 | **Estimate:** 5 SP

---

### US-1.3 Execute Click Action
**As the** script execution engine  
**I want to** execute a click on a specified node  
**so that** user clicks on buttons or links are simulated  

**Acceptance Criteria:**
- [ ] Nodes can be located by nodeId or text
- [ ] Successful clicks return result: ok
- [ ] Returns explicit error when the node does not exist

**Priority:** P0 | **Estimate:** 3 SP

---

### US-1.4 Execute Swipe Action
**As the** script execution engine  
**I want to** execute swipe commands (up/down/left/right)  
**so that** scrolling or switching pages is simulated  

**Acceptance Criteria:**
- [ ] Supports direction and distance parameters
- [ ] Swipe is smooth without obvious stutter

**Priority:** P0 | **Estimate:** 3 SP

---

### US-1.5 Execute Back and Home
**As the** script execution engine  
**I want to** execute global Back and Home keys  
**so that** it quickly navigates back to the previous page or home screen  

**Acceptance Criteria:**
- [ ] Back key navigates to previous Activity
- [ ] Home key navigates to home screen

**Priority:** P0 | **Estimate:** 2 SP

---

### US-1.6 Launch Target App
**As the** script execution engine  
**I want to** launch a specified App via Package Name or Deep Link  
**so that** the script can directly jump to the target page  

**Acceptance Criteria:**
- [ ] Supports package launch
- [ ] Supports custom scheme Deep Link
- [ ] Returns error when App is not installed

**Priority:** P1 | **Estimate:** 3 SP

---

### US-1.7 Display Status Indicator During Execution
**As a** user  
**I want to** see a visible floating icon during script execution  
**so that** it is clear OpenRing is currently controlling the phone  

**Acceptance Criteria:**
- [ ] Displays parrot icon during execution
- [ ] Clickable to collapse/expand
- [ ] Does not block main interaction areas

**Priority:** P0 | **Estimate:** 2 SP

---

### US-1.8 Sensitive Node Filtering
**As the** system  
**I want to** consistently return empty values for password and key input fields  
**so that** sensitive information is not leaked  

**Acceptance Criteria:**
- [ ] Password type nodes have empty text
- [ ] Filters known key input fields (like seed phrases)
- [ ] Covered by unit tests

**Priority:** P0 | **Estimate:** 2 SP

---

## Epic 2: Script Storage and Execution (In-App)

### US-2.1 Local Script Storage
**As a** user  
**I want to** store my scripts on the phone  
**so that** they can be reused and scheduled for execution  

**Acceptance Criteria:**
- [ ] Supports script CRUD (Create, Read, Update, Delete)
- [ ] Scripts contain name, steps, schedule
- [ ] Can list all scripts

**Priority:** P0 | **Estimate:** 5 SP

---

### US-2.2 Script Execution Engine
**As the** system  
**I want to** execute actions sequentially according to steps  
**so that** automated workflows are completed  

**Acceptance Criteria:**
- [ ] Supports launch_app, wait, find_and_click, click_node, swipe, back, home, extract_text
- [ ] Can choose to abort or continue upon single step failure
- [ ] Execution results can be written to execution history

**Priority:** P0 | **Estimate:** 5 SP

---

### US-2.3 Scheduled Script Trigger
**As a** user  
**I want to** set schedules to run scripts automatically  
**so that** automated patrols can run without manual triggers  

**Acceptance Criteria:**
- [ ] Supports daily, hourly, custom times
- [ ] Triggered using WorkManager or AlarmManager
- [ ] Can enable/disable schedules

**Priority:** P0 | **Estimate:** 5 SP

---

### US-2.4 Manual Immediate Execution
**As a** user  
**I want to** click the "Execute" button in the script list  
**so that** the script runs once immediately  

**Acceptance Criteria:**
- [ ] Each item in the script list has an execute button
- [ ] Clicking starts execution and shows the floating window
- [ ] Prompt shown upon execution completion

**Priority:** P0 | **Estimate:** 2 SP

---

## Epic 3: In-App UI

### US-3.1 Script List Screen
**As a** user  
**I want to** see all saved scripts  
**so that** they can be managed and executed  

**Acceptance Criteria:**
- [ ] List shows script names, schedule status
- [ ] Can add, delete scripts
- [ ] Click to enter editing

**Priority:** P0 | **Estimate:** 3 SP

---

### US-3.2 Script Editor (In-App)
**As a** user  
**I want to** edit workflows using blocks on the phone  
**so that** automated scripts can be created without a computer  

**Acceptance Criteria:**
- [ ] Can add/delete/reorder steps
- [ ] Supports launch_app, wait, find_and_click, extract_text, swipe, back, home
- [ ] Can set parameters for each step (text, package, milliseconds, etc.)
- [ ] Can execute after saving

**Priority:** P0 | **Estimate:** 8 SP

---

### US-3.3 Schedule Settings Screen
**As a** user  
**I want to** set a schedule in the script edit page  
**so that** the script can execute automatically  

**Acceptance Criteria:**
- [ ] Can choose daily, hourly, custom
- [ ] Displays next execution time (if scheduled)
- [ ] Toggle to enable/disable

**Priority:** P0 | **Estimate:** 3 SP

---

### US-3.4 Execution History
**As a** user  
**I want to** see recent script execution results  
**so that** I can debug and confirm success  

**Acceptance Criteria:**
- [ ] List shows time, script name, status (success/failure)
- [ ] Can expand to view error details on failure

**Priority:** P1 | **Estimate:** 4 SP

---

### US-3.5 Script Export/Import (JSON)
**As a** user  
**I want to** export scripts as JSON files, or import from files  
**so that** they can be backed up or moved between different devices  

**Acceptance Criteria:**
- [ ] Can export single script or all
- [ ] Can import JSON from file picker
- [ ] Format matches SCRIPT_FORMAT.md

**Priority:** P2 | **Estimate:** 3 SP

---

## Epic 4: Chat-Driven OS and ReAct Loop (Gemini)

### US-4.1 Main Chat Room (Chat-Driven OS)
**As a** user  
**I want to** have natural language conversations on the phone and initiate tasks  
**so that** OpenRing executes cross-App actions according to commands (MVP targets a verifiable closed loop)  

**Acceptance Criteria:**
- [ ] Chat UI allows text input and displays model responses
- [ ] Shows Working Bubble during execution (see US-4.2)
- [ ] Tasks can show traceable error messages on failure (see US-4.5)

**Priority:** P0 | **Estimate:** 3 SP

---

### US-4.2 Working Bubble Status Display (Inner Monologue/Status)
**As a** user  
**I want to** see a semi-transparent floating window with AI status text during automated execution  
**so that** I have control over the ongoing steps (e.g., calling a computation skill)  

**Acceptance Criteria:**
- [ ] Displays Working Bubble (semi-transparent) during execution
- [ ] Can display status events (status / step) from the Brain
- [ ] Automatically collapses or fades out after task completion

**Priority:** P0 | **Estimate:** 2 SP

---

### US-4.3 Human Takeover (Red Vibration + Single Tap Hint)
**As a** user  
**I want to** be reminded and provide manual assistance via a single tap when Gemini continuously fails to find the target node  
**so that** unparseable blind spots are bypassed and the process continues  

**Acceptance Criteria:**
- [ ] When the tool continuously returns `NODE_NOT_FOUND` reaching a threshold (e.g. N=3), bubble turns red and vibrates
- [ ] Displays prompt "Please tap once to provide a hint"
- [ ] After the user completes a tap, the system feeds back the semantic node/text corresponding to the tap to the ReAct loop
- [ ] User hint must be recorded in execution history (including time, task id)

**Priority:** P0 | **Estimate:** 5 SP

---

### US-4.4 ReAct Loop Coordinator (Closed Loop Execution)
**As the** system  
**I want to** execute a `sense -> think/tool -> act -> finish` closed loop in a task flow  
**so that** Gemini converts "intent" into an executable action set  

**Acceptance Criteria:**
- [ ] Can input target commands and get the view tree (tool)
- [ ] Supports tool rounds (tool results) until task completion or maximum rounds are reached
- [ ] Execution process can report the status of each round to the Working Bubble
- [ ] After task completion, a summary response viewable by the user is generated

**Priority:** P0 | **Estimate:** 8 SP

---

### US-4.5 Gemini Function Calling Dispatcher (Tool Set and Result Feedback)
**As the** system  
**I want to** define a Function Calling tool set, and map Gemini's tool calls to local gesture/data tools  
**so that** AI commands are converted into verifiable device actions  

**Acceptance Criteria:**
- [ ] Supports at least the following tools: `get_view_tree`, `find_and_click`, `click_node`, `swipe`, `back`, `home`, `extract_text`
- [ ] Tool result returns include explicit error codes (e.g., `NODE_NOT_FOUND`, `ACTION_FAILED`)
- [ ] Dispatcher can feed back tool results to Gemini in structured JSON
- [ ] Supports `call_skill` tool (see Epic 5)

**Priority:** P0 | **Estimate:** 8 SP

---

### US-4.6 BYOK (Gemini API Key Management) and Sensitive Data Protection
**As a** user  
**I want to** input Gemini API Key in the App, and ensure sensitive fields are not uploaded to the model  
**so that** the user controls key and privacy security  

**Acceptance Criteria:**
- [ ] Key management page can add/modify/delete API key
- [ ] API key is stored securely (e.g., Android Keystore/Encrypted storage; implementation details determined by engineering)
- [ ] Any `password` type node content in the view tree must be masked as empty value/`***` (consistent with US-1.8)
- [ ] Request assembly during execution does not contain any sensitive fields (including: password content, API key)

**Priority:** P0 | **Estimate:** 5 SP

---

## Epic 5: Skill Plugin Engine (QuickJS)

### US-5.1 QuickJS Runtime (Initialization and Execution)
**As the** system  
**I want to** initialize QuickJS engine locally and be able to execute JS skill programs  
**so that** Gemini obtains computation/data results via `call_skill`  

**Acceptance Criteria:**
- [ ] QuickJS runtime can be initialized and executed repeatedly
- [ ] Can limit execution time (timeout) and return traceable errors on timeout
- [ ] JS execution results can be serialized back to the host (JSON-friendly)

**Priority:** P0 | **Estimate:** 8 SP

---

### US-5.2 Skill Manifest Specification and Schema Validation
**As a** skill developer  
**I want to** use a consistent `manifest.json` to describe skill inputs, outputs, and permissions  
**so that** Gemini and host can understand correctly and execute securely  

**Acceptance Criteria:**
- [ ] Supports reading `manifest.json` (including name/description/inputSchema/outputSchema/permissions)
- [ ] Host can validate schema (at least: required fields, types)
- [ ] Returns explicit error when manifest fields are missing (skill_invalid_manifest)

**Priority:** P0 | **Estimate:** 5 SP

---

### US-5.3 Skill Call Interface (Input/Output Bridge)
**As the** system  
**I want to** convert tool arguments passed by Gemini into input for JS `run(input)`  
**so that** the return value is fed back to Gemini as basis for subsequent reasoning  

**Acceptance Criteria:**
- [ ] JS skills are exported with standard entry points (e.g., `export function run(input)` or fixed function name)
- [ ] Host converts return value to JSON and feeds back to Gemini
- [ ] JS error / throw can be mapped to structured error (skill_runtime_error)

**Priority:** P0 | **Estimate:** 5 SP

---

### US-5.4 Single Skill Installation (MVP: zip import)
**As a** user  
**I want to** install a Skill package and use it immediately without recompiling the APK  
**so that** plugin capabilities can be quickly validated  

**Acceptance Criteria:**
- [ ] Can import `.zip` from file picker (MVP)
- [ ] Zip contains `manifest.json` and `script.js` (or convention path)
- [ ] Visible in skill list after installation and can be enabled/disabled

**Priority:** P0 | **Estimate:** 5 SP

---

### US-5.5 Skill Sandbox Permission Control (network/storage)
**As a** user  
**I want to** confirm permissions before executing third-party Skills  
**so that** malicious scripts are prevented from stealing local data or abusing the network  

**Acceptance Criteria:**
- [ ] `permissions` in manifest can be displayed in UI and require authorization
- [ ] Without authorization, skill execution fails and returns security error
- [ ] Authorization status can be saved and modified in subsequent management pages

**Priority:** P0 | **Estimate:** 5 SP

---

## Epic 6: Skill Marketplace and VLM Advancement (Subsequent Phases)

### US-6.1 GitHub Skill Import (Marketplace Basic Entry)
**As a** user  
**I want to** import/update Skills via GitHub links  
**so that** community capabilities can quickly spread  

**Acceptance Criteria:**
- [ ] Can paste GitHub URL (or repo/zip release URL) to download and import
- [ ] Prompts version difference when updating skill of the same name (MVP can simplify)

**Priority:** P1 | **Estimate:** 5 SP

---

### US-6.2 Permission Management and Skill List (Marketplace Management)
**As a** user  
**I want to** manage permissions and statuses of installed skills  
**so that** risks brought by plugins are controlled  

**Acceptance Criteria:**
- [ ] Skill list shows: name, description, version (if available), authorized permissions
- [ ] Supports single skill enable/disable

**Priority:** P1 | **Estimate:** 3 SP

---

### US-6.3 VLM Screenshot Fallback (Phase 3)
**As the** system  
**I want to** switch to screenshot + vision model localization when specific Apps cannot parse the DOM tree  
**so that** coverage and fault tolerance are improved  

**Acceptance Criteria:**
- [ ] Trigger condition: N continuous tool operation failures and very low available nodes in view tree
- [ ] Corresponding mode: Take screenshot every second (or configurable frequency) and send necessary info to Gemini/VLM (decided later)

**Priority:** P2 | **Estimate:** 8 SP

---

## Priority Description

- **P0**: MVP required, product is unusable without it
- **P1**: MVP important, can be delayed to next version
- **P2**: Nice to have

## Estimate Description

- **SP**: Story Point, 1 SP ≈ 0.5–1 man-day (adjustable based on team velocity)