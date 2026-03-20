# OpenRing Skill Plugins (QuickJS)

OpenRing Skill Plugins empower the Gemini Agent inside the app with **deterministic logic, custom integrations, and data processing** capabilities. Since the agent's context window is limited and LLMs can hallucinate logic, Skills provide safe, local sandboxed code (running on QuickJS) that the Agent can call reliably.

This directory contains templates to help developers and users build powerful Skills for the OpenRing ecosystem.

---

## 🚀 Why Build a Skill?

1. **Deterministic Processing**: Need complex regex extraction or specific math? The LLM might make mistakes, but a JS script won't.
2. **Data Transformation**: Convert massive raw inputs (like raw HTML or JSON) into small, clean payloads before feeding them back to Gemini, saving tokens.
3. **Local Tooling**: (Roadmap) Perform network requests, database lookups, or interact with device sensors safely under the user's permission.

## 📦 How to Build and Package a Skill

An OpenRing Skill is simply a `.zip` file containing two files at its root:

```text
my_awesome_skill.zip
├── manifest.json   # Describes the tool to the Gemini Agent (Input/Output schema)
└── script.js       # The actual JavaScript logic executed by QuickJS
```

### 1. `manifest.json`
This is essentially a **Gemini Function Calling Schema**. You must define what the LLM needs to provide (`inputSchema`), what it will get back (`outputSchema`), and what sandbox permissions your script needs.

```json
{
  "name": "my_skill_name",
  "description": "Tell the AI EXACTLY when and why to use this skill.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": { "type": "string" }
    },
    "required": ["query"]
  },
  "outputSchema": {
    "type": "object",
    "properties": {
      "result": { "type": "string" }
    }
  },
  "permissions": {
    "network": { "required": false }
  }
}
```

### 2. `script.js`
Your script runs inside a lightweight [QuickJS](https://bellard.org/quickjs/) engine on the Android device. 
- **Rule 1**: It must `export function run(input)`.
- **Rule 2**: It runs in pure ES2020 JavaScript. There is **NO DOM (`window`, `document`)** and **NO Node.js (`require('fs')`)**.
- **Rule 3**: Return a JSON-serializable object that matches your `outputSchema`.

```javascript
export function run(input) {
  const query = input && input.query ? String(input.query) : "";
  // Do pure JS logic here...
  return { result: query.toUpperCase() };
}
```

### 3. Zip it
```bash
# Inside your skill folder
zip -j my_awesome_skill.zip manifest.json script.js
```
Now users can install `my_awesome_skill.zip` via the OpenRing app's Skills UI!

---

## 🛠️ Included Templates

Browse the folders in this directory to see different types of Skills:

- **`html_metadata_extractor`**: (Advanced) Shows how to parse raw HTML strings using pure Regex (since there is no DOM parser in QuickJS) to extract titles and meta tags. Saves thousands of tokens for the Agent.
- **`markdown_to_blocks`**: (Advanced) Parses raw Markdown into a structured JSON AST (Abstract Syntax Tree) so the Agent can query specific sections instead of reading the whole text.
- **`threads`**: (App Integration) Prepares a specific payload format for another App/Service.
- **`json_reformatter`**: (Data Transform) Reformats JSON (minify or pretty-print).
- **`crypto_price_fetcher`**: (Network) A placeholder for fetching data via network permissions (once implemented in the host).
- **`text_uppercase`**: (Basic) The absolute simplest example of a pure function.

## 🤝 How to Distribute
1. Build your ZIP file.
2. Upload it to GitHub Releases, a personal server, or any public URL.
3. Users add your domain to their OpenRing **Allowed Sources (白名單)**.
4. The AI or the User can now install it directly via the URL!