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
  - `playback/PlayerConnection.kt` — UI side: a `MediaController` exposed as a `StateFlow<PlayerUi>`. Also Smart
    Shuffle: the shuffle button cycles off → shuffle → Smart Shuffle; on, it sends the queue's ids to
    `POST /api/smart-shuffle` (about one recommendation per three songs) and adds the answer with `addMediaItems`
    (Media3's shuffle order places each one randomly). Those items carry `reddify.smart` in their metadata extras
    (sparkle in the queue/mini player, "Recommended by Smart Shuffle" on now playing) and are removed again when it is
    turned off. A list started meanwhile drops a late answer (`listSeq`).
  - `ui/` — Compose: `Root.kt` (sign-in gate, navigation, mini player, now playing (adaptive: width capped, cover shrinks to fit on foldables/landscape), queue, cast button),
    `screens/Browse.kt` (home, search, album, artist, playlist + "Save as my playlist"), `screens/Library.kt`
    (pencil = edit mode: ✕ deletes an own playlist after a confirm, or hides a Spotify playlist/album/artist in Reddify
    only via `PUT /api/library/hidden/{kind}/{id}`; Spotify keeps it; "Removed" lists them with Restore),
    `screens/MyPlaylist.kt` (own playlists: edit details, add songs, remove, move up/down, delete), `screens/Account.kt`
    (sign-in, settings). `Components.kt` has the track menu with "Add to playlist" (`AddToPlaylistDialog`) and the
    2x2 `PlaylistCover`; `data/AppGraph.kt` `MyPlaylists` keeps the list for the library and the picker. Theme tokens = the web UI's; icons generated from the web's `Icon.tsx`.

## Security

- Secrets only in the Keystore-encrypted files in `noBackupFilesDir`; `allowBackup=false`.
- The token is sent only to the configured server origin; the authentik token only to its signed-in site.
- Permissions: INTERNET, network state, foreground media-playback service, notifications, wake lock.

## Offline

App start without network and with downloaded songs opens "On this phone"; they play from local files.
