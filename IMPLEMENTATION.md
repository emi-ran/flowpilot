# FlowPilot — Implementation Plan

Real native Android automation app. Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL.
Builds on a proven toolchain: Gradle 9.5.0, AGP 9.3.2, Kotlin 2.2.10, compileSdk 36 (Android 16),
minSdk 26, JDK 17.

## Feature set

Automation rules: WHEN [app opened | app closed | charger connected | charger disconnected | battery below |
battery above | screen on | screen off | scheduled time | Wi-Fi connected | Wi-Fi disconnected | notification received] (AND optional conditions: battery, charger, screen, Wi-Fi) DO one or more [NFC on | NFC off | Battery Saver on |
Battery Saver off | Dark theme on | Dark theme off | Auto-rotate on | Auto-rotate off | Do Not Disturb on | Do Not Disturb off | Sound profile normal/vibrate/silent | create alarm | start timer | Send HTTP webhook | show notification | vibrate | play sound | set media volume | launch app | open URL | Speak text (offline TTS)] actions. Rules may also be manually test-run from Edit automation; this bypasses trigger and conditions without changing rule state. Schedules support daily, weekdays, or selected days. Engine detects foreground apps via
UsageStatsManager, Wi-Fi transitions via ConnectivityManager/NetworkCallback, notification arrivals via NotificationListenerService, and charger/battery transitions via Android broadcasts, evaluates enabled rules and conditions, executes
each schedule occurrence once, and restarts on boot/app update when the engine-startup preference is enabled.

Wi-Fi rules persist only user-selected SSIDs. Users may type an SSID or request a one-shot nearby-network scan; scan results are transient, deduplicated, and never persisted. Android throttles scan frequency and may return cached results. The tracker reads SSID from Wi-Fi-specific `NetworkCallback` capabilities instead of `activeNetwork`, so Xiaomi can detect Wi-Fi transitions even when cellular remains the default data network.

## Capability matrix (verified against Android 16 / HyperOS constraints)

| Action            | Plain app           | ADB (WRITE_SECURE_SETTINGS) | Shizuku             | Root    |
|-------------------|---------------------|------------------------------|---------------------|---------|
| Detect app        | Yes (Usage Access)  | -                            | -                   | -       |
| Auto-rotate       | YES (WRITE_SETTINGS special access) | -                    | -                   | -       |
| Do Not Disturb    | YES (Notification Policy Access) | -                | -                   | -       |
| Sound Profile     | YES (Notification Policy Access) | -                | -                   | -       |
| Alarm / Timer     | YES (Standard Clock intents / SET_ALARM) | -        | -                   | -       |
| Dark Theme        | NO                  | NO (needs system uimode)     | YES (`cmd uimode night yes\|no`) | YES |
| Battery Saver     | NO                  | YES (`pm grant` + write global low_power) | YES (`settings put global low_power`) | YES |
| NFC               | NO (API 29+ removed NfcAdapter.enable) | NO (needs shell uid) | YES (`svc nfc enable|disable`) | YES |

- Auto-rotate toggling modifies `Settings.System.ACCELEROMETER_ROTATION` (1 for on, 0 for off / portrait lock).
  It uses Android's standard user-grantable special app access `android.permission.WRITE_SETTINGS` checked via
  `Settings.System.canWrite(context)` and opened with `Settings.ACTION_MANAGE_WRITE_SETTINGS`.
- Do Not Disturb toggling uses `NotificationManager.setInterruptionFilter` (`INTERRUPTION_FILTER_NONE` for on,
  `INTERRUPTION_FILTER_ALL` for off). It requires Android's standard user-grantable `android.permission.ACCESS_NOTIFICATION_POLICY`
  special access checked via `NotificationManager.isNotificationPolicyAccessGranted` and opened with
  `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`.
- Alarm creation and Timer start use Android's standard `AlarmClock.ACTION_SET_ALARM` and `AlarmClock.ACTION_SET_TIMER`
  intents resolved and launched with `FLAG_ACTIVITY_NEW_TASK`. `CREATE_ALARM` omits `EXTRA_SKIP_UI` to allow confirmation, while `START_TIMER` sets `EXTRA_SKIP_UI = true` for background timer execution.
- NFC toggling is a privileged action on Android 10+; `NfcAdapter.enable()/disable()` exist but are
  restricted to system/DPC. Normal apps can only redirect the user to NFC settings. On Xiaomi 15T Pro /
  HyperOS 3, FlowPilot uses Shizuku to run `svc nfc enable|disable` as shell. `cmd nfc` crashed the target
  device NFC service during testing and is not used.
- Battery Saver is gated by `android.permission.WRITE_SECURE_SETTINGS` (development-level, grantable with
  `adb shell pm grant`, or obtained via Shizuku). FlowPilot writes `Settings.Global.low_power` when the
  app itself holds the permission, or uses Shizuku `cmd power set-mode <0|1>` with settings fallback.
- Dark theme toggling uses Shizuku to execute `cmd uimode night yes` (dark theme on) or `cmd uimode night no`
  (dark theme off). The executor reads `Settings.Secure.ui_night_mode` (2 for dark, 1 for light) to verify
  the resulting state so success is never falsely reported.

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
      CreateScreen.kt                WHEN -> DO flow, app picker, and IME-safe form scrolling
      DetailScreen.kt                rule detail, manual run test action, delete, and IME-safe form scrolling
      PermissionsScreen.kt           setup wizard
      SettingsScreen.kt
    components/                      toggle, cards, picker controls, focus-gated bring-into-view modifier
  data/
    model/Automation.kt              kotlinx.serialization data model with encrypted secret mapping
    security/SecretCipher.kt         Android Keystore AES-256-GCM authenticated encryption at rest
    AutomationRepository.kt          DataStore persistence (Flow) with automatic crypto migration
  engine/
    AutomationEngine.kt              foreground/charger/battery/schedule evaluate -> execute loop + dedupe
    RuleEvaluator.kt                 pure logic (unit-testable)
    ScheduleEvaluator.kt             pure schedule matching
    ForegroundReducer.kt             foreground transition batch reduction
    ForegroundAppTracker.kt          UsageStatsManager polling
    ChargerStateTracker.kt           power connected/disconnected broadcasts
    BatteryLevelTracker.kt           battery level transitions
    ScreenStateTracker.kt            screen on/off broadcasts
    WifiStateTracker.kt              Wi-Fi NetworkCallback state + SSID transition reducer
    FlowPilotNotificationListener.kt transient notification listener and dedupe
    AutomationService.kt             foreground service
    BootReceiver.kt                  restart on boot
  actions/
    ActionExecutor.kt                interface + dispatch
    NfcExecutor.kt                   Shizuku `svc nfc enable|disable`
    DarkThemeExecutor.kt             Shizuku `cmd uimode night yes|no`
    PowerSaverExecutor.kt            WRITE_SECURE_SETTINGS direct OR Shizuku `cmd power set-mode`
    AutoRotateExecutor.kt            WRITE_SETTINGS direct write to Settings.System.ACCELEROMETER_ROTATION
    DndExecutor.kt                   NotificationManager.setInterruptionFilter (Notification Policy Access)
    SoundProfileExecutor.kt          AudioManager.ringerMode (Notification Policy Access)
    ClockExecutor.kt                 AlarmClock.ACTION_SET_ALARM and ACTION_SET_TIMER intent dispatch
    NotificationExecutor.kt           visible user-configured automation alerts
    VibrationExecutor.kt              configurable waveform vibration
    SoundExecutor.kt                  selected system/custom sound playback with bounded duration
    MediaVolumeExecutor.kt            percentage-to-music-stream volume mapping
    LaunchExecutor.kt                 selected app or validated HTTP(S) URL activity launch
    WebhookExecutor.kt                outbound HTTP/HTTPS webhook request with bounded timeout, secret redaction, and header/body template expansion
    WebhookTemplateRenderer.kt        pure one-pass header/body substitution for known webhook variables
    TtsManager.kt                     offline voice discovery and pre-synthesized cache management
    TtsExecutor.kt                    offline cached TTS playback
    ShizukuShell.kt                  Shizuku connection + run shell command via UserService
  permission/
    CapabilityManager.kt             per-action and setup checks
tests (Robolectric + Truth) for rule/charger/battery/schedule matching, foreground reduction, encrypted persistence, webhook templates, manual execution summaries, and action executors.
```

## Engine loop

1. AutomationEngine polls foreground events, queued charger/battery broadcasts, and schedules every 500 ms.
2. On foreground package change -> report `AppOpened(pkg)` / `AppClosed(pkg)` event.
3. RuleEvaluator matches enabled rules whose trigger app == pkg and event matches.
4. For each match, check `lastTriggeredAt`/active-lock dedupe (a rule for "app opened" fires once
   per open, not while app stays foreground).
5. Execute actions via capability-aware executors. Battery Saver uses direct access when available or Shizuku fallback.
6. Update lastTriggeredAt, persist.

ChargerStateTracker registers only while the engine runs. It queues `ACTION_POWER_CONNECTED` and
`ACTION_POWER_DISCONNECTED`, dedupes consecutive identical states, and does not query current charger state
at startup. This prevents an existing cable connection from replaying an action after service restart.

BatteryLevelTracker seeds current percentage from sticky `ACTION_BATTERY_CHANGED` at engine start without
queuing an event. It evaluates only later percentage crossings: above to at-or-below a below-threshold, or
below to at-or-above an above-threshold. A level remaining beyond threshold cannot retrigger an action.

ScreenStateTracker registers `ACTION_SCREEN_ON` and `ACTION_SCREEN_OFF` only while the engine runs,
dedupes consecutive events, and does not read or replay current screen state when it starts.

WifiStateTracker seeds the connected SSID at engine start without replaying it. Subsequent Wi-Fi-specific
`NetworkCallback` capability changes emit deduplicated connected/disconnected transitions. It does not use
the default network to derive an SSID because Xiaomi may retain cellular as default while Wi-Fi is connected.
SSID scans are user-initiated only; their results remain transient. Location permission, device Location, and
on Android 13+ Nearby devices access are required for reliable SSID reading/scanning.

FlowPilotNotificationListener receives notification posts only after the user grants Notification Listener
access. It creates an in-memory event with package, title, and text only long enough to match the selected
package and optional keyword, dedupes by post key/time, and never persists or logs notification content.

AutoRotateExecutor writes `Settings.System.ACCELEROMETER_ROTATION` to `1` (free rotation) or `0`
(portrait lock), then reads back the value. It requires `android.permission.WRITE_SETTINGS` checked via
`Settings.System.canWrite(context)` and returns explicit honest failures for permission blocks or write mismatches.

DndExecutor sets `NotificationManager.setInterruptionFilter` to `INTERRUPTION_FILTER_NONE` (total silence) or
`INTERRUPTION_FILTER_ALL` (all interruptions), then verifies `currentInterruptionFilter`. It requires
`android.permission.ACCESS_NOTIFICATION_POLICY` checked via `NotificationManager.isNotificationPolicyAccessGranted`
and fails with clear messages if permission is missing, state mismatches, or system throws.

ClockExecutor dispatches `AlarmClock.ACTION_SET_ALARM` with `EXTRA_HOUR`, `EXTRA_MINUTES`, and optional `EXTRA_MESSAGE` (without `EXTRA_SKIP_UI`),
or `AlarmClock.ACTION_SET_TIMER` with `EXTRA_LENGTH` (1s-24h), optional `EXTRA_MESSAGE`, and `EXTRA_SKIP_UI = true` for background timer starts. Both use `FLAG_ACTIVITY_NEW_TASK`
and resolve the handling component before starting. Result reports request dispatched to the system Clock app.

NotificationExecutor posts title/body configured on the rule to `automation_alerts_v2` at high importance.
Android preserves channel importance after creation, so channel IDs are versioned when alert behavior changes.
Android 13+ notification permission is required; OEM notification settings can still suppress banners.

LaunchExecutor uses a selected package's launcher intent or `ACTION_VIEW` for an absolute `http` or `https`
URL. Both intents carry `FLAG_ACTIVITY_NEW_TASK` because the automation engine runs outside an activity.
Launch failure is logged and returned to the engine; target app removal, missing URL resolver, and OEM
background-activity restrictions remain explicit failure cases.

WebhookExecutor dispatches HTTP/HTTPS requests via standard `HttpURLConnection`. Validates strict `http` or `https` schemes with host, enforces bounded timeouts (1-60s), renders known variables in headers/body only, sets headers and request body, and considers strictly HTTP 2xx status codes as success. URL templates are excluded because URL encoding context differs; unknown and malformed variables are preserved and rendering is non-recursive. Sensitive headers (`Authorization`, `Cookie`, tokens, secrets) and sensitive parameter values are redacted from log entries and execution failure messages to prevent credential leakage.

Manual test runs execute a saved rule's effective actions on `Dispatchers.IO`, bypassing its trigger and conditions without altering `enabled` or `lastTriggeredAt`. The manual webhook context uses `MANUAL` as its trigger and reads current battery, charger, and Wi-Fi state; result summaries redact sensitive error values before reaching UI.

MediaVolumeExecutor converts stored 0-100% configuration to the current device's `STREAM_MUSIC` range,
sets volume without a system UI overlay, then reads the resulting level. A mismatch reports failure rather
than claiming a blocked volume change succeeded.

SoundExecutor plays the device's current notification, alarm, ringtone, or a persistently permitted
document-picker audio URI. Create and edit screens show source duration when metadata is available,
then preview and execute only the configured first 1-60 seconds. Preview owns one active player: a new
preview stops the old one, Stop preview releases it, and Compose disposal stops it when leaving the screen.

TtsManager filters TextToSpeech voice inventory for offline-ready voices (`isNetworkConnectionRequired = false`).
During rule creation/editing, the user configures text, offline voice, and speech rate. Synthesis writes a WAV
file to the app-private `files/tts_cache` directory. Save is gated until a valid synthesized cache file matching
the exact text/voice/rate configuration exists. TtsExecutor plays the pre-synthesized audio directly using
MediaPlayer without triggering synthesis or network requests at rule execution time. Orphan cache files are
cleaned up automatically when rules are modified or removed. The picker filters by voice name, BCP-47 locale,
and localized language name; reopening it scrolls to the selected voice. A hold gesture synthesizes and plays a
temporary preview for that row without changing the selected voice. Preview requires nonblank spoken text and
shows an in-picker instruction when text is missing.

## Battery / reliability

- Engine runs in a special-use foreground service with an ongoing notification
  (explains why it is running).
- BootReceiver restarts engine after reboot / app update.
- Foreground detection and schedule matching use a 500 ms polling interval. No partial wake lock is held.
- Battery-optimization exemption and HyperOS Autostart setup are needed for reliable scheduled execution.

## Build / verify

- `.\gradlew.bat assembleDebug --no-daemon` -> APK at app/build/outputs/apk/debug/app-debug.apk
- `.\gradlew.bat testDebugUnitTest --no-daemon` (Robolectric) and `.\gradlew.bat lintDebug --no-daemon`.
- Per `AGENTS.md`, the primary agent runs Gradle verification; after a successful build it installs the debug APK on the connected target and launches `com.flowpilot.app`. Subagents do not compile, test, build, install, or launch.
- No TODO placeholders for core paths; compile is the completion gate.
