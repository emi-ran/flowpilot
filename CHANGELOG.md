# Changelog

All notable FlowPilot changes are documented here.

## Unreleased

### Added

- Sound profile actions for Normal, Vibrate, and Silent with Notification Policy Access checks and ringer-mode readback.
- Dark theme actions through Shizuku with post-command state verification.
- Webhook header/body template variables for live automation context.
- Webhook secret encryption at rest using Android Keystore AES-256-GCM and legacy configuration migration.
- Manual saved-rule test run from Edit automation with confirmation and result feedback.
- Persistent automation run history for engine and manual executions, with per-action outcomes, overall success/partial/failure state, and 100-entry retention.
- Bluetooth bonded-device connected/disconnected triggers with Android 12+ `BLUETOOTH_CONNECT` gating, public ACL broadcast lifecycle, no startup replay, and per-device duplicate suppression.
- Bluetooth on/off actions through exact allowlisted Shizuku `svc bluetooth enable|disable` commands with adapter-state readback verification.
- NFC tag scanned trigger with normalized selected tag UID matching, transient intent handoff, and no NDEF payload or tag-tech persistence.
- Optional per-action 0-300 second pre-execution delays, sequential action order, cancellation, and run-history recording.
- Per-rule cooldown options (None, 1m, 5m, 15m, 60m) for all automatic triggers after successful execution; manual tests bypass cooldown and skipped runs create no history spam.

### Changed

- Android backup is disabled to prevent webhook credential exposure through backup snapshots.
- Create/Edit form keyboard behavior uses Android `adjustResize`, IME-aware padding, and focus-gated bring-into-view scrolling. Keyboard opens only on text-field focus; focused fields return above the IME while typing.
- Webhook logging and execution failures redact sensitive values.
- Run-history diagnostics redact sensitive values and never persist webhook or action configuration fields.
- Bluetooth ACL receiver uses an Android 13+ exported dynamic receiver required for Bluetooth-stack delivery; Bluetooth radio actions now wait for asynchronous adapter-state changes before reporting success or failure.
