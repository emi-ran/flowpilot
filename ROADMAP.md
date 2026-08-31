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
2. **Charger state**
   - Charger connected
   - Charger disconnected
3. **Battery threshold**
   - Below selected percentage, such as 20%
   - Above selected percentage, such as 80%
   - Edge-triggered; must not fire repeatedly while remaining past threshold
4. **Screen state**
   - Screen on
   - Screen off
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
2. **Show notification**
3. **Play sound**
4. **Vibrate**
5. **Launch app**
6. **Open URL**
7. **Create alarm or timer**
8. **Set media volume**

### Phase 2 — HyperOS 3 system controls

Each must expose its required permission or Shizuku state. Do not show success unless target device state changes.

1. Wi-Fi on/off
2. Bluetooth on/off
3. Mobile data on/off
4. Airplane mode on/off
5. Location services on/off
6. Hotspot on/off
7. Do Not Disturb on/off
8. Auto-rotate on/off
9. Dark theme on/off
10. Sound profile
    - Silent
    - Vibrate
    - Normal

## Existing Device-specific Controls

- NFC: Xiaomi 15T Pro / HyperOS 3 path is `svc nfc enable|disable` through Shizuku. Do not use `cmd nfc`; it crashed the device NFC service during testing.
- Battery Saver: use direct `WRITE_SECURE_SETTINGS` write first when granted. Shizuku fallback uses `cmd power set-mode <0|1>` with settings fallback.

## Proposed Implementation Order

1. Charger trigger
2. Battery threshold trigger
3. Notification, vibration, sound, app launch, URL actions
4. HTTP webhook action
5. Media volume and alarm/timer actions
6. Screen and headset triggers
7. Wi-Fi and Bluetooth device/context triggers
8. HyperOS 3 system controls, one action at a time

## Acceptance Gate

No feature is marked complete until:

- Unit tests pass.
- Debug APK builds.
- Xiaomi 15T Pro / HyperOS 3 smoke test passes.
- Duplicate event, delayed event, and unavailable-permission cases are checked.
- Any device-specific limitation is documented.
