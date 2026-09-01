# FlowPilot

Native Android automation app based on supplied Stitch UI. Kotlin, Jetpack Compose, Material 3, DataStore, Coroutines.

## Build

Requirements:

- JDK 17
- Android SDK platform 36 / build-tools 36.0.0
- Gradle wrapper included

```bash
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install:

```bash
adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Package ID: `com.flowpilot.app`
Version: `1.0.0` / versionCode `1`
Min SDK: 26
Target / compile SDK: 36 (Android 16)

## Implemented

- Automations list matching supplied dark Stitch design.
- Create rule flow: app opened/closed, charger connected/disconnected, battery threshold, screen on/off, Wi-Fi connected/disconnected (selected SSID), Bluetooth device connected/disconnected (selected bonded device), NFC tag scanned (selected tag UID), notification received (selected app + optional keyword), or scheduled time -> one or more Bluetooth on/off, NFC on/off, Dark theme on/off, Battery Saver on/off, Auto-rotate on/off, Do Not Disturb on/off, Sound profile (Normal/Vibrate/Silent), Create alarm, Start timer, Send HTTP webhook, notification, vibrate, play sound, set media volume, launch app, open URL, and Speak text (offline TTS) actions.
- Rule conditions (AND semantics): battery below/above, charger connected/disconnected, screen on/off, Wi-Fi connected/disconnected. Rules execute only when trigger matches AND all configured conditions match current live state.
- Installed launchable app picker with search, display name, package ID internally.
- Trigger and action pickers: searchable icon cards grouped by purpose. Triggers use App, Power, Display, Time, Network, Bluetooth, and Notification; actions use Alerts, Clock, Audio, Apps & Links, Display, Battery, Connectivity, and NFC. Connectivity contains Bluetooth on/off through Shizuku.
- Rule detail and delete.
- Automation run history: persistent log of rule executions (engine-triggered and manual test runs) retained up to the newest 100 entries in DataStore. Displays readable local timestamp, rule name, trigger source (`MANUAL` or trigger event name), overall execution status (Success, Partial success, Failure), and individual action outcomes. Sensitive credentials (tokens, query parameters, auth headers) in action messages are safely redacted before persistence, and action parameters/webhook payloads are never persisted in history. Users can view history from Settings -> Run history and clear it with confirmation.
- Manual automation test run: in Edit automation TopAppBar, Play icon prompts confirmation (bypassing triggers/conditions, excluding unsaved edits, leaving rule state untouched), runs actions immediately on IO dispatcher with safe live system context (`trigger = MANUAL`), records a manual history entry, and displays summary feedback via Snackbar.
- Persistent rules through DataStore JSON.
- Enable/disable switches.
- Foreground automation service with visible ongoing notification.
- UsageStatsManager event polling every 500 ms.
- App transition detection: one open event per foreground residency, one close event on transition.
- Time schedules: daily, weekdays, or selected days; one execution per matching minute with no past-occurrence replay after engine start.
- Charger triggers: connected/disconnected broadcasts while engine runs; duplicate state broadcasts are deduped and current charger state is not replayed after engine start.
- Battery threshold triggers: below/above a selected percentage; only a crossing triggers an action and current level is seeded without replay after engine start.
- Screen triggers: on/off broadcasts while engine runs; duplicate consecutive broadcasts are deduped and current screen state is not replayed after engine start.
- Bluetooth device triggers: public ACL connected/disconnected broadcasts while engine runs, matching only selected bonded device address. Per-device consecutive state broadcasts are deduped; no initial connection state is queried or replayed on engine start. Picker never scans or stores paired-device history.
- NFC tag triggers: match a user-selected normalized tag UID. Tag payloads and technologies are never persisted; UID is retained only in the rule needed for matching. NFC scans route to the engine only while it runs.
- Per-action delays: each action can wait 0-300 seconds before it runs. Actions remain sequential in configured order; engine stop cancels a pending delay and records cancellation in run history.
- Per-rule cooldown: choose None, 1m, 5m, 15m, or 60m. After a successful automatic run, matching events are skipped until cooldown expires; manual test runs bypass cooldown without resetting `lastTriggeredAt`.
- Show notification action: per-rule title and message, posted through visible `Automation alerts` channel.
- Boot/app-update receiver restarts the engine only when Run engine on device startup is enabled.
- Capability labels: Available, Permission required, Shizuku required, Unsupported on this device.
- Shizuku UserService AIDL command bridge. Commands run with Shizuku shell/root identity; app never claims success if the command failed.
- Action executors:
    - NFC ON/OFF: `svc nfc enable|disable` through Shizuku.
    - Dark theme ON/OFF: `cmd uimode night yes|no` through Shizuku with `Settings.Secure.ui_night_mode` state verification.
    - Battery Saver ON/OFF: direct `Settings.Global` write with `WRITE_SECURE_SETTINGS`, or Shizuku `cmd power set-mode <0|1>` with settings fallback.
    - Auto-rotate ON/OFF: direct `Settings.System.ACCELEROMETER_ROTATION` write with user-grantable `android.permission.WRITE_SETTINGS` special access.
    - Do Not Disturb ON/OFF: direct `NotificationManager.setInterruptionFilter` (`INTERRUPTION_FILTER_NONE` / `INTERRUPTION_FILTER_ALL`) with user-grantable Notification Policy Access (`android.permission.ACCESS_NOTIFICATION_POLICY`).
    - Sound profile (Normal/Vibrate/Silent): toggles `AudioManager.ringerMode` (`RINGER_MODE_NORMAL` / `RINGER_MODE_VIBRATE` / `RINGER_MODE_SILENT`) with user-grantable Notification Policy Access (`android.permission.ACCESS_NOTIFICATION_POLICY`).
    - Create alarm: dispatches `AlarmClock.ACTION_SET_ALARM` with hour, minute, and optional label to the system Clock app (`FLAG_ACTIVITY_NEW_TASK`).
    - Start timer: dispatches `AlarmClock.ACTION_SET_TIMER` with bounded duration (1s-24h), optional label, and `EXTRA_SKIP_UI = true` to start the timer in the background without opening the Clock app UI (`FLAG_ACTIVITY_NEW_TASK`).
    - Launch app: starts selected installed launchable app.
    - Open URL: opens a validated `http` or `https` URL through Android intent resolution.
    - Send HTTP webhook: dispatches outbound HTTP/HTTPS request (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`) with custom headers, body, dynamic template variable expansion in headers/body only (`${time}`, `${timestamp}`, `${batteryPercent}`, `${isCharging}`, `${wifiSsid}`, `${trigger}`), bounded 1-60s timeout, secret redaction, strict 2xx success verification, and at-rest AES-256-GCM Keystore encryption for webhook URLs, headers, and payloads. URL templates are intentionally unsupported because URL encoding context differs; unknown or malformed variables remain unchanged and expansion is one pass only.
- Security & data protection: Android app backup is disabled (`android:allowBackup="false"`) to prevent credential leakage via ADB/cloud backup snapshots. Keystore keys remain on-device and never export to backups. In-app data migration transparently upgrades legacy plaintext webhook configurations on load and save.
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

### 8. Bluetooth paired-device access

On Android 12+, open FlowPilot -> Settings -> Advanced permissions -> **Bluetooth paired-device access** and allow Nearby devices, or grant permission when selecting Bluetooth trigger device.

Reason: `BLUETOOTH_CONNECT` is required to list bonded devices and read device data from public ACL connect/disconnect broadcasts. FlowPilot uses bonded-device selection only; it does not start discovery, pair devices, or persist device-list history. If selected device is later unpaired, rule remains saved but matches no future ACL event until a bonded device is selected again.

### 9. NFC tag trigger

Turn NFC on. Create or edit an automation, choose **NFC tag scanned**, then tap the tag while FlowPilot is open to fill its UID. Save rule and keep engine running for tag scans to execute actions.

Reason: Android delivers tag discovery through activity intents. FlowPilot keeps only normalized UID needed to match configured rule; it does not read or store NDEF payloads or tag technology data.

### 10. Battery Saver actions: ADB path

With USB debugging enabled and device connected:

```bash
adb -s <device-serial> shell pm grant com.flowpilot.app android.permission.WRITE_SECURE_SETTINGS
```

Verify:

```bash
adb -s <device-serial> shell dumpsys package com.flowpilot.app
```

This gives FlowPilot direct Battery Saver access. NFC still needs Shizuku.

### 11. Shizuku path

Install Shizuku from its official source:

<https://shizuku.rikka.app/download/>

Start Shizuku through Wireless debugging or USB debugging. Open FlowPilot's permission screen and grant FlowPilot access when Shizuku asks.

Shizuku is required for NFC and Bluetooth ON/OFF because normal Android apps cannot reliably toggle these radios on modern Android. It also enables Battery Saver actions when direct ADB access is absent. FlowPilot runs only these narrow commands through its Shizuku UserService:

```text
svc nfc enable
svc nfc disable
svc bluetooth enable
svc bluetooth disable
cmd uimode night yes
cmd uimode night no
cmd power set-mode 1
cmd power set-mode 0
settings put system POWER_SAVE_MODE_OPEN 1
settings put system POWER_SAVE_MODE_OPEN 0
settings put global low_power 1
settings put global low_power 0
pm grant com.flowpilot.app android.permission.WRITE_SECURE_SETTINGS
```

No root is required. Bluetooth on/off uses only `svc bluetooth enable|disable` through Shizuku, then reads `BluetoothAdapter.isEnabled`; command success with mismatched adapter state reports failure. If Shizuku is stopped, action reports failure and does not fake success.

## Android restrictions and limitations

- Normal apps cannot toggle NFC on Android 10+; `NfcAdapter.enable()` / `disable()` are privileged/system/DPC operations. FlowPilot therefore marks NFC as Shizuku-required.
- Normal apps cannot reliably toggle Bluetooth on modern Android. Bluetooth on/off requires active Shizuku access and `BLUETOOTH_CONNECT` on Android 12+; FlowPilot runs only `svc bluetooth enable|disable` and waits up to 5 seconds for adapter readback. This path passed Xiaomi 15T Pro / HyperOS 3 smoke testing.
- Bluetooth device triggers use public `BluetoothDevice.ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED` broadcasts only while engine runs. Android 12+ requires `BLUETOOTH_CONNECT`. No existing connection is synthesized at engine start; selected-device trigger execution passed Xiaomi 15T Pro / HyperOS 3 smoke testing.
- NFC tag triggers require NFC hardware and NFC enabled. A configured rule matches tag UID only; tag UIDs can be cloned on some tag types and must not be treated as authentication. Configured-tag Xiaomi 15T Pro / HyperOS 3 smoke testing passed.
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
  data/model/                 Serializable rule model and action/trigger catalog
  data/security/              Android Keystore secret encryption
  data/                       DataStore repository and legacy secret migration
  engine/                     UsageStats, charger, battery, screen, Wi-Fi, and Bluetooth ACL trackers, reducers/evaluators, service, boot receiver
  actions/                    Action executors, webhook template rendering, and Shizuku UserService bridge
  permission/                 Capability and setup checks
  ui/                         Compose screens, state, manual test run, IME focus visibility, theme, components
app/src/test/                 Rule, schedule, reducer, encryption, template, manual-run, and action executor tests
```

## Verification performed

- `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon` — passed for current Bluetooth source changes.
- Debug APK installed on Xiaomi 15T Pro / HyperOS 3.
- Foreground automation service verified active with silent `engine_silent_v2` notification channel.
- Scheduled-rule persistence and an execution at a future selected time verified on device.
- Charger connected and disconnected rules verified on device.
- Battery below/above threshold rules verified on device.
- Show notification rule verified on device, including visible `automation_alerts_v2` heads-up channel.
- Launch app verified from charger-connected and app-opened rules.
- Open URL verified on Xiaomi 15T Pro / HyperOS 3.
- Screen on/off, vibration, and Play sound passed Xiaomi 15T Pro / HyperOS 3 device smoke testing.
- Media volume implementation has unit coverage and passed Xiaomi 15T Pro / HyperOS 3 device smoke testing.
- Speak text (offline TTS) builds, unit tests, and Xiaomi device smoke test passed.
- Auto-rotate on/off (WRITE_SETTINGS) implementation has unit coverage; ADB validation confirmed target device values (0 and 1), and FlowPilot app path passed Xiaomi 15T Pro / HyperOS 3 device smoke testing.
- Clock create alarm and start timer passed Xiaomi 15T Pro / HyperOS 3 device smoke tests. Start timer uses `EXTRA_SKIP_UI = true` and starts its countdown without opening Clock UI.
- Rule conditions, including AND matching of app, battery, charger, screen, and Wi-Fi state, passed Xiaomi 15T Pro / HyperOS 3 device smoke tests.
- Notification-received trigger passed Xiaomi 15T Pro / HyperOS 3 device smoke tests with selected-app and keyword matching.
- Wi-Fi connected/disconnected triggers passed Xiaomi 15T Pro / HyperOS 3 device smoke tests, including an SSID selected through the nearby-network picker while cellular remained the default network.
- Do Not Disturb on/off (Notification Policy Access) implementation has unit coverage and passed Xiaomi 15T Pro / HyperOS 3 device smoke testing.
- Send HTTP webhook action has unit coverage and passed Xiaomi 15T Pro / HyperOS 3 device smoke testing.
- NFC and Battery Saver action paths passed Xiaomi 15T Pro / HyperOS 3 device smoke testing.
- Dark theme on/off (`cmd uimode night yes|no` through Shizuku) has unit coverage and passed Xiaomi 15T Pro / HyperOS 3 device smoke testing through the FlowPilot rule path.
- Sound profile (Normal/Vibrate/Silent) has unit coverage; Xiaomi 15T Pro maps Vibrate and Silent to the same observed ringer behavior. Other devices can differ.
- Webhook template variables passed Xiaomi 15T Pro / HyperOS 3 device smoke testing in request headers/body, including unknown-token preservation.
- Manual test run passed Xiaomi 15T Pro / HyperOS 3 device smoke testing.
- Bluetooth on/off through Shizuku passed Xiaomi 15T Pro / HyperOS 3 device smoke testing; state changes can settle asynchronously, so executor waits up to 5 seconds for adapter-state readback.
- Bluetooth selected-bonded-device connection trigger passed Xiaomi 15T Pro / HyperOS 3 device smoke testing by executing a configured Battery Saver action.
- NFC tag trigger and per-action delay build and unit tests passed; debug APK installed and launched on Xiaomi 15T Pro / HyperOS 3. Configured NFC tag scan smoke testing passed; delayed-action device smoke testing remains pending.
- Per-rule cooldown build and unit tests passed and Xiaomi 15T Pro / HyperOS 3 smoke testing confirmed a matching trigger runs once, suppresses another matching event during cooldown, then runs again after expiry.
- Create/Edit keyboard behavior passed Xiaomi 15T Pro / HyperOS 3 device smoke testing: keyboard opens only for focused text fields, scroll remains available, and a focused field returns above the IME when typing after it was scrolled out of view.

## Next validation

1. Create Sound profile rules for Normal, Vibrate, and Silent with Do Not Disturb access granted; confirm Xiaomi ringer-mode readback and document that this device maps Vibrate and Silent identically. Deny access and confirm a failure result.
2. Create a TTS rule, select an offline voice, generate and preview audio, disable internet, then trigger the saved rule and confirm cached audio plays.
   - Search for Turkish with `Türkçe`, `Turkish`, `tr`, or `tr-TR`.
   - Hold a voice row to preview it without changing selection; reopen picker and confirm it returns to selected voice.
3. Stop or deny Shizuku, and deny Do Not Disturb access; confirm Dark theme and DND rules report failure instead of success.
4. Grant `BLUETOOTH_CONNECT`, select bonded device, then verify ACL connect/disconnect rules fire once per transition and do not fire on engine restart while already connected. Test unpairing selected device, permission denial, stopped Shizuku, shell failure, and Bluetooth adapter readback mismatch on Xiaomi 15T Pro / HyperOS 3.
5. Create an NFC tag rule, scan selected tag with engine running, and confirm only matching UID executes once. Scan while engine is stopped, a different tag, NFC disabled, and after app relaunch. Add 5-second delay before a visible action; confirm action order, stop-engine cancellation, and history record.

## License / distribution note

Shizuku is an external dependency. Follow its license and distribution guidance when publishing FlowPilot.

## License

Add project license before public distribution.

