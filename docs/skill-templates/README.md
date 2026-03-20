# OpenRing 技能外掛程式範本 (QuickJS)

[English Version Below](#english-version)

OpenRing 技能外掛程式是透過下載 ZIP 套件來安裝的。安裝程式預期每個套件 ZIP 內包含：

1. `manifest.json`
2. `script.js`

這個資料夾提供了一些小型的、可複製並修改的範本，讓團隊成員在開發新技能時不需要猜測套件格式。

## 如何打包技能（ZIP 結構）

對於每個範本目錄，建立一個包含 `manifest.json` 和 `script.js` 在 ZIP 根目錄下的 ZIP 檔案。

預期的 ZIP 結構：
```text
your-skill.zip
└── manifest.json
└── script.js
```

## `manifest.json`（建議欄位）

目前的宿主安裝程式只要求 `name` 來衍生 `skillId`，但這個範本使用了 `docs/PRD.md` 中描述的更豐富格式。

最低要求：
```json
{ "name": "your_skill_id" }
```

建議格式：
```json
{
  "name": "your_skill_id",
  "description": "What this skill does and when it should be used",
  "inputSchema": { "type": "object", "properties": {}, "required": [] },
  "outputSchema": { "type": "object", "properties": {} },
  "permissions": { "network": { "required": true } }
}
```

## `script.js` 介面

每個技能模組應該匯出單一入口函式：

```js
export function run(input) {
  return { /* output object */ };
}
```

回傳值應該是一個可被 JSON 序列化的物件。

## 這個資料夾中的範本

1. `crypto_price_fetcher`：展示了需要 `network` 權限以及穩定的輸出格式。
2. `text_uppercase`：展示了一個純轉換的技能，不需要任何權限。
3. `json_reformatter`：展示了架構驅動（schema-driven）的格式化以及確定性的輸出。

---
<a id="english-version"></a>

# OpenRing Skill Plugin Templates (QuickJS)

OpenRing Skill plugins are installed by downloading a ZIP package. The installer expects each package ZIP to contain:

1. `manifest.json`
2. `script.js`

This folder provides a few small, copy-and-modify templates so teammates can build new skills without guessing the package format.

## How to package a skill (ZIP layout)

For each template directory, create a ZIP that includes `manifest.json` and `script.js` at the ZIP root.

Expected ZIP structure:
```text
your-skill.zip
└── manifest.json
└── script.js
```

## `manifest.json` (recommended fields)

The current host installer only requires `name` to derive `skillId`, but this template uses the richer format described in `docs/PRD.md`.

Minimal requirement:
```json
{ "name": "your_skill_id" }
```

Recommended shape:
```json
{
  "name": "your_skill_id",
  "description": "What this skill does and when it should be used",
  "inputSchema": { "type": "object", "properties": {}, "required": [] },
  "outputSchema": { "type": "object", "properties": {} },
  "permissions": { "network": { "required": true } }
}
```

## `script.js` interface

Each skill module should export a single entry function:

```js
export function run(input) {
  return { /* output object */ };
}
```

Return value should be a JSON-serializable object.

## Templates in this folder

1. `crypto_price_fetcher`: Demonstrates a `network` permission requirement and a stable output shape.
2. `text_uppercase`: Demonstrates a pure transform skill with no permissions.
3. `json_reformatter`: Demonstrates schema-driven formatting and deterministic outputs.
