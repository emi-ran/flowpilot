# FlowPilot Status

Last updated: 2026-09-14

## Build state

- Debug/release builds, unit tests, and lint passed: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false`.
- Resource contract test passed: `python scripts/test_lint_resource_contracts.py`.
- Latest debug APK was installed and launched on Xiaomi (2506BPN68G) / HyperOS (Android 16).
- Background resilience and Shizuku startup safety fixes verified on connected device.
- Action reordering, live location fetcher, Automation Presets, and Geofencing unit tests (`AutomationRepositoryGeofenceQueueTest`, `GeofenceConfigValidationTest`, `GeofenceDiffTest`, `GeofencePrerequisitesTest`, `LocationDependencyTest`, `RuleEvaluatorGeofenceTest`) implemented and verified.
- Encrypted backup unit coverage verifies full-secret round trips, enabled-state preservation, plaintext non-leakage, wrong-password/tamper rejection, format/version/KDF bounds, single-rule backup, normal-export regression, and cross-device Android Keystore re-encryption.
- History localization unit coverage verifies locale-neutral outcome records, masked SMS result arguments, legacy successful outcome mapping, technical failure fallback, and Turkish automatic rule-name generation.
- Conflict analyzer and pre-save/pre-enable warning implemented: runtime-aligned trigger overlap, opposing state-action matrix, likely/possible confidence, conflict rule inspection with pending-state restoration, and deliberate non-blocking override. GitHub `Build & Test` passed; physical-device validation remains pending.
- GitHub Pages site modularized: split single monolithic `docs/index.html` into external stylesheet (`docs/assets/css/style.css`) and script (`docs/assets/js/app.js`), unified brand favicon (`docs/assets/favicon.svg`), converted brand into accessible home link, compacted desktop footer, and added mobile-first responsive pass (#2, #3).

## Background stability & engine keepalive

- Resolved fatal crash loop on background launch (`IllegalStateException: Not an attached client` / `binder haven't been received` in `ShizukuShell.hasPermission()`).
- `AutomationService` configured with `android:stopWithTask="false"` and `onTaskRemoved` handling to prevent termination when app task is swiped from Recent Apps.
- `FlowPilotNotificationListener` acts as an active watchdog to restart `AutomationService` if killed by system memory pressure.
- `BootReceiver` enhanced with `QUICKBOOT_POWERON` actions for Chinese OEM fast boot.
- Shizuku `CommandUserService` switched from `daemon(true)` to `daemon(false)` to eliminate orphaned zombie shell processes.
- Shizuku automatically optimizes background flags (`cmd deviceidle whitelist` and Xiaomi autostart/background appops `10008`/`10017`).

## Device-verified features

- Time schedules, charger, battery threshold, screen, Wi-Fi, and notification triggers.
- Notifications, app launch, URL opening, alarm, timer, offline TTS, media volume, vibration, Play sound, webhook base action, NFC, Battery Saver, Auto-rotate, Do Not Disturb, and Dark theme actions.
- Webhook header/body template variables and unknown-token preservation.
- Manual in-progress test run from both Create and Edit automation screens, including confirmation, direct-call warnings, immediate execution with on-screen unsaved edits, result feedback, and unchanged rule/store state.
- Persistent run history for engine and manual executions: action-level outcomes, 100-entry newest-first retention, and redacted diagnostics.
- Bluetooth bonded-device connected/disconnected triggers and Bluetooth on/off Shizuku actions, including delayed adapter-state readback.
- Per-rule cooldown: matching trigger runs once, suppresses repeated matching event during cooldown, and runs again after expiry.
- Create/Edit keyboard behavior: keyboard opens only for focused fields, form scroll remains available, and a focused field returns above the IME when typing after manual scrolling.
- Device motion / flip triggers (`DEVICE_FLIPPED_DOWN`, `DEVICE_FLIPPED_UP`):
  - Dual verification: Proximity (NEAR) + Gravity/Accelerometer Z-axis ($Z \le -6.5 m/s^2$) + lateral horizontal stability ($\le 6.0 m/s^2$).
  - 500ms stability debounce prevents spurious triggers during hand rotation; startup seeding prevents immediate execution on engine start.
  - Dynamic sensor registration: sensors completely detached when no flip rules are enabled or when screen turns off (unless `flipScreenOffDetection = true`).
  - Unit tests and Xiaomi 15T Pro / HyperOS 3 physical device smoke tests passed.

## Implemented; device validation pending

- Safe rule duplication from the Home list overflow menu: creates a disabled copy with a new UUID/creation time, resets `lastTriggeredAt` and transient registration state, preserves complete configuration, re-encrypts webhook secrets with fresh Android Keystore ciphertext, and opens the copy in Edit immediately. GitHub unit/build verification and SDK-free static/resource contracts passed; physical-device validation remains pending.
- Time Window (`TIME_BETWEEN`) and Days of the Week (`DAYS_OF_WEEK`) conditions (unit tests passed; device smoke tests pending):
  - Time interval filtering with overnight span support (e.g. 23:00 - 07:00 crossing midnight).
  - Day of week filtering with Daily, Weekdays, Weekends, and custom day toggles.
- Phone call automations family on Xiaomi 15T Pro / HyperOS 3:
  - Triggers: `CALL_RINGING`, `CALL_ANSWERED`, `CALL_OUTGOING`, `CALL_ENDED` (state-only matching; phone-number filtering removed because Android 12+ does not provide outgoing numbers without default-dialer role; legacy filter-configured rules operate as state-only / any-number rules). Device validation for this removal has not been run on device.
  - Actions: `OPEN_DIALER`, `DIAL_NUMBER`, `CALL_NUMBER` (direct call with `CALL_PHONE` permission).
  - Privacy safeguards: UI number masking for dial/call actions, log redaction, zero call-log / contact storage.
- Connectivity & Flashlight actions (unit tests passed; device smoke tests pending):
  - Wi-Fi on/off (`WIFI_ON`, `WIFI_OFF`) via Shizuku `svc wifi enable|disable` with `WifiManager.isWifiEnabled` verification.
  - Mobile Data on/off (`MOBILE_DATA_ON`, `MOBILE_DATA_OFF`) via Shizuku `svc data enable|disable` with `Settings.Global.mobile_data` / `TelephonyManager.isDataEnabled` verification.
  - Airplane Mode on/off (`AIRPLANE_MODE_ON`, `AIRPLANE_MODE_OFF`) via Shizuku `cmd connectivity airplane-mode enable|disable` with `Settings.Global.AIRPLANE_MODE_ON` verification.
  - Flashlight on/off (`TORCH_ON`, `TORCH_OFF`) via direct `CameraManager.setTorchMode` with hardware camera flash detection.
- Action list reordering in Create and Edit screens (`ReorderableActionList`, `ActionCardItem`) with Move Up / Move Down controls; execution preserves exact order and per-action delay sequence; unit tests passed (`ActionsReorderStateTest`).
- Active Location & Background GPS Support (unit tests passed; device smoke test pending):
  - Multi-tier `LocationFetcher`: fresh cache (<60s, <50m) -> 5s active GPS fix timeout -> best cached fallback.
  - Background location permission flow (`ACCESS_BACKGROUND_LOCATION`) with system app settings guidance for "Allow all the time".
  - Foreground service location type (`FOREGROUND_SERVICE_LOCATION`) attached to `AutomationService`.
  - Dynamic template variables: `${location.lat}`, `${location.lng}`, `${location.coords}`, and `${location.maps_url}` in `WebhookTemplateRenderer` and manual run context.
- Location Geofencing triggers (`GEOFENCE_ENTER`, `GEOFENCE_EXIT`) (unit tests passed; device smoke test pending):
  - Hardware-assisted geofencing via Google Play Services `GeofencingClient` with event-driven battery behavior and zero CPU wake-locks while idle.
  - Persistent DataStore event queue (`geofence_event_queue`, max 50 events) with `GeofenceBroadcastReceiver` and `AutomationService` reconciliation.
  - Prerequisites validation (`ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` "Allow all the time", system location services enabled).
  - Strict circular boundary config validation (latitude: -90..90, longitude: -180..180 excluding 0,0; radius: 50..1000m).
  - Lifecycle diffing (`calculateGeofenceDiff`): dynamically adds new geofences, unregisters disabled/deleted rules, and removes-before-re-adding modified rules to prevent request ID collisions.
  - Rule evaluation filtering for `ENTER` vs `EXIT` events (`RuleEvaluator.evaluateGeofence`) with cooldown and live condition checks.
  - Transition coordinate reuse (`resolveExecutionCoordinates`): event coordinates reused directly for location template variables; skips fresh GPS lookup for notification-only or template-driven geofence actions.
  - Engine lifecycle integration: system geofences remain registered during temporary engine restarts while enabled; unregistered only when engine is disabled.
  - Registration diagnostics and status UI on Home screen (`REGISTERED`, `UNREGISTERED`, `TRANSITION_ENTER`, `TRANSITION_EXIT`, `REGISTRATION_FAILED`) and receiver error banners.
- Password-encrypted full backup and restore (unit tests passed; device smoke test pending):
  - AES-256-GCM portable envelope with PBKDF2-HMAC-SHA256 (100,000 iterations), random salt/IV, and six-character minimum password.
  - Encrypted export/share retains full rule data and enabled state; wrong passwords, altered payloads, unsupported versions/formats, and unsafe KDF bounds fail before import mutation.
  - Normal JSON export/share remains sanitized and normal import disables imported rules.
- Localized history results and automatic rule names (unit tests passed; Turkish UI smoke test pending):
  - New records store locale-neutral result codes; known legacy successful results render in current app language.
  - SMS recipients stay masked; raw technical failures remain redacted fallback text.
- Sound profile denied Notification Policy Access behavior.
- Run history screen smoke test on Xiaomi 15T Pro / HyperOS 3.
- NFC tag trigger non-matching/engine-stopped paths.
- Per-action delay timing, order, and engine-stop cancellation on Xiaomi 15T Pro / HyperOS 3.

## Current constraints

- Geofence triggers require Google Play Services, `ACCESS_FINE_LOCATION`, and `ACCESS_BACKGROUND_LOCATION` ("Allow all the time"). If permissions or system location services are missing, registration is withheld and an error is displayed in the UI.
- Geofence prerequisite and registration failures retry with bounded backoff (10s to 60s); config or prerequisite changes retry immediately.
- Geofence radius is bounded between 50m and 1000m in the UI configuration.
- Phone call triggers require `android.permission.READ_PHONE_STATE`. Direct call action (`CALL_NUMBER`) requires `android.permission.CALL_PHONE`. FlowPilot does not request the default dialer role or change the default Phone app.
- Phone numbers are masked in UI and rule summaries (`+905 •••• 567`). Execution history, action results, logcat, and diagnostic messages contain no phone numbers.
- Dark theme, NFC, and Bluetooth on/off require active Shizuku permission. Bluetooth on/off also requires `BLUETOOTH_CONNECT` on Android 12+ and verifies adapter state after exact allowlisted `svc bluetooth enable|disable`.
- Battery Saver needs `WRITE_SECURE_SETTINGS` or Shizuku.
- Webhook secrets are Android Keystore AES-256-GCM encrypted at rest; Android backups are disabled.
- Bluetooth triggers use selected bonded devices, public ACL broadcasts, and `BLUETOOTH_CONNECT` on Android 12+; no discovery, pairing, scan history, or startup replay. Bluetooth device/profile behavior can still differ on other OEMs.
- NFC tag rules match a persisted tag UID, not tag payload. UID is identifier only, not authentication; cloned tags can match. Foreground `NfcAdapter.ReaderCallback` scans remain automatic. Background Android discovery intents are untrusted and can only open a visible confirmation; their UID reaches the engine after the user confirms, never automatically. ReaderMode stays disabled for that NFC-intent activity session, preventing its scan from bypassing confirmation after resume.
- Per-action delay is bounded to 300 seconds in UI. Engine cancellation during delay creates failed run-history record.
- Rule cooldown begins only after successful automatic execution, applies to every automatic trigger, and is bypassed by manual test runs.
- Xiaomi 15T Pro maps Sound profile Vibrate and Silent to the same observed ringer behavior; other devices can differ.
- Encrypted full backups intentionally exclude execution history, transient geofence queue/diagnostics, engine state, Android permissions, Shizuku state, and TTS cache.

## Next validation

See `ROADMAP.md` for exact device smoke-test scenarios.
