<div align="center">
  <a href="https://tally.so/r/your-openring-waitlist-id">
    <img src="docs/assets/openring-cloud-dashboard-concept.svg" alt="OpenRing Cloud Dashboard Concept for Android RPA and Multiple Device Control" width="1200">
  </a>
  
  <br/><br/>
  
  [![Join OpenRing Cloud Private Beta (Waitlist)](https://img.shields.io/badge/%F0%9F%9A%80%20Join%20OpenRing%20Cloud%20Private%20Beta%20(Waitlist)-FF6B00?style=for-the-badge)](https://openring.vercel.app/en/cloud)
  
  <br/><br/>
  
  <img src="docs/assets/openring-logo.png" alt="OpenRing logo" width="128" height="128">
  
  <h1>OpenRing</h1>
  <p><b>Android AccessibilityService RPA + Chat-Driven AI Agent (Gemini & optional on-device GGUF)</b></p>
  <p><i>Runs on the phone—no PC backend, no Root; cloud LLM is optional (BYOK)</i></p>

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

## 📖 Introduction

OpenRing helps teams searching for **Android RPA**, **multiple device control**, and **AI-powered mobile automation** deploy reliable, phone-first workflows with no root and no custom backend.

OpenRing is a **local Android automation agent** that moves across apps with **AccessibilityService**–driven semantics: it reads structured UI trees, runs **scripts and schedules**, and—through **Chat‑Driven OS**—uses **Gemini function calling (ReAct)** or **on-device GGUF models** to plan, call tools, and complete tasks.

**Everything critical runs on the phone** — scripts, schedules, accessibility, skill sandboxes (QuickJS), and optional **local LLM** inference. A **Gemini API key (BYOK)** unlocks cloud reasoning, vision (`describe_screen`), and the full tool loop; without it, you can still use **downloaded GGUF** models for text chat with **streaming** replies.

---

## ✨ Key Features

- **🚫 No Root Required**: Built on Android's official `AccessibilityService`, no need to hack or root your phone.
- **📱 Phone-First**: No PC or ADB required; **no OpenRing backend** — scripts, data, and skills stay on device unless you call a cloud API yourself (e.g. Gemini).
- **🤖 Chat-Driven Agent**: **ReAct** loop with **Gemini** tools — `get_view_tree`, **`summarize_view_tree`** (compact UI), **`describe_screen`** (vision fallback when the tree is not enough), **`describe_ambient_audio`** (hearing: prefers **device-internal playback** capture via MediaProjection; microphone fallback), taps, input, memory, skills, and more.
- **🦙 Optional On-Device LLM**: Curated **GGUF** catalog (`LocalModelCatalog`) with in-app download; **token streaming** in chat; chat templates for Qwen / Phi / Gemma / TinyLlama-style models (`LocalLlmChatPrompt`).
- **👁️ Semantic UI Parsing**: View tree → JSON for scripts and automation; large trees can be **compacted** for LLM context (`UiTreeCompact`).
- **🖱️ Human-Like Actions**: Clicks, swipes, long press, Back, Home, app launch.
- **⏰ Scheduling**: WorkManager-based triggers for recurring scripts.
- **🧩 Skill Plugins (QuickJS)**: `call_skill` runs sandboxed JS for deterministic helpers (see `docs/skill-templates/`).
- **🛠️ Script Editor & Workflows**: Create, edit, and run JSON workflows on device.

---

## 📈 Activity & Stars

<a href="https://www.star-history.com/?repos=SunZhi-Will%2FOpenRing&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=SunZhi-Will/OpenRing&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=SunZhi-Will/OpenRing&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/image?repos=SunZhi-Will/OpenRing&type=date&legend=top-left" />
 </picture>
</a>

---

## 🏗 Architecture & Tech Stack

OpenRing is built entirely in **Kotlin** and leverages modern Android development practices, including **Jetpack Compose** for the UI and **Coroutines/Flow** for asynchronous programming. 

### Core Components

| Module                    | Description                                                                                             |
| ------------------------- | ------------------------------------------------------------------------------------------------------- |
| **View Tree Parser**      | Uses `AccessibilityService` to traverse the screen's UI node tree and converts it into structured JSON. |
| **Action Executor**       | Dispatches standard gestures (click, swipe, global actions) safely using the Accessibility API.         |
| **Intent Router**         | Wakes up or navigates to target applications using Android Intents, Deep Links, or Package Names.       |
| **Script Engine**         | Parses and executes predefined JSON/DSL scripts, integrating logic, variables, and conditions.          |
| **Scheduler**             | Built on Android `WorkManager` for reliable, background execution of periodic or delayed tasks.         |
| **Agent (ReAct + Tools)** | `ReActCoordinator` + `ToolDispatcher` — Gemini function calling, tool results, optional UI compaction.  |
| **Local LLM**             | `LocalLlmEngine` — GGUF load/inference via `llama-kotlin-android`, streaming generation for chat.       |

### Project Structure

```text
OpenRing/
├── app/                  # The main Android Application module
│   └── src/main/
│       ├── core/         # AccessibilityService, Parser, Executor, IntentRouter, ScreenCapture, playback audio / MediaProjection
│       ├── agent/        # ReActCoordinator, ToolSchemas, ToolDispatcher, UiTreeCompact
│       ├── localmodel/   # GGUF catalog, downloader, LocalLlmEngine, chat prompts
│       ├── gemini/       # Gemini REST client & models
│       ├── data/         # Room, ChatRepository, MemoryRepository, ScriptStore
│       ├── domain/       # ScriptExecutor, Scheduler
│       ├── skills/       # QuickJS skill install & execution
│       └── ui/           # Jetpack Compose (Chat, Settings, Skills, Scripts, …)
├── docs/                 # Documentation
│   ├── product/          # PRD, Backlog, Project Plan
│   └── technical/        # Script format, CI/CD, AI agent, skills
└── gradle/               # Build configuration
```

For more detailed technical documentation, please refer to the files in the `docs/` directory.

---

## 🚀 Getting Started

Ready to start contributing or build OpenRing yourself? Follow these steps:

### 1. Prerequisites
- [Android Studio Jellyfish](https://developer.android.com/studio) or newer
- Java Development Kit (JDK) 17+
- Android SDK Platform 36 and Build-Tools 36.0.0
- Android targetSdk 34 device/emulator for runtime validation

### 2. Clone the repo
```bash
git clone https://github.com/SunZhi-Will/OpenRing.git
cd OpenRing
```

### 3. Build & Run
You can open the project directly via Android Studio and click `Run`, or compile using the command line:
```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/` (typically `app-debug.apk`).

### 4. Verification and test status

- Current CI baseline is `./gradlew assembleDebug` + security scans (CodeQL / dependency review).
- The project currently has no committed `app/src/test` or `app/src/androidTest` suites.
- For now, quality validation relies on successful build plus manual feature checks on device/emulator.
- Lint tasks are temporarily disabled due to an AGP lint tool crash in some environments; see `docs/technical/CI_CD.md`.

#### Download prebuilt debug APK (CI)

Each successful run of **Android CI** uploads a **debug APK** as the workflow artifact `openring-debug-apk` (a ZIP containing the `.apk` file).

| Method                                     | Link                                                                                                                                                                                                                                       |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Direct download (ZIP, no GitHub login)** | [Latest `openring-debug-apk.zip` via nightly.link](https://nightly.link/SunZhi-Will/OpenRing/workflows/android-ci/main/openring-debug-apk.zip) — unzip locally, then install the `.apk` inside.                                            |
| **GitHub Actions UI**                      | [Android CI workflow runs (branch `main`)](https://github.com/SunZhi-Will/OpenRing/actions/workflows/android-ci.yml?query=branch%3Amain) → open a green run → **Artifacts** → `openring-debug-apk` (GitHub may require login to download). |

#### Attach APK/AAB to GitHub Releases (recommended)

Release page: [SunZhi-Will/OpenRing Releases](https://github.com/SunZhi-Will/OpenRing/releases)

When creating a **New release**, upload installable/distributable files directly in **Assets** so users can download them without browsing CI runs:

- `app-release.apk` (installable)
- `app-release.aab` (for Play Console)
- `SHA256SUMS.txt` (integrity checks)
- `CHANGELOG` or release notes (what changed and known limitations)

> Note: this repository’s default CI artifact is an **unsigned debug APK**. For production-grade release files, make sure you generate/sign the artifacts you attach.

> **Note**: After installing the App for the first time, you must manually go to the system's "Settings > Accessibility" and enable the **OpenRing Accessibility Service** for the App to function properly.

---

## 📚 Documentation Navigation

### Quick paths by role

- **Users**: [README.zh-TW.md](README.zh-TW.md), [PRD.md](docs/product/PRD.md)
- **Contributors**: [CONTRIBUTING.md](CONTRIBUTING.md), [CI_CD.md](docs/technical/CI_CD.md), [CHANGELOG.md](CHANGELOG.md)
- **Developers**: [AI_AGENT.md](docs/technical/AI_AGENT.md), [SKILLS.md](docs/technical/SKILLS.md), [SCRIPT_FORMAT.md](docs/technical/SCRIPT_FORMAT.md), [PROJECT_PLAN.md](docs/product/PROJECT_PLAN.md)

| Document                                                | Description                                                                                                                      |
| ------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| [PROJECT_PLAN.md](docs/product/PROJECT_PLAN.md)         | Project overview, architecture design, milestones, and potential risks                                                           |
| [PRODUCT_BACKLOG.md](docs/product/PRODUCT_BACKLOG.md)   | Product feature backlog, user stories, and priority evaluation                                                                   |
| [PRD.md](docs/product/PRD.md)                           | Product requirements: Chat-Driven OS, Gemini, skills, accessibility                                                              |
| [AI_AGENT.md](docs/technical/AI_AGENT.md)               | **Agent stack**: ReAct, tools (`summarize_view_tree`, vision, `describe_ambient_audio`, …), permissions UI, local GGUF, file map |
| [SKILLS.md](docs/technical/SKILLS.md)                   | Tools vs Skills, QuickJS, morality guardrails                                                                                    |
| [SCRIPT_FORMAT.md](docs/technical/SCRIPT_FORMAT.md)     | JSON format definition and action list supported by the script engine                                                            |
| [TEAM_ASSIGNMENT.md](docs/technical/TEAM_ASSIGNMENT.md) | Team assignments and system Prompt references for AI development                                                                 |
| [CI_CD.md](docs/technical/CI_CD.md)                     | GitHub Actions (debug APK artifacts, CodeQL, Dependabot, Dependency Review)                                                      |
| [CONTRIBUTING.md](CONTRIBUTING.md)                      | Complete open-source contribution guide and PR submission process                                                                |

---

## 🟢 Runtime status (schedules & background work)

- **Top-bar status lamp** (Chat): Green (breathing) when **any schedule is enabled**, when **chat AI is running**, or when **a script is executing** (manual run, `WorkManager` worker, or always-on loop). Gray when nothing is armed and nothing is processing.
- **Unified status notification** (`SchedulerStatusNotification`): Stays visible while schedules are enabled and/or background work is active. Title switches to **“background processing”** while scripts or chat runs are in progress (tracked by `BackgroundWorkTracker`), not only when an alarm fires.
- **Always-on mode** (`schedule.mode = "always_on"`): Uses a foreground scheduler service for stable timing under idle/doze. The notification can include **Terminate always-on** while that service is running.
- **Suspended until next app launch**: After **Terminate always-on**, always-on stays off until the next cold app launch (`AlwaysOnRunGate`).

On **Android 13+**, grant **notification permission** or you will not see status notifications.

### Permission settings

Open **Settings** (from Chat) → **Permission settings** to manage:

| Area                        | Purpose                                                                                                                                                                                                     |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Notifications**           | Status / scheduler / AI run alerts (Android 13+ runtime grant; older OS links to system notification settings).                                                                                             |
| **Microphone**              | `RECORD_AUDIO` — required for agent audio tools (including internal playback capture APIs).                                                                                                                 |
| **Device playback audio**   | Android 10+: user-granted **MediaProjection** (same consent pattern as screen recording) + foreground service — used so `describe_ambient_audio` can capture **other apps’ speaker mix**, not only the mic. |
| **Display over other apps** | Floating stop control during runs.                                                                                                                                                                          |
| **Accessibility**           | Core automation (OpenRing accessibility service).                                                                                                                                                           |

The same screen is available from the Chat menu as **Permissions & accessibility**.

---

## 🤝 Contributing

We welcome community participation! Whether it's reporting bugs, suggesting new features, or directly opening Pull Requests, every contribution is crucial to us.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feat/AmazingFeature`)
3. Commit your Changes (`git commit -m 'feat: Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feat/AmazingFeature`)
5. Open a Pull Request

For detailed contribution guidelines, please see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 🛡️ Security

If you discover any security vulnerabilities, please **do not** report them in public Issues.
Please refer to our [Security Policy (SECURITY.md)](SECURITY.md) to learn how to contact us privately and help fix the issue.

---

## 🔁 CI/CD & automated checks

GitHub Actions builds a **debug APK** on each push/PR (downloadable from the workflow run’s **Artifacts**), runs **CodeQL** static analysis for Java/Kotlin after a Gradle build, and uses **Dependency Review** plus **Dependabot** to surface vulnerable or outdated dependencies. See [docs/technical/CI_CD.md](docs/technical/CI_CD.md) for workflow names, permissions, and artifact usage.

---

## 📜 License

This project is licensed under the **MIT License** — this means you are free to use, modify, and distribute this code. See the [LICENSE](LICENSE) file for details.

---

<div align="center">
  <b>Free your hands with technology, let your phone automate work for you.</b>
</div>
