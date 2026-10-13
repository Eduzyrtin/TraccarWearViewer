# Viewer for Traccar (Wear OS)

A lightweight Wear OS viewer for a [Traccar](https://www.traccar.org) server: see where your
devices are, pin the ones you care about to a tile, and get alerts when you are away from your
phone. It is a viewer, not a manager: to administer the server keep using the web interface or the
official Traccar Manager on your phone.

This is an independent project and is not affiliated with or endorsed by Traccar.

It works with any Traccar 6 server without changing its configuration. It uses the REST API with
a bearer token.

## Features

- **Sign in:** with email and password, the app requests a long-lived token (one year) and
  **does not store your password**. You can also paste a token created in the web interface
  (*Settings › Preferences*), which is required for SSO/OpenID accounts. Nothing is masked on
  screen, to make typing on a watch less painful.
- **Device list and detail:** status, last update, speed and address, plus *Open map* on the watch
  or, if it has no map app, on the phone.
- **Tile:** up to 3 pinned devices (or the most recently active ones), colored by status, with a
  refresh button.
- **Alerts away from the phone:** while the phone is connected over Bluetooth, the official
  Traccar Manager on it already notifies you. When the phone is not nearby, the watch checks the
  server every 15 minutes for the event types you have configured under *Notifications* and shows
  them. No server setup and no Firebase required.
- English and Spanish.

## Limitations

- Own alerts arrive with up to ~15 minutes of delay (the WorkManager minimum). Real time would
  need a persistent WebSocket (`/api/socket`), at a battery cost.
- If the phone is away but online, some watches also mirror its notification through the cloud,
  so an alert may appear twice.
- The token is kept in the app's private storage, which is excluded from backups.
- Plain `http://` servers are allowed, as many self-hosted servers use it.

## Building

Requirements: Android Studio (or JDK 17+), Android Gradle Plugin 9.4 and Android SDK 37.

```sh
./gradlew assembleDebug
```

The APK is generated in `app/build/outputs/apk/debug/`. Install it on a watch or emulator with
`adb install`. To see the tile on the emulator, add it from the tile carousel.

## Project structure

| Path | Purpose |
|---|---|
| `api/TraccarApi.kt` | REST client (OkHttp + kotlinx.serialization) |
| `data/` | DataStore storage and repository |
| `tile/DevicesTileService.kt` | The tile (ProtoLayout Material 3) |
| `notify/` | Periodic event check and notifications |
| `ui/` | Wear OS screens |

## Status and roadmap

Tested on the Wear OS API 34 emulator (UI and tile). Sign-in and device list have not been tested
against a real server yet.

- Test with a real server and watch.
- Static map in the device detail screen.
- Optional real-time alerts over WebSocket.

## License

GNU General Public License v3.0 or later. See [LICENSE](LICENSE).
