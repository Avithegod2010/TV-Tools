# Changelog

All notable changes to TV Tools are documented here.

The project follows [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`.
Versions before 1.2 were development milestones and were not published as signed releases.

## [1.8.1] — 2026-11-07

### Fixed
- **Remote lag on older TVs.** TV Tools now pushes a ~2 KB injector to the TV and
  keeps it running under `app_process`; each key press is a line on an existing
  pipe instead of starting a new `input`/ART process. On the Amlogic JHSD200 this
  removes the largest remaining per-press delay. The injector only listens on the
  app's private ADB stream, exits on disconnect, and falls back to the previous
  input shell on TVs that block `app_process`.
- VersionCode 11 / versionName 1.8.1.

## [1.8.0] — 2026-11-07

### Added
- **Connection watchdog & auto-reconnect:** a heartbeat over the input shell checks the TV every
  15 seconds; two failures trigger a reconnect, and a successful reconnect shows a snackbar. A
  screenshot that hits a silent socket drop retries once instead of failing with a cryptic error.
- **Music v2:** MediaStore title/artist/album/duration, a real queue with *Play next* /
  *Add to queue*, a queue sheet, queue-aware next/previous, and a **persistent mini-player** on the
  device screen so controls stay available on Tools/Apps/Shell/Info.
- **Gallery v2:** per-TV folders, date-grouped timeline, multi-select share/delete, and
  **Show on TV** (pushes a capture to the TV and opens it there).
- **Performance card** in Settings: local cold-start time (process → first frame) and a
  frame-time jank percentage, with reset. Nothing is uploaded.
- AndroidX ProfileInstaller, so library baseline profiles are installed on first launch.
- F-Droid fastlane metadata, `docs/FDROID.md`, `docs/CI.md` and `docs/PERFORMANCE.md`.
- Instrumentation launch smoke test and a manual/weekly emulator workflow.
- CI now builds an unsigned release APK and runs lint as a separate, non-blocking job.

### Changed
- **targetSdk 35** with an edge-to-edge pass (onboarding insets, bottom-sheet navigation padding).
- Music scanning uses Android's MediaStore first; the old `find` walk is a capped fallback, so
  the list no longer waits on a huge Downloads folder.
- Per-TV screenshots live in `files/screenshots/<model>/`; legacy captures stay readable under
  *Unsorted*.
- VersionCode 10 / versionName 1.8.0.

### Fixed
- Details in the remote, Stats tab and screenshots no longer sit on a dead ADB socket without
  telling the user.
- Clearing the screenshot gallery now deletes per-TV folders too.

## [1.7.0] — 2026-11-06

### Added
- **Screenshot retention:** choose how many captures to keep (10 / 25 / 50 / 100 / unlimited).
  Older screenshots are deleted automatically; the gallery can also be cleared from Settings.
- **Customisable Tools tab:** pin the tools you actually use, search the full catalogue in
  **All features**, and pin or unpin anything with one tap.
- **Music player redesign:** Material You tonal cards, generated cover art, search, sort by name
  or size, multi-track upload, a now-playing bar with progress and play/pause/next/previous, and
  a shuffle button.

### Changed
- **Remote latency:** key presses and text now go through one persistent ADB input shell instead
  of opening a new shell for every press. Press-and-hold repeats drop extra events instead of
  building a backlog, and the input channel is pre-opened when the remote sheet appears.
- Remote status polling is slower (12 s) and pauses for 3 s after any input, so
  `dumpsys media_session` can no longer delay key presses.
- Info-tab stats polling pauses while the remote sheet is open.

### Fixed
- The app no longer captures a TV screenshot in the background every 20 seconds. Screenshots are
  taken only when you tap **Take screenshot**, and the home thumbnail updates from that capture.
- Entering the Tools tab no longer triggers an automatic screenshot.

## [1.6.1] — 2026-11-06

A quality-only release: no new screens, but a much smaller APK and a real test/CI net.

### Added
- 45 JVM unit tests covering the ADB/media parsers, endpoint parsing, WOL packets, macro and
  quick-key codecs, package listings and the crash-report formatter.
- GitHub Actions workflow that runs the unit tests and assembles a debug APK on every push and PR.
- Gradle wrapper scripts so the project builds with `./gradlew` on any machine and in CI.
- Local crash capture (`Diagnostics`) with a **Copy diagnostics** button in Settings. Reports stay
  in app-private storage and are never uploaded.
- `CHANGELOG.md`, `CONTRIBUTING.md`, `SECURITY.md`, issue/PR templates and release docs.
- Dependabot configuration for Gradle and GitHub Actions updates.

### Changed
- **APK size: 23.5 MB → 12.0 MB** (−49%) with R8 code and resource shrinking. Keep rules cover
  libadb, Conscrypt, the repackaged Bouncy Castle, JSch and ZXing.
- The 2,200-line `RemoteControlSheet.kt` is now split into `RemoteControlSheet` (orchestration),
  `RemotePads` (D-pad, touchpad, gesture, gamepad) and `RemoteDialogs` (keyboard, macros, media,
  history, power), with shared key codes in `RemoteKeys`.
- Custom remote controls expose button semantics, so TalkBack can focus and activate them.
- The Settings footer reads the installed version instead of a hard-coded string.
- Media polling in the remote now runs every 6 s (was 4 s) and pauses during broadcast.
- AGP 8.5.2 → 8.7.3, Kotlin 2.0.20 → 2.0.21, Compose BOM 2024.10.01, lifecycle/navigation/datastore
  patch updates.

### Fixed
- Settings showed a stale hard-coded version in the footer.
- CI installs the Android 35 platform and build tools explicitly instead of relying on the runner image.

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
