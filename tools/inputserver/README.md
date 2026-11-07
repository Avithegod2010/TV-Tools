# TV-side input injector

`InputServer.java` is a tiny, persistent process that runs on the TV under the
ADB shell's `app_process`. It reads Android keycodes from stdin, one line at a
time, and injects them with the same hidden `InputManager.injectInputEvent`
API that `adb shell input` uses.

Why: `input keyevent` starts a new ART process for every key. On older TV boxes
that costs 150–400 ms per press. Keeping one injector alive removes that startup
cost; each key is a line on an existing pipe, and the TV spends its time in the
input dispatcher instead of spawning processes.

## Safety and scope

- Runs as the `shell` user; no root, no installed APK, no listening port.
- Only stdin from the app's private ADB stream is accepted.
- The process exits when the ADB stream closes (disconnect or app exit).
- It only injects key presses. Text, taps/swipes and file operations still use
  the normal ADB shell.
- If `app_process`/the jar is blocked, the app silently falls back to the old
  input shell.

## Rebuild the jar

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\android-sdk"
.\tools\build-inputserver.ps1
```

That writes `app/src/main/assets/tvtools-input.jar`. The jar is committed so a
normal Gradle build does not need the extra toolchain.
