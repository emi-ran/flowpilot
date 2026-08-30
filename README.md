# FlowPilot

Native Android automation app based on supplied Stitch UI. Kotlin, Jetpack Compose, Material 3, DataStore, Coroutines.

## Build

Requirements:

- JDK 17
- Android SDK platform 36 / build-tools 36.0.0
- Gradle wrapper included

```bash
export JAVA_HOME=/home/hermes/android-toolchain/jdk17
export ANDROID_HOME=/home/hermes/Android/Sdk
./gradlew testDebugUnitTest assembleDebug --no-daemon
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Package ID: `com.flowpilot.app`
Version: `1.0.0` / versionCode `1`
Min SDK: 26
Target / compile SDK: 36 (Android 16)

## Implemented

- Automations list matching supplied dark Stitch design.
- Create rule flow: app opened/closed -> NFC on/off or Battery Saver on/off.
- Installed launchable app picker with search, display name, package ID internally.
- Rule detail and delete.
- Persistent rules through DataStore JSON.
- Enable/disable switches.
- Foreground automation service with visible ongoing notification.
- UsageStatsManager event polling with ~1.5 second interval.
- App transition detection: one open event per foreground residency, one close event on transition.
- Boot/app-update receiver to restart the engine where Android permits it.
- Capability labels: Available, Permission required, Shizuku required, Unsupported on this device.
- Shizuku UserService AIDL command bridge. Commands run with Shizuku shell/root identity; app never claims success if the command failed.
- Four real action executors:
  - NFC ON/OFF: `cmd nfc on|off` through Shizuku.
  - Battery Saver ON/OFF: direct `Settings.Global` write with `WRITE_SECURE_SETTINGS`, or `settings put global low_power` through Shizuku.
- Unit tests for rule matching, open dedupe, close events, and disabled rules.

## Setup permissions

### 1. Usage Access

Open FlowPilot -> Settings -> Advanced permissions -> Usage Access -> allow FlowPilot.

Reason: Android does not expose foreground-app changes as a normal runtime permission. Usage Access lets FlowPilot read foreground transition events.

### 2. Notifications

On Android 13+, allow notifications when prompted.

Reason: Android requires the foreground service to have a visible status notification. It shows that FlowPilot is watching app transitions.

### 3. Battery optimization

Optional. If HyperOS stops the engine, allow FlowPilot under system Battery / Autostart / Battery optimization settings.

Reason: OEM background restrictions can stop any polling service. FlowPilot does not silently request exemption.

### 4. Battery Saver actions: ADB path

With USB debugging enabled and device connected:

```bash
adb shell pm grant com.flowpilot.app android.permission.WRITE_SECURE_SETTINGS
```

Verify:

```bash
adb shell dumpsys package com.flowpilot.app | grep WRITE_SECURE_SETTINGS
```

This makes Battery Saver actions available directly to FlowPilot. NFC still needs Shizuku.

### 5. Shizuku path

Install Shizuku from its official source:

<https://shizuku.rikka.app/download/>

Start Shizuku through Wireless debugging or USB debugging. Open FlowPilot's permission screen and grant FlowPilot access when Shizuku asks.

Shizuku is required for NFC ON/OFF because normal Android apps cannot toggle NFC on modern Android. FlowPilot runs only these narrow commands through its Shizuku UserService:

```text
cmd nfc on
cmd nfc off
settings put global low_power 1
settings put global low_power 0
```

No root is required. If Shizuku is stopped, the action reports failure and does not fake success.

## Android restrictions and limitations

- Normal apps cannot toggle NFC on Android 10+; `NfcAdapter.enable()` / `disable()` are privileged/system/DPC operations. FlowPilot therefore marks NFC as Shizuku-required.
- Battery Saver is a protected global setting. It requires `WRITE_SECURE_SETTINGS` through ADB development grant or Shizuku.
- UsageStatsManager polling is Android-supported but not an instantaneous callback API. Event timing can vary by device and OEM, especially HyperOS.
- HyperOS may require manual Autostart and battery-policy exemptions. Android can still stop/restrict background work.
- `QUERY_ALL_PACKAGES` is declared to provide a complete installed launchable-app picker. Store distribution policy may require justification.
- No AccessibilityService is used. It is not necessary for UsageStats-based app detection and would add broader access than required.
- No root, hidden API calls, silent shell execution, or fake fallback behavior.

## Project structure

```text
app/src/main/java/com/flowpilot/app/
  data/model/                 Serializable rule model
  data/                       DataStore repository
  engine/                     UsageStats tracker, evaluator, service, boot receiver
  actions/                    Action executors and Shizuku UserService bridge
  permission/                 Capability and setup checks
  ui/                         Compose screens, state, theme, components
```

## Verification performed

- `./gradlew :app:compileDebugKotlin --no-daemon` — passed.
- `./gradlew testDebugUnitTest --no-daemon` — passed.
- `./gradlew lintDebug --no-daemon` — passed.
- `./gradlew assembleDebug --no-daemon` — passed.
- APK inspected with `aapt`: package `com.flowpilot.app`, SDK 26–36, declared Usage Access, Query All Packages, notification, foreground service, boot, battery optimization, and WRITE_SECURE_SETTINGS permissions.
- Device-side smoke test requires a connected Android device; build host currently has no verified device result.

## License / distribution note

Shizuku is an external dependency. Follow its license and distribution guidance when publishing FlowPilot.

## License

Add project license before public distribution.

<!-- Generated delivery artifact: debug APK only. -->

SHA-256 of verified local debug APK at delivery time:

```text
49e2719e7e1bec36d3238bb03dec9187f51c43b007d9d07977df24ad382955ca
```
