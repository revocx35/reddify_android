# Reddify Android — architecture

Client of the Reddify server (API contract: `shared/api.d.ts` in revocx35/reddify, mirrored in
`core/.../Models.kt`). HTTPS only, system CAs only (`network_security_config.xml`, no cleartext).

## Modules

- `core` (pure Kotlin/JVM, unit-tested): `Models.kt`, `ApiClient.kt` (OkHttp, error mapping, https-only server
  address), `Authentik.kt` (shared with wa_logger / insta_logger / web-ip-phone / RWR: OAuth 2 + PKCE, token refresh,
  the forward-auth redirect detection, `X-App-Authorization` swap).
- `app`:
  - `data/AppGraph.kt` — one server per install; app token (local accounts) and authentik tokens encrypted with
    AES-256-GCM keys in the Android Keystore (`KeystoreBox`, `KeystoreAuthentikStore`); likes cache.
  - `data/OfflineStore.kt` — songs downloaded to the phone (`filesDir/offline/<id>.<ext>` + `index.json`), fetched
    with `GET /api/stream/<id>?download=1` (the server answers once the whole file is in its library).
  - `playback/PlaybackService.kt` — Media3 `MediaSessionService` + ExoPlayer. Queue items are
    `reddify://track/<id>`; a `ResolvingDataSource` maps them to the offline file or to the server's
    `/api/stream/<id>` with the auth header (the server streams while it downloads on a first play). Also: prefetch of
    the next track (`/prepare`), autoplay from Spotify recommendations when the queue ends, play reports after 30 s,
    skip on errors.
  - `playback/CastSupport.kt` — Google Cast (Default Media Receiver). Mirroring: while casting, the local player runs
    muted and track changes / play / pause / seek are repeated on the Chromecast, which loads the server's signed
    `/cast/<token>` URL.
  - `playback/PlayerConnection.kt` — UI side: a `MediaController` exposed as a `StateFlow<PlayerUi>`.
  - `ui/` — Compose: `Root.kt` (sign-in gate, navigation, mini player, now playing, queue, cast button),
    `screens/Browse.kt` (home, search, album, artist, playlist), `screens/Library.kt`, `screens/Account.kt`
    (sign-in, settings). Theme tokens = the web UI's; icons generated from the web's `Icon.tsx`.

## Security

- Secrets only in the Keystore-encrypted files in `noBackupFilesDir`; `allowBackup=false`.
- The token is sent only to the configured server origin; the authentik token only to its signed-in site.
- Permissions: INTERNET, network state, foreground media-playback service, notifications, wake lock.

## Offline

App start without network and with downloaded songs opens "On this phone"; they play from local files.
