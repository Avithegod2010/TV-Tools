# Releasing TV Tools

Releases are signed APKs attached to a GitHub release. The signing key lives outside the repo.

## Prerequisites

- JDK 17 and Android SDK 35.
- `keystore.properties` and `tvtools-release.jks` in the project root (both git-ignored).
- GitHub CLI authenticated as the repo owner.

```properties
# keystore.properties (never commit)
storeFile=tvtools-release.jks
storePassword=…
keyAlias=tvtools
keyPassword=…
```

The release certificate must stay the same across versions so users can update in place.
Expected SHA-256 fingerprint:

```
810cb903ef272f3bb4ad4dcfbe3a5d7b273484a26a16d96480480ad99d634a7b
```

## 1. Prepare

1. Pick the next `versionCode` (monotonically increasing) and `versionName`.
2. Update `app/build.gradle.kts`.
3. Move the `[Unreleased]` entries in `CHANGELOG.md` into the new version heading.
4. Update the version badge and feature table in `README.md` if needed.

## 2. Verify

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

All tests must pass. CI runs the same two commands on every push.

## 3. Build and sign

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

Confirm the signer before publishing:

```bash
"$ANDROID_HOME/build-tools/<latest>/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

The SHA-256 must match the fingerprint above.

## 4. Stage the artifact

```bash
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/TV-Tools-v<version>-release.apk
```

`dist/` is git-ignored on purpose.

## 5. Publish

```bash
gh release create v<version> dist/TV-Tools-v<version>-release.apk \
  --title "v<version> - <short title>" \
  --notes-file <notes.md>
```

Notes should list user-visible changes, the minimum Android version, the signing continuity and a
short privacy reminder. Use `--draft` first if you want to review the rendered page.

## 6. Install for a smoke test

```bash
adb install -r dist/TV-Tools-v<version>-release.apk
```

The release certificate must match the previously installed build. If the install fails with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the APK was signed with a different key — do not uninstall
the user's app to work around it, fix the signing config instead.

## Rollback

GitHub releases can be deleted and re-created, but `versionCode` must keep increasing. Never
re-publish a smaller `versionCode` for the same package: devices will refuse the downgrade.
