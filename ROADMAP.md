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
4. **Screen state** (implementation complete; device smoke test pending)
   - Screen on
   - Screen off
   - Dynamic broadcasts while engine runs; duplicate events deduped and no startup replay
5. **Headset state**
   - Wired headset connected/disconnected
   - Bluetooth audio device connected/disconnected

### Phase 2 — Network/device context

6. **Wi-Fi network state**
   - Connected to selected SSID
   - Disconnected from selected SSID
7. **Bluetooth device state**
   - Selected Bluetooth device connected/disconnected

## Planned Actions

### Phase 1 — App-level actions

1. **HTTP webhook**
   - Home Assistant
   - ntfy
   - Discord webhook
   - Custom HTTP endpoint
   - Requirements: method, headers, body, timeout, redacted secrets, explicit success/failure result
2. **Show notification** (complete)
    - Per-rule title and message
    - Android 13+ notification permission check and visible high-importance `automation_alerts_v2` channel
3. **Play sound** (implementation complete; device smoke test pending)
   - Current notification, alarm, ringtone, or selected custom audio URI
   - Metadata duration display, 1-60 second playback limit, preview, and Stop preview
4. **Speak text / TTS** (complete; Xiaomi device smoke test passed)
   - Offline voice filter (`isNetworkConnectionRequired = false`)
   - Pre-synthesis during configuration to app-private cache
   - Zero-network, zero-synthesis execution at trigger time via MediaPlayer
   - Automatic orphan cache cleanup
5. **Vibrate** (implementation complete; device smoke test pending)
6. **Launch app** (complete)
    - Selected launchable app, separate from app trigger picker
    - Verified from charger-connected and app-opened rules
7. **Open URL** (complete)
    - Validated `http` / `https` URLs through Android `ACTION_VIEW`
    - Verified on Xiaomi 15T Pro / HyperOS 3
8. **Create alarm or timer** (complete; Xiaomi device smoke test passed)
   - Alarm opens system Clock UI for confirmation.
   - Timer starts its countdown without opening Clock UI (`AlarmClock.EXTRA_SKIP_UI = true`).
9. **Set media volume** (implementation complete; device smoke test pending)
    - Configurable 0-100% music-stream level
    - Uses `AudioManager.setStreamVolume` and verifies resulting level

### Phase 2 — HyperOS 3 system controls

Each must expose its required permission or Shizuku state. Do not show success unless target device state changes.

1. Wi-Fi on/off
2. Bluetooth on/off
3. Mobile data on/off
4. Airplane mode on/off
5. Location services on/off
6. Hotspot on/off
7. **Do Not Disturb on/off** (implementation complete; unit tests added, device smoke test pending)
   - `NotificationManager.setInterruptionFilter` (`INTERRUPTION_FILTER_NONE` / `INTERRUPTION_FILTER_ALL`)
   - Requires user-grantable `android.permission.ACCESS_NOTIFICATION_POLICY` special access
8. **Auto-rotate on/off** (implementation complete; target ADB tested, app device smoke test pending)
    - `Settings.System.ACCELEROMETER_ROTATION` write (1 = on, 0 = off / portrait lock)
    - Requires user-grantable `android.permission.WRITE_SETTINGS` special access (`Settings.System.canWrite(context)`)
9. **Create alarm or timer** (complete; Xiaomi device smoke test passed)
    - `AlarmClock.ACTION_SET_ALARM` opens Clock UI; `AlarmClock.ACTION_SET_TIMER` starts in background with `AlarmClock.EXTRA_SKIP_UI = true`
10. Dark theme on/off
11. Sound profile
    - Silent
    - Vibrate
    - Normal

## Existing Device-specific Controls

- NFC: Xiaomi 15T Pro / HyperOS 3 path is `svc nfc enable|disable` through Shizuku. Do not use `cmd nfc`; it crashed the device NFC service during testing.
- Battery Saver: use direct `WRITE_SECURE_SETTINGS` write first when granted. Shizuku fallback uses `cmd power set-mode <0|1>` with settings fallback.

## Proposed Implementation Order

1. **Media volume device smoke test**
    - Create `Charger connected -> Set media volume -> 20%`.
    - Reconnect charger and confirm Xiaomi media volume changes to 20%.
    - Repeat with 0% and 100%; verify a blocked change reports failure.
2. **Vibration device smoke test**
    - Verify every preset, duration, and strength control on device.
    - Confirm selected duration is total waveform duration.
3. **Screen and sound device smoke tests**
   - Confirm screen on/off rules run only after a new display transition.
   - Confirm system/custom sounds honor selected 1-60 second duration and Stop preview.
4. **Auto-rotate app-path device smoke test**
    - Grant Modify system settings to FlowPilot.
    - Trigger Auto-rotate off then on; confirm quick settings state and phone orientation behavior.
    - Deny special access and confirm rule reports failure instead of success.
5. **HTTP webhook action**
    - Add method, headers, body, bounded timeout, redacted secrets, and explicit response result.
6. **DND device smoke test**
    - Verify DND on/off transitions and permission error path on Xiaomi 15T Pro / HyperOS 3.
7. **Headset triggers**
   - Add wired and Bluetooth audio state one family at a time with startup baseline and duplicate-event tests.
8. **Wi-Fi and Bluetooth device/context triggers**
    - Add selected SSID/device persistence and unavailable-permission states.
9. **HyperOS 3 system controls**
    - One control at a time; device-state evidence required before marking complete.

## Acceptance Gate

No feature is marked complete until:

- Unit tests pass.
- Debug APK builds.
- Xiaomi 15T Pro / HyperOS 3 smoke test passes.
- Duplicate event, delayed event, and unavailable-permission cases are checked.
- Any device-specific limitation is documented.
