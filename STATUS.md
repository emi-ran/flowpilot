# FlowPilot Status

Last updated: 2026-09-01

## Build state

- Debug build and unit tests pass: `.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon`.
- Latest debug APK is installed on Xiaomi 15T Pro / HyperOS 3.
- `com.flowpilot.app` was launched after the latest APK install.
- Current source changes are not yet committed.

## Device-verified features

- Time schedules, charger, battery threshold, screen, Wi-Fi, and notification triggers.
- Notifications, app launch, URL opening, alarm, timer, offline TTS, media volume, vibration, Play sound, webhook base action, NFC, Battery Saver, Auto-rotate, Do Not Disturb, and Dark theme actions.
- Webhook header/body template variables and unknown-token preservation.
- Manual test run from Edit automation, including confirmation, saved-action execution, result feedback, and unchanged rule state.
- Create/Edit keyboard behavior: keyboard opens only for focused fields, form scroll remains available, and a focused field returns above the IME when typing after manual scrolling.

## Implemented; device validation pending

- Sound profile denied Notification Policy Access behavior.

## Current constraints

- Dark theme and NFC require active Shizuku permission.
- Battery Saver needs `WRITE_SECURE_SETTINGS` or Shizuku.
- Webhook secrets are Android Keystore AES-256-GCM encrypted at rest; Android backups are disabled.
- Wired headset and Bluetooth device triggers are deferred and must not be prioritized unless explicitly requested.
- Xiaomi 15T Pro maps Sound profile Vibrate and Silent to the same observed ringer behavior; other devices can differ.

## Next validation

See `ROADMAP.md` for exact device smoke-test scenarios.
