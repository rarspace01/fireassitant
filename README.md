# Fire Assistant

Native Android app for Amazon Fire tablets that runs a small REST server so Home Assistant (or anything on your LAN) can control the tablet.

## Endpoints

Port `8765`. GET or POST. Authenticate with `Authorization: Bearer <token>` or `?token=<token>` (token is shown in the app).

| Endpoint | Action |
|---|---|
| `/api/wake` | Turn the screen on |
| `/api/sleep` | Turn the screen off (locks the device) |
| `/api/unlock` | Wake and swipe away the (ad-supported) lock screen |
| `/api/update` | Install the newest GitHub release without any tapping (unlocks the screen, downloads the APK, confirms the installer) |
| `/api/ha` | Open the Home Assistant companion app |
| `/api/ha/dashboard?path=lovelace/kitchen` | Open the companion app on a dashboard (`homeassistant://navigate/<path>`) |

Responses are JSON: `{"ok": true, "message": "..."}`.

## Setup on the tablet

1. Install the APK (`./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`, or `adb install`).
2. Open the app and grant the three permissions it lists:
   - **Device admin**: only the force-lock policy; needed for `/api/sleep`.
   - **Display over other apps**: needed to open Home Assistant from the background on Android 10+ (Fire OS 8).
   - **Accessibility service**: swipes the lock screen away for `/api/unlock` (Fire OS doesn't let apps dismiss the keyguard directly).
3. Disable battery optimisation for the app. The server restarts on boot.

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
```

Then call them from automations or scripts, e.g. wake, unlock and show a dashboard when someone arrives:

```yaml
action:
  - action: rest_command.fire_tablet_wake
  - action: rest_command.fire_tablet_unlock
  - delay: "00:00:02"
  - action: rest_command.fire_tablet_open_dashboard
    data:
      path: lovelace/kitchen
```

## Releases

Every push to `main` (except docs-only changes) runs `.github/workflows/release.yml`, which builds the release APK and publishes it as a GitHub release tagged `v1.0.<run number>`. Pull requests run a debug build and lint (`ci.yml`). Dependabot checks daily to keep Gradle dependencies and the workflow actions up to date.

To sign with your own key, add these repository secrets: `KEYSTORE_BASE64` (`base64 -w0 release.keystore`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the APK is signed with the debug key, which installs fine but changes if the CI runner's debug key changes, so updates may require uninstalling first. Use your own key for anything long-lived.

## Updating

Tap **Check for updates** in the app. If a newer GitHub release exists, **Download and install** fetches the APK and hands it to Android's installer; the token and permissions are kept. The first time, Android asks you to allow installs from Fire Assistant. This only works between builds signed with the same key (CI releases); a locally built debug APK must be uninstalled first.

`/api/update` needs the accessibility service and "Display over other apps", and Android's "install unknown apps" permission for Fire Assistant (tap **Check for updates** once, or `adb shell appops set net.fireassistant REQUEST_INSTALL_PACKAGES allow`). It replies `updating to vX` or `up to date` straight away; the install finishes about 20 seconds later and the API restarts by itself. The accessibility service only sees the system package installer and only taps Install within 3 minutes of an `/api/update` call.

## Notes

- `minSdk 23` covers Fire OS 6 and up (API 25 / 28 / 30 for Fire OS 6 / 7 / 8). Fire OS 5 tablets (2015-2017, API 22) are not supported. No Google Play services are used.
- Unlock only works with a swipe lock screen (the "Special Offers" ads one). A PIN/password cannot be bypassed.
- The server speaks plain HTTP; keep it on a trusted LAN.
