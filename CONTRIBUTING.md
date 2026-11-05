# Contributing to TV Tools

Thanks for wanting to help. TV Tools is a privacy-first ADB client: no analytics, no accounts,
no cloud services. Please keep it that way.

## Ground rules

- **No telemetry, ads, tracking or remote services.** The app may only talk to the user's TV and
  the local network.
- **No new runtime permissions without a clear, user-facing reason.** Explain them in the PR.
- **Do not commit signing material.** `*.jks`, `keystore.properties`, `local.properties` and
  `dist/` are git-ignored — keep it that way.
- **Keep the ADB key private.** It lives in app-private storage; never log it or send it anywhere.

## Building

Requirements: JDK 17 and Android SDK 35 (`ANDROID_HOME` or `local.properties`).

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew assembleRelease      # signed release (needs keystore.properties)
```

APKs land in `app/build/outputs/apk/`.

On a low-RAM machine, `gradle.properties` already caps the daemon at 768 MB and runs Kotlin
in-process. Do not raise those limits unless you have the RAM to spare.

## Tests

Every parser, codec or protocol helper should have a unit test. Current coverage includes:

- `parseMediaSession` (`MediaSessionTest`)
- `parseHostPort` / `formatEndpoint` (`EndpointTest`)
- `pm list packages -f` parsing (`PackageListingTest`)
- macro and quick-key storage formats (`MacroCodecTest`, `QuickKeyCodecTest`)
- WOL MAC parsing and magic packets (`WolTest`)
- WAV generation (`BeepWavTest`)
- crash report formatting (`DiagnosticsTest`)

Add a test next to the code when you fix a bug that can be reproduced without a device.

## Code style

- Kotlin official style, 4-space indent, trailing commas where the file already uses them.
- Compose: state hoisted into the screen composable or a ViewModel; blocking ADB work on
  `Dispatchers.IO`; UI updates through `StateFlow`.
- ADB commands must have timeouts. A flaky TV must never hang the app.
- Keep user-visible strings in sentence case.

## Commits

Conventional-ish prefixes are appreciated:

```
feat(remote): add sleep timer
fix(files): handle empty directories
docs: update build instructions
test: cover package listing parsing
```

One logical change per commit. Reference the issue in the body when there is one.

## Pull requests

- Describe the user-visible change and how you tested it (device/TV model or unit tests).
- Include screenshots for UI changes when you can.
- Make sure `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` pass.
- CI runs both automatically; a red build will block the PR.

## Reporting bugs

Use the bug report template and paste the output of **Settings → Diagnostics → Copy diagnostics**.
It contains the app version, Android version and the last local crash, and nothing else.
