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
- `./gradlew assembleDebug` (or a local Gradle 8.9 install: `gradle assembleDebug --no-daemon`)
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
