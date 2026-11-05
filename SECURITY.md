# Security policy

## Supported versions

Only the latest release receives fixes. At the time of writing that is **v1.6**.

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Use GitHub's private reporting: **Security → Advisories → Report a vulnerability**
(<https://github.com/Avithegod2010/TV-Tools/security/advisories/new>).

Include:

- the app version and Android version,
- the TV model / ADB setup,
- steps to reproduce,
- the impact you expect (data exposure, command execution, permission bypass, …).

You will get an acknowledgement as soon as possible, and credit in the advisory once a fix ships
unless you prefer to stay anonymous.

## Scope

TV Tools speaks the ADB protocol to TVs on the user's local network. In scope:

- ADB key or certificate leakage (the key pair lives in app-private storage),
- command injection through file names, package names, host/port input or shell execution,
- path traversal in the file manager, sync protocol or backup import,
- intent/IPC exposure through exported components or the share-sheet receiver,
- overlay, foreground-service or notification abuse,
- backup encryption weaknesses (AES-256-GCM, PBKDF2).

Out of scope / by design:

- **Trust-all TLS during ADB pairing.** ADB's pairing protocol uses a self-signed certificate
  exchange; the library's trust manager accepts the TV's certificate as part of that protocol.
  This is how wireless ADB works, not a defect.
- **ADB itself grants broad device control.** Anyone who can pair with the TV can install apps and
  run shell commands. Enable wireless debugging only on trusted networks and turn it off when done.
- **No certificate pinning for the TV**: the TV's certificate is not known in advance.
- Denial of service against your own TV through the app's own controls.

## Privacy

The app has no analytics, telemetry, ads or cloud services. Crash reports are written to
app-private storage and only leave the device if the user taps **Copy diagnostics** or shares the
text themselves.
