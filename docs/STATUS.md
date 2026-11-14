# FlowPilot Status

Last updated: 2026-11-13

## Build state

- Unreleased media automation: play, pause, toggle, next and previous controls can target a media app; playing, paused and track-changed triggers support an optional app filter. Notification access is required. Media observation is demand-driven by enabled rules: sessions present when the tracker starts are silently seeded without events, while a newly observed session already playing after startup produces a playing event. Transient `${media.title}` and `${media.artist}` are not stored in logs or history, although explicitly configured template actions may use them. On 2026-11-12 the JDK 17 gate (`testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false`) and resource contracts passed. The user reported successful phone testing; the exact installed build and scenarios for the final tracker correction were not independently confirmed.
- Song search and service-specific song links target Spotify or YouTube Music. Automatic playback and background launches depend on the app and Android. End-to-end FlowPilot device verification is pending.
- The song-link editor displays the selected service's accepted formats and validates static links immediately. Search and link fields support trigger variables; resolved links are validated at execution, and manual runs do not carry notification event values. This UI change has not been verified on a device.
- YouTube Music links have surrounding whitespace trimmed before validation and launch, including resolved templates. Device verification of this whitespace correction is pending.
- The music editor marks missing or disabled apps and refreshes their state on resume; saving and manual execution require the selected app to be available. Existing and imported rules are preserved. Device verification of this validation is pending.

- History grouping and music/template diagnostics (implemented in the working tree; post-integration unit test re-run and device verification pending):
  - Execution history retains the last 100 runs with sanitized outcomes and only records rules that actually executed; events suppressed by package/keyword filters, unsatisfied conditions, cooldowns, deduplication, or reservation checks do not create history entries. `HistoryScreen` groups non-successful runs that have a non-blank `ruleId` and a single failed action carrying an `ActionDiagnostic` by `(ruleId, actionType, reason)`, leaving multi-action failures ungrouped.
  - Structured action diagnostics (`ActionDiagnostic` / `ActionDiagnosticReason` on `ActionResult` and `ActionExecutionRecord`, backward-compatible nullable JSON) report non-content music-link failure reasons (`MusicLinkValidator.diagnoseInvalidLink`) and bounded numeric metadata (expected vs. observed video ID lengths capped to `0..1000`) without persisting URLs, raw templates, or exception messages.
  - Additional safe reasons cover observed Shizuku availability and permission, relevant Android permissions, system command failures, unavailable or mismatched state, target app or handler availability, invalid input, media sessions, and network timeout or failure. Coverage is branch-specific, not a claim that every failure is classified; unidentified causes retain a generic failure. Older records remain readable and diagnostics exclude sensitive content and raw errors.
  - Recognized webhook failure messages yield only a bounded HTTP response status for display, including the known legacy format; other stored message text is not shown. Device verification is pending.
  - Per-action template diagnostics (`TemplateDiagnostics` / `ActionTemplateDiagnostic` on `ActionExecutionRecord`, defaulting to empty list for legacy JSON) record content-free `TemplateValueState` (`MISSING`, `EMPTY`, `PRESENT`) for `notification.text` and `notification.bigText` together with `hasParseErrors`, `hasUnknownVariables`, `analysisFailed`, and `renderedEmpty` across template-consuming actions in both `AutomationEngine` and `ManualRuleRunner`.
  - Unit tests (`NotificationPackageFilterTest`, `TemplateDiagnosticsTest`, `TemplateDiagnosticHistoryTest`, `MusicExecutorTest`) exist in the working tree; re-running the test suite and contract scripts after the latest integration changes and verifying on a physical device remain pending.
  - The expanded action-reason integration has not yet been verified by a post-integration test/build/lint run or on a device. Earlier validation of history features does not establish coverage for these new branches.
- Automated coverage is JVM-only as of 2026-11-04 (PR #52): the instrumentation suite, the local device runner, and the manual emulator CI job were removed together with the androidx.test/espresso/compose-test dependencies. For Android or build input changes, CI runs `Build & Test` (`testDebugUnitTest`, `lint`, debug/release assembles) plus every SDK-free `scripts/*.py` contract script; other changes can pass the required check without these steps. Device and OEM behavior is verified by hand on the target phone.
- 2.2.1 is a patch release with version code 8 and version name 2.2.1. Its three fixes recover an allowed but unbound notification listener, ignore HyperOS `com.miui.misound` volume/headphone notifications, and explain speech-template variables left over from a previous trigger. Unit coverage includes `NotificationListenerRebindGateTest` and `NotificationPackageFilterTest`. On 2026-11-05 the local JDK 17 gate (`testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false`) and all eight SDK-free contract scripts passed. APK inspection confirmed debug `com.flowpilot.app.debug`/`2.2.1-dev` and unsigned release `com.flowpilot.app`/`2.2.1`, both version code 8. No device was connected, so installation, launch, and the 2.2.1 fixes were not verified on a device.
- First-launch spotlight tutorial shipped on 2026-11-03: a six-step tour (welcome, presets shortcut, empty-state create, engine status card, Settings tab, done) dims the screen, highlights the real anchor for each step, supports Back/Next, and can be skipped. Anchors are a root-coordinate `Rect` registry in `ui/tutorial/TutorialAnchors.kt` (`LocalTutorialAnchors`, `Modifier.tutorialAnchor`), the overlay and scrim hole live in `ui/tutorial/TutorialOverlay.kt`, `AppViewModel` exposes `tutorialVisible`/`startTutorial`/`finishTutorial`/`requestTutorialPresets`, and the final step can hand off to the preset sheet. Completion is stored as the `tutorial_completed` key in `AutomationRepository`, so the tour auto-opens only on first launch and can be replayed from the Settings row. Verification: `python scripts/test_lint_resource_contracts.py` 7/7 and `python scripts/test_locale_contracts.py` 5/5; JDK 17 gate `testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false` was BUILD SUCCESSFUL with 500 unit tests. Debug-APK smoke on Xiaomi 2506BPN68G covered all six steps, opening the preset sheet from the last step, no automatic re-show after restart, Settings replay, and Skip. Known limits: the create step is skipped when rules already exist, tapping the scrim or the card advances a step, and this gate requires JDK 17 because JDK 25 breaks the Robolectric/ASM toolchain.
- Debug builds use `com.flowpilot.app.debug`, label **FlowPilot Dev**, `versionName` suffix `-dev`, and a green/mint launcher icon; release remains `com.flowpilot.app` with `2.2.1`. The debug app skips release update checks because a release APK cannot replace a debug-signed package. Gate passed on 2026-11-03 with JDK 17 (`testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false`); at 2.0.0, APK inspection showed debug `com.flowpilot.app.debug`/`2.0.0-dev`/`FlowPilot Dev` and release `com.flowpilot.app`/`2.0.0`/`FlowPilot`. Installed on Xiaomi 15T Pro: App info showed the debug icon, label, and `2.0.0-dev`; the app launched and Settings showed no Updates card. Shizuku/notification permissions for the new package were not granted in this check.
- In-app update checks implemented: optional daily GitHub Releases check, manual check, notes dialog with skip/remind, trusted APK download, DownloadManager completion receiver, and installer notification. Unit tests cover version parsing, release parsing/URL trust, prompt policy, notes formatting, and filename sanitization. Xiaomi 15T Pro (Android 16) preview smoke test: auto check showed v2.0.0, Skip removed the banner, manual check re-showed the skipped version, Download & Install downloaded the 2.6 MB GitHub APK, and the system installer opened at its confirmation screen. Update checker gate passed on 2026-11-03: `testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false` plus `python scripts/test_lint_resource_contracts.py`. Not verified: installing a real 1.1.0 → 2.0.0 signature path, blocked background-activity notification fallback, or signed release build.
- Hotspot actions branch passed `testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false` plus `python scripts/test_lint_resource_contracts.py` on 2026-11-02. The R8-minified release APK retains `HotspotBridge`.
- 2.0.0 release gate passed on 2026-11-02: `testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false` and `python scripts/test_lint_resource_contracts.py`. Version code is 5, version name is 2.0.0.
- 2.1.0 release gate passed on 2026-11-03 with JDK 17: `testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false` and `python scripts/test_lint_resource_contracts.py`. Version code is 6, version name is 2.1.0. APK inspection showed debug `com.flowpilot.app.debug`/`2.1.0-dev` and release `com.flowpilot.app`/`2.1.0`.
- 2.2.0 release gate passed on 2026-11-04 with JDK 17: `testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false` and `python scripts/test_lint_resource_contracts.py`. Version code is 7, version name is 2.2.0. APK inspection showed debug `com.flowpilot.app.debug`/`2.2.0-dev` and release `com.flowpilot.app`/`2.2.0`.
- Security advisory remediation passed local release gate on 2026-09-14: `testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false`. Debug APK device install remains blocked because wireless ADB target is offline.
- Debug/release builds, unit tests, and lint passed: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease -PreleaseSigningRequired=false`.
- Resource contract test passed: `python scripts/test_lint_resource_contracts.py`.
- Latest debug APK was installed and launched on Xiaomi (2506BPN68G) / HyperOS (Android 16).
- Background resilience and Shizuku startup safety fixes verified on connected device.
- Action reordering, live location fetcher, Automation Presets, and Geofencing unit tests (`AutomationRepositoryGeofenceQueueTest`, `GeofenceConfigValidationTest`, `GeofenceDiffTest`, `GeofencePrerequisitesTest`, `LocationDependencyTest`, `RuleEvaluatorGeofenceTest`) implemented and verified.
- Encrypted backup unit coverage verifies full-secret round trips, enabled-state preservation, plaintext non-leakage, wrong-password/tamper rejection, format/version/KDF bounds, single-rule backup, normal-export regression, and cross-device Android Keystore re-encryption.
- History localization unit coverage verifies locale-neutral outcome records, masked SMS result arguments, legacy successful outcome mapping, technical failure fallback, and Turkish automatic rule-name generation.
- Service locale and notification refresh contract verified: persisted app locale drives foreground engine and startup-failure notification channels and text across boot, service restart, process recreation, and task removal; language switch refreshes active notifications and channel metadata immediately (#23).
- Conflict analyzer and pre-save/pre-enable warning implemented: runtime-aligned trigger overlap, opposing state-action matrix, likely/possible confidence, conflict rule inspection with pending-state restoration, and deliberate non-blocking override. GitHub `Build & Test` and physical-device validation passed.
- GitHub Pages site modularized: split single monolithic `docs/index.html` into external stylesheet (`docs/assets/css/style.css`) and script (`docs/assets/js/app.js`), unified brand favicon (`docs/assets/favicon.svg`), converted brand into accessible home link, compacted desktop footer, and added mobile-first responsive pass (#2, #3).
- Supply-chain hardening (#22): the release workflow now creates a GitHub build provenance attestation for the signed APK after signer/checksum verification and before upload, with `id-token`/`attestations` write scoped to that job. `settings.gradle.kts` content-filters `google()`. Gradle dependency verification metadata is generated by a maintainer and, once committed, runs in Gradle's default strict mode so local and CI builds fail on a missing or mismatched artifact checksum. User verification is documented in `README.md`, `README.tr.md`, and `docs/SUPPLY_CHAIN.md`. Metadata generation and the release workflow itself are not exercised by this change set and remain for the release run.

## Background stability & engine keepalive

- Resolved fatal crash loop on background launch (`IllegalStateException: Not an attached client` / `binder haven't been received` in `ShizukuShell.hasPermission()`).
- `AutomationService` configured with `android:stopWithTask="false"` and `onTaskRemoved` handling to prevent termination when app task is swiped from Recent Apps.
- `FlowPilotNotificationListener` acts as an active watchdog to restart `AutomationService` if killed by system memory pressure.
- `BootReceiver` enhanced with `QUICKBOOT_POWERON` actions for Chinese OEM fast boot.
- Shizuku `CommandUserService` switched from `daemon(true)` to `daemon(false)` to eliminate orphaned zombie shell processes.
- Shizuku automatically optimizes background flags (`cmd deviceidle whitelist` and Xiaomi autostart/background appops `10008`/`10017`).

## Device-verified features

- First-run spotlight tutorial on Xiaomi 2506BPN68G with the debug APK: each of the six steps highlighted its anchor, "Show presets" closed the tour and opened the preset sheet, "Finish" and Skip closed it, a relaunch did not reopen it, and Settings → "Replay tutorial" started it again from Home.
- Safe rule duplication, pre-save/pre-enable conflict warnings, background NFC confirmation, and app language switching.
- Time schedules, charger, battery threshold, screen, Wi-Fi, and notification triggers.
- Notifications, app launch, URL opening, alarm, timer, offline TTS, media volume, vibration, Play sound, webhook base action, NFC, Battery Saver, Auto-rotate, Do Not Disturb, and Dark theme actions.
- Alarm creation requests UI skipping, supports repeat weekdays and templated labels; scheduling, presentation and deletion remain controlled by the Clock app.
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

## Implemented; device validation partial or pending

- Per-rule pinned home-screen shortcuts: Edit → Advanced requests a `ShortcutManagerCompat` pin with only the rule id; `RuleShortcutActivity` reloads the rule, blocks missing/disabled rules, and confirms before running. `ManualRuleRunner` is shared with editor test runs, so shortcut runs bypass trigger/conditions/cooldown and the engine pause state while recording redacted history and leaving enabled/last-trigger state unchanged. Full local gate passes. Verified on Xiaomi 15T Pro / HyperOS 3: a denied Home screen shortcuts app-op opens the MIUI permission page, granting it allows pinning, the shortcut runs an enabled rule after confirmation, and a disabled rule is blocked with the Open FlowPilot offer.

- Notification listener recovery (2.2.1): `FlowPilotNotificationListener.ensureBound` is called during rule reconciliation whenever enabled rules need notification triggers, and `onListenerDisconnected` retries about one second later. `NotificationListenerRebindGate` permits one request per 30 seconds, skips requests while connected or without access, and does not consume the retry window when access is missing. HyperOS `com.miui.misound` volume/headphone UI posts and FlowPilot's own release/debug packages are dropped before queueing. Unit coverage lives in `NotificationListenerRebindGateTest` and `NotificationPackageFilterTest`; no 2.2.1 test or device run is claimed here, and on-device recovery after an in-place update or OEM process kill remains unverified.

- Dynamic TTS and general templates: `${...}` rendering for supported action text fields, strict speech rendering, a shared trigger-aware variable picker for notification, URL, alarm, timer, webhook, phone, SMS, and speech fields, on-demand synthesis, and a content-addressed cache with TTL/LRU cleanup and a manual Settings clear action. The speech editor also lists template variables that no longer apply to the selected triggers (`tts_template_trigger_mismatch`, 2.2.1) instead of leaving Save disabled unexplained. Unit tests, `lintDebug`, `assembleDebug`, `assembleRelease`, and EN/TR string parity pass locally. On Xiaomi 15T Pro / HyperOS 3, notification speech, the voice picker, System default, and language-aware preview were verified; SMS speech remains pending.
- Preset library expansion and the simplified condition editor: five presets use OR triggers and nested/NOT conditions, preset cards show trigger counts plus an AND/OR marker, and flat rules keep a single add row while single-child groups flatten on open. Unit tests, `lintDebug` and `assembleRelease` pass locally; the sheet and hands-on editing were not verified on a device because the wireless ADB target stayed offline.
- Multiple OR triggers (#5): up to five independently configured, reorderable triggers per rule; typed versioned lists, legacy single-trigger JSON preservation, actual source enum/index in history/templates, one shared cooldown, callback dedupe and enabled-trigger listener union. Unit tests, `lintDebug` and `assembleDebug` pass locally; Xiaomi app + Wi-Fi, engine-restart and disabled-rule device smoke verification remains pending.
- Time Window (`TIME_BETWEEN`) and Days of the Week (`DAYS_OF_WEEK`) conditions (unit tests passed; device smoke tests pending):
  - Time interval filtering with overnight span support (e.g. 23:00 - 07:00 crossing midnight).
  - Day of week filtering with Daily, Weekdays, Weekends, and custom day toggles.
- Nested AND / OR / NOT condition groups (unit tests, `lintDebug` and `assembleDebug` pass locally; device validation pending):
  - `Automation.conditionTree` supports recursive groups with a per-group combinator, negation, and leaf conditions; rules without a tree keep the legacy flat-AND list unchanged.
  - Create/Edit expose a Match AND/OR selector that converts the flat list into a root group, nested group editing with a collapsed summary, and an AND/OR control plus NOT toggle per group. Add actions are disabled once a tree reaches the 32-leaf import limit.
  - Import rejects trees deeper than 4 levels, trees with more than 32 leaves, and empty groups before any mutation; legacy payloads without a tree still import.
- Phone call automations family on Xiaomi 15T Pro / HyperOS 3:
  - Triggers: `CALL_RINGING`, `CALL_ANSWERED`, `CALL_OUTGOING`, `CALL_ENDED` (state-only matching; phone-number filtering removed because Android 12+ does not provide outgoing numbers without default-dialer role; legacy filter-configured rules operate as state-only / any-number rules). Device validation for this removal has not been run on device.
  - Actions: `OPEN_DIALER`, `DIAL_NUMBER`, `CALL_NUMBER` (direct call with `CALL_PHONE` permission).
  - Privacy safeguards: UI number masking for dial/call actions, log redaction, zero call-log / contact storage.
- Connectivity & Flashlight actions (unit tests passed; device smoke tests pending):
  - Wi-Fi on/off (`WIFI_ON`, `WIFI_OFF`) via Shizuku `svc wifi enable|disable` with `WifiManager.isWifiEnabled` verification.
  - Mobile Data on/off (`MOBILE_DATA_ON`, `MOBILE_DATA_OFF`) via Shizuku `svc data enable|disable` with `Settings.Global.mobile_data` / `TelephonyManager.isDataEnabled` verification.
  - Airplane Mode on/off (`AIRPLANE_MODE_ON`, `AIRPLANE_MODE_OFF`) via Shizuku `cmd connectivity airplane-mode enable|disable` with `Settings.Global.AIRPLANE_MODE_ON` verification.
  - Wi-Fi Hotspot on/off (`HOTSPOT_ON`, `HOTSPOT_OFF`) on Android 16+ via Shizuku `newProcess` running `HotspotBridge` under `app_process` against the app APK. The bridge registers as the tethering owner (`com.android.shell`) and uses the public `TetheringManager.startTethering`/`stopTethering`; Android 15 and older report `UNSUPPORTED`. Verified on Xiaomi 15T Pro / Android 16: the shell bridge toggled tethering off and on with the screen off, and a targeted run of the instrumentation suite that existed at the time toggled it through the full app → Shizuku → bridge path.
  - Flashlight on/off/toggle (`TORCH_ON`, `TORCH_OFF`, `TORCH_TOGGLE`) via direct `CameraManager.setTorchMode` with hardware camera flash detection. Toggle state follows `CameraManager.registerTorchCallback`, so system-tile toggles are reflected; an unobserved state is treated as off.
- Per-rule shake force (unit tests passed; device threshold validation pending):
  - The `DEVICE_SHAKE` trigger exposes a strength slider in Create/Edit (`Automation.shakeThreshold`, 6–32 m/s² above gravity, default 20), so different automations can react to light or firm shakes.
  - The accelerometer listens at the softest enabled rule force; every detected shake carries its measured magnitude and only rules whose threshold it meets execute.
  - `ShakeReducer` uses hysteresis (signal must drop below 50% of the listening threshold) before re-arming, so one continuous shake burst produces a single trigger even when its spikes outlast the 800 ms debounce.
- Action list reordering in Create and Edit screens (`ReorderableActionList`, `ActionCardItem`) with Move Up / Move Down controls; execution preserves exact order and per-action delay sequence; unit tests passed (`ActionsReorderStateTest`).
- Active Location & Background GPS Support (unit tests passed; device smoke test pending):
  - Multi-tier `LocationFetcher`: fresh cache (<60s, <50m) -> 5s active GPS fix timeout -> best cached fallback.
  - Background location permission flow (`ACCESS_BACKGROUND_LOCATION`) with system app settings guidance for "Allow all the time".
  - Foreground service location type (`FOREGROUND_SERVICE_LOCATION`) attached to `AutomationService`.
  - Dynamic template variables: general `${...}` context (notification, SMS, call, app, Wi-Fi, Bluetooth, NFC, shake, light, geofence, screen, power), with location aliases preserved.
- Location Geofencing triggers (`GEOFENCE_ENTER`, `GEOFENCE_EXIT`) (unit tests passed; device smoke test pending):
  - Hardware-assisted geofencing via Google Play Services `GeofencingClient` with event-driven battery behavior and zero CPU wake-locks while idle.
  - Persistent DataStore event queue (`geofence_event_queue`, max 50 events) with `GeofenceBroadcastReceiver` and `AutomationService` reconciliation.
  - Prerequisites validation (`ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` "Allow all the time", system location services enabled).
  - Strict circular boundary config validation (latitude: -90..90, longitude: -180..180 excluding 0,0; radius: 50..1000m).
  - Lifecycle diffing (`calculateGeofenceDiff`): dynamically adds new geofences, unregisters disabled/deleted rules, and removes-before-re-adding modified rules to prevent request ID collisions.
  - Rule evaluation filtering for `ENTER` vs `EXIT` events (`RuleEvaluator.evaluateGeofence`) with cooldown and live condition checks.
  - Transition coordinates are reused for location templates only when the location fix is less than 60 seconds old at action dispatch. Missing, legacy, future, or stale timestamps trigger a fresh lookup; unavailable fixes produce empty location values.
  - Engine lifecycle integration: system geofences remain registered during temporary engine restarts while enabled; unregistered only when engine is disabled.
  - Registration diagnostics and status UI on Home screen (`REGISTERED`, `UNREGISTERED`, `TRANSITION_ENTER`, `TRANSITION_EXIT`, `REGISTRATION_FAILED`) and receiver error banners.
- Password-encrypted full backup and restore (unit tests passed; device smoke test pending):
  - AES-256-GCM portable envelope with PBKDF2-HMAC-SHA256 (100,000 iterations), random salt/IV, and six-character minimum password.
  - Encrypted export/share retains full rule data and enabled state; wrong passwords, altered payloads, unsupported versions/formats, and unsafe KDF bounds fail before import mutation.
  - Normal JSON export/share remains sanitized and normal import disables imported rules.
- Localized history results and automatic rule names (unit tests passed):
  - New records store locale-neutral result codes; known legacy successful results render in current app language.
  - SMS recipients stay masked; raw technical failures remain redacted fallback text.
  - English, Turkish, and system-language switching passed physical-device validation; active background notification text and channel metadata refresh immediately.
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

### Import validation

Normal imports remain disabled and encrypted restores preserve enabled states. Both import strategies generate new IDs. Dynamic speech rules keep their template and synthesize on first play; legacy rules copy verified device-local audio when available, and missing legacy audio is cleared and reported without blocking the import. Validation rejects invalid inputs before mutation; limits are 8 MiB document, 3 MiB encrypted plaintext, 1,000 rules, 100 actions/conditions/delays per rule, 32 condition-tree leaves and 4 group nesting levels, and 64 Ki UTF-16 units per string. Empty condition groups are rejected on import.
