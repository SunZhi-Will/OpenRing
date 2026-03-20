# 參與貢獻 (Contributing to OpenRing)

首先，感謝您有興趣參與 OpenRing 的開發與維護！我們非常歡迎所有形式的貢獻，不論是回報 Bug、提議新功能、改進文件，或是直接提交程式碼。

為了讓大家都能在一個愉快、高效率的環境中協作，請在開始之前詳細閱讀這份貢獻指南。

---

## 🚀 快速開始 (Getting Started)

如果您打算提交程式碼變更，請遵循以下標準的開源流程：

1. **Fork** 這個儲存庫到您自己的 GitHub 帳號。
2. 從 `main` 分支建立一個具有描述性的**功能分支 (Feature Branch)**。
   - 命名建議：`feat/add-new-parser`、`fix/crash-on-accessibility-event`、`docs/update-readme`
3. 進行您的程式碼修改，並確保每個提交 (Commit) 都專注且清晰。
4. **確保專案能夠成功建置** (這非常重要！)：
   ```bash
   ./gradlew build
   ```
5. 將您的分支推送到您 Fork 的儲存庫。
6. 開啟一個 **Pull Request (PR)** 到我們的 `main` 分支。

---

## 💻 開發原則 (Development Principles)

為了維持程式碼庫的品質與一致性，我們遵循以下原則：

- **單一職責 (Keep changes minimal)**：一個 PR 盡量只解決一個特定的問題或新增一個功能。避免在同一個 PR 中混合多個無關的修改或重構。
- **可讀性優先 (Readable over clever)**：比起過於簡潔或「聰明」的語法，我們更偏好容易閱讀與理解的程式碼。
- **文件同步更新 (Update documentation)**：如果您新增了功能或改變了 API 的行為，請記得一併更新相關的文件（例如 `README.md`、`docs/` 資料夾下的文件）。
- **無秘密憑證 (No secrets)**：**絕對不要**將任何 API Key、私人憑證或 `.keystore` 提交到版本控制系統中。請確保這些檔案都已包含在 `.gitignore` 內。

---

## 📝 提交訊息規範 (Commit Message Guidelines)

我們建議遵循 [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) 規範，使用**英文**撰寫您的 Commit 訊息。這有助於我們自動產生 Changelog 並清楚了解修改的範圍。

格式範例：
```
<type>(<scope>): <subject>

<body>

<footer>
```

常見的 `<type>`：
- `feat`: 新增功能 (A new feature)
- `fix`: 修復 Bug (A bug fix)
- `docs`: 文件修改 (Documentation only changes)
- `style`: 不影響程式碼運作的格式調整 (空白、格式化、缺少分號等)
- `refactor`: 重構 (A code change that neither fixes a bug nor adds a feature)
- `perf`: 效能優化 (A code change that improves performance)
- `test`: 新增或修改測試 (Adding missing tests or correcting existing tests)
- `chore`: 建置過程或輔助工具的變更 (Changes to the build process or auxiliary tools and libraries)

範例：
```text
feat(parser): add support for extracting text from RecyclerViews
fix(executor): prevent crash when clicking out of bounds
docs: update getting started section in README
```

---

## ✅ Pull Request 檢查清單 (Pull Request Checklist)

在送出 PR 前，請自行核對以下事項：

- [ ] 程式碼可以透過 `./gradlew build` 成功編譯且沒有錯誤。
- [ ] 您的修改有經過測試，並確認行為符合預期。
- [ ] 相關的文件或註解已經更新。
- [ ] PR 描述清楚說明了「為什麼」要做這些修改，以及修改的「內容」。
- [ ] 已確認沒有包含任何敏感資訊或密鑰。

---

## 🐛 回報問題 (Reporting Issues)

如果您發現了 Bug 或有任何改進建議，請善用 GitHub 的 Issues 功能。
在建立 Issue 時，請盡量提供以下資訊以幫助我們快速重現與解決問題：

- 您的裝置型號與作業系統版本 (Device / OS version)
- 詳細的重現步驟 (Reproduction steps)
- 預期發生的行為 vs 實際發生的行為 (Expected vs actual behavior)
- 相關的截圖、錄影或 Logcat 輸出 (Logs or screenshots if available)

---

## 🛡️ 安全性漏洞 (Security)

如果您發現了嚴重的安全性漏洞，請**不要**在公開的 GitHub Issues 中回報。
請參閱我們的 [SECURITY.md](SECURITY.md) 了解正確的通報流程。我們將會在私下與您聯繫並盡速修補問題。

再次感謝您的參與，讓我們一起打造更好的 OpenRing！