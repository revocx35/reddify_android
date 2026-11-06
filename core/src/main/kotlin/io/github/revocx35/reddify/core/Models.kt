package io.github.revocx35.reddify.core

import kotlinx.serialization.Serializable

/*
 * Mirror of shared/api.d.ts (server repo). Unknown fields are ignored and every field has a default, so a newer
 * server never crashes this client. Keep in step with the server: change shared/api.d.ts first.
 */

@Serializable
data class ArtistRef(val id: String = "", val name: String = "")

@Serializable
data class AlbumRef(val id: String = "", val name: String = "")

@Serializable
data class Track(
    val id: String = "",
    val name: String = "",
    val artists: List<ArtistRef> = emptyList(),
    val album: AlbumRef? = null,
    val cover: String? = null,
    val durationMs: Long = 0,
    val explicit: Boolean = false,
    val playable: Boolean = true,
    val cached: Boolean? = null,
) {
    val artistLine: String get() = artists.joinToString(", ") { it.name }
}

@Serializable
data class Card(
    val kind: String = "",
    val id: String = "",
    val name: String = "",
    val subtitle: String = "",
    val image: String? = null,
    val track: Track? = null,
)

@Serializable
data class Section(val title: String = "", val items: List<Card> = emptyList())

@Serializable
data class HomeResponse(val greeting: String = "", val sections: List<Section> = emptyList(), val offline: Boolean = false)

@Serializable
data class SearchResponse(
    val query: String = "",
    val top: Card? = null,
    val tracks: List<Track> = emptyList(),
    val artists: List<Card> = emptyList(),
    val albums: List<Card> = emptyList(),
    val playlists: List<Card> = emptyList(),
    val offline: Boolean = false,
)

@Serializable
data class TrackPage(val tracks: List<Track> = emptyList(), val total: Int = 0, val offset: Int = 0)

@Serializable
data class AlbumInfo(
    val id: String = "",
    val name: String = "",
    val cover: String? = null,
    val artists: List<ArtistRef> = emptyList(),
    val year: String = "",
    val type: String = "album",
    val label: String = "",
)

@Serializable
data class AlbumResponse(val album: AlbumInfo = AlbumInfo(), val tracks: List<Track> = emptyList())

@Serializable
data class ArtistInfo(
    val id: String = "",
    val name: String = "",
    val image: String? = null,
    val header: String? = null,
    val monthlyListeners: Long? = null,
)

@Serializable
data class ArtistResponse(
    val artist: ArtistInfo = ArtistInfo(),
    val topTracks: List<Track> = emptyList(),
    val albums: List<Card> = emptyList(),
    val singles: List<Card> = emptyList(),
    val related: List<Card> = emptyList(),
)

@Serializable
data class PlaylistInfo(val id: String = "", val name: String = "", val description: String = "", val image: String? = null, val owner: String = "")

@Serializable
data class PlaylistResponse(val playlist: PlaylistInfo = PlaylistInfo(), val tracks: List<Track> = emptyList(), val total: Int = 0, val offset: Int = 0)

@Serializable
data class PrepareResponse(val id: String = "", val status: String = "none", val error: String? = null)

@Serializable
data class CastResponse(val url: String = "", val contentType: String = "audio/mp4", val cover: String? = null, val track: Track = Track())

@Serializable
data class LibraryResponse(val liked: Int = 0, val downloaded: Int = 0, val playlists: List<Card> = emptyList())

@Serializable
data class SpotifyStatus(
    val instance: Int = 0,
    val mode: String = "per-user",
    val canManage: Boolean = false,
    val browser: String = "stopped",
    val loggedIn: Boolean = false,
    val account: String? = null,
    val learned: List<String> = emptyList(),
)

@Serializable
data class Me(
    val id: Int = 0,
    val username: String = "",
    val displayName: String = "",
    val role: String = "user",
    val totp: Boolean = false,
    val local: Boolean = false,
    val oidc: Boolean = false,
    val csrf: String = "",
)

@Serializable
data class OidcInfo(val enabled: Boolean = false, val label: String = "authentik")

@Serializable
data class AuthState(val setupNeeded: Boolean = false, val localLogin: Boolean = true, val oidc: OidcInfo = OidcInfo(), val me: Me? = null)

@Serializable
data class LoginResponse(val token: String? = null, val me: Me = Me(), val mustChangePassword: Boolean = false)

@Serializable
data class ApiErrorBody(val error: String = "error", val message: String = "")
