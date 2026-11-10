# Fire Assistant

Native Android app for Amazon Fire tablets that runs a small REST server so Home Assistant (or anything on your LAN) can control the tablet.

## Endpoints

Port `8765`. GET or POST. Authenticate with `Authorization: Bearer <token>` or `?token=<token>` (token is shown in the app).

| Endpoint | Action |
|---|---|
| `/api/wake` | Turn the screen on (no-op if already on) |
| `/api/sleep` | Turn the screen off (locks the device) |
| `/api/unlock` | Wake and swipe away the (ad-supported) lock screen (no-op if already unlocked) |
| `/api/update` | Install the newest GitHub release without any tapping (unlocks the screen, downloads the APK, confirms the installer) |
| `/api/ha` | Open the Home Assistant companion app (no-op if already in front) |
| `/api/ha/dashboard?path=lovelace/kitchen` | Show a dashboard, doing only what is missing: wakes the screen, unlocks, and navigates (`homeassistant://navigate/<path>`) unless Home Assistant is already showing that dashboard. Home Assistant doesn't need to be open first. |
| `/api/tapo/camera?id=<device id>` | Open the Tapo app on one camera's live view, waking and unlocking first if needed (see [Tapo cameras](#tapo-cameras)) |
| `/api/adb` | Report the ADB ports and pairing code as JSON: `connect_port` (wireless debugging), `pairing_port` and `pairing_code` (the "Pair device with pairing code" dialog), `tcp_port` (classic `adb tcpip`), `wireless_debugging` (on/off) and `pairing_error`. Every call opens Settings and the pairing dialog through the accessibility service and leaves it open, because the code and port are only valid while it is showing. Turns wireless debugging on if `WRITE_SECURE_SETTINGS` was granted. Unavailable values are `null`. Takes a few seconds. **Exposes a credential to anyone with the API token.** |

Responses are JSON: `{"ok": true, "message": "..."}`. The message says what was done, e.g. `screen woken, unlocked, opened dashboard lovelace/kitchen` or `dashboard lovelace/kitchen already shown`.

To see which dashboard is showing, the accessibility service reads the URL of Home Assistant's page. If the WebView doesn't expose it, it remembers the last dashboard opened through the API until someone taps inside Home Assistant. Use the full path as it appears in the URL (`lovelace/0`, not `lovelace`), otherwise it never matches and the dashboard is reopened each time.

## Setup on the tablet

1. Install the APK (`./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`, or `adb install`).
2. Open the app and grant the three permissions it lists:
   - **Device admin**: only the force-lock policy; needed for `/api/sleep`.
   - **Display over other apps**: needed to open Home Assistant or Tapo from the background on Android 10+ (Fire OS 8).
   - **Accessibility service**: swipes the lock screen away for `/api/unlock` (Fire OS doesn't let apps dismiss the keyguard directly), and checks which Home Assistant dashboard is in front so `/api/ha` and `/api/ha/dashboard` skip what is already done.
3. Disable battery optimisation for the app. The server restarts on boot.
4. Optional: hide the lock screen ads (see below).

## Lock screen ads

The swipe in `/api/unlock` gets past the "Special Offers" lock screen, but the ad has already been drawn by then. To keep ads off the lock screen, grant one extra permission from a PC with ADB (USB debugging on):

```sh
adb shell pm grant net.fireassistant android.permission.WRITE_SECURE_SETTINGS
```

The app then sets the `LOCKSCREEN_AD_ENABLED` setting to `0` every time the screen turns off, and when the service starts. It writes the setting in both the global and secure tables. Fire OS uses this setting to decide whether the lock screen shows an ad, and Amazon turns it back on, so it is reset each time before the lock screen comes back. This is the method Fire Toolbox uses on Fire OS 8, with its "[FTB] Lockscreen Ads Remover" Automate flow. The older method, disabling the Special Offers app with `pm disable-user com.amazon.kindle.kso`, is unreliable on restricted Fire OS 8 firmware (8.3.1.9+), which blocks changes to protected Amazon packages.

The app shows whether the permission is granted and the current value of the setting. The lock screen itself stays (Fire OS always shows it on wake), so `/api/unlock` still swipes it away. Ads already downloaded may show once more before the first screen-off.

## Home Assistant example

In `secrets.yaml`:

```yaml
fire_tablet_auth: "Bearer <token>"
```

In `configuration.yaml` (replace the IP with your tablet's; use `lovelace/0` or any dashboard path):

```yaml
rest_command:
  fire_tablet_wake:
    url: "http://192.168.1.141:8765/api/wake"
    headers:
      Authorization: !secret fire_tablet_auth
  fire_tablet_sleep:
    url: "http://192.168.1.141:8765/api/sleep"
    headers:
      Authorization: !secret fire_tablet_auth
  fire_tablet_unlock:
    url: "http://192.168.1.141:8765/api/unlock"
    headers:
      Authorization: !secret fire_tablet_auth
  fire_tablet_update:
    url: "http://192.168.1.141:8765/api/update"
    headers:
      Authorization: !secret fire_tablet_auth
  fire_tablet_open_ha:
    url: "http://192.168.1.141:8765/api/ha"
    headers:
      Authorization: !secret fire_tablet_auth
  fire_tablet_open_dashboard:
    url: "http://192.168.1.141:8765/api/ha/dashboard?path={{ path }}"
    headers:
      Authorization: !secret fire_tablet_auth
  fire_tablet_open_tapo_camera:
    url: "http://192.168.1.141:8765/api/tapo/camera?id={{ id }}"
    headers:
      Authorization: !secret fire_tablet_auth
```

Then call them from automations or scripts, e.g. show a dashboard when someone arrives. One call wakes, unlocks and navigates as needed, and leaves the tablet alone if the dashboard is already on screen:

```yaml
action:
  - action: rest_command.fire_tablet_open_dashboard
    data:
      path: lovelace/kitchen
```

Or show the doorbell camera when someone rings:

```yaml
action:
  - action: rest_command.fire_tablet_open_tapo_camera
    data:
      id: YOUR_CAMERA_DEVICE_ID
```

## Tapo cameras

`/api/tapo/camera?id=<device id>` opens the [Tapo app](https://www.tapo.com/) straight on a camera's live view. It uses the screen Tapo opens when you tap a motion notification (`com.tplink.iot/.view.notification.NotificationClickActivity`, with the extras `deviceId`, `msgType=Motion` and `hasCloudVideo=false`). TP-Link doesn't document this, so a Tapo update may break it. It needs the Tapo app installed and signed in, and "Display over other apps".

The device ID is the camera's Tapo cloud ID, not its name or MAC address. The Tapo app doesn't show it. The [Tapo Control](https://github.com/JurajNyiri/HomeAssistant-Tapo-Control) integration for Home Assistant can show it, or you can use a lookup tool such as [tapo-device-id-lookup](https://github.com/danwig/tapo-device-id-lookup) (Windows, doesn't work with 2FA on the Tapo account).

Unlike `/api/ha/dashboard`, this always opens the camera again even if it is already on screen, because there is no reliable way to tell which camera Tapo is showing.

## Releases

Every push to `main` (except docs-only changes) runs `.github/workflows/release.yml`, which builds the release APK and publishes it as a GitHub release tagged `v1.0.<run number>`. Pull requests run a debug build and lint (`ci.yml`). Dependabot checks daily to keep Gradle dependencies and the workflow actions up to date.

To sign with your own key, add these repository secrets: `KEYSTORE_BASE64` (`base64 -w0 release.keystore`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the APK is signed with the debug key, which installs fine but changes if the CI runner's debug key changes, so updates may require uninstalling first. Use your own key for anything long-lived.

## Updating

Tap **Check for updates** in the app. If a newer GitHub release exists, **Download and install** fetches the APK and hands it to Android's installer; the token and permissions are kept. The first time, Android asks you to allow installs from Fire Assistant. This only works between builds signed with the same key (CI releases); a locally built debug APK must be uninstalled first.

`/api/update` needs the accessibility service and "Display over other apps", and Android's "install unknown apps" permission for Fire Assistant (tap **Check for updates** once, or `adb shell appops set net.fireassistant REQUEST_INSTALL_PACKAGES allow`). It replies `updating to vX` or `up to date` straight away; the install finishes about 20 seconds later and the API restarts by itself. The accessibility service only sees the system package installer and the Home Assistant app, and only taps Install within 3 minutes of an `/api/update` call or a tap on **Download and install** in the app.

## Notes

- `minSdk 23` covers Fire OS 6 and up (API 25 / 28 / 30 for Fire OS 6 / 7 / 8). Fire OS 5 tablets (2015-2017, API 22) are not supported. No Google Play services are used.
- Unlock only works with a swipe lock screen (the "Special Offers" ads one). A PIN/password cannot be bypassed.
- The server speaks plain HTTP; keep it on a trusted LAN.
