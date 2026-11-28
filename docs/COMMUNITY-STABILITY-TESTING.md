# Community stability update verification

Run these checks with the debug APK, an Android phone, and at least one TV you can control. The debug app uses `com.tvtools.app.debug` and installs alongside the regular release. Record the APK SHA-256, phone and TV OS versions, connection mode, expected result, observed result, and elapsed time for connection checks. Use a disposable APK or split archive for install tests. Keep published logs free of pairing codes, Wi-Fi details, TV addresses, and personal paths.

## Network switching and reconnection

| Scenario | Expected on a real device |
| --- | --- |
| Switch between two Wi-Fi networks on different subnets while the first TV is connected. Start discovery or a TV operation just before switching. | The old session and discovery results are invalidated. Discovery uses the second network. The app remains responsive and saved TVs and settings survive. |
| Switch between networks with the same IPv4 prefix and different gateways, then change a DHCP address on one network. | The app detects the new link even though the address range stays the same. Results from the earlier scan disappear. |
| Turn Wi-Fi off, wait, and turn it back on. Repeat while a connection attempt or scan is in progress. | The UI reflects network loss and return. Outdated attempts cannot replace the current session. A remembered TV can recover when reachable again. |
| Briefly interrupt the TV connection during remote input, mirroring, and file operations. | The failed operation returns control within its timeout. Later manual actions use the recovered session. A mutating operation is never automatically repeated just to establish that it finished. |
| Send the TV to sleep, wake it, then power it off and on. Test with quick and slow wake-up. | The connection status updates and retries are bounded. After the retry window, manual reconnect is available. Existing authorization is reused when the TV still trusts the phone. |
| Background the app for a minute and again for an extended period. Wake or disconnect the TV while the app is in the background. | The session is checked on return. A recoverable TV is usable without a process restart. Check that recovery does not continue polling during an extended background period. |
| Connect manually to a known unreachable TV address, then immediately select a reachable saved TV. | The unreachable request times out and the second TV's state takes precedence. Each user action has a clear visible outcome. |
| Change the TV's DHCP address or restart wireless debugging so its port changes. Test an mDNS-discovered TV and a legacy/manual TV. | A previously identified mDNS TV can be rediscovered where the network and TV still advertise it. Legacy or manually entered endpoints can be selected again or edited. No automatic connection is made based only on a matching display name. |
| Rapidly press Connect or Scan. Connect and disconnect repeatedly. Repeat with auto-connect enabled and disabled. | Discovery and connection attempts are serialized or cancelled as appropriate. Manual disconnect is respected. Saved devices, rooms, looks, and the ADB authorization are retained. |
| Repeat with a directly attached Ethernet LAN or a network without Internet if available. | Discovery and commands continue to work when local reachability exists. |

## APK selection

1. Install an APK using **Tools → Install APK** from a nested Downloads folder. Open the picker again, then force-stop and restart TV Tools and open it once more. Confirm the folder opens both times when the provider resolves it.
2. Repeat using a split APK archive and one secondary provider such as SD card or cloud storage. Verify the chosen file reaches the existing TV installation flow.
3. Move a disposable test folder or revoke the provider's access and open the picker again. Verify that the system offers an accessible location and you can select another APK. A cancelled picker should not change the remembered location.
4. Check the long-press **Install APK** phone-app workflow and file uploads. Confirm they still open their own pickers or app lists and can install or upload as before.

## English and Russian

1. On Android 13 or newer, use the app-language settings to switch to Russian, then back to English. On older Android versions, repeat using the phone's system language. Check relaunch and device rotation with both settings.
2. In Russian, try first launch, TV discovery, pairing, reconnect, remote controls, APK install, app management, file browsing, settings, widgets, and notifications. Check errors and count labels with values such as 1, 2, 5, and 21 where applicable.
3. Try a compact screen and the app's larger text sizes. Verify that tabs, controls, error banners, and dialog buttons fit or scroll and that TalkBack reads their labels.
4. Smoke-test TV selection, remote keys, screenshots, mirroring, file transfer, and settings in English after switching back.

## Automation

```text
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
gradlew.bat :app:connectedDebugAndroidTest
```

Run the connected task with a dedicated Android test phone or emulator attached. The checklist above still requires hardware for TV, DHCP, Wi-Fi, and provider behavior.
