# FlowPilot — Implementation Plan

Real native Android automation app. Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL.
Builds on a proven toolchain: Gradle 9.5.0, AGP 9.3.2, Kotlin 2.2.10, compileSdk 36 (Android 16),
minSdk 26, JDK 17.

## Feature set

Automation rules: WHEN [app opened | app closed | scheduled time] DO one or more [NFC on | NFC off |
Battery Saver on | Battery Saver off] actions. Schedules support daily, weekdays, or selected days. Engine
detects foreground apps via UsageStatsManager, evaluates enabled rules, executes each schedule occurrence
once, and restarts on boot/app update when the engine-startup preference is enabled.

## Capability matrix (verified against Android 16 / HyperOS constraints)

| Action            | Plain app           | ADB (WRITE_SECURE_SETTINGS) | Shizuku             | Root    |
|-------------------|---------------------|------------------------------|---------------------|---------|
| Detect app        | Yes (Usage Access)  | -                            | -                   | -       |
| Battery Saver     | NO                  | YES (`pm grant` + write global low_power) | YES (`settings put global low_power`) | YES |
| NFC               | NO (API 29+ removed NfcAdapter.enable) | NO (needs shell uid) | YES (`svc nfc enable|disable`) | YES |

- NFC toggling is a privileged action on Android 10+; `NfcAdapter.enable()/disable()` exist but are
  restricted to system/DPC. Normal apps can only redirect the user to NFC settings. On Xiaomi 15T Pro /
  HyperOS 3, FlowPilot uses Shizuku to run `svc nfc enable|disable` as shell. `cmd nfc` crashed the target
  device NFC service during testing and is not used.
- Battery Saver is gated by `android.permission.WRITE_SECURE_SETTINGS` (development-level, grantable with
  `adb shell pm grant`, or obtained via Shizuku). FlowPilot writes `Settings.Global.low_power` when the
  app itself holds the permission, or uses Shizuku `cmd power set-mode <0|1>` with settings fallback.

Root is never required anywhere.

## Architecture (plain, readable)

```
app/src/main/java/com/flowpilot/app/
  FlowPilotApp.kt                    Application class, notification channel
  MainActivity.kt                    Edge-to-edge Compose host
  ui/
    theme/                          Color/Theme (dark-first, neutral M3)
    screens/
      HomeScreen.kt                  list + FAB
      CreateScreen.kt                WHEN -> DO flow and app picker
      DetailScreen.kt                rule detail and delete
      PermissionsScreen.kt           setup wizard
      SettingsScreen.kt
    components/                      toggle, cards, picker controls
  data/
    model/Automation.kt              kotlinx.serialization data model
    AutomationRepository.kt          DataStore persistence (Flow)
  engine/
    AutomationEngine.kt              foreground/schedule evaluate -> execute loop + dedupe
    RuleEvaluator.kt                 pure logic (unit-testable)
    ScheduleEvaluator.kt             pure schedule matching
    ForegroundReducer.kt             foreground transition batch reduction
    ForegroundAppTracker.kt          UsageStatsManager polling
    AutomationService.kt             foreground service
    BootReceiver.kt                  restart on boot
  actions/
    ActionExecutor.kt                interface + dispatch
    NfcExecutor.kt                   Shizuku `svc nfc enable|disable`
    PowerSaverExecutor.kt            WRITE_SECURE_SETTINGS direct OR Shizuku `cmd power set-mode`
    ShizukuShell.kt                  Shizuku connection + run shell command via UserService
  permission/
    CapabilityManager.kt             per-action and setup checks
tests (Robolectric + Truth) for rule/schedule matching, foreground reduction, and action executors.
```

## Engine loop

1. AutomationEngine polls foreground events and schedules every 500 ms.
2. On foreground package change -> report `AppOpened(pkg)` / `AppClosed(pkg)` event.
3. RuleEvaluator matches enabled rules whose trigger app == pkg and event matches.
4. For each match, check `lastTriggeredAt`/active-lock dedupe (a rule for "app opened" fires once
   per open, not while app stays foreground).
5. Execute actions via capability-aware executors. Battery Saver uses direct access when available or Shizuku fallback.
6. Update lastTriggeredAt, persist.

## Battery / reliability

- Engine runs in a special-use foreground service with an ongoing notification
  (explains why it is running).
- BootReceiver restarts engine after reboot / app update.
- Foreground detection and schedule matching use a 500 ms polling interval. No partial wake lock is held.
- Battery-optimization exemption and HyperOS Autostart setup are needed for reliable scheduled execution.

## Build / verify

- `./gradlew assembleDebug` -> APK at app/build/outputs/apk/debug/app-debug.apk
- `./gradlew testDebugUnitTest` (Robolectric) and `./gradlew lintDebug`.
- No TODO placeholders for core paths; compile is the completion gate.
