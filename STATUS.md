# FlowPilot Status

Last updated: 2026-09-01

## Build state

- Debug build and unit tests passed: `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon`.
- Latest debug APK was installed and launched on Xiaomi 15T Pro / HyperOS 3.
- Current source changes are ready to commit.

## Device-verified features

- Time schedules, charger, battery threshold, screen, Wi-Fi, and notification triggers.
- Notifications, app launch, URL opening, alarm, timer, offline TTS, media volume, vibration, Play sound, webhook base action, NFC, Battery Saver, Auto-rotate, Do Not Disturb, and Dark theme actions.
- Webhook header/body template variables and unknown-token preservation.
- Manual test run from Edit automation, including confirmation, saved-action execution, result feedback, and unchanged rule state.
- Persistent run history for engine and manual executions: action-level outcomes, 100-entry newest-first retention, and redacted diagnostics.
- Bluetooth bonded-device connected/disconnected triggers and Bluetooth on/off Shizuku actions, including delayed adapter-state readback.
- Create/Edit keyboard behavior: keyboard opens only for focused fields, form scroll remains available, and a focused field returns above the IME when typing after manual scrolling.

## Implemented; device validation pending

- Sound profile denied Notification Policy Access behavior.
- Run history screen smoke test on Xiaomi 15T Pro / HyperOS 3.

## Current constraints

- Dark theme, NFC, and Bluetooth on/off require active Shizuku permission. Bluetooth on/off also requires `BLUETOOTH_CONNECT` on Android 12+ and verifies adapter state after exact allowlisted `svc bluetooth enable|disable`.
- Battery Saver needs `WRITE_SECURE_SETTINGS` or Shizuku.
- Webhook secrets are Android Keystore AES-256-GCM encrypted at rest; Android backups are disabled.
- Bluetooth triggers use selected bonded devices, public ACL broadcasts, and `BLUETOOTH_CONNECT` on Android 12+; no discovery, pairing, scan history, or startup replay. Bluetooth device/profile behavior can still differ on other OEMs.
- Xiaomi 15T Pro maps Sound profile Vibrate and Silent to the same observed ringer behavior; other devices can differ.

## Next validation

See `ROADMAP.md` for exact device smoke-test scenarios.
