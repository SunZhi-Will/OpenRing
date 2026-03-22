# OpenRing AI Agent (Chat + ReAct + Tools)

This document describes the **chat-driven automation agent** layer: how the app combines cloud LLMs (Gemini), optional **on-device GGUF** models, and **AccessibilityService**-backed tools.

---

## 1. High-level flow

1. **User** sends a message in **Chat** (`ChatScreen`).
2. The app walks the **model chain** from settings (`ModelStore`): typically **Gemini** (BYOK) and/or **local** models.
3. **Gemini path**: `ReActCoordinator` runs a multi-turn **ReAct** loop with Gemini function calling; tools are executed by `ToolDispatcher` and results are fed back to the model.
4. **Local path**: `LocalLlmEngine` runs **text-only** inference (no native tool loop inside the GGUF); streaming tokens update the UI in real time.

---

## 2. Model providers

| Provider | Role | Notes |
|----------|------|--------|
| **Gemini** | Primary agent brain (cloud) | Requires API key per model entry (`ApiKeyStore`); tools use `ActiveChatContext` for keys and model id. |
| **Local (GGUF)** | Offline / privacy-friendly chat | Curated catalog in `LocalModelCatalog` (download URLs + filenames); inference via `llama-kotlin-android` (`LlamaModel`). Context size and max tokens are tuned per catalog id (`localInferenceParamsForCatalog`). |

Chat prompts for local models are formatted per family in `LocalLlmChatPrompt` (e.g. ChatML for Qwen, Phi-3–style markers, Gemma 2 turn tags, TinyLlama-style blocks).

---

## 3. Tooling (Gemini function calling)

Defined in `ToolSchemas.kt`, implemented in `ToolDispatcher.kt`. Examples:

| Tool | Purpose |
|------|---------|
| `get_view_tree` | Full semantic UI tree JSON (password fields masked). |
| `get_cached_scan` | Last cached scan (same shape as `get_view_tree` data) without a fresh traversal. |
| `summarize_view_tree` | **Compact** summary: content fingerprint + clickable node ids/labels (no full `root` tree). Updates the same scan cache as `get_view_tree`. |
| `describe_screen` | **Vision fallback**: screenshot + Gemini multimodal description (API 30+; requires Gemini key). Use when the tree is insufficient. |
| `find_and_click`, `click_node`, `input_text`, … | Gesture and input automation. |
| `call_skill` / `skill_*` | QuickJS skill execution. |
| Memory tools | Long-term / session memory (`MemoryRepository`). |

ReAct may shrink large tree payloads before sending to Gemini using `UiTreeCompact` (`ReActCoordinator.shrinkToolResultForModel`).

---

## 4. UI tree compaction

`UiTreeCompact` centralizes:

- Text fingerprinting for stable screen identity hints.
- Clickable node summaries (id, label, bounds hints).
- `compactViewTreeData` for replacing heavy `root` JSON with a compact object.

Used both for **Gemini** tool-result shrinking and for **`summarize_view_tree`** output.

---

## 5. Key source files

| Area | Location |
|------|----------|
| ReAct loop + shrinking | `app/src/main/java/com/openring/agent/ReActCoordinator.kt` |
| Tool schemas | `app/src/main/java/com/openring/agent/ToolSchemas.kt` |
| Tool dispatch | `app/src/main/java/com/openring/agent/ToolDispatcher.kt` |
| UI tree compaction | `app/src/main/java/com/openring/agent/UiTreeCompact.kt` |
| Local GGUF engine | `app/src/main/java/com/openring/localmodel/LocalLlmEngine.kt` |
| Local prompts | `app/src/main/java/com/openring/localmodel/LocalLlmChatPrompt.kt` |
| Model catalog / downloads | `app/src/main/java/com/openring/localmodel/LocalModelCatalog.kt`, `LocalModelDownloader.kt` |
| Chat UI | `app/src/main/java/com/openring/ui/screens/ChatScreen.kt` |

---

## 6. Limitations (intentional)

- **Local GGUF** path does not execute the full Gemini tool loop; it is **plain text** continuation with history + system/memory injected as text.
- **Vision** (`describe_screen`) depends on **Gemini** credentials and Android screenshot capabilities.
- **Skills** are invoked through the **Gemini** tool path (`call_skill`), not from the local-only chat path.

For skills packaging and QuickJS behavior, see [SKILLS.md](SKILLS.md) and [skill-templates/README.md](../skill-templates/README.md).
