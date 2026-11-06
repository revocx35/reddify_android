package io.github.revocx35.reddify.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.net.ssl.SSLException

/** An API failure; [message] is safe to show. */
class ApiException(val status: Int, val code: String, override val message: String, cause: Throwable? = null) : IOException(message, cause) {
    companion object {
        const val NETWORK = "network"
        const val AUTHENTIK = "authentik"
        const val NOT_REDDIFY = "not_reddify"
    }
}

fun Throwable.userMessage(): String = (this as? ApiException)?.message ?: "Something went wrong."

/**
 * Normalises what the user typed as the server address into its root URL (https only: the app never sends
 * credentials or plays media over cleartext). null when unusable.
 */
fun parseServerUrl(input: String): HttpUrl? {
    val raw = input.trim()
    if (raw.isEmpty() || raw.any { it.isWhitespace() }) return null
    val withScheme = if (raw.contains("://")) raw else "https://$raw"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    return url.newBuilder().encodedPath("/").query(null).fragment(null).build()
}

val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

/**
 * Reddify's HTTP API for the Android client. [token] is the app token of a local account (sent as
 * `Authorization: Bearer rdf_…`; the [Authentik] interceptor moves it to `X-App-Authorization` when the server sits
 * behind authentik). With authentik and no local account the authentik bearer alone identifies the user.
 */
class ApiClient(private val http: OkHttpClient, val base: HttpUrl, @Volatile var token: String?) {
    private val jsonType = "application/json".toMediaType()

    fun url(path: String): HttpUrl = base.resolve(path.removePrefix("/")) ?: throw IllegalArgumentException(path)

    private fun request(path: String): Request.Builder {
        val b = Request.Builder().url(url(path)).header("Accept", "application/json")
        token?.let { b.header("Authorization", "Bearer $it") }
        return b
    }

    /** Headers for media requests made outside this client (ExoPlayer, Coil, downloads). */
    fun authHeaders(): Map<String, String> = token?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()

    private suspend fun <T> call(req: Request, ser: KSerializer<T>): T = withContext(Dispatchers.IO) {
        val res = try {
            http.newCall(req).execute()
        } catch (e: AuthentikRequiredException) {
            throw ApiException(401, ApiException.AUTHENTIK, "Sign in with authentik to continue.", e)
        } catch (e: SSLException) {
            throw ApiException(0, ApiException.NETWORK, "Secure connection failed: ${e.message ?: "TLS error"}", e)
        } catch (e: IOException) {
            throw ApiException(0, ApiException.NETWORK, "Cannot reach the server.", e)
        }
        res.use {
            val body = it.body.string()
            if (!it.isSuccessful) {
                val err = runCatching { JSON.decodeFromString(ApiErrorBody.serializer(), body) }.getOrNull()
                throw ApiException(it.code, err?.error ?: "http_${it.code}", err?.message?.ifEmpty { null } ?: "Request failed (${it.code}).")
            }
            if (!(it.header("Content-Type") ?: "").contains("json")) throw ApiException(it.code, ApiException.NOT_REDDIFY, "That address is not a Reddify server.")
            try {
                JSON.decodeFromString(ser, body)
            } catch (e: Exception) {
                throw ApiException(it.code, ApiException.NOT_REDDIFY, "Unexpected answer from the server.", e)
            }
        }
    }

    private suspend fun <T> get(path: String, ser: KSerializer<T>): T = call(request(path).get().build(), ser)
    private suspend fun <T> send(method: String, path: String, body: String, ser: KSerializer<T>): T =
        call(request(path).method(method, body.toRequestBody(jsonType)).build(), ser)

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private val ok = kotlinx.serialization.json.JsonObject.serializer()

    // auth
    suspend fun state() = get("/api/auth/state", AuthState.serializer())
    suspend fun login(username: String, password: String, totp: String?): LoginResponse {
        val b = buildJsonObject {
            put("username", username); put("password", password); put("client", "app"); put("label", "Android")
            if (!totp.isNullOrBlank()) put("totp", totp)
        }
        return send("POST", "/api/auth/login", b.toString(), LoginResponse.serializer())
    }
    suspend fun logout() { runCatching { send("POST", "/api/auth/logout", "{}", ok) } }
    suspend fun changePassword(next: String) = send("POST", "/api/me/password", buildJsonObject { put("current", ""); put("next", next) }.toString(), ok)

    // catalogue
    suspend fun home() = get("/api/home", HomeResponse.serializer())
    suspend fun search(q: String) = get("/api/search?q=${enc(q)}", SearchResponse.serializer())
    suspend fun searchTracks(q: String, offset: Int) = get("/api/search/tracks?q=${enc(q)}&offset=$offset", TrackPage.serializer())
    suspend fun album(id: String) = get("/api/album/${enc(id)}", AlbumResponse.serializer())
    suspend fun artist(id: String) = get("/api/artist/${enc(id)}", ArtistResponse.serializer())
    suspend fun playlist(id: String, offset: Int = 0) = get("/api/playlist/${enc(id)}?offset=$offset", PlaylistResponse.serializer())
    suspend fun recommendations(id: String) = get("/api/track/${enc(id)}/recommendations", ListSerializer(Track.serializer()))

    // library
    suspend fun library() = get("/api/library", LibraryResponse.serializer())
    suspend fun liked() = get("/api/library/liked", ListSerializer(Track.serializer()))
    suspend fun likedIds() = get("/api/library/liked/ids", ListSerializer(String.serializer()))
    suspend fun like(id: String, on: Boolean) = send(if (on) "PUT" else "DELETE", "/api/library/liked/${enc(id)}", "{}", ok)
    suspend fun downloads(q: String, offset: Int) = get("/api/library/downloads?q=${enc(q)}&offset=$offset", TrackPage.serializer())
    suspend fun recent() = get("/api/library/recent", ListSerializer(Track.serializer()))
    suspend fun spotifyLiked(offset: Int) = get("/api/library/spotify-liked?offset=$offset", TrackPage.serializer())

    // own playlists
    suspend fun myPlaylists() = get("/api/playlists", ListSerializer(MyPlaylist.serializer()))
    suspend fun myPlaylist(id: Long) = get("/api/playlists/$id", MyPlaylistResponse.serializer())
    suspend fun createPlaylist(name: String? = null, tracks: List<String> = emptyList()): MyPlaylistResponse {
        val b = buildJsonObject {
            if (name != null) put("name", name)
            if (tracks.isNotEmpty()) put("tracks", JsonArray(tracks.map { JsonPrimitive(it) }))
        }
        return send("POST", "/api/playlists", b.toString(), MyPlaylistResponse.serializer())
    }
    suspend fun updatePlaylist(id: Long, name: String, description: String) =
        send("PATCH", "/api/playlists/$id", buildJsonObject { put("name", name); put("description", description) }.toString(), MyPlaylist.serializer())
    suspend fun deletePlaylist(id: Long) = send("DELETE", "/api/playlists/$id", "{}", ok)
    suspend fun addToPlaylist(id: Long, ids: List<String>) =
        send("POST", "/api/playlists/$id/tracks", buildJsonObject { put("ids", JsonArray(ids.map { JsonPrimitive(it) })) }.toString(), AddTracksResponse.serializer())
    suspend fun removeFromPlaylist(id: Long, trackId: String) = send("DELETE", "/api/playlists/$id/tracks/${enc(trackId)}", "{}", MyPlaylist.serializer())
    suspend fun movePlaylistTrack(id: Long, trackId: String, to: Int) =
        send("PUT", "/api/playlists/$id/tracks/${enc(trackId)}/position", buildJsonObject { put("to", to) }.toString(), MyPlaylist.serializer())
    suspend fun trackPlaylists(id: String) = get("/api/tracks/${enc(id)}/playlists", ListSerializer(Long.serializer()))

    // playback
    suspend fun prepare(id: String) = send("POST", "/api/tracks/${enc(id)}/prepare", "{}", PrepareResponse.serializer())
    suspend fun status(id: String) = get("/api/tracks/${enc(id)}/status", PrepareResponse.serializer())
    suspend fun played(id: String) { runCatching { send("POST", "/api/tracks/${enc(id)}/played", "{}", ok) } }
    suspend fun cast(id: String) = send("POST", "/api/tracks/${enc(id)}/cast", "{}", CastResponse.serializer())
    fun streamUrl(id: String): HttpUrl = url("/api/stream/${enc(id)}")

    // spotify browser
    suspend fun spotifyStatus() = get("/api/spotify/status", SpotifyStatus.serializer())
    suspend fun spotifyCookie(spDc: String) = send("POST", "/api/spotify/cookie", buildJsonObject { put("spDc", spDc) }.toString(), ok)
    suspend fun spotifyLogout() = send("POST", "/api/spotify/logout", "{}", ok)
}
