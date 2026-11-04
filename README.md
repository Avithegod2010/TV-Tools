<div align="center">
  <img src="assets/banner.svg" alt="TV Tools — Your TV, in your pocket." width="100%" />

  <h1>TV Tools</h1>

  <p>
    <b>Manage your Android TV, Google TV, Xiaomi TV or Amazon Fire TV — wirelessly, from your phone.</b><br/>
    Install apps, browse files, mirror the screen, run shell commands and use a modern remote.
    No root. No accounts. No cloud.
  </p>

  <p>
    <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3ddc84?logo=android&logoColor=white" alt="Platform" />
    <img src="https://img.shields.io/badge/Kotlin-2.0-7f52ff?logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285f4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
    <img src="https://img.shields.io/badge/License-MIT-22c55e" alt="MIT License" />
    <img src="https://img.shields.io/badge/Version-1.1-166d32" alt="Version" />
  </p>
</div>

---

## ✨ Features

| | |
|---|---|
| 📡 **Wireless ADB** | Auto-discovery (mDNS + LAN scan), Android 11+ pairing codes, Fire TV legacy ADB, auto-reconnect |
| 🛠 **Tools** | Install APK, upload to TV, clear caches, live screenshots (update / share / save) |
| 📺 **Screen mirroring** | Live TV view with tap & swipe control, LIVE fps counter, on-screen back/home/menu keys |
| 📤 **Share to TV** | Send photos, videos or APKs from Android's share sheet — the app uploads and opens/installs them |
| 🧩 **Apps** | Browse all apps with icons & sizes, launch, uninstall, extract APK, force stop, clear data, disable |
| 🗂 **File manager** | Browse TV storage, thumbnails, upload, download, rename, delete |
| 💻 **Shell** | Interactive ADB shell with CTRL+C, clear and copy |
| 🎛 **Remote** | Modern remote sheet: D-Pad ↔ touchpad, media keys, volume, text input, power menu |
| ⚡ **Power** | Sleep, power toggle, restart, and Wake-on-LAN |
| 📊 **Device info** | Storage / RAM / CPU gauges, network usage, uptime |
| 🎨 **Themes & haptics** | System/Light/Dark, 5 accent colors incl. Material You, haptic feedback |

## 🔐 Privacy & security

- **No analytics, no telemetry, no ads, no cloud.** The app only talks to the TV on your local network.
- The ADB key pair is generated on-device and kept in app-private storage; it never leaves the phone.
- Nothing is uploaded anywhere. There is no account system.
- Source code is fully open — audit it yourself.

> TV Tools is an ADB client: it does what `adb` on a computer could do over your LAN. Only enable ADB on TVs you trust, and turn it off when you're done.

## 🚀 Install

1. Download the latest APK from [Releases](../../releases).
2. Allow installs from unknown sources and install it.
3. Put your phone and TV on the **same Wi-Fi**.

## 📺 Enable ADB on your TV

**Android TV / Google TV / Xiaomi**
1. Settings → Device Preferences → About → tap **Build** 7 times.
2. Developer options → **Wireless debugging** → ON (Android 11+).
3. In TV Tools tap the TV → on the TV choose **Pair device with pairing code** → enter the 6-digit code.

**Amazon Fire TV**
1. Settings → My Fire TV → About → tap the device name 7 times.
2. Developer Options → **ADB debugging** → ON.
3. Connect from TV Tools (port 5555). Accept the "Allow debugging" prompt on the TV once.

## 🛠 Build from source

```bash
# Requirements: JDK 17, Android SDK 35
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

## 🗺 Roadmap

- [x] Wireless ADB connect + pairing
- [x] Apps / files / shell / device info
- [x] Screen mirroring with touch control
- [x] Share-to-TV + Wake-on-LAN
- [x] Themes, haptics, modern remote
- [ ] Bulk app backup & restore (split APKs)
- [ ] Storage analyzer & cleanup
- [ ] Multi-TV dashboard
- [ ] Quick Settings tile & home-screen widget

## 🤝 Contributing

Issues and pull requests are welcome. Please keep the app privacy-first: no telemetry, no cloud services.

## 📄 License

[MIT](LICENSE) © TV Tools contributors

<div align="center"><sub>Built with Kotlin & Jetpack Compose. Not affiliated with Google or Amazon.</sub></div>
