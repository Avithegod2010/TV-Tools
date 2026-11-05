# Changelog

All notable changes to TV Tools are documented here.

The project follows [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`.
Versions before 1.2 were development milestones and were not published as signed releases.

## [Unreleased]

### Added
- 45 JVM unit tests covering the ADB/media parsers, endpoint parsing, WOL packets, macro and
  quick-key codecs, package listings and the crash-report formatter.
- GitHub Actions workflow that runs the unit tests and assembles a debug APK on every push and PR.
- Gradle wrapper scripts so the project builds with `./gradlew` on any machine and in CI.
- Local crash capture (`Diagnostics`) with a **Copy diagnostics** button in Settings. Reports stay
  in app-private storage and are never uploaded.
- `CHANGELOG.md`, `CONTRIBUTING.md`, `SECURITY.md`, issue/PR templates and release docs.

### Changed
- The 2,200-line `RemoteControlSheet.kt` is now split into `RemoteControlSheet` (orchestration),
  `RemotePads` (D-pad, touchpad, gesture, gamepad) and `RemoteDialogs` (keyboard, macros, media,
  history, power), with shared key codes in `RemoteKeys`.
- Custom remote controls expose button semantics, so TalkBack can focus and activate them.
- The Settings footer reads the installed version instead of a hard-coded string.

### Fixed
- Assemble/release builds can no longer silently drift from the documented target SDK.

## [1.6] — 2026-11-05

### Added
- **Connect:** QR handoff, opt-in auto-connect, network map, connection score, hotspot guide,
  IPv6 + custom ports, remembered pairing ports, wake scheduler, batch connect, rooms & groups,
  guest mode, connection doctor and an mDNS service browser.
- **Remote:** live keyboard, voice typing, Assistant key, macro recorder, per-app shortcut rows,
  now-playing card with seek, sleep timer with volume fade, ring-the-TV beep, custom quick keys,
  gesture pad, gyro air mouse, multi-touch trackpad, physical gamepad mapping, broadcast to a room
  or all TVs, key history with undo, and a floating overlay bubble.
- New ADB helpers for key combinations, Assistant, mouse events, foreground app, media sessions,
  relative seek and media volume.

### Changed
- Remote sheet content scrolls, so every mode fits on small phones.

## [1.5] — 2026-11-05

### Added
- Live TV thumbnail, radar discovery animation, shared-element transitions, AMOLED theme and
  automatic day/night theme.
- Per-TV accent colors and a palette sampled from the TV screen.
- Four remote skins and three launcher icon variants.
- Screenshot gallery, grid/list file manager and animated health rings.
- Haptic profiles and optional key-click sounds.

## [1.4] — 2026-11-05

### Added
- Labs: DLNA casting, SFTP browser, speed test, live TV inputs, mouse & keyboard, health monitor,
  TV users, IR blaster, kids mode, launchpad and WorkManager automations.
- External app-data backup and AES-256-GCM encrypted backup export/import.
- Wake & reconnect, ambient mode and screen-timeout controls.

## [1.3] — 2026-11-04

### Added
- Channel keys and a 0–9 pad, gamepad mode, screen recording and a music manager.
- Real app icons, batch app actions, phone-app install and multi-file/folder upload.
- Remote moved into the top app bar.

### Fixed
- APK size lookup on strict TV `adbd` builds.

## [1.2] — 2026-11-04

### Added
- App backup/restore with split-APK support, storage analyzer and a multi-TV dashboard.
- Multi-select file operations, download queue and previews.
- Quick Settings tile, home-screen widget and onboarding.
- Signed release builds.

## [1.1] — 2026-11-04

### Added
- Initial public source drop: wireless ADB connect/pair, app management, file manager, shell,
  device info, screen mirroring, share-to-TV, power/Wake-on-LAN and the first Compose remote.
