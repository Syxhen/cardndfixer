# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2026-11-24

First public version.

### Added
- Automatic Do Not Disturb disable → delay → re-enable cycle on car Bluetooth
  disconnect via Android `CompanionDeviceService`, supporting Android 12 (API
  31) through Android 17 (API 37).
- Jetpack Compose + Material 3 dashboard with dynamic color support, live DND
  status, setup checklist (DND access, Bluetooth permission, and battery
  optimization), and car Bluetooth device management.
- Configurable **Re-enable delay** (`0.5 s`–`10.0 s`, default `1.5 s`),
  **Wait after disconnect** debounce (`0 s`–`15 s`, default `3 s`), and
  **Only re-enable if DND was ON** toggle persisted via DataStore Preferences.
- Toggleable **Diagnostics & Log Collector** with MAC-address redaction and
  one-tap Share/Copy report export.
- Side-by-side **Debug APK** (`cardndfixer-debug.apk`) with diagnostic logging
  enabled by default and **Sim Connect / Sim Disconnect** buttons for testing
  without a car.
- Custom Material 3 adaptive launcher icon (including Android 13+ monochrome
  themed icon layer).
- Unit tests (`DndCycleCoordinatorTest`) covering disconnect debounce,
  reconnect cancellation, DND filter preservation, missing permissions, log
  serialization, and MAC redaction.
- GitHub Actions CI and tag-driven dual-APK release workflow.

[Unreleased]: https://github.com/Syxhen/cardndfixer/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Syxhen/cardndfixer/releases/tag/v0.1.0
