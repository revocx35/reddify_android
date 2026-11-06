package io.github.revocx35.reddify.data

import android.content.Context
import android.widget.Toast
import io.github.revocx35.reddify.BuildConfig
import io.github.revocx35.reddify.core.ApiClient
import io.github.revocx35.reddify.core.Authentik
import io.github.revocx35.reddify.core.MyPlaylist
import io.github.revocx35.reddify.core.userMessage
import io.github.revocx35.reddify.core.parseServerUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * App-wide objects. One server per install: its address (plain prefs), the local-account app token (encrypted,
 * [KeystoreBox]) and the authentik sign-in (encrypted, [KeystoreAuthentikStore]).
 */
class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences("reddify", Context.MODE_PRIVATE)
    private val tokenBox = KeystoreBox(appContext, "token.bin", "reddify_token")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Plain client for authentik's own endpoints (system trust, no interceptors). */
    private val plainHttp = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
    val authentik = Authentik(KeystoreAuthentikStore(appContext), plainHttp)

    /** Client for the Reddify server: authentik interceptors (no-ops when not signed in there). */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(authentik.interceptor)
        .addNetworkInterceptor(authentik.networkInterceptor)
        .build()

    var serverUrl: String
        get() = prefs.getString("server", null) ?: BuildConfig.DEFAULT_SERVER
        set(v) {
            prefs.edit().putString("server", v).apply()
            rebuild()
        }

    private val _api = MutableStateFlow(makeApi())
    val api: StateFlow<ApiClient> = _api.asStateFlow()
    val offline = OfflineStore(appContext, this)
    val likes = Likes(this)
    val playlists = MyPlaylists(this)

    private fun makeApi(): ApiClient =
        // No server chosen yet: a placeholder that is never contacted before the sign-in screen sets the real one.
        ApiClient(http, parseServerUrl(serverUrl) ?: parseServerUrl("https://reddify.invalid")!!, tokenBox.read())

    private fun rebuild() {
        _api.value = makeApi()
    }

    /** Tests only: talk to a local (http) mock server. */
    fun useApiForTests(api: ApiClient) {
        _api.value = api
    }

    fun saveToken(token: String?) {
        tokenBox.write(token)
        _api.value.token = token
    }

    fun signOut() {
        val api = _api.value
        scope.launch {
            api.logout()
            saveToken(null)
            authentik.signOut()
        }
    }
}

/** The user's liked track ids, cached for the hearts in every list. */
class Likes(private val graph: AppGraph) {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    suspend fun load() {
        runCatching { _ids.value = graph.api.value.likedIds().toSet() }
    }

    fun toggle(id: String) {
        val on = id !in _ids.value
        _ids.value = if (on) _ids.value + id else _ids.value - id
        graph.scope.launch {
            runCatching { graph.api.value.like(id, on) }.onFailure { _ids.value = if (on) _ids.value - id else _ids.value + id }
        }
    }
}

/** The user's own Reddify playlists (library list, "Add to playlist" picker). */
class MyPlaylists(private val graph: AppGraph) {
    private val _list = MutableStateFlow<List<MyPlaylist>>(emptyList())
    val list: StateFlow<List<MyPlaylist>> = _list.asStateFlow()

    suspend fun refresh() {
        runCatching { _list.value = graph.api.value.myPlaylists() }
    }

    /** Most recently changed first, like the server orders them. */
    fun upsert(p: MyPlaylist) {
        _list.value = listOf(p) + _list.value.filter { it.id != p.id }
    }

    fun remove(id: Long) {
        _list.value = _list.value.filter { it.id != id }
    }

    fun toast(text: String) = Toast.makeText(graph.appContext, text, Toast.LENGTH_SHORT).show()

    /** Creates a playlist (optionally with songs) and calls [then] with it on success. */
    fun create(tracks: List<String> = emptyList(), name: String? = null, then: (MyPlaylist) -> Unit = {}) {
        graph.scope.launch {
            runCatching { graph.api.value.createPlaylist(name, tracks) }
                .onSuccess { upsert(it.playlist); toast(if (tracks.isEmpty()) "Created ${it.playlist.name}" else "Saved as ${it.playlist.name}"); then(it.playlist) }
                .onFailure { toast(it.userMessage()) }
        }
    }

    fun add(p: MyPlaylist, ids: List<String>) {
        graph.scope.launch {
            runCatching { graph.api.value.addToPlaylist(p.id, ids) }
                .onSuccess { upsert(it.playlist); toast(if (it.added > 0) "Added to ${p.name}" else "Already in ${p.name}") }
                .onFailure { toast(it.userMessage()) }
        }
    }
}
