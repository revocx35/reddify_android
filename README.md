# Reddify for Android

Native Android client for [Reddify](https://github.com/revocx35/reddify), the self-hosted music app with
Spotify's catalogue. Spotify-style UI in red, background playback (notification, lock screen, headset buttons),
songs downloaded to the phone for offline play, your own playlists (create, edit, reorder, copy from Spotify),
Chromecast, and sign-in through authentik.

The phone only talks to your Reddify server (never to Spotify or YouTube).

## Install

Download `app-release.apk` from the [releases](https://github.com/revocx35/reddify_android/releases) (or from your
server: Settings → Android app). Enter your server address (https only) and sign in:
- behind authentik: **Sign in with authentik** opens the browser once (OAuth 2 + PKCE, public client
  `reddify-android`, see [docs/authentik.md](docs/authentik.md));
- local accounts: username + password (+ 2FA code).

## Build

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :core:test :app:testDebugUnitTest      # JVM + Robolectric screenshot tests
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease                   # signed when keystore.properties exists (see AGENTS.md)
```

Docs: [ARCHITECTURE.md](ARCHITECTURE.md), [AGENTS.md](AGENTS.md).
