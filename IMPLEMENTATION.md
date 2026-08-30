# FlowPilot — Implementation Plan

Real native Android automation app. Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL.
Builds on a proven toolchain: Gradle 9.5.0, AGP 9.3.2, Kotlin 2.2.10, compileSdk 36 (Android 16),
minSdk 24, JDK 17.

## Feature set

Automation rules: WHEN [app opened | app closed] DO [NFC on | NFC off | Battery Saver on | Battery Saver off],
optional ONLY IF condition (future). Engine detects foreground app via UsageStatsManager, evaluates enabled
rules, executes actions once per event, dedupes while app stays foreground, survives lifecycle/reboot.

## Capability matrix (verified against Android 16 / HyperOS constraints)

| Action            | Plain app           | ADB (WRITE_SECURE_SETTINGS) | Shizuku             | Root    |
|-------------------|---------------------|------------------------------|---------------------|---------|
| Detect app        | Yes (Usage Access)  | -                            | -                   | -       |
| Battery Saver     | NO                  | YES (`pm grant` + write global low_power) | YES (`settings put global low_power`) | YES |
| NFC               | NO (API 29+ removed NfcAdapter.enable) | NO (needs shell uid) | YES (`cmd nfc on/off`) | YES |

- NFC toggling is a privileged action on Android 10+; `NfcAdapter.enable()/disable()` exist but are
  restricted to system/DPC. Normal apps can only redirect the user to NFC settings. FlowPilot uses
  Shizuku to run `cmd nfc on|off` as shell — the honest, root-free path.
- Battery Saver is gated by `android.permission.WRITE_SECURE_SETTINGS` (development-level, grantable with
  `adb shell pm grant`, or obtained via Shizuku). FlowPilot writes `Settings.Global.low_power` when the
  app itself holds the permission (ADB path) or runs `settings put global low_power` under Shizuku.

Root is never required anywhere.

## Architecture (plain, readable)

```
app/src/main/java/com/flowpilot/app/
  FlowPilotApp.kt                    Application class, notification channel
  MainActivity.kt                    Edge-to-edge Compose host
  ui/
    theme/                          Color/Theme (dark-first, neutral M3)
    AppNav.kt                        Navigation routes
    screens/
      AutomationsScreen.kt           list + FAB
      CreateAutomationScreen.kt      WHEN -> DO flow
      ChooseTriggerScreen.kt
      ChooseActionScreen.kt
      AppPickerScreen.kt             installed launchable apps, search
      AutomationDetailScreen.kt
      PermissionsScreen.kt           setup wizard
      SettingsScreen.kt
    Components.kt                    toggle, cards, when/do blocks
  data/
    model/Automation.kt              kotlinx.serialization data model
    AutomationRepository.kt          DataStore persistence (Flow)
  engine/
    AutomationEngine.kt              event -> rule evaluate -> execute loop + dedupe
    RuleEvaluator.kt                 pure logic (unit-testable)
    ForegroundAppTracker.kt          UsageStatsManager polling
    AutomationService.kt             foreground service
    BootReceiver.kt                  restart on boot
  actions/
    ActionExecutor.kt                interface + dispatch
    NfcExecutor.kt                   Shizuku `cmd nfc on|off`
    PowerSaverExecutor.kt            WRITE_SECURE_SETTINGS direct OR Shizuku `settings put global low_power`
    ShizukuShell.kt                  Shizuku connection + run shell command via UserService
  permission/
    CapabilityManager.kt             per-action status: Available / Permission required / Shizuku required / Unsupported
    Permissions.kt                   wrappers (Usage Access, WRITE_SECURE_SETTINGS check, Shizuku)
tests (Robolectric + Truth) for RuleEvaluator, model serialization, capability status logic.
```

## Engine loop

1. ForegroundAppTracker polls UsageStatsManager every ~1500ms (bounded, battery-aware).
2. On foreground package change -> report `AppOpened(pkg)` / `AppClosed(pkg)` event.
3. RuleEvaluator matches enabled rules whose trigger app == pkg and event matches.
4. For each match, check `lastTriggeredAt`/active-lock dedupe (a rule for "app opened" fires once
   per open, not while app stays foreground).
5. Execute actions via the capability-aware executors (only if that action's capability is Available).
6. Update lastTriggeredAt, persist.

## Battery / reliability

- Engine runs in a special-use foreground service with an ongoing notification
  (explains why it is running).
- BootReceiver restarts engine after reboot / app update.
- Foreground detection via UsageStats uses a modest, adaptive poll interval; service holds a
  partial wake lock only during work to avoid draining battery between polls.
- Battery-optimization exemption is optional, offered in the permissions screen.

## Build / verify

- `./gradlew assembleDebug` -> APK at app/build/outputs/apk/debug/app-debug.apk
- `./gradlew testDebugUnitTest` (Robolectric) and `./gradlew lintDebug`.
- No TODO placeholders for core paths; compile is the completion gate.
