# CI workflows

Two GitHub Actions workflows live in `.github/workflows/`. None of them uses secrets, and
none of them publishes anything: they only build, test and upload artifacts.

## `android.yml` — pull requests and `main`

Runs on every push to `main`, every pull request and on manual dispatch. It is split into
three independent jobs so a slow or failing lint run cannot block the build:

| Job | What it does | Blocking? |
| --- | --- | --- |
| `test` | `testDebugUnitTest`, then `assembleDebug` | Yes |
| `release` | `assembleRelease` with R8 and resource shrinking | Yes |
| `lint` | `lintDebug` | No (`continue-on-error: true`) |

Artifacts (kept for 14 days):

- `unit-test-report` — JUnit HTML report from the `test` job.
- `app-debug` — `app/build/outputs/apk/debug/app-debug.apk`.
- `app-release-unsigned` — `app/build/outputs/apk/release/app-release-unsigned.apk`.
  CI has no `keystore.properties`, so the release build is deliberately unsigned. It exists to
  prove that minification and shrinking succeed; release signing stays local.
- `lint-report` — `lint-results-debug.html` and, when generated, `.sarif`.

Lint is advisory by design. The `lint` job always uploads its report, but a lint error is not a
merge blocker. If a finding matters, fix it in a normal pull request; do not make the job
required.

CI overrides `org.gradle.jvmargs` to 2 GB for the `release` and `lint` tasks. The repository's
`gradle.properties` is tuned for a low-RAM development machine (768 MB), which is too tight
for R8 and lint.

The workflows run `chmod +x gradlew` before invoking Gradle because the repository is
maintained on Windows, where the executable bit is not preserved reliably.

## `instrumentation.yml` — emulator smoke tests

Triggered manually (`workflow_dispatch`) and every Monday at 03:00 UTC (`cron: '0 3 * * 1'`).
It boots an Android emulator through `reactivecircus/android-emulator-runner@v2`:

- API 34, `google_apis`, `x86_64`, Pixel 5 profile
- animations disabled for stable Compose tests
- KVM enabled via a udev rule so the emulator runs with hardware acceleration

The test command is:

```bash
./gradlew connectedDebugAndroidTest --stacktrace --console=plain
```

Reports are uploaded to the `instrumentation-reports` artifact
(`app/build/reports/androidTests`) even when the tests fail.

## Running instrumentation tests locally

1. Start an emulator (API 26 or newer) or connect a device with USB debugging enabled.
2. Confirm it is visible:

   ```bash
   adb devices
   ```

3. Run the suite:

   ```bash
   ./gradlew connectedDebugAndroidTest
   ```

   On Windows use `gradlew.bat connectedDebugAndroidTest`.

To run only the launch smoke test:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tvtools.app.AppLaunchTest
```

The smoke test (`app/src/androidTest/java/com/tvtools/app/AppLaunchTest.kt`) accepts both
states of a fresh install: the onboarding screen ("Skip") and the home screen ("TV Tools").
Uninstall the debug build (`adb uninstall com.tvtools.app.debug`) if you want to force the
onboarding path.

## Notes

- There are no signing keys, Play Store credentials or other secrets in CI.
- The release job never publishes; `docs/RELEASING.md` covers the signed release flow.
- A green `release` job only means the unsigned build succeeded. Always sign and smoke-test
  locally before publishing.
