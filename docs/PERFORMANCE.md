# Performance

TV Tools measures its own startup and frame health locally. No numbers leave the phone.

## What is measured

| Metric | How | Where to see it |
|---|---|---|
| Cold start | `Process.getStartUptimeMillis()` → first `onPreDraw` of the activity window | Settings → Performance |
| Frame jank | a `Choreographer` frame-time tracker (a frame slower than ~42 fps counts as jank), flushed when the app goes to the background | Settings → Performance |

Both counters are stored in app-private SharedPreferences (`tvtools_performance`). **Reset
measurements** clears them.

## Baseline profiles

The app includes `androidx.profileinstaller`. On first launch Android installs the baseline
profiles that ship with AndroidX/Compose, which improves first-frame work without any extra
setup.

An **app-specific** profile (our own startup path) still needs a device run. It is deliberately
not guessed by hand because a wrong profile can make startup slower. To generate one:

1. Add the `androidx.baselineprofile` Gradle plugin and a `:baselineprofile` test module (or use
   Android Studio's *Baseline Profile Generator* template).
2. Connect a physical device with the TV Tools release build installed and run:

   ```bash
   ./gradlew :app:generateBaselineProfile
   ```

3. The generated file lands in `app/src/main/generated/baselineProfiles/` (or
   `app/src/release/generated/baselineProfiles/`). Commit it and rebuild the release APK.

Until that device run happens, the app relies on the library profiles installed by
`ProfileInstaller`.

## Startup work already in place

- ADB keys are generated lazily on the first connect, not at app start.
- No background screenshot loop; the TV is only captured on demand.
- The device screen starts its watchdog and MAC lookup outside the first frame.
- Compose navigation uses a lightweight fade/slide transition.

## Measuring a change

1. Force-stop the app, open it, wait for the first frame, then open Settings → Performance.
2. Compare **Cold start** across builds. Run at least three times and use the median.
3. Use the app normally for a few minutes, background it, then check the jank percentage.
4. For repeatable comparisons use Macrobenchmark (`StartupTimingMetric`, `FrameTimingMetric`) on
   the same device and build type.
