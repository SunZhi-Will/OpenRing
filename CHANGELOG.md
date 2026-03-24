# 更新日誌 (Changelog)

[English Version Below](#english-version)

所有的重要變更都會記錄在這個檔案中。

這個專案遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.0.0/) 的格式，並且版本號遵循 [Semantic Versioning](https://semver.org/lang/zh-TW/spec/v2.0.0.html)。

## [未發布] (Unreleased)

### 新增
- **設定 → 權限設定**：從「設定」頁可進入與聊天選單相同的權限集中頁（通知、麥克風、手機播放音訊／MediaProjection、懸浮窗、無障礙）；畫面標題統一為權限設定。
- 專案初始化與基礎架構建立。
- 基於 `AccessibilityService` 的核心引擎 (View Tree Parser & Action Executor)。
- 內建的腳本編輯器與管理介面 (Jetpack Compose UI)。
- 基於 `WorkManager` 的本地定時排程系統。
- 支援透過 Intent / Deep Link 喚醒目標 App。

### 變更
- 同步更新 README、`docs/` 內技術／產品文件，反映權限設定頁與 Agent 聽覺（`describe_ambient_audio`、MediaProjection）相關說明。

### 棄用
- 無

### 移除
- 無

### 修復
- 無

### 安全性
- 無

---
*詳細的開發歷史請參考 GitHub 上的 Commit 紀錄。*

---
<a id="english-version"></a>

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Settings → Permission settings**: entry from the Settings page to the same consolidated permissions screen as the Chat menu (notifications, microphone, device playback audio / MediaProjection, overlay, accessibility); screen title unified as permission settings.
- Project initialization and basic architecture setup.
- Core engine based on `AccessibilityService` (View Tree Parser & Action Executor).
- Built-in script editor and management interface (Jetpack Compose UI).
- Local scheduling system based on `WorkManager`.
- Support for waking up target apps via Intent / Deep Link.

### Changed
- Documentation refresh across `README*` and `docs/` for **Permission settings** and agent hearing (`describe_ambient_audio`, MediaProjection).

### Deprecated
- None

### Removed
- None

### Fixed
- None

### Security
- None

---
*For detailed development history, please refer to the Commit records on GitHub.*
