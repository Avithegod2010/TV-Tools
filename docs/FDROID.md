# Publishing TV Tools on F-Droid

TV Tools is a good fit for F-Droid: it is MIT licensed, has no ads or tracking, and every
dependency is open source. This document covers the metadata in this repository and how to
submit the app to the official F-Droid repository.

## Store metadata

F-Droid reads Fastlane-style metadata from:

```
fastlane/metadata/android/en-US/
├── title.txt
├── short_description.txt          # max 80 characters
├── full_description.txt           # plain text, up to 4000 characters
├── changelogs/<versionCode>.txt   # one file per release, e.g. 10.txt
└── images/
    └── phoneScreenshots/          # 1.png, 2.png, ... (added separately)
```

- `title.txt` — app name shown in the client.
- `short_description.txt` — one line, at most 80 characters.
- `full_description.txt` — a few short paragraphs; no HTML is needed.
- `changelogs/11.txt` — the notes for versionCode 11 (versionName 1.8.1). The file name must
  match `versionCode`, not `versionName`.
- `images/phoneScreenshots/` — six redacted phone screenshots are included (home, Tools,
  All features, Music, Settings and Remote). They match the README gallery and contain no IP
  addresses, device names or TV content.

The app icon comes from the APK (`@mipmap/ic_launcher`); no `images/icon.png` is required. A
feature graphic is optional and can be added later as `images/featureGraphic.png`.

## Build recipe

F-Droid builds the app from source; it does not reuse the GitHub release APK. The build
command is the same one used locally:

```bash
./gradlew assembleRelease
```

In `fdroiddata`, a minimal build entry looks like this (replace the commit with the release
tag once it exists):

```yaml
Categories:
  - System
License: MIT
AuthorName: TV Tools contributors
SourceCode: https://github.com/Avithegod2010/TV-Tools
IssueTracker: https://github.com/Avithegod2010/TV-Tools/issues
AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.8.1
CurrentVersionCode: 11
Builds:
  - versionName: 1.8.1
    versionCode: 11
    commit: v1.8.1
    subdir: .
    gradle:
      - assembleRelease
```

Notes:

- The project uses the Gradle wrapper and JDK 17. `compileSdk` is 35, `minSdk` is 26.
- `keystore.properties` is git-ignored and is **not** present on the build server. Gradle then
  produces an unsigned APK, which F-Droid signs with its own key.
- `android.enableR8.fullMode=false` in `gradle.properties` is intentional (build time on
  low-memory machines); F-Droid can use it as-is.

## Anti-features and dependencies

The app has **no anti-features**: no advertising, no tracking or analytics, no accounts, no
network calls other than ADB to the user's own TV on the local network. Nothing is uploaded
to a third party, so the "NonFreeNet" and "Tracking" anti-features do not apply.

All dependencies are open source and F-Droid-compatible:

- `androidx.*`, `com.google.zxing:core` (Apache-2.0)
- `com.github.MuntashirAkon:libadb-android` and `sun-security-android` (Apache-2.0)
- `org.conscrypt:conscrypt-android` (Apache-2.0)
- `com.github.mwiede:jsch` (BSD-style)

There are no proprietary SDKs, no Google Play Services and no crash-reporting libraries.

## Submitting to F-Droid

1. Tag the release in this repository (for example `v1.8.0`) and make sure CI is green.
2. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata) on GitLab.
3. Add `metadata/com.tvtools.app.yml` using the recipe above.
4. Validate locally with the F-Droid tools:

   ```bash
   fdroid lint com.tvtools.app
   fdroid build -v -l com.tvtools.app
   ```

5. Commit, push to your fork and open a merge request titled
   `New app: TV Tools (com.tvtools.app)`.
6. Answer the reviewer questions. Typical requests are a reproducible-build check and a
   confirmation that the metadata matches the tagged source.

After the app is accepted, update `CurrentVersion`/`CurrentVersionCode` (or let
`AutoUpdateMode: Version` plus `UpdateCheckMode: Tags` pick up new tags) for each release.

## Reproducible builds (optional)

F-Droid can compare its build against the developer APK. To make that possible, publish the
release APK built from the same tag and keep the signing key stable. The expected certificate
fingerprint is documented in `docs/RELEASING.md`.
