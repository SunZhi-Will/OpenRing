# CI/CD, Security Scanning, and Debug APK Artifacts

This document describes the GitHub Actions automation for OpenRing: continuous integration builds, security-related checks, and how to obtain installable APK files from CI.

## Workflows

| Workflow | File | Purpose |
|----------|------|---------|
| **Android CI** | `.github/workflows/android-ci.yml` | Runs `./gradlew assembleDebug` on pushes and pull requests; uploads a **debug APK** as a workflow artifact. |
| **CodeQL** | `.github/workflows/codeql.yml` | Runs [CodeQL](https://codeql.github.com/) analysis for **Java/Kotlin** after a successful Gradle build; uploads results to the repository **Security** tab when enabled. |
| **Dependency Review** | `.github/workflows/dependency-review.yml` | On pull requests, reviews dependency changes against GitHub’s advisory database (high/critical issues are highlighted in the PR). |

## Current quality-gate status

- **Build gate**: CI currently enforces `./gradlew assembleDebug`.
- **Security gate**: CodeQL + Dependency Review are enabled in workflows.
- **Test gate**: No committed unit/UI test suites yet (`app/src/test`, `app/src/androidTest` are currently absent).
- **Lint gate**: Lint tasks are temporarily disabled in Gradle due to an AGP/UAST lint runtime crash in some environments (tooling issue, not app logic regression).

### Local verification order (recommended)

1. `./gradlew assembleDebug`
2. Install and smoke-test on emulator/device (permissions, chat, scripts, schedule trigger)
3. `./gradlew build` before merge/release branches

When lint/tooling stability is restored, re-enable lint tasks and promote lint back to CI gate.

## Supply-chain and dependency hygiene

- **Dependabot** (`.github/dependabot.yml`) opens weekly update pull requests for **Gradle** and **GitHub Actions** dependencies.
- **Dependency Review** complements Dependabot by focusing on **what changed in a PR**, not only on the default branch.

Enable **Dependency graph** and **Dependabot alerts** in the repository **Settings → Code security and analysis** if they are not already on.

## Downloading the debug APK

1. Open the repository on GitHub and go to **Actions**.
2. Select **Android CI** and open a successful run.
3. Under **Artifacts**, download **openring-debug-apk**.
4. Unzip locally; install with `adb install -r app-debug.apk` or transfer the file to a device and open it (you may need to allow installs from unknown sources).

The artifact is an **unsigned debug** build. It is suitable for development and CI validation, not for Play Store release.

## Release / signed APKs

Producing a **signed release APK or AAB** in CI requires **keystore secrets** (e.g. GitHub Encrypted Secrets) and a separate workflow or job. That is intentionally not committed here; follow Android signing guides and your team’s key-management policy.

## Troubleshooting

- If **CodeQL** fails while **Android CI** passes, compare logs: both use `assembleDebug` with JDK 17 and the Android SDK setup action. Report discrepancies as issues with workflow logs attached.
- **Dependency Review** may be limited on private repositories without appropriate GitHub features; see [GitHub Docs: Dependency review](https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/about-dependency-review).

## See also

- [AI_AGENT.md](AI_AGENT.md) — chat agent, tools, on-device GGUF, and **Permission settings** overview (not CI-specific).
- [README.md](../../README.md) — **Permission settings** table and getting started.
