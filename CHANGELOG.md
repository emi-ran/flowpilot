# Changelog

All notable FlowPilot changes are documented here.

## Unreleased

### Added

- Sound profile actions for Normal, Vibrate, and Silent with Notification Policy Access checks and ringer-mode readback.
- Dark theme actions through Shizuku with post-command state verification.
- Webhook header/body template variables for live automation context.
- Webhook secret encryption at rest using Android Keystore AES-256-GCM and legacy configuration migration.
- Manual saved-rule test run from Edit automation with confirmation and result feedback.

### Changed

- Android backup is disabled to prevent webhook credential exposure through backup snapshots.
- Create/Edit form keyboard behavior now uses Android `adjustResize` rather than extra IME content padding.
- Webhook logging and execution failures redact sensitive values.
