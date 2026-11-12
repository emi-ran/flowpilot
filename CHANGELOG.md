# Changelog

All notable FlowPilot changes are documented here.

## [Unreleased]

### Added

- Media session controls: play, pause, toggle, next and previous, with an optional target app; media playing, paused and track-changed triggers with an optional app filter. Notification access is required, and observation runs only when enabled rules need it. Sessions present when the tracker starts are silently seeded without events; a newly observed session already playing after startup produces a playing event. Media title and artist are transient template values and are not written to logs or execution history; user-configured template actions can explicitly use them.
- Search for a song or open a supported Spotify or YouTube Music song link. Automatic playback and background launches depend on the target app and Android.
- Show service-specific song-link formats and validate links as they are entered, before saving or running a rule.
- Use trigger variables in music search queries and song-link actions; resolved links are validated against the selected service before opening.
- Mark unavailable music apps in the editor and explain how to install or enable the selected app; report an actionable failure if the target cannot handle the action.
- Prevent saving or manually running music rules when the selected app is missing or disabled; preserve existing and imported rules.
- Execution history and notification diagnostics: History groups repeated single-action failures by rule, action type, and structured `ActionDiagnosticReason` when a non-successful run has exactly one failed action with a diagnostic, while leaving multi-action failures ungrouped; each action row renders structured music-link failure reasons (including bounded expected/observed video ID lengths) and content-free template diagnostics (`ActionTemplateDiagnostic` reporting `notification.text` / `notification.bigText` missing/empty/present states, syntax errors, unknown variables, analysis failures, and empty rendered output) without persisting URLs, phone numbers, notification text, or raw error strings.

### Fixed

- Trim surrounding whitespace from YouTube Music links before validating and opening them, including links resolved from templates.
- The execution-history filters now read as dropdowns instead of plain buttons: each filter shows the current selection with a chevron, fills with the primary container color while a filter is active, offers a check-marked menu and a clear button that resets both filters. A successful action no longer prints "no diagnostic information".

## [2.2.1] - 2026-11-05

### Fixed

- Notification triggers recover when Android leaves the notification listener allowed but unbound after an in-place update or OEM process kill. The engine asks the system to rebind on rule reconciliation, retries after a disconnect, and throttles requests to once every 30 seconds.
- HyperOS volume/headphone UI notifications from `com.miui.misound` no longer fire unfiltered notification rules, so pressing a volume key cannot speak the rule's literal speech text.
- The spoken-message editor now explains when a template still uses variables from the previous trigger after the trigger changes, instead of leaving Save disabled without feedback.

## [2.2.0] - 2026-11-04

### Added

- Dynamic speech and general action templates: `${variable}` placeholders now render from the actual notification, SMS, call, app, Wi-Fi, Bluetooth, NFC, shake, light, geofence, screen, and power events. The speech editor's trigger-aware searchable picker inserts only variables that apply to the rule's configured triggers and shows filter examples.
- Safe template filters (`trim`, `stripPrefix`, `drop`, `truncate`, `lower`, `upper`, `default`, `urlencode`, `jsonescape`), quote-aware parsing, one-pass rendering, and `$${` escaping.
- Offline TTS now synthesizes on demand instead of requiring a separate Generate step. Speech is strict: unknown variables, malformed expressions, or empty rendered text fail with a generic result, while other action fields keep unknown tokens unchanged.
- Content-addressed TTS cache shared across rules, with atomic writes, a 7-day TTL, a 50 MB cap, LRU eviction, periodic cleanup, and a manual Settings clear action.
- The trigger-aware variable picker is available from every template-capable action field: notification, URL, alarm, timer, webhook, phone, SMS, and speech. SMS now uses the same searchable picker instead of a fixed quick-variable chip row.
- First-launch interactive spotlight tutorial: a six-step guided tour (welcome, ready-made presets, create, engine status, settings, done) dims the screen, highlights the real anchor for each step, and can be skipped at any point. Completion is persisted in DataStore so the tour runs once, and Settings gains a Replay tutorial row to run it again on demand.
- Per-rule home-screen shortcuts: Edit → Advanced can pin or remove a launcher shortcut for one automation. The shortcut carries only the rule id; `RuleShortcutActivity` reloads the current rule and always asks for confirmation. Missing or disabled rules never run, and an enabled run bypasses trigger, conditions, cooldown, and the engine pause state without changing the rule's enabled state or last-trigger time. On HyperOS, a denied Home screen shortcuts app-op is detected before the request and the editor opens the MIUI permission page instead of failing silently.

### Changed

- Rule save no longer requires a pre-generated TTS file. Existing rules with blank speech text and a valid legacy cache file still play as before; dynamic rules ignore stale audio.
- Import/export carries speech templates instead of audio. Missing legacy audio is reported after import, and dynamic rules synthesize on first play.
- Notification matching keeps primary text and expanded text separate for `${notification.text}` and `${notification.bigText}` while still matching both.
- Variable rendering moved into `ActionParametersFactory`; webhook and SMS executors now receive already-rendered values and do not apply templates a second time.

### Security

- Notification, SMS, call, app, Wi-Fi, Bluetooth, NFC, shake, light, geofence, screen, and power values enter only a transient template context; they are not written to rule fields, execution history, logs, or exports.
- Rendered TTS audio remains in the app-private cache (excluded from backups), and rendered text is never logged.

### Fixed

- FlowPilot's own engine/status notifications, including those from a side-by-side debug build, are ignored by the notification listener. A rule with no app filter can no longer trigger on FlowPilot's ongoing notification and repeatedly speak its own status.
- New TTS actions no longer adopt the alphabetically first offline voice when none was chosen. An unset voice now uses the phone's default TTS voice, shows as **System default**, and can be restored from the voice picker. The editor preview also uses a demo sentence in the selected voice's language instead of always using the Turkish sample.

## [2.1.0] - 2026-11-03

### Added

- In-app updates: Settings can check the public GitHub Releases API automatically at most once a day or on demand. A newer stable release opens a notes dialog with Download &amp; Install, Open release page, Remind me later, and Skip this version. Downloads are limited to the `emi-ran/flowpilot` GitHub release APK, handed to Android's DownloadManager, and installed only after the user confirms the system installer. The automatic check can be disabled; the About and README privacy wording now says "offline by default" instead of "100% offline".
- Wi-Fi hotspot on/off actions (`HOTSPOT_ON`, `HOTSPOT_OFF`) for Android 16+: FlowPilot runs a shell-side `HotspotBridge` through Shizuku's `newProcess` as `app_process` against its own APK, registers the call as the tethering owner (`com.android.shell`), and toggles tethering through the public `TetheringManager` API. Requires Shizuku and Wi-Fi hardware; Android 15 and older report the action as unsupported. Verified on Xiaomi 15T Pro (Android 16): the shell bridge toggled tethering off and on with the screen off, and a targeted instrumentation run toggled it through the full app → Shizuku → bridge path. The R8-minified release APK retains `HotspotBridge`.

### Fixed

- Shizuku-backed actions no longer fail permanently when the Shizuku `UserService` cannot start on Android 16 (`LoadedApk.makeApplicationInner` crash, Shizuku issue #2541). `ShizukuShell` falls back to Shizuku's allowlisted `newProcess` shell execution with the same 10-second timeout, and retries the UserService after 60 seconds or a binder restart.

### Changed

- Debug builds install as **FlowPilot Dev** with application ID `com.flowpilot.app.debug`, a `-dev` version name, and a green/mint launcher icon, so a development APK no longer replaces or uninstalls the signed release app. Debug builds also hide and skip release update checks because a release APK cannot replace a debug-signed package. Release builds keep `com.flowpilot.app` and the existing dark/blue icon unchanged.
- Android CI and the tag-release workflow now restore/save the Gradle cache via `gradle/actions/setup-gradle` v4.4.4 instead of the retired v1 cache client, use the Gradle build cache (`org.gradle.caching=true`), and run tests, lint, debug assembly, and release validation in a single Gradle invocation on CI. The Android-change detector now fails open (builds when the base commit is unresolved) and only pull-request runs cancel in progress, so main push runs used by release validation are not cancelled by a newer push.

## [2.0.0] - 2026-11-02

### Added

- Multiple OR triggers per rule (#5): Create/Edit can add, remove and reorder up to five independently configured trigger definitions. Existing single-trigger JSON stays unchanged; normal export/share and encrypted backup/restore preserve multi-trigger lists. Matching records the actual source and one-based source index, shares the rule cooldown across sources, and reconciles listeners from all enabled definitions. Unit coverage added; Xiaomi app + Wi-Fi, restart and disabled-rule smoke tests remain pending.
- Nested AND / OR / NOT condition groups: `Automation.conditionTree` adds recursive groups with per-group combinators, negation, empty-group semantics, and a 4-level depth guard. Rules without a tree keep the existing flat-AND `conditions` behavior, and the editor keeps `conditions` as a flattened leaf list for older readers.
- Five new ready-to-use presets exercise the multi-trigger and nested-logic features: Morning Wake Up, Night Quiet, Morning Commute, Focus / Work Mode, and Night Screen Dimming. Preset cards now show the actual trigger count (`+N`) and an AND/OR marker when a preset ships a condition tree; Battery Saver Emergency only fires while unplugged.
- Flashlight toggle action (`TORCH_TOGGLE`): turns the torch on when it is off and off when it is on, resolved from camera torch state callbacks so changes made by the system flashlight tile are respected. The "Shake for Flashlight" preset now switches the light instead of only turning it on.

### Security

- Update checks accept only HTTPS GitHub URLs under `/emi-ran/flowpilot/releases/`, require a parseable version tag newer than `BuildConfig.VERSION_NAME`, and offer only an APK asset from the exact release download path. A downloaded file is installed only when the completion broadcast id matches the one recorded before enqueue, the download reached `STATUS_SUCCESSFUL`, and the user confirms the system installer; no silent install path exists.
- Reject imported condition trees deeper than 4 group levels, with more than 32 leaves, or containing empty groups before any DataStore mutation; existing flat-only payloads still import unchanged.
- Release APKs now carry a GitHub build provenance attestation created after the signed APK passes package/version identity, `apksigner` certificate-digest, and SHA-256 checks and before the release assets are uploaded. If attestation cannot be created, the release job fails. Verification with `gh attestation verify`, `apksigner verify --print-certs`, and `sha256sum` is documented in `docs/SUPPLY_CHAIN.md`.
- Gradle dependency verification checksums, generated and reviewed as `gradle/verification-metadata.xml`, now cover plugins, artifact metadata, and the app dependency graph; Gradle's default strict mode fails local and CI builds on a missing or mismatched checksum. `google()` is content-filtered to the Android/Gradle groups, and every workflow `uses:` stays pinned to a full commit SHA. Dependency lockfiles are intentionally not used; see `docs/SUPPLY_CHAIN.md` for the rationale.
- Keep geofence event retention separate from location freshness: reuse coordinates only for fixes under 60 seconds old at action dispatch, otherwise fetch a fresh location or omit location template values.
- Validate normal and encrypted imports before changing stored rules, rejecting invalid rule IDs, unsafe references, invalid active settings, and oversized documents or fields. Imported rules receive new IDs; failed imports preserve existing rules.

### Changed

- The condition editor now keeps flat rules flat: "Add group" moved behind a single "Nested groups" entry, group headers compact to an "All of / Any of" menu plus NOT, unavailable add actions are hidden, and single-child groups left by older drafts flatten on open instead of rendering as a group inside a group.
- Condition editing now opens in the legacy flat list with a Match AND/OR selector; choosing OR or adding a nested group converts it into a tree editor with per-group AND/OR segmented controls, a NOT toggle, add-condition/add-group actions, remove actions, and a compact summary when a group is collapsed. Add actions disable at the 32-leaf import limit, and the section hint now reads "optional · combine with AND / OR".
- Preserve normal-import disabled states and encrypted-restore enabled states. When device-local TTS audio is unavailable, import the rule and prompt the user to regenerate its audio; available verified audio is copied to the new rule ID.
- Redesigned the preset sheet: cards are neutral with a per-category accent limited to the icon tile and dot, category filters wrap instead of scrolling off-screen, each card keeps one filled "Use" action, and the drag handle and header now clear the status bar and camera cutout.
- Trigger cards reuse the trigger picker's per-trigger accent colours (icon tile, schedule picker icon, ambient-light controls) instead of a blue primary container, and the create-screen presets banner is now a neutral card with an amber sparkle instead of a blue fill.
- Secondary actions (Preview vibration, Play sound, set light target, Test) are outlined buttons instead of tonal fills, because the tonal fill matched the dark card surface and read as disabled.
- Home uses the same neutral treatment: the engine status card and rule cards are neutral surfaces with a green status accent or per-trigger accent instead of a blue fill, rule detail lines use muted text, and "Available" capability pills use a muted success green.
- Shake force is now configured per rule: selecting the "Device shaken" trigger reveals a strength slider (6–32 m/s² above gravity, default 20) so each automation can require a light or a firm shake. A hysteresis re-arm keeps one continuous shake from firing multiple times, so a single shake now toggles the flashlight once.
- Rule editor (create and edit) uses one neutral card language: trigger, condition, action, and advanced-options cards share a bordered low surface, section headers read as letter-spaced labels with a muted hint ("CONDITIONS optional · all must match") and neutral reorder hint, action numbers and delay chips no longer use the blue primary fill, the redundant action subtitle is gone, and the footer separates Cancel/Save with a hairline divider and taller buttons.
- Privileged actions now verify platform state instead of trusting command exit codes: NFC and Battery Saver poll the live adapter/setting after the command and fail honestly on mismatch or an unreadable state, while Lock Screen reports verified completion when the display becomes non-interactive and otherwise reports the command as accepted without claiming completion.

## [1.1.0] - 2026-09-15

### Added

- Safe rule duplication from the Home list: creates a disabled, immediately editable copy with a new identity and reset runtime state. Webhook secrets are decrypted and re-encrypted with fresh Android Keystore ciphertext; TTS cache files are copied independently with failure-safe cleanup.
- Non-blocking conflict warnings before saving or enabling automations: detects opposing state actions with likely/possible confidence, links to the conflicting rule for inspection, preserves the pending operation across inspection, and requires a deliberate override. Trigger overlap follows runtime wildcard semantics without exposing notification keywords or other sensitive arguments.

### Changed

- Foreground engine and startup-failure notifications now follow the persisted English, Turkish, or system-language selection across boot, service restart, process recreation, and task removal. Language changes refresh active notification text, channel metadata, and widget state immediately.
- GitHub Pages now has improved mobile navigation, browser-language selection, accessible brand navigation, modular assets, and release-aligned installation guidance.

### Security

- Background NFC discovery now opens a confirmation gate before it can run matching automations; only foreground Android ReaderMode scans execute automatically.
- Webhooks pin initial TCP connections to prevalidated public IP addresses, preserve TLS hostname verification, reject unsafe rendered headers, and use bounded HTTP/1.1 parsing.
- Sensitive SMS and notification events are accepted only while the engine is enabled, bounded and freshness-limited, and reauthorized immediately before execution.
- Automatic rule runs now use durable execution leases and revision checks, preventing cooldown bypasses and revoking queued work after rule changes.
- Execution-history rule names, trigger snapshots, action arguments, and failure messages are sanitized before persistence and during legacy migration; raw provider errors and embedded sensitive markers are not retained.
- Release workflow now requires a current `main` commit, exact successful CI, matching version tag, detailed prepared notes, verified APK identity/signature, and signing environment before publication.
- Enabled Dependabot vulnerability alerts, security update pull requests, secret scanning, push protection, private vulnerability reporting, and protected `main` rules.

### Verification

- GitHub `Build & Test` passed for the merged feature and security changes.
- Physical-device validation passed for safe rule duplication, conflict warnings, background NFC confirmation, and language switching.
- Android instrumentation remains a manual emulator gate.

## [1.0.2] - 2026-09-13

### Added

- Location & Geofencing automations feature family:
  - Triggers: Enter designated geographical area (`GEOFENCE_ENTER`) and exit designated geographical area (`GEOFENCE_EXIT`).
  - Google Play Services `GeofencingClient` integration: hardware-assisted circular region monitoring with zero CPU wake-locks while idle.
  - Persistent DataStore event queue (`geofence_event_queue`, max 50 events): `GeofenceBroadcastReceiver` enqueues boundary events and invokes `AutomationService` reconciliation, guaranteeing zero event loss across process restarts or memory pressure.
  - Permission and system prerequisites validation (`evaluateGeofencePrerequisites`): enforces `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` ("Allow all the time"), and system location service availability prior to hardware registration.
  - Safe lifecycle diff synchronization (`calculateGeofenceDiff`): dynamically adds new geofences, unregisters disabled/deleted rules, and removes-before-re-adding modified rules to prevent request ID collisions in Google Play Services.
  - Rule evaluation filtering (`RuleEvaluator.evaluateGeofence`): strict `ENTER` vs `EXIT` event matching, per-rule cooldown suppression, and live condition satisfaction (time windows, days of week, battery, Wi-Fi).
  - Smart coordinate reuse (`resolveExecutionCoordinates`): transition coordinates from Google Play Services are injected directly into rule context for location templates (`${location.lat}`, `${location.lng}`, `${location.coords}`, `${location.maps_url}`), avoiding redundant fresh GPS lookups for notification-only or template-driven geofence actions.
  - Background service reconciliation: explicit `PendingIntent` delivery from Google Play Services grants API 31+ background foreground-service start exemptions to evaluate events promptly.
  - Lifecycle persistence: system geofences remain registered during temporary engine restarts while engine is enabled, and are fully unregistered only when engine is disabled by the user.
  - Real-time registration diagnostics and status UI: home screen rule cards display live geofence state (`REGISTERED`, `UNREGISTERED`, `TRANSITION_ENTER`, `TRANSITION_EXIT`, `REGISTRATION_FAILED`) and receiver error banners.
  - Unit test suite: coverage for persistent queueing, config validation, diff calculation, prerequisites evaluation, location dependency coordinate resolution, and rule evaluator matching.
- Password-encrypted full backup and restore: explicit encrypted export/share for full backups and single rules, while normal JSON export/share stays sanitized. Portable envelopes use AES-256-GCM, random salt/IV, and PBKDF2-HMAC-SHA256 (100,000 iterations); passwords require six characters. Encrypted restore preserves full rule data and enabled state, validates format/version/KDF bounds/password/authentication before mutation, then re-encrypts secrets with the target device Android Keystore.
- Localized automation history and generated names: new history records persist locale-neutral outcome codes; History renders known outcomes in selected app language. SMS recipients remain masked, technical/redacted failures remain visible, legacy successful history maps to localized generic outcomes, and automatic rule names use saved English or Turkish app language without changing custom names.

### Changed

- Normal exports continue omitting webhook secrets and normal imports continue disabling imported rules.
- Geofence prerequisite and registration failures now use bounded retry backoff (10 seconds to 60 seconds), avoiding rapid retry/DataStore/UI loops. A prerequisite or desired-config change retries immediately.
- Geofence transition coordinates are reused for template context before requesting a fresh location, so notification-only and template-driven geofence actions avoid an unnecessary GPS wait.

## [1.0.1] - 2026-09-05

### Added

- Ready-to-use Automation Presets (Templates) in CreateScreen: Users can browse categorized pre-configured recipes (Bedtime Routine, Full Battery Protection, Battery Saver Emergency, Flip to Silence, Shake for Torch, Cinema/Low Light Mode, Leaving Home, Welcome Home, and SMS Location Responder with 5-second satellite GPS lock delay before sending SMS). Presets populate the creation form with full trigger, condition, multi-action, delay, and parameter states for quick editing and saving.
- Time Window (`TIME_BETWEEN`) and Days of the Week (`DAYS_OF_WEEK`) conditions: rules can now be restricted to specific time intervals (e.g. 23:00 - 07:00, with full overnight midnight-crossing support) and/or specific days of the week (Weekdays, Weekends, or custom day toggles), evaluated alongside any trigger.
- Sound profile actions for Normal, Vibrate, and Silent with Notification Policy Access checks and ringer-mode readback.
- Dark theme actions through Shizuku with post-command state verification.
- Webhook header/body template variables for live automation context.
- Webhook secret encryption at rest using Android Keystore AES-256-GCM and legacy configuration migration.
- In-progress manual action test run in both Create and Edit automation screens: users can immediately test actions directly on the current form state (including all unsaved edits, parameters, delays, volumes, etc.) without needing to save first, via both the TopAppBar Play action and a dedicated Test button next to Add action.
- Persistent automation run history for engine and manual executions, with per-action outcomes, overall success/partial/failure state, and 100-entry retention.
- Bluetooth bonded-device connected/disconnected triggers with Android 12+ `BLUETOOTH_CONNECT` gating, public ACL broadcast lifecycle, no startup replay, and per-device duplicate suppression.
- Bluetooth on/off actions through exact allowlisted Shizuku `svc bluetooth enable|disable` commands with adapter-state readback verification.
- Phone call automations feature family:
  - Triggers: Incoming call ringing (`CALL_RINGING`), call answered (`CALL_ANSWERED`), outgoing call placed (`CALL_OUTGOING`), and call ended (`CALL_ENDED`) with TelephonyCallback / PhoneStateListener integration and edge-triggered transition deduplication. Triggers match on state only without phone-number filtering due to Android 12+ outgoing number limitations for non-default dialers; legacy filter-configured rules operate as state-only / any-number rules. Device validation for this removal has not been run on device.
  - Actions: Open dialer (`OPEN_DIALER`), Prepare dialer with number (`DIAL_NUMBER`), and Direct automated call (`CALL_NUMBER` with `android.permission.CALL_PHONE`).
  - Privacy and data minimization: phone numbers for dial/call actions are masked in UI and rule summaries; execution history, action results, logcat, and diagnostics contain no phone numbers; no call logs or contacts are accessed.
  - Confirmation dialog warning in Create, Detail, and Manual Test Run dialogs for direct call actions.
- Device motion / flip automations feature family:
  - Triggers: Device flipped face down (`DEVICE_FLIPPED_DOWN`) and device flipped face up (`DEVICE_FLIPPED_UP`).
  - Dual physical sensor validation with Proximity (NEAR) + Gravity/Accelerometer Z-axis ($Z \le -6.5 m/s^2$) + lateral horizontal stability ($\le 6.0 m/s^2$).
  - 500ms stability debounce prevents spurious triggers during hand rotation; startup seeding prevents immediate execution on engine start.
  - Battery-optimized sensor lifecycle: dynamic demand-driven registration (sensors detached when no flip rules exist), automatic sleep when screen turns off, and user-configurable screen-off detection (`flipScreenOffDetection`) with low-frequency `SENSOR_DELAY_NORMAL` (~5Hz).
  - Pure deterministic state reducer (`DeviceFlipReducer`) and unit test suite covering state transitions, debounce, screen-off gating, cooldown suppression, and environmental condition matching.
- System connectivity and flashlight control actions:
  - Wi-Fi on/off (`WIFI_ON`, `WIFI_OFF`) through Shizuku `svc wifi enable|disable` with bounded `WifiManager.isWifiEnabled` readback.
  - Mobile Data on/off (`MOBILE_DATA_ON`, `MOBILE_DATA_OFF`) through Shizuku `svc data enable|disable` with `Settings.Global.mobile_data` / `TelephonyManager.isDataEnabled` readback.
  - Airplane Mode on/off (`AIRPLANE_MODE_ON`, `AIRPLANE_MODE_OFF`) through Shizuku `cmd connectivity airplane-mode enable|disable` with `Settings.Global.AIRPLANE_MODE_ON` readback.
  - Flashlight on/off (`TORCH_OFF`, `TORCH_ON`) using standard `CameraManager.setTorchMode` and hardware flash detection, requiring no special permissions or Shizuku.
  - Unit test suite for all new executors and 100% action dispatcher coverage.
- Action list reordering: users can reorder configured actions directly in Create and Edit automation screens via Move Up / Move Down buttons (`ReorderableActionList`, `ActionCardItem`). Execution sequence strictly follows the configured order with per-action delay preservation; covered by unit tests in `ActionsReorderStateTest`.
- Active GPS and background location support:
  - Live GPS/network coordinate acquisition via multi-tier `LocationFetcher`: checks recent cache (<60s, <50m accuracy), triggers active GPS/network fix with 5-second timeout, and falls back to best available cached location.
  - Background location permission flow (`ACCESS_BACKGROUND_LOCATION`) with dedicated setup card and guidance dialog in Permissions screen redirecting users to system app info for "Allow all the time" selection.
  - `FOREGROUND_SERVICE_LOCATION` declaration and `foregroundServiceType="specialUse|location"` attached to `AutomationService` for Android 14+ background compliance.
  - Dynamic template variables for location: `${location.lat}`, `${location.lng}`, `${location.coords}`, and `${location.maps_url}` in `WebhookTemplateRenderer`.
  - Live location coordinates injected during in-app manual test runs (`runRuleNow`).

### Changed

- Removed phone-number filtering from phone call triggers (`CALL_RINGING`, `CALL_ANSWERED`, `CALL_OUTGOING`, `CALL_ENDED`). Android 12+ does not provide outgoing numbers to non-default dialers, so call triggers now match purely on call state. Existing legacy rules with configured number filters are preserved through DataStore JSON deserialization and operate as state-only / any-number rules.
- Android backup is disabled to prevent webhook credential exposure through backup snapshots.
- Create/Edit form keyboard behavior uses Android `adjustResize`, IME-aware padding, and focus-gated bring-into-view scrolling. Keyboard opens only on text-field focus; focused fields return above the IME while typing.
- Webhook logging and execution failures redact sensitive values.
- Run-history diagnostics redact sensitive values and never persist webhook or action configuration fields.
- Bluetooth ACL receiver uses an Android 13+ exported dynamic receiver required for Bluetooth-stack delivery; Bluetooth radio actions now wait for asynchronous adapter-state changes before reporting success or failure.
