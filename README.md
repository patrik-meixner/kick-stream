# kick-stream

Lightweight Android TV app for watching Kick.com streams.

## Features

- Browse followed channels (live + offline)
- Watch HLS streams with ExoPlayer
- Read-only chat sidebar (Pusher WebSocket)
- Toggle chat with remote button
- OAuth 2.1 authentication via QR code

## Setup

1. Clone the repo
2. Install Android SDK (API 35) and set `sdk.dir` in `local.properties`
3. Register an app at [dev.kick.com](https://dev.kick.com) with redirect URI `http://127.0.0.1:8374/callback`
4. Add your credentials to `local.properties`:
   ```properties
   kick.client.id=YOUR_CLIENT_ID
   kick.client.secret=YOUR_CLIENT_SECRET
   kick.redirect.uri=http://127.0.0.1:8374/callback
   ```
5. Build: `./gradlew assembleDebug`
6. Install on Android TV: `adb install app/build/outputs/apk/debug/app-debug.apk`

## License

MIT
