# Fire Assistant

Native Android app for Amazon Fire tablets that runs a small REST server so Home Assistant (or anything on your LAN) can control the tablet.

## Endpoints

Port `8765`. GET or POST. Authenticate with `Authorization: Bearer <token>` or `?token=<token>` (token is shown in the app).

| Endpoint | Action |
|---|---|
| `/api/wake` | Turn the screen on |
| `/api/sleep` | Turn the screen off (locks the device) |
| `/api/unlock` | Wake and swipe away the (ad-supported) lock screen |
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

## Notes

- `minSdk 22` covers Fire OS 5 and up (API 22 / 25 / 28 / 30 for Fire OS 5 / 6 / 7 / 8). No Google Play services are used.
- Unlock only works with a swipe lock screen (the "Special Offers" ads one). A PIN/password cannot be bypassed.
- The server speaks plain HTTP; keep it on a trusted LAN.
