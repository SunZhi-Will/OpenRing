# 模型圖示：使用各品牌官方圖案

本 App 的模型下拉與列表會顯示 **各供應商的官方 logo**。目前 `app/src/main/res/drawable/` 內為佔位圖，請依下方說明替換為 **官方圖案**。

## 為何要替換？

- 圖示應為 **該模型的官方品牌圖案**，不得使用自繪或無關圖案。
- 使用官方素材時請遵守各品牌之商標與使用規範。

---

## 1. Google Gemini

- **官方來源**：Google AI / Gemini 品牌素材（來自 [gemini.google.com](https://gemini.google.com) 或 Google AI Studio）。
- **可下載處（公眾使用）**：  
  [Wikimedia Commons: Google Gemini logo](https://commons.wikimedia.org/wiki/File:Google_Gemini_logo.svg)  
  [Wikimedia: Google Gemini icon (圖示版)](https://commons.wikimedia.org/wiki/File:Google-gemini-icon.svg)
- **匯入後請命名為**：`ic_provider_gemini.xml`（若為 SVG 轉成 Android Vector Drawable）或 `ic_provider_gemini.png`，並放在 `app/src/main/res/drawable/`，覆蓋現有佔位檔。

---

## 2. OpenAI

- **官方來源**：  
  [OpenAI Brand Guidelines](https://openai.com/brand) — 可下載官方 logo（含 Blossom 圖示）。
- **匯入後請命名為**：`ic_provider_openai.xml` 或 `ic_provider_openai.png`，放在 `app/src/main/res/drawable/`，覆蓋現有佔位檔。

---

## 3. Anthropic (Claude)

- **官方來源**：  
  [Anthropic Brand / 品牌素材](https://www.anthropic.com) 或 [Anthropic 品牌入口](https://assets.anthropic.com)（若可取得）。  
  亦可使用 Wikipedia/Commons 上的 [Anthropic logo](https://en.wikipedia.org/wiki/File:Anthropic_logo.svg)（請確認授權符合您的使用情境）。
- **匯入後請命名為**：`ic_provider_anthropic.xml` 或 `ic_provider_anthropic.png`，放在 `app/src/main/res/drawable/`，覆蓋現有佔位檔。

---

## 在 Android Studio 中匯入 SVG

1. 從上述連結下載 **官方 SVG**（或經授權的 PNG）。
2. 在 Android Studio：**File → New → Vector Asset**。
3. 選擇 **Local file (SVG, PSD)**，選取下載的 SVG。
4. 調整大小（建議 24dp 或 24×24 以配合列表/下拉）。
5. 匯出時命名為：
   - `ic_provider_gemini`
   - `ic_provider_openai`
   - `ic_provider_anthropic`
6. 儲存至 `app/src/main/res/drawable/`，覆蓋既有同名檔案。

若使用 **PNG**：將 PNG 檔命名為上述名稱（如 `ic_provider_gemini.png`），放入 `drawable` 或 `drawable-nodpi` 即可，程式會以 `painterResource(R.drawable.ic_provider_*)` 載入。

---

## 檔案對應關係

| 供應商   | Drawable 資源名稱           | 說明           |
|----------|-----------------------------|----------------|
| Gemini   | `ic_provider_gemini`        | 請使用 Google 官方圖示 |
| OpenAI   | `ic_provider_openai`        | 請使用 OpenAI 官方圖示 |
| Anthropic| `ic_provider_anthropic`     | 請使用 Anthropic 官方圖示 |
| 其他     | `ic_provider_generic`       | 通用佔位圖，可依需要替換 |

替換上述檔案後無需改程式碼，重新 build 即可顯示官方圖案。
