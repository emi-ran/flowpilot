# Changelog

All notable FlowPilot changes are documented here.

## Unreleased

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
