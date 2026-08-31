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
- Create rule flow: app opened/closed, charger connected/disconnected, battery threshold, screen on/off, Wi-Fi connected/disconnected (selected SSID), notification received (selected app + optional keyword), or scheduled time -> one or more NFC on/off, Battery Saver on/off, Auto-rotate on/off, Do Not Disturb on/off, Create alarm, Start timer, notification, vibrate, play sound, set media volume, launch app, open URL, and Speak text (offline TTS) actions.
- Rule conditions (AND semantics): battery below/above, charger connected/disconnected, screen on/off, Wi-Fi connected/disconnected. Rules execute only when trigger matches AND all configured conditions match current live state.
- Installed launchable app picker with search, display name, package ID internally.
- Trigger and action pickers: searchable icon cards grouped by purpose. Triggers use App, Power, Display, Time, Network, and Notification; actions use Alerts, Clock, Audio, Apps & Links, Display, Battery, and NFC.
- Rule detail and delete.
- Persistent rules through DataStore JSON.
- Enable/disable switches.
- Foreground automation service with visible ongoing notification.
- UsageStatsManager event polling every 500 ms.
- App transition detection: one open event per foreground residency, one close event on transition.
- Time schedules: daily, weekdays, or selected days; one execution per matching minute with no past-occurrence replay after engine start.
- Charger triggers: connected/disconnected broadcasts while engine runs; duplicate state broadcasts are deduped and current charger state is not replayed after engine start.
- Battery threshold triggers: below/above a selected percentage; only a crossing triggers an action and current level is seeded without replay after engine start.
- Screen triggers: on/off broadcasts while engine runs; duplicate consecutive broadcasts are deduped and current screen state is not replayed after engine start.
- Show notification action: per-rule title and message, posted through visible `Automation alerts` channel.
- Boot/app-update receiver restarts the engine only when Run engine on device startup is enabled.
- Capability labels: Available, Permission required, Shizuku required, Unsupported on this device.
- Shizuku UserService AIDL command bridge. Commands run with Shizuku shell/root identity; app never claims success if the command failed.
- Action executors:
   - NFC ON/OFF: `svc nfc enable|disable` through Shizuku.
   - Battery Saver ON/OFF: direct `Settings.Global` write with `WRITE_SECURE_SETTINGS`, or Shizuku `cmd power set-mode <0|1>` with settings fallback.
    - Auto-rotate ON/OFF: direct `Settings.System.ACCELEROMETER_ROTATION` write with user-grantable `android.permission.WRITE_SETTINGS` special access.
    - Do Not Disturb ON/OFF: direct `NotificationManager.setInterruptionFilter` (`INTERRUPTION_FILTER_NONE` / `INTERRUPTION_FILTER_ALL`) with user-grantable Notification Policy Access (`android.permission.ACCESS_NOTIFICATION_POLICY`).
    - Create alarm: dispatches `AlarmClock.ACTION_SET_ALARM` with hour, minute, and optional label to the system Clock app (`FLAG_ACTIVITY_NEW_TASK`).
    - Start timer: dispatches `AlarmClock.ACTION_SET_TIMER` with bounded duration (1s-24h), optional label, and `EXTRA_SKIP_UI = true` to start the timer in the background without opening the Clock app UI (`FLAG_ACTIVITY_NEW_TASK`).
    - Launch app: starts selected installed launchable app.
   - Open URL: opens a validated `http` or `https` URL through Android intent resolution.
   - Set media volume: maps configured 0-100% to device music-stream range and verifies resulting level.
   - Play sound: selected current notification, alarm, ringtone, or a user-selected audio file, limited to selected first 1-60 seconds with preview and stop controls.
    - Speak text (TTS): offline-first pre-synthesized audio caching to app-private storage with voice filter (`isNetworkConnectionRequired = false`), rate adjustment, preview/stop lifecycle, and offline playback during rule execution. Search voices by name or language (`Türkçe`, `Turkish`, `tr-TR`); hold a voice row to preview it without selecting it.
- Unit tests for rule matching, schedule matching, foreground reduction, action executors, and disabled rules.

## Setup permissions

### 1. Usage Access

Open FlowPilot -> Settings -> Advanced permissions -> Usage Access -> allow FlowPilot.

Reason: Android does not expose foreground-app changes as a normal runtime permission. Usage Access lets FlowPilot read foreground transition events.

### 2. Notifications

On Android 13+, allow notifications when prompted.

Reason: Android requires a foreground-service status notification. FlowPilot uses a silent, minimum-importance channel with no sound, vibration, badge, or lock-screen content.

The Show notification action uses separate `Automation alerts` channel with heads-up importance. If HyperOS does not show banners, enable floating notifications for this channel in system notification settings.

### 3. Battery restrictions and HyperOS Autostart

Open FlowPilot -> Settings -> Advanced permissions and allow Battery restrictions. Then use HyperOS Autostart -> Open list, enable FlowPilot, and set Battery saver to No restrictions.

Reason: schedules run in a foreground service, but HyperOS can still stop/restrict it. Android has no public API to read or enable HyperOS Autostart. FlowPilot opens HyperOS's Autostart list for manual verification and setup.

### 4. Modify system settings (WRITE_SETTINGS) for Auto-rotate

Open FlowPilot -> Settings -> Advanced permissions -> Modify system settings -> allow FlowPilot.

Reason: Toggling system auto-rotation (`Settings.System.ACCELEROMETER_ROTATION`) requires Android's user-grantable `WRITE_SETTINGS` special app access (`Settings.System.canWrite(context)`), not `WRITE_SECURE_SETTINGS` or Shizuku.

### 5. Do Not Disturb access (Notification Policy Access)

Open FlowPilot -> Settings -> Advanced permissions -> Do Not Disturb access -> allow FlowPilot.

Reason: Changing Do Not Disturb (`NotificationManager.setInterruptionFilter`) requires Android's user-grantable Notification Policy Access (`NotificationManager.isNotificationPolicyAccessGranted`). Standard `POST_NOTIFICATIONS` is insufficient.

### 6. Notification listener access

Open FlowPilot -> Settings -> Advanced permissions -> Notification listener access -> allow FlowPilot.

Reason: Detecting incoming notifications from selected applications requires Android's user-grantable NotificationListenerService access (`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`). FlowPilot uses this strictly to match user-configured package names and optional keywords without persisting or logging notification content.

### 7. Wi-Fi, Nearby devices & Location permissions

Open FlowPilot -> Settings -> Advanced permissions -> Wi-Fi & Location permissions -> allow FlowPilot.

Reason: Android requires `ACCESS_FINE_LOCATION`, `ACCESS_WIFI_STATE`, and device Location enabled to identify connected Wi-Fi SSIDs for Wi-Fi triggers and live condition checks. Android 13+ also requires Nearby devices (`NEARBY_WIFI_DEVICES`) for on-demand nearby-network scans.

Use a Wi-Fi trigger or Wi-Fi condition's scan icon / **Scan nearby** control to select an SSID from nearby scan results, or type it manually. Scans are user-initiated; Android throttles scan frequency and may return recently cached results. FlowPilot persists only the SSID selected for a rule, not scan-result history.

### 8. Battery Saver actions: ADB path

With USB debugging enabled and device connected:

```bash
adb shell pm grant com.flowpilot.app android.permission.WRITE_SECURE_SETTINGS
```

Verify:

```bash
adb shell dumpsys package com.flowpilot.app | grep WRITE_SECURE_SETTINGS
```

This gives FlowPilot direct Battery Saver access. NFC still needs Shizuku.

### 9. Shizuku path

Install Shizuku from its official source:

<https://shizuku.rikka.app/download/>

Start Shizuku through Wireless debugging or USB debugging. Open FlowPilot's permission screen and grant FlowPilot access when Shizuku asks.

Shizuku is required for NFC ON/OFF because normal Android apps cannot toggle NFC on modern Android. It also enables Battery Saver actions when direct ADB access is absent. FlowPilot runs only these narrow commands through its Shizuku UserService:

```text
svc nfc enable
svc nfc disable
cmd power set-mode 1
cmd power set-mode 0
settings put system POWER_SAVE_MODE_OPEN 1
settings put system POWER_SAVE_MODE_OPEN 0
settings put global low_power 1
settings put global low_power 0
```

No root is required. If Shizuku is stopped, the action reports failure and does not fake success.

## Android restrictions and limitations

- Normal apps cannot toggle NFC on Android 10+; `NfcAdapter.enable()` / `disable()` are privileged/system/DPC operations. FlowPilot therefore marks NFC as Shizuku-required.
- Battery Saver is a protected global setting. `WRITE_SECURE_SETTINGS` gives FlowPilot direct access; Shizuku provides a supported fallback action path.
- UsageStatsManager polling is Android-supported but not an instantaneous callback API. Event timing can vary by device and OEM, especially HyperOS.
- Scheduled rules do not need Usage Access. App opened/closed rules still require Usage Access. A rule created at the current or past minute waits for its next valid day; missed times are not replayed after the engine starts.
- Charger rules do not need Usage Access. They listen for Android power connected/disconnected broadcasts only while the engine is running, so they do not fire for a cable already connected at engine startup.
- Battery threshold rules do not need Usage Access. They react only when the level crosses selected threshold; a battery level already above or below threshold at engine startup does not trigger an action.
- Show notification needs `POST_NOTIFICATIONS` on Android 13+. The action reports failure when permission is denied. Android/HyperOS channel settings can still suppress a heads-up banner.
- Do Not Disturb on/off requires Notification Policy Access (`android.permission.ACCESS_NOTIFICATION_POLICY`). Normal notification permissions cannot change DND filter state.
- Wi-Fi connected/disconnected triggers and Wi-Fi conditions require Location permission plus device Location enabled to read SSIDs. On Android 13+, nearby-network scanning also requires Nearby devices permission. FlowPilot derives SSID from Wi-Fi-specific `NetworkCallback` capabilities, so it continues to detect Wi-Fi while cellular data is the default network.
- Notification-received triggers require Notification Listener access. Notification title/text is matched only in memory against the selected package and optional keyword; it is never persisted or logged. Notification post key/time dedupe prevents replaying the same post.
- Create alarm dispatches `AlarmClock.ACTION_SET_ALARM` without `EXTRA_SKIP_UI`, allowing the system Clock app to present confirmation or UI as needed. Start timer dispatches `AlarmClock.ACTION_SET_TIMER` with `EXTRA_SKIP_UI = true` to request background timer start without opening the Clock app UI. Both actions start an activity from the foreground service with `FLAG_ACTIVITY_NEW_TASK` and are subject to Android and HyperOS background activity start policies.
- Launch app requires an installed launchable target. Open URL accepts only absolute `http` or `https` URLs with a host. Both actions start a new activity from the foreground service and can be restricted by future Android or OEM background-activity-launch policies.
- Custom Play sound files use Android's document picker and persist read access to the selected URI. Removing or moving access to that source makes the action report failure.
- Play sound preview stops any prior preview, can be stopped manually, and stops when leaving create or edit screen.
- TTS voice preview requires spoken text. The voice picker states this when a voice row is held before text is entered. Reopening the picker scrolls to the selected voice; the selected voice remains unchanged by a hold-to-preview gesture.
- HyperOS Autostart is an OEM-owned setting and must be enabled manually. Battery restriction exemption reduces, but cannot eliminate, OEM service termination.
- `QUERY_ALL_PACKAGES` is declared to provide a complete installed launchable-app picker. Store distribution policy may require justification.
- No AccessibilityService is used. It is not necessary for UsageStats-based app detection and would add broader access than required.
- No root, hidden API calls, silent shell execution, or fake fallback behavior.

## Project structure

```text
app/src/main/java/com/flowpilot/app/
  data/model/                 Serializable rule model
  data/                       DataStore repository
  engine/                     UsageStats, charger, and battery trackers, foreground reducer, schedule/rule evaluators, service, boot receiver
  actions/                    Action executors and Shizuku UserService bridge
  permission/                 Capability and setup checks
  ui/                         Compose screens, state, theme, components
app/src/test/                 Rule, schedule, foreground reducer, and action executor tests
```

## Verification performed

- `./gradlew testDebugUnitTest assembleDebug --no-daemon` — passed.
- Debug APK installed on Xiaomi 15T Pro / HyperOS 3.
- Foreground automation service verified active with silent `engine_silent_v2` notification channel.
- Scheduled-rule persistence and an execution at a future selected time verified on device.
- Charger connected and disconnected rules verified on device.
- Battery below/above threshold rules verified on device.
- Show notification rule verified on device, including visible `automation_alerts_v2` heads-up channel.
- Launch app verified from charger-connected and app-opened rules.
- Open URL verified on Xiaomi 15T Pro / HyperOS 3.
- Vibration implementation builds and has unit coverage; device smoke test remains pending.
- Media volume implementation builds and has unit coverage; device smoke test remains pending.
- Play sound and screen on/off implementations build and have unit coverage; device smoke tests remain pending.
- Speak text (offline TTS) builds, unit tests, and Xiaomi device smoke test passed.
- Auto-rotate on/off (WRITE_SETTINGS) implementation builds and has unit coverage; ADB validation confirmed target device values (0 and 1), while FlowPilot app path device smoke test remains pending.
- Clock create alarm and start timer passed Xiaomi 15T Pro / HyperOS 3 device smoke tests. Start timer uses `EXTRA_SKIP_UI = true` and starts its countdown without opening Clock UI.
- Rule conditions, including AND matching of app, battery, charger, screen, and Wi-Fi state, passed Xiaomi 15T Pro / HyperOS 3 device smoke tests.
- Notification-received trigger passed Xiaomi 15T Pro / HyperOS 3 device smoke tests with selected-app and keyword matching.
- Wi-Fi connected/disconnected triggers passed Xiaomi 15T Pro / HyperOS 3 device smoke tests, including an SSID selected through the nearby-network picker while cellular remained the default network.
- Do Not Disturb on/off (Notification Policy Access) implementation complete with unit tests; device smoke test remains pending.

## Next validation

1. Create `Charger connected -> Set media volume -> 20%`, reconnect charger, and confirm Xiaomi media volume reaches 20%.
2. Repeat media-volume test at 0% and 100%.
3. Create a screen on/off rule with notification, vibration, or sound and confirm it fires only after a new screen-state transition.
4. Verify Play sound presets, custom file, selected duration, and Stop preview on device.
5. Create a TTS rule, select an offline voice, generate and preview audio, disable internet, then trigger the saved rule and confirm cached audio plays.
   - Search for Turkish with `Türkçe`, `Turkish`, `tr`, or `tr-TR`.
   - Hold a voice row to preview it without changing selection; reopen picker and confirm it returns to selected voice.
6. Grant Do Not Disturb access, trigger DND on then off, and confirm both Xiaomi system states change. Deny access and confirm the rule reports failure.
7. NFC and Battery Saver device action paths still require per-action verification after permission changes.

## License / distribution note

Shizuku is an external dependency. Follow its license and distribution guidance when publishing FlowPilot.

## License

Add project license before public distribution.

