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

### Changed

- Android backup is disabled to prevent webhook credential exposure through backup snapshots.
- Create/Edit form keyboard behavior uses Android `adjustResize`, IME-aware padding, and focus-gated bring-into-view scrolling. Keyboard opens only on text-field focus; focused fields return above the IME while typing.
- Webhook logging and execution failures redact sensitive values.
- Run-history diagnostics redact sensitive values and never persist webhook or action configuration fields.
