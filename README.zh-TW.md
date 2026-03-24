<div align="center">
  <img src="docs/assets/openring-logo.png" alt="OpenRing 標誌" width="128" height="128">
  
  <h1>OpenRing</h1>
  <p><b>基於 Android AccessibilityService 的 RPA 與對話驅動 AI Agent（Gemini／可選本機 GGUF）</b></p>
  <p><i>純手機端執行，無需電腦後台、無需 Root；雲端 LLM 採 BYOK 可選</i></p>

  [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
  [![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
  [![Language](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
  [![Build](https://img.shields.io/badge/Build-Gradle-blueviolet.svg)](https://gradle.org/)
  [![Android CI](https://github.com/SunZhi-Will/OpenRing/actions/workflows/android-ci.yml/badge.svg)](https://github.com/SunZhi-Will/OpenRing/actions/workflows/android-ci.yml)
  [![CodeQL](https://github.com/SunZhi-Will/OpenRing/actions/workflows/codeql.yml/badge.svg)](https://github.com/SunZhi-Will/OpenRing/actions/workflows/codeql.yml)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)
  
  <br/>
  
  [**繁體中文**](README.zh-TW.md) | [**English**](README.md)
</div>

<br/>

## 📖 專案簡介

OpenRing 是基於 **無障礙服務** 的 **Android 自動化 Agent**：可解析語意化 UI 樹、執行 **腳本與排程**，並透過 **Chat‑Driven OS** 以 **Gemini 函式呼叫（ReAct）** 或 **本機 GGUF** 模型推理，完成讀畫面、呼叫工具、輸入與點擊等閉環。

**核心仍在手機端** — 腳本、排程、無障礙、QuickJS 技能沙盒與可選的 **本機大模型推論** 皆在裝置上；若設定 **Gemini API Key（BYOK）**，即可使用雲端推理、**螢幕視覺描述**（`describe_screen`）與完整工具迴圈；未設定時仍可透過 **已下載的 GGUF** 做純文字對話並支援 **串流輸出**。

---

## ✨ 核心特色

- **🚫 免 Root 權限**：基於 Android 官方的 `AccessibilityService` 開發，無需破解手機。
- **📱 以手機為中心**：不需 ADB；**無 OpenRing 自建後台** — 腳本、資料與技能預設留在本機，除非您自行呼叫雲端 API（例如 Gemini）。
- **🤖 對話驅動 Agent**：**ReAct** 與 **Gemini 工具** — `get_view_tree`、**`summarize_view_tree`**（精簡 UI）、**`describe_screen`**（樹不可靠時的視覺後援）、**`describe_ambient_audio`**（聽覺：優先 **手機內部播放音訊** 擷取＋MediaProjection；可退回麥克風）、點擊輸入、記憶、技能等。
- **🦙 可選本機 LLM**：內建 **GGUF 型錄**（`LocalModelCatalog`）與 App 內下載；聊天支援 **串流**；依模型族系套用對話模板（Qwen／Phi／Gemma／TinyLlama 等，`LocalLlmChatPrompt`）。
- **👁️ 語意化 UI 解析**：View Tree → JSON 供腳本與自動化使用；送進雲端模型前可 **壓縮** 大型樹（`UiTreeCompact`）。
- **🖱️ 模擬人類操作**：點擊、滑動、長按、返回、Home、喚醒 App。
- **⏰ 本地排程**：WorkManager 定時觸發腳本。
- **🧩 技能外掛（QuickJS）**：`call_skill` 於沙盒執行 JS（見 `docs/skill-templates/`）。
- **🛠️ 腳本與工作流**：App 內編輯 JSON 工作流並執行。

---

## 📈 專案熱度

<a href="https://www.star-history.com/?repos=SunZhi-Will%2FOpenRing&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=SunZhi-Will/OpenRing&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=SunZhi-Will/OpenRing&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/image?repos=SunZhi-Will/OpenRing&type=date&legend=top-left" />
 </picture>
</a>

---

## 🏗 架構與技術

*技術細節以英文記錄以供開發者參考。*

OpenRing 完全使用 **Kotlin** 構建，並利用現代 Android 開發實踐，包含用於 UI 的 **Jetpack Compose** 以及用於非同步程式設計的 **Coroutines/Flow**。

### 核心組件

| 模組                      | 說明                                                                              |
| ------------------------- | --------------------------------------------------------------------------------- |
| **View Tree Parser**      | 使用 `AccessibilityService` 遍歷螢幕的 UI 節點樹並將其轉換為結構化的 JSON。       |
| **Action Executor**       | 使用 Accessibility API 安全地分派標準手勢 (點擊、滑動、全域動作)。                |
| **Intent Router**         | 使用 Android Intents、Deep Links 或套件名稱喚醒或導航至目標應用程式。             |
| **Script Engine**         | 解析並執行預定義的 JSON/DSL 腳本，整合邏輯、變數和條件。                          |
| **Scheduler**             | 基於 Android `WorkManager` 構建，用於可靠地在背景執行定期或延遲任務。             |
| **Agent（ReAct + 工具）** | `ReActCoordinator` + `ToolDispatcher` — Gemini 函式呼叫、工具結果、可選 UI 壓縮。 |
| **本機 LLM**              | `LocalLlmEngine` — 透過 `llama-kotlin-android` 載入 GGUF 並推論，聊天支援串流。   |

### 專案結構

```text
OpenRing/
├── app/                  # 主要的 Android 應用程式模組
│   └── src/main/
│       ├── core/         # AccessibilityService, Parser, Executor, IntentRouter, 螢幕截圖、播放音訊擷取／MediaProjection
│       ├── agent/        # ReActCoordinator, ToolSchemas, ToolDispatcher, UiTreeCompact
│       ├── localmodel/   # GGUF 型錄、下載器、LocalLlmEngine、對話模板
│       ├── gemini/       # Gemini REST 與模型定義
│       ├── data/         # Room、ChatRepository、MemoryRepository、ScriptStore
│       ├── domain/       # ScriptExecutor、Scheduler
│       ├── skills/       # QuickJS 技能安裝與執行
│       └── ui/           # Jetpack Compose（聊天、設定、技能、腳本…）
├── docs/                 # 文件
│   ├── product/          # PRD, Backlog, 專案規劃
│   └── technical/        # 腳本格式、CI/CD、AI Agent、技能
└── gradle/               # 建置設定
```

如需更詳細的技術文件，請參閱 `docs/` 目錄中的檔案。

---

## 🚀 快速開始

準備好開始貢獻或自行編譯 OpenRing 了嗎？請遵循以下步驟：

### 1. 開發環境需求
- [Android Studio Jellyfish](https://developer.android.com/studio) 或更新版本
- Java Development Kit (JDK) 17+
- Android SDK Platform 36 與 Build-Tools 36.0.0
- 執行驗證建議使用 targetSdk 34 的裝置或模擬器

### 2. 複製專案
```bash
git clone https://github.com/SunZhi-Will/OpenRing.git
cd OpenRing
```

### 3. 編譯與執行
您可以直接透過 Android Studio 開啟專案並點擊 `Run`，或者使用命令列編譯：
```bash
./gradlew assembleDebug
```

除錯版 APK 會輸出到 `app/build/outputs/apk/debug/`（常見檔名為 `app-debug.apk`）。

### 4. 驗證與測試現況

- 目前 CI 基線為 `./gradlew assembleDebug`，並搭配 CodeQL 與相依性安全檢查。
- 目前專案尚未提交 `app/src/test` 與 `app/src/androidTest` 自動化測試目錄。
- 當前品質驗證以可成功建置 + 裝置/模擬器手動驗證為主。
- 由於部分環境下 AGP lint 工具本身會崩潰，lint tasks 目前暫時停用；請見 `docs/technical/CI_CD.md`。

#### 下載預先建置的除錯版 APK（CI）

每次 **Android CI** 成功執行後，會將 **debug APK** 以 workflow 產物 **`openring-debug-apk`** 上傳（ZIP 內含 `.apk`）。

| 方式                                 | 連結                                                                                                                                                                                                                       |
| ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **直連下載（ZIP，無需登入 GitHub）** | [透過 nightly.link 取得最新 `openring-debug-apk.zip`](https://nightly.link/SunZhi-Will/OpenRing/workflows/android-ci/main/openring-debug-apk.zip) — 解壓縮後安裝其中的 `.apk`。                                            |
| **GitHub Actions 頁面**              | [Android CI 執行紀錄（`main` 分支）](https://github.com/SunZhi-Will/OpenRing/actions/workflows/android-ci.yml?query=branch%3Amain) → 選擇成功的執行 → **Artifacts** → `openring-debug-apk`（自 GitHub 下載時可能需登入）。 |

#### 在 GitHub Release 附上 APK／AAB（建議）

Release 頁面：[SunZhi-Will/OpenRing Releases](https://github.com/SunZhi-Will/OpenRing/releases)

建立 **New release** 時，建議將可安裝檔直接附在 **Assets**，方便使用者不經 CI 頁面就能下載：

- `app-release.apk`（可安裝）
- `app-release.aab`（供 Play Console 上傳）
- `SHA256SUMS.txt`（校驗碼）
- `CHANGELOG` 或 release notes（本次更新內容與已知限制）

> **注意**：目前專案的 CI 預設會產出 **unsigned debug APK**（工作流產物）。正式 release 檔請確認你上傳的是已簽名後的產物。

> **注意**：首次安裝 App 後，請務必前往系統的「設定 > 無障礙設定 (Accessibility)」中，手動啟用 **OpenRing Accessibility Service**，App 才能正常運作。

---

## 📚 文件導覽

### 依使用情境快速入口

- **一般使用者**：`README.zh-TW.md`、`docs/product/PRD.md`
- **貢獻者**：`CONTRIBUTING.md`、`docs/technical/CI_CD.md`、`CHANGELOG.md`
- **開發者**：`docs/technical/AI_AGENT.md`、`docs/technical/SKILLS.md`、`docs/technical/SCRIPT_FORMAT.md`、`docs/product/PROJECT_PLAN.md`

| 文件名稱                                                | 說明                                                                                 |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| [PROJECT_PLAN.md](docs/product/PROJECT_PLAN.md)         | 專案總覽、架構設計、里程碑規劃與潛在風險                                             |
| [PRODUCT_BACKLOG.md](docs/product/PRODUCT_BACKLOG.md)   | 產品功能待辦清單、使用者故事與優先級評估                                             |
| [PRD.md](docs/product/PRD.md)                           | 產品需求：對話驅動、Gemini、技能、無障礙等                                           |
| [AI_AGENT.md](docs/technical/AI_AGENT.md)               | **Agent 架構**：ReAct、工具（含 `summarize_view_tree`、視覺、`describe_ambient_audio`）、權限設定頁、本機 GGUF、原始碼索引 |
| [SKILLS.md](docs/technical/SKILLS.md)                   | 工具與技能、QuickJS、道德鎖定說明                                                    |
| [SCRIPT_FORMAT.md](docs/technical/SCRIPT_FORMAT.md)     | 腳本引擎支援的 JSON 格式定義與動作清單                                               |
| [TEAM_ASSIGNMENT.md](docs/technical/TEAM_ASSIGNMENT.md) | 團隊分工與 AI 開發的系統 Prompt 參考                                                 |
| [CI_CD.md](docs/technical/CI_CD.md)                     | GitHub Actions（除錯版 APK 產物、CodeQL、Dependabot、Dependency Review）             |
| [CONTRIBUTING.md](CONTRIBUTING.md)                      | 完整的開源貢獻指南與 PR 提交流程                                                     |

---

## 🟢 運行狀態（排程與背景處理）

- **頂欄狀態燈**（Chat）：只要 **有啟用排程**、**聊天 AI 執行中**，或 **腳本正在跑**（手動執行、`WorkManager` 觸發、常駐迴圈），右側即為綠色呼吸燈；若無排程且無處理中工作則為灰色。
- **統一狀態通知**：在排程啟用或背景工作進行中會顯示；當腳本或聊天真正在處理時，標題會改為 **「背景處理中」**（由 `BackgroundWorkTracker` 計數），而不只在「鬧鐘剛響」那一瞬間。
- **常駐模式**（`schedule.mode = "always_on"`）：使用前景排程服務以在待機／Doze 下維持較穩觸發；通知上可 **終止常駐**。
- **停用到下次開啟 App**：按下終止常駐後，本次程序生命週期內不再自動啟動常駐服務，下次冷啟 App 會恢復（`AlwaysOnRunGate`）。
- **排程建立後先立即跑一次**：`create_scheduled_script` 會在成功建立後額外觸發一次 `runOnce`，可立即驗證腳本是否能產生結果，不必等待下一個排程週期。
- **短週期預設使用常駐模式**：若建立 `interval` 且 `minutes <= 5`、且未指定 `mode`，系統會預設為 `always_on`，降低 WorkManager 在待機下的延遲機率。
- **排程 AI 回覆寫回 Chat**：當腳本包含 `ai_action`，執行後會寫入聊天記錄（`[排程：腳本名]` + 模型回覆/錯誤），並優先寫回建立排程當下的聊天工作階段（`replyChatSessionId`）。

**Android 13 起** 需允許 **通知權限**，否則看不到狀態通知。

### 權限設定

從聊天進入 **設定** → **權限設定**（與選單 **權限與無障礙** 為同一畫面），可集中處理：

| 項目 | 說明 |
|------|------|
| **通知** | 排程／AI 執行等狀態通知（Android 13+ 執行期授權；較舊版可轉跳系統通知設定）。 |
| **麥克風** | `RECORD_AUDIO`，Agent 音訊相關工具需要（含內部播放擷取 API 要求）。 |
| **手機播放音訊** | Android 10+：使用者授權 **MediaProjection**（流程類似螢幕錄製）＋前景服務，讓 `describe_ambient_audio` 優先錄 **他 App 的播放混音**，而非僅麥克風。 |
| **懸浮窗** | 執行中顯示中斷按鈕等。 |
| **無障礙** | 讀樹與手勢自動化（OpenRing 無障礙服務）。 |

---

## 🤝 參與貢獻

我們非常歡迎社群的參與！無論是回報 Bug、提交新功能建議，還是直接發起 Pull Request，您的每一份貢獻對我們都至關重要。

1. Fork 這個專案
2. 建立您的功能分支 (`git checkout -b feat/AmazingFeature`)
3. 提交您的變更 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feat/AmazingFeature`)
5. 開啟一個 Pull Request

詳細的貢獻規範請參閱 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## 🛡️ 安全回報

如果您發現了任何安全漏洞，請**不要**在公開的 Issue 中回報。
請參閱我們的 [安全政策 (SECURITY.md)](SECURITY.md) 以了解如何私下聯絡我們並協助修復問題。

---

## 🔁 CI/CD 與自動化檢查

GitHub Actions 會在每次 push／PR 建置 **debug APK**（於該次 workflow 執行頁面的 **Artifacts** 下載），並在 Gradle 建置成功後對 Java／Kotlin 執行 **CodeQL** 靜態分析；另透過 **Dependency Review** 與 **Dependabot** 協助發現有風險或過期的相依套件。詳見 [docs/technical/CI_CD.md](docs/technical/CI_CD.md)。

---

## 📜 授權條款

本專案採用 **MIT License** 開源授權，這意味著您可以自由地使用、修改與散佈此程式碼。詳細條款請參閱 [LICENSE](LICENSE) 檔案。

---

<div align="center">
  <b>用科技解放雙手，讓手機為您自動化工作。</b>
</div>

---
