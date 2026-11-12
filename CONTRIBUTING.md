# Contributing to FlowPilot

Thank you for your interest in contributing to **FlowPilot**! FlowPilot is an open-source, privacy-first, lightweight Android automation engine.

---

## Code of Conduct & Core Philosophy

When contributing to FlowPilot, keep our core architectural principles in mind:

1. **Privacy-First:** Do not add telemetry, analytics, or automatic synchronization of rules or device data. Optional release update checks contact GitHub; user-configured actions such as webhooks and SMS can send the selected data to external recipients.
2. **Battery & Resource Respect:** Background listeners, sensor managers, and broadcast receivers must be demand-driven. If no active rule requires a sensor (such as light or accelerometer), it must be unhooked.
3. **Graceful Degradation & Safety:** Actions requiring elevated privileges (such as Shizuku) must verify execution and state readback where possible. Direct calls and SMS require their Android permissions; editor test runs show confirmation, but automatic rule execution does not ask for a separate confirmation on every run.
4. **Clean Code & Idiomatic Kotlin:** Use Jetpack Compose, Material 3, Coroutines, StateFlow, and DataStore. Keep business logic testable and separated from Compose UI.

---

## Getting Started

### Prerequisites
- **JDK 17** (Temurin recommended)
- **Android SDK** (API level 36, compileSdk 36)
- **Git** and **Python 3** (for SDK-free contract checks)

### Clone & Build
```bash
git clone https://github.com/emi-ran/flowpilot.git
cd flowpilot

# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

The debug APK installs as **FlowPilot Dev** (`com.flowpilot.app.debug`) and can coexist with the release app (`com.flowpilot.app`). Debug builds skip release update checks.

---

## Submitting a Pull Request (PR)

1. **Fork the Repository:** Create a new branch from `main` (e.g., `feature/new-sensor-trigger` or `fix/tts-crash`).
2. **Write Unit Tests:** If you are adding a new trigger reducer, action executor, or condition validator, add corresponding unit tests under `app/src/test/java/com/flowpilot/app/`.
3. **Verify Build:** Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false` and each `scripts/*.py` contract check before submitting Android or build changes. CI runs its `lint` task and these checks when Android or build inputs change; other changes can pass the required check without Gradle.
4. **Conventional Commits:** Use clear commit messages (e.g., `feat: add screen brightness action`, `fix: prevent duplicate bluetooth broadcast`).
5. **Open a PR:** Describe the change, the problem it solves, and how it was tested.

Automated tests are the JVM suites under `app/src/test/java/com/flowpilot/app/` plus the SDK-free checks in `scripts/`. There is no instrumentation/androidTest package and pull requests do not run device or emulator tests; device and OEM behavior is verified manually, so state what you checked by hand in the PR.

For suspected security vulnerabilities, follow [SECURITY.md](SECURITY.md) and use private vulnerability reporting instead of a public issue.

---

## Localization / Translations

FlowPilot currently supports:
- English (`app/src/main/res/values/strings.xml`)
- Turkish (`app/src/main/res/values-tr/strings.xml`)

When adding new UI strings, always declare keys in both files. New language contributions (`values-<lang>/strings.xml`) are warmly welcome!

---

## License

By contributing to FlowPilot, you agree that your contributions will be licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
