# TV Tools — Developer Notes

Android app that manages Android TV / Google TV / Xiaomi TV / Fire TV devices wirelessly over the **ADB protocol** (inspired by atvTools).

## Stack
- Kotlin + Jetpack Compose (Material 3), MVVM + StateFlow, Navigation Compose
- ADB: `com.github.MuntashirAkon:libadb-android` (pure-Java ADB client, TLS pairing for Android 11+)
  - `com.github.MuntashirAkon:sun-security-android` → X509 cert generation (package prefix `android.sun.security.*`)
  - `org.conscrypt:conscrypt-android` → TLS 1.3 provider
- DataStore for settings + last-connected device

## Build
- JDK 17, Android SDK 35 (set `ANDROID_HOME`)
- `./gradlew assembleDebug` (wrapper included; or a local Gradle 8.9 install: `gradle assembleDebug --no-daemon`)
- `./gradlew testDebugUnitTest` — JVM tests for parsers/codecs (no device needed)
- `compileSdk 35`, `targetSdk 34` (bumping to 35 needs an edge-to-edge pass)
- Low-RAM machines: cap `org.gradle.jvmargs` (≈1.2 GB) and use `kotlin.compiler.execution.strategy=in-process`
- **Kotlin has no octal literals** — use decimals for ADB mode bits (0644 = 420, S_IFDIR = 16384, S_IFMT = 61440)
- **Kotlin nested comments**: never write `/*` inside a comment (e.g. `/sys/class/net/*/address` breaks parsing)
- APK output → `app/build/outputs/apk/debug/app-debug.apk`

## Architecture
- `adb/`
  - `AdbKeyStore.kt` — persisted RSA keypair + self-signed cert (app's ADB identity)
  - `TvAdbConnectionManager.kt` — singleton `AbsAdbConnectionManager` (connect/pair/disconnect/openStream)
  - `AdbClient.kt` — high-level coroutine API (exec shell, file ops, screencap, install, key events)
  - `AdbSync.kt` — ADB `sync:` protocol v1 (LIST/STAT/RECV/SEND)
  - `DeviceDiscovery.kt` — NSD/mDNS (`_adb-tls-connect._tcp`, `_adb._tcp`, `_adb-tls-pairing._tcp`) + subnet scan for port 5555
  - `DeviceInfoProvider.kt` — /proc & getprop parsing
- `data/` — models, `Prefs` (last device/MAC), `Settings` (theme/haptics)
- `ui/` — `home`, `device` (Tools/Apps/Shell/Info), `files`, `mirror`, `remote`, `settings`, `share`
- `AppServices` — service locator singleton

## Real-device findings
- `pm list packages -f` paths contain `=` (`/data/app/~~xxx==/pkg/base.apk`) → parse in Kotlin with `substringBeforeLast('=')`; batch sizes via one `stat -c '%s %n'` call.
- `pm clear --cache-only` is missing on many TVs — use `pm trim-caches 32G`.
- Cheap TV boxes often allow **one ADB client at a time** — disconnect other computers before testing.
- TV Wi-Fi can be very slow: don't auto-pull large APKs for icons (small user apps only, ≤40 MB); lazy version lookup via `dumpsys package <pkg> | grep -m1 versionName`.
- `screencap` can hang on flaky links → all `AdbClient.exec` calls have hard timeouts.
- MAC for Wake-on-LAN: `ip -o link show wlan0` (sysfs address is often restricted).
- `pm trim-caches`, `input keyevent`, `screencap -p <path>` + sync pull are the reliable primitives.

## Protocol notes
- Shell: `openStream("shell:<cmd>")`, read until EOF; libadb throws `IOException("Stream closed.")` when the remote closes with an empty queue → treat as EOF.
- Files: `openStream("sync:")` + AdbSync for pulls; pushes use `shell:cat > path` because some TVs stall the sync SEND path.
- Pairing (Android 11+): `manager.pair(host, pairingPort, code)` then connect to the port from the `_adb-tls-connect` advert.
- Fire TV legacy: connect to `ip:5555`; first attempt triggers the RSA prompt on the TV.

## Security notes
- No analytics, no telemetry, no network calls except to the user's TV.
- ADB private key + cert stay in app-private storage (`files/adbkeys`).
- ADB TLS uses the library's trust-all manager (inherent to ADB pairing).

## Feature map (v1.6)
- Connect: `ui/connect/QrHandoffScreen`, `NetworkMapScreen`, `HotspotGuideScreen`, `ConnectionDoctorScreen`, `MdnsBrowserScreen`, `RoomsScreen`; routes `qr`, `netmap`, `hotspot`, `doctor`, `mdns`, `rooms`.
- `Prefs`: pairing-port memory, rooms, macros, quick-key layout.
- `Settings`: autoConnect, wakeEnabled/wakeMinutes/wakeOnWifi, guestMode, bubble.
- Remote: `ui/remote/RemoteControlSheet.kt` (orchestration), `RemotePads.kt` (D-pad, touchpad, gesture, gamepad), `RemoteDialogs.kt` (keyboard, macros, media, history, power), `RemoteKeys.kt` (key codes, mode enum). Header assistant key, live keyboard + voice typing, macro recorder, per-app rows, now playing + seek, sleep timer, ring TV, custom quick keys, broadcast mode, key history/undo.
- Tooling: 45 JVM tests in `app/src/test`, GitHub Actions (`android.yml`), Gradle wrapper, local crash diagnostics (`data/Diagnostics.kt`).
- `data/SleepTimer`, `data/Macro`, `data/QuickKey`, `data/MediaSession`, `util/BeepWav`, `service/OverlayBubbleService` (SYSTEM_ALERT_WINDOW + FGS specialUse).
- `AdbClient` extras: `inputKeyCombo`, `assistant`, `mouseMove/Down/Up`, `foregroundPackage`, `nowPlaying`, `seekRelative`, `setMediaVolume`, `getMediaVolume`, `mediaCommand`.

## Feature map (v1.5)
- Visuals: Home live thumbnail (`DeviceViewModel` writes `files/tvthumbs/<hosthash>.png` every 20s; HomeViewModel loads it), radar `RadarScan` Canvas, animated logo (`graphicsLayer`), shared-element device title (`SharedTransitionLayout` in MainActivity → Home/Device params).
- Themes: `ThemeMode.AMOLED` (pure black), `AUTO` (7–19h), `Accent.TV` from `AppSettings.tvColor` (SettingsSheet "Match TV colors" captures a screencap and extracts a saturated color).
- Per-TV accents: `Prefs.saveDeviceAccent/loadDeviceAccent`; applied on connect; "Save accent for this TV" in the device menu.
- Remote skins: `RemoteSkin` (Classic/Circular/Minimal/Gamepad-first) in `RemoteControlSheet`.
- Launcher icons: activity-aliases `.MainDefault/.MainRemote/.MainMono`; `SettingsSheet.setLauncherIcon` toggles components.
- Haptics/sounds: `Haptics.kt` uses `View.performHapticFeedback` with strength profiles + `AudioManager.FX_KEY_CLICK`.
- Gallery: `ui/gallery/GalleryScreen` (files in `files/screenshots`; ToolsViewModel copies each capture).
- Files: grid/list toggle (`FilesUiState.grid`).
- Health: animated `ArcGauge` rings in `Labs.HealthCard`.
- Widgets: full remote + compact 2×1 (`CompactRemoteWidgetProvider`).
- Typography: `AppTypography` in `Theme.kt`.

## Feature map (v1.4)
- `ui/labs/Labs.kt`: DLNA cast (SSDP + AVTransport SOAP + single-file HTTP server), speed test (8 MB push/pull + ping), live TV (`dumpsys tv_input`), mouse/keyboard (`input motionevent`), health monitor (sparklines), users (`pm list users` + `am switch-user`), IR (`ConsumerIrManager`), kids mode (disable/enable user apps), launchpad (favorites + launch).
- `ui/labs/SftpScreen.kt`: SFTP browser via jsch — browse and push files straight to the TV.
- `data/Automation.kt`: WorkManager daily cache trim + daily screenshot; scheduled from SettingsRepository flags; `ui/labs/AutomationsScreen.kt`.
- `data/BackupCrypto.kt`: AES-256-GCM (PBKDF2) encrypted export/import of the backups folder; UI in `BackupsScreen`.
- `data/BackupManager`: external app-data backup/restore (tar of `/sdcard/Android/data/<pkg>`); note internal data requires root.
- Wake & reconnect: `HomeViewModel.wakeAndReconnect` (WOL + retries); ambient + timeout in the remote power dialog.
- Tools has a "Labs" grid wiring all new screens.

## Feature map (v1.3)
- Remote: `RemoteControlSheet` has D-Pad / Touchpad / Gamepad modes, channel keys (166/167), number pad (keycodes 7–16), text input, power dialog.
- Screen record: `ToolsViewModel.screenRecord` (`screenrecord --time-limit N`, pull, MediaStore Movies/TV Tools).
- Music manager: `ui/music/` scans `/sdcard/Music` + `/sdcard/Download`, plays on TV via VIEW intent, download/delete/upload.
- Phone-app install: long-press Install → `ToolsViewModel.loadPhoneApps` (PackageManager) → copies sourceDir/splits → installs on TV.
- Multi-upload: long-press Upload → multi-file (`GetMultipleContents`) or folder (`OpenDocumentTree` + DocumentFile recursion).
- App selection: batch backup/apks/force-stop/clear-data/disable/uninstall in `AppsViewModel`.
- Real icons: background loader pulls APKs ≤60 MB sorted smallest-first with progress (`iconsLoaded/iconsTotal`).
- Remote entry moved to the top app bar (removed the overlapping FAB).

## Feature map (v1.2)
- Backup/restore: `data/BackupManager.kt` (APK paths via `pm path`, metadata in `meta.txt`), `ui/apps/BackupsScreen.kt`; split install via `AdbClient.installSplitApk` (`pm install-multiple`), `.apks/.xapk` unpacked in `ToolsViewModel`.
- Storage analyzer: `ui/storage/` — `du -sk`, `find -size +50M` + batch `stat`.
- Multi-TV: saved devices in `Prefs` (`stringSetPreferencesKey`), chips on Home.
- File manager: multi-select + copy/move (`cp -r`/`mv`), previews, download queue in `FilesViewModel`.
- QS tile: `tile/RemoteTileService.kt`; widget: `widget/RemoteWidgetProvider.kt` + `res/layout/widget_remote.xml` (signature permission guards the receiver).
- Onboarding: `ui/onboarding/OnboardingScreen.kt` gated by `AppSettings.onboardingDone`; splash via core-splashscreen `Theme.TvTools.Starting`.
- Signing: `keystore.properties` + `tvtools-release.jks` (both git-ignored), wired in `app/build.gradle.kts`.

## Conventions
- All blocking ADB calls on `Dispatchers.IO`; UI via StateFlow.
- Theme colors in `ui/theme/Theme.kt`.
