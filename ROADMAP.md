# FlowPilot Roadmap — Xiaomi 15T Pro / HyperOS 3

## Scope

Current implementation target: **Xiaomi 15T Pro running HyperOS 3**.

System controls below are not claimed as generic Android behavior. Every feature must be implemented, built, installed, and tested on this target device before next feature starts.

## Delivery Rules

For each item:

1. Define trigger/action model and persistence migration needs.
2. Add unit tests for rule matching, dedupe, and failure behavior.
3. Add device capability check and clear unsupported/permission state.
4. Build debug APK and run unit tests.
5. Install with ADB on Xiaomi 15T Pro / HyperOS 3.
6. Run device smoke test for success, repeated trigger, and reversal/failure path.
7. Record device command, permission, latency, and limitation in README or feature docs.

Do not bundle unrelated features. One feature family at a time.

## Planned Triggers

### Phase 1 — Framework-safe signals

1. **Time schedule** (complete)
    - At a chosen time every day
    - Weekdays
    - Selected days of week
    - Polls every 500 ms, executes once per matching minute, and does not replay missed occurrences after engine start
2. **Charger state** (complete)
    - Charger connected
    - Charger disconnected
    - Android power broadcasts while engine runs; duplicate states are deduped and existing charger state is not replayed after engine start
3. **Battery threshold** (complete)
    - Below selected percentage, such as 20%
    - Above selected percentage, such as 80%
    - Edge-triggered; must not fire repeatedly while remaining past threshold
    - Current level seeds at engine start without replay; later battery broadcasts trigger only a threshold crossing
4. **Screen state** (complete; Xiaomi device smoke test passed)
   - Screen on
   - Screen off
   - Dynamic broadcasts while engine runs; duplicate events deduped and no startup replay
5. **Headset state** (deferred; do not prioritize unless explicitly requested)
   - Wired headset connected/disconnected
   - Bluetooth audio device connected/disconnected

### Phase 2 — Network/device context

6. **Wi-Fi network state** (complete; Xiaomi device smoke test passed)
    - Connected to selected SSID
    - Disconnected from selected SSID
    - Manual SSID entry or user-initiated nearby-network picker; results remain transient and Android scan throttling can return cached results
    - Wi-Fi callback uses transport-specific SSID data, including when cellular remains default data network
7. **Notification received** (complete; Xiaomi device smoke test passed)
   - Selected app package matching
   - Optional case-insensitive title/text keyword filtering
   - NotificationListenerService lifecycle and duplicate post suppression
   - Zero content logging or persistence
8. **Bluetooth device state** (complete; Xiaomi device smoke test passed)
    - Selected Bluetooth device connected/disconnected
    - Selected bonded device MAC address matching; cached name for UI
    - Android public ACL broadcasts only while engine runs; no discovery, pairing, scan history, or startup replay
    - Android 12+ `BLUETOOTH_CONNECT` runtime permission required
9. **NFC tag scanned** (complete; Xiaomi configured-tag smoke test passed)
    - Selected normalized tag UID matching, with no NDEF payload or tag-tech persistence
    - Tag UID capture in Create/Edit while FlowPilot is open
    - Tag/tech discovery intent handoff evaluates only while engine runs

10. **Phone call triggers** (implementation complete; Xiaomi device smoke test pending)
    - Incoming call ringing (`CALL_RINGING`), answered (`CALL_ANSWERED`), outgoing call placed (`CALL_OUTGOING`), and call ended (`CALL_ENDED`).
    - State-only trigger matching every call of that state. Phone-number filtering removed because Android 12+ does not provide outgoing numbers to normal apps without default-dialer role; legacy filter-configured rules operate as state-only / any-number rules. Device validation for this removal has not been run on device.
    - Uses `TelephonyCallback` (API 31+) and `PhoneStateListener` (< 31) with edge transition deduplication and no startup replay.
    - Requires `android.permission.READ_PHONE_STATE`. Zero access to call logs or contacts.

11. **Device motion / flip triggers** (complete; Xiaomi device smoke test passed)
    - Phone placed face down on surface (`DEVICE_FLIPPED_DOWN`) and turned face up back to normal (`DEVICE_FLIPPED_UP`).
    - Dual physical verification: Proximity (NEAR) + Gravity/Accelerometer Z-axis ($Z \le -6.5 m/s^2$) + lateral horizontal stability ($\le 6.0 m/s^2$).
    - 500ms stability debounce prevents spurious triggers during hand rotation; startup seeding prevents immediate execution on engine start.
    - Dynamic sensor registration: sensors completely detached when no flip rules are enabled or when screen turns off (unless `flipScreenOffDetection = true`).
    - User-configurable screen-off detection with `SENSOR_DELAY_NORMAL` (~5Hz) and background battery-exemption notice.

## Planned Actions

### Phase 1 — App-level actions

1. **HTTP webhook** (complete; Xiaomi device smoke test passed)
   - Home Assistant
   - ntfy
   - Discord webhook
   - Custom HTTP endpoint
   - Dynamic template variables for headers & body: `${time}`, `${timestamp}`, `${batteryPercent}`, `${isCharging}`, `${wifiSsid}`, `${trigger}`
   - Requirements: method, headers, body, timeout, redacted secrets, explicit success/failure result
2. **Show notification** (complete)
    - Per-rule title and message
    - Android 13+ notification permission check and visible high-importance `automation_alerts_v2` channel
3. **Play sound** (complete; Xiaomi device smoke test passed)
   - Current notification, alarm, ringtone, or selected custom audio URI
   - Metadata duration display, 1-60 second playback limit, preview, and Stop preview
4. **Speak text / TTS** (complete; Xiaomi device smoke test passed)
   - Offline voice filter (`isNetworkConnectionRequired = false`)
   - Pre-synthesis during configuration to app-private cache
   - Zero-network, zero-synthesis execution at trigger time via MediaPlayer
   - Automatic orphan cache cleanup
5. **Vibrate** (complete; Xiaomi device smoke test passed)
6. **Launch app** (complete)
    - Selected launchable app, separate from app trigger picker
    - Verified from charger-connected and app-opened rules
7. **Open URL** (complete)
    - Validated `http` / `https` URLs through Android `ACTION_VIEW`
    - Verified on Xiaomi 15T Pro / HyperOS 3
8. **Create alarm or timer** (complete; Xiaomi device smoke test passed)
   - Alarm opens system Clock UI for confirmation.
   - Timer starts its countdown without opening Clock UI (`AlarmClock.EXTRA_SKIP_UI = true`).
9. **Set media volume** (complete; Xiaomi device smoke test passed)
    - Configurable 0-100% music-stream level
    - Uses `AudioManager.setStreamVolume` and verifies resulting level
10. **Phone call actions** (implementation complete; Xiaomi device smoke test pending)
    - Open dialer (`OPEN_DIALER` via `Intent.ACTION_DIAL`).
    - Prepare dialer with number (`DIAL_NUMBER` via `Intent.ACTION_DIAL` with `tel:URI`).
    - Direct phone call (`CALL_NUMBER` via `Intent.ACTION_CALL` with `tel:URI` and `android.permission.CALL_PHONE`).
    - Requires explicit user warning card and dialogs; phone numbers are masked in UI and omitted from logs, action results, and history.

11. **Manual test run** (complete; updated for in-progress action testing in both Create and Edit screens)
    - Available in both Create automation and Edit automation screens (TopAppBar Play icon and "Test" button beside Add action). Tests current on-screen form edits without saving first, with confirmation, trigger/condition bypass, unchanged saved rule state, direct-call warning, and secret redaction.
12. **Per-action delay** (implementation complete; Xiaomi smoke test pending)
    - Optional 0-300 second delay before each action
    - Actions remain sequential; engine stop cancels delay and records cancellation
13. **Per-rule cooldown** (complete; Xiaomi smoke test passed)
    - None, 1m, 5m, 15m, or 60m options
    - Applies after successful automatic runs across every trigger type; manual tests bypass it

### Phase 2 — HyperOS 3 system controls

Each must expose its required permission or Shizuku state. Do not show success unless target device state changes.

1. **Wi-Fi on/off** (implementation complete; unit tests passed; device smoke test pending)
    - Shizuku `svc wifi enable|disable` with bounded `WifiManager.isWifiEnabled` readback verification
2. **Bluetooth on/off** (complete; Xiaomi device smoke test passed)
    - Shizuku-only `svc bluetooth enable|disable`, strict bridge allowlist, and bounded `BluetoothAdapter.isEnabled` readback
    - Requires Android 12+ `BLUETOOTH_CONNECT`; standard app path intentionally absent
3. **Mobile data on/off** (implementation complete; unit tests passed; device smoke test pending)
    - Shizuku `svc data enable|disable` with `Settings.Global.mobile_data` / `TelephonyManager.isDataEnabled` readback verification
4. **Airplane mode on/off** (implementation complete; unit tests passed; device smoke test pending)
    - Shizuku `cmd connectivity airplane-mode enable|disable` with `Settings.Global.AIRPLANE_MODE_ON` readback verification
5. Location services on/off
6. **Hotspot on/off** (technical caveat: `cmd tethering` has no shell command implementation on Android 11+ / HyperOS 3; programmatic control requires signature-level `TETHER_PRIVILEGED`)
7. **Do Not Disturb on/off** (complete; Xiaomi device smoke test passed)
   - `NotificationManager.setInterruptionFilter` (`INTERRUPTION_FILTER_NONE` / `INTERRUPTION_FILTER_ALL`)
   - Requires user-grantable `android.permission.ACCESS_NOTIFICATION_POLICY` special access
8. **Auto-rotate on/off** (complete; Xiaomi device smoke test passed)
    - `Settings.System.ACCELEROMETER_ROTATION` write (1 = on, 0 = off / portrait lock)
    - Requires user-grantable `android.permission.WRITE_SETTINGS` special access (`Settings.System.canWrite(context)`)
9. **Create alarm or timer** (complete; Xiaomi device smoke test passed)
    - `AlarmClock.ACTION_SET_ALARM` opens Clock UI; `AlarmClock.ACTION_SET_TIMER` starts in background with `AlarmClock.EXTRA_SKIP_UI = true`
10. **Dark theme on/off** (complete; Xiaomi device smoke test passed)
    - Shizuku `cmd uimode night yes|no`; executor verifies `Settings.Secure.ui_night_mode` (2 = dark, 1 = light).
11. **Sound profile (Normal/Vibrate/Silent)** (implementation complete; device smoke test pending)
    - `AudioManager.ringerMode` (`RINGER_MODE_NORMAL` / `RINGER_MODE_VIBRATE` / `RINGER_MODE_SILENT`)
    - Requires user-grantable `android.permission.ACCESS_NOTIFICATION_POLICY` special access (`NotificationManager.isNotificationPolicyAccessGranted`)
12. **Flashlight (Torch) on/off** (implementation complete; unit tests passed; device smoke test pending)
    - Direct `CameraManager.setTorchMode` with rear camera flash hardware detection; requires zero permissions and no Shizuku.

## Existing Device-specific Controls

- NFC: Xiaomi 15T Pro / HyperOS 3 path is `svc nfc enable|disable` through Shizuku. Do not use `cmd nfc`; it crashed the device NFC service during testing.
- Battery Saver: use direct `WRITE_SECURE_SETTINGS` write first when granted. Shizuku fallback uses `cmd power set-mode <0|1>` with settings fallback.
- NFC and Battery Saver action paths passed Xiaomi device smoke tests.

## Next validation

1. **Phone call automations device smoke test**
   - Place incoming test call to device; verify `CALL_RINGING` trigger fires (e.g. shows notification or vibrates).
   - Answer incoming call; verify `CALL_ANSWERED` trigger fires.
   - Hang up; verify `CALL_ENDED` trigger fires.
   - Test call triggers matching broadly on state regardless of incoming/outgoing number.
   - Test `OPEN_DIALER`, `DIAL_NUMBER`, and `CALL_NUMBER` actions on Xiaomi 15T Pro / HyperOS 3.
   - Verify permission denial path for `READ_PHONE_STATE` and `CALL_PHONE`.
    - Verify execution history records no phone numbers, raw or masked; summaries and home show state-only summaries for call triggers and masked numbers only for dial/call actions (`+905 •••• 567`).
2. **Sound profile permission denial smoke test**
     - Normal, Vibrate, and Silent passed basic Xiaomi smoke testing; Xiaomi maps Vibrate and Silent to the same observed ringer behavior. Do not generalize this result to other devices.
     - Deny access and verify an honest failure result.
2. **Permission denial paths**
     - Stop or deny Shizuku and confirm Dark theme reports failure instead of success.
     - Deny Modify system settings and Do Not Disturb access; confirm Auto-rotate and DND report failure instead of success.
3. **HyperOS 3 system controls**
     - One control at a time; device-state evidence required before marking complete.
4. **Bluetooth negative paths**
     - Deny `BLUETOOTH_CONNECT`; verify bonded-device picker and rules show permission-required state.
     - Restart engine while device remains connected; verify no replay.
     - Unpair selected device; verify no crash and no false match.
     - Stop/deny Shizuku and verify Bluetooth on/off failures remain explicit.
5. **NFC tag and action delay**
     - Scan different tag UID with engine running; verify it does not fire.
     - Scan with engine stopped and NFC disabled; verify no action or false success.
     - Add a visible action after 5 seconds; verify timing, order, stop cancellation, and history.

## Acceptance Gate

No feature is marked complete until:

- Unit tests pass.
- Debug APK builds.
- Xiaomi 15T Pro / HyperOS 3 smoke test passes.
- Duplicate event, delayed event, and unavailable-permission cases are checked.
- Any device-specific limitation is documented.
