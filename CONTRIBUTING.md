# Contributing to FlowPilot

Thank you for your interest in contributing to **FlowPilot**! FlowPilot is an open-source, privacy-first, lightweight Android automation engine.

---

## Code of Conduct & Core Philosophy

When contributing to FlowPilot, keep our core architectural principles in mind:

1. **Privacy-First & Offline:** FlowPilot must never send user telemetry, analytics, rule definitions, or device data over the network to external servers. Any network actions must be strictly user-configured (e.g. HTTP Webhooks).
2. **Battery & Resource Respect:** Background listeners, sensor managers, and broadcast receivers must be demand-driven. If no active rule requires a sensor (such as light or accelerometer), it must be unhooked.
3. **Graceful Degradation & Safety:** Actions requiring elevated privileges (such as Shizuku) must verify execution and state readback. Direct phone calls and SMS must include user confirmation safeguards.
4. **Clean Code & Idiomatic Kotlin:** Use Jetpack Compose, Material 3, Coroutines, StateFlow, and DataStore. Keep business logic testable and separated from Compose UI.

---

## Getting Started

### Prerequisites
- **JDK 17** (Temurin recommended)
- **Android SDK** (API level 36, compileSdk 36)
- **Git**

### Clone & Build
```bash
git clone https://github.com/emi-ran/flowpilot.git
cd flowpilot

# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

---

## Submitting a Pull Request (PR)

1. **Fork the Repository:** Create a new branch from `main` (e.g., `feature/new-sensor-trigger` or `fix/tts-crash`).
2. **Write Unit Tests:** If you are adding a new trigger reducer, action executor, or condition validator, add corresponding unit tests under `app/src/test/java/com/flowpilot/app/`.
3. **Verify Build:** Ensure `./gradlew testDebugUnitTest` passes with zero errors before pushing.
4. **Conventional Commits:** Use clear commit messages (e.g., `feat: add screen brightness action`, `fix: prevent duplicate bluetooth broadcast`).
5. **Open a PR:** Describe the change, the problem it solves, and how you tested it on device.

---

## Localization / Translations

FlowPilot currently supports:
- English (`app/src/main/res/values/strings.xml`)
- Turkish (`app/src/main/res/values-tr/strings.xml`)

When adding new UI strings, always declare keys in both files. New language contributions (`values-<lang>/strings.xml`) are warmly welcome!

---

## License

By contributing to FlowPilot, you agree that your contributions will be licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
