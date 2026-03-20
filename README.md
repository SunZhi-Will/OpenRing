<div align="center">
  <h1>OpenRing</h1>
  <p><b>基於 Android AccessibilityService 的輕量化手機 RPA 工作流引擎</b></p>
  <p><i>純手機端執行，無需電腦後台、無需 Root 權限</i></p>

  [![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
  [![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
  [![Language](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
  [![Build](https://img.shields.io/badge/Build-Gradle-blueviolet.svg)](https://gradle.org/)
</div>

<br/>

## 📖 專案簡介 (Introduction)

OpenRing 是一款能在 Android 系統上自由穿梭、擁有「Ring 0」般跨應用操作能力的本地端自動化 Agent。如同聰明的月輪與玄鳳鸚鵡，它能精準「看懂」畫面結構、「啄取」關鍵資料，並完美「模仿」人類的點擊與滑動。

**最大的特色是「一切在手機上完成」** — 無論是建立腳本、編輯排程，還是實際執行，都不需要依賴電腦或任何後端伺服器，讓自動化徹底回歸本地端，保障您的隱私與效率。

---

## ✨ 核心特色 (Key Features)

- **🚫 免 Root 權限**：基於 Android 官方的 `AccessibilityService` 開發，無需破解手機。
- **📱 純本地端執行**：不需連接電腦 ADB、不依賴雲端伺服器，所有資料留在您的手機內。
- **👁️ 畫面結構解析**：自動將目前的畫面的 DOM Tree 解析為結構化的 JSON 供腳本辨識。
- **🖱️ 模擬人類操作**：精準模擬點擊 (Click)、滑動 (Swipe)、長按 (Long Press) 以及系統級別的返回 (Back) 與主畫面 (Home)。
- **⏰ 本地排程支援**：內建基於 WorkManager 的定時觸發機制，支援離線自動執行任務。
- **🤖 內建腳本編輯器**：直接在 App 內撰寫、編輯與測試您的自動化工作流。

---

## 🏗 架構與技術 (Architecture & Tech Stack)

*Technical details are documented in English for developer reference.*

OpenRing is built entirely in **Kotlin** and leverages modern Android development practices, including **Jetpack Compose** for the UI and **Coroutines/Flow** for asynchronous programming. 

### Core Components

| Module | Description |
|--------|-------------|
| **View Tree Parser** | Uses `AccessibilityService` to traverse the screen's UI node tree and converts it into structured JSON. |
| **Action Executor** | Dispatches standard gestures (click, swipe, global actions) safely using the Accessibility API. |
| **Intent Router** | Wakes up or navigates to target applications using Android Intents, Deep Links, or Package Names. |
| **Script Engine** | Parses and executes predefined JSON/DSL scripts, integrating logic, variables, and conditions. |
| **Scheduler** | Built on Android `WorkManager` for reliable, background execution of periodic or delayed tasks. |

### Project Structure

```text
OpenRing/
├── app/                  # The main Android Application module
│   └── src/main/
│       ├── core/         # AccessibilityService, Parser, Executor, IntentRouter
│       ├── data/         # Room Database, DAOs, ScriptStore
│       ├── domain/       # Use cases: ScriptExecutor, Scheduler logic
│       └── ui/           # Jetpack Compose screens (Editor, History, Settings)
├── docs/                 # Documentation (PRD, Backlog, Script Format)
└── gradle/               # Build configuration
```

For more detailed technical documentation, please refer to the files in the `docs/` directory.

---

## 🚀 快速開始 (Getting Started)

準備好開始貢獻或自行編譯 OpenRing 了嗎？請遵循以下步驟：

### 1. 開發環境需求 (Prerequisites)
- [Android Studio Jellyfish](https://developer.android.com/studio) 或更新版本
- Java Development Kit (JDK) 17+
- Android SDK (API Level 34)

### 2. 複製專案 (Clone the repo)
```bash
git clone https://github.com/YourOrg/OpenRing.git
cd OpenRing
```

### 3. 編譯與執行 (Build & Run)
您可以直接透過 Android Studio 開啟專案並點擊 `Run`，或者使用命令列編譯：
```bash
./gradlew assembleDebug
```

> **注意**：首次安裝 App 後，請務必前往系統的「設定 > 無障礙設定 (Accessibility)」中，手動啟用 **OpenRing Accessibility Service**，App 才能正常運作。

---

## 📚 文件導覽 (Documentation)

| 文件名稱 | 說明 |
|----------|------|
| [PROJECT_PLAN.md](docs/PROJECT_PLAN.md) | 專案總覽、架構設計、里程碑規劃與潛在風險 |
| [PRODUCT_BACKLOG.md](docs/PRODUCT_BACKLOG.md) | 產品功能待辦清單、使用者故事與優先級評估 |
| [SCRIPT_FORMAT.md](docs/SCRIPT_FORMAT.md) | 腳本引擎支援的 JSON 格式定義與動作清單 |
| [TEAM_ASSIGNMENT.md](docs/TEAM_ASSIGNMENT.md) | 團隊分工與 AI 開發的系統 Prompt 參考 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 完整的開源貢獻指南與 PR 提交流程 |

---

## 🤝 參與貢獻 (Contributing)

我們非常歡迎社群的參與！無論是回報 Bug、提交新功能建議，還是直接發起 Pull Request，您的每一份貢獻對我們都至關重要。

1. Fork 這個專案
2. 建立您的功能分支 (`git checkout -b feat/AmazingFeature`)
3. 提交您的變更 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feat/AmazingFeature`)
5. 開啟一個 Pull Request

詳細的貢獻規範請參閱 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## 🛡️ 安全回報 (Security)

如果您發現了任何安全漏洞，請**不要**在公開的 Issue 中回報。
請參閱我們的 [安全政策 (SECURITY.md)](SECURITY.md) 以了解如何私下聯絡我們並協助修復問題。

---

## 📜 授權條款 (License)

本專案採用 **MIT License** 開源授權，這意味著您可以自由地使用、修改與散佈此程式碼。詳細條款請參閱 [LICENSE](LICENSE) 檔案。

---

<div align="center">
  <b>用科技解放雙手，讓手機為您自動化工作。</b>
</div>