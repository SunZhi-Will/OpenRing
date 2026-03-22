<div align="center">
  <img src="docs/assets/openring-logo.png" alt="OpenRing logo" width="128" height="128">
  
  <h1>OpenRing</h1>
  <p><b>A Lightweight On-Device RPA Workflow Engine based on Android AccessibilityService</b></p>
  <p><i>Executes entirely on the phone, no PC backend required, no Root access needed</i></p>

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

OpenRing is a local automation Agent that freely navigates the Android system with "Ring 0"-like cross-app operational capabilities. Like a smart parrot, it accurately "reads" screen structures, "pecks" key data, and perfectly "imitates" human clicks and swipes.

**The most significant feature is "Everything is done on the phone"** — whether it's creating scripts, editing schedules, or actual execution, it does not rely on a PC or any backend server. Automation completely returns to the local device, ensuring your privacy and efficiency.

---

## ✨ Key Features

- **🚫 No Root Required**: Built on Android's official `AccessibilityService`, no need to hack or root your phone.
- **📱 Pure Local Execution**: No need to connect to PC via ADB, no reliance on cloud servers. All data stays on your phone.
- **👁️ Screen Structure Parsing**: Automatically parses the current screen's DOM Tree into structured JSON for script recognition.
- **🖱️ Simulates Human Actions**: Accurately simulates clicks, swipes, long presses, and system-level actions like Back and Home.
- **⏰ Local Scheduling Support**: Built-in WorkManager-based scheduled trigger mechanism, supporting offline automated task execution.
- **🤖 Built-in Script Editor**: Write, edit, and test your automated workflows directly within the App.

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
├── docs/                 # Documentation
│   ├── product/          # PRD, Backlog, Project Plan
│   └── technical/        # Script Format, Protocol, Team Assignment
└── gradle/               # Build configuration
```

For more detailed technical documentation, please refer to the files in the `docs/` directory.

---

## 🚀 Getting Started

Ready to start contributing or build OpenRing yourself? Follow these steps:

### 1. Prerequisites
- [Android Studio Jellyfish](https://developer.android.com/studio) or newer
- Java Development Kit (JDK) 17+
- Android SDK (API Level 34)

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

> **Note**: After installing the App for the first time, you must manually go to the system's "Settings > Accessibility" and enable the **OpenRing Accessibility Service** for the App to function properly.

---

## 📚 Documentation Navigation

| Document | Description |
|----------|-------------|
| [PROJECT_PLAN.md](docs/product/PROJECT_PLAN.md) | Project overview, architecture design, milestones, and potential risks |
| [PRODUCT_BACKLOG.md](docs/product/PRODUCT_BACKLOG.md) | Product feature backlog, user stories, and priority evaluation |
| [SCRIPT_FORMAT.md](docs/technical/SCRIPT_FORMAT.md) | JSON format definition and action list supported by the script engine |
| [TEAM_ASSIGNMENT.md](docs/technical/TEAM_ASSIGNMENT.md) | Team assignments and system Prompt references for AI development |
| [CI_CD.md](docs/technical/CI_CD.md) | GitHub Actions (debug APK artifacts, CodeQL, Dependabot, Dependency Review) |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Complete open-source contribution guide and PR submission process |

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
