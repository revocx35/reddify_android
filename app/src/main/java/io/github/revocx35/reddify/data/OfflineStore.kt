package io.github.revocx35.reddify.data

import android.content.Context
import io.github.revocx35.reddify.core.JSON
import io.github.revocx35.reddify.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Songs downloaded to this phone for offline play (Library → "On this phone"). Audio goes to
 * filesDir/offline/<id>.<ext> (app-private), the track list to index.json. The player prefers these files over
 * the server; they keep playing with no network at all.
 */
class OfflineStore(context: Context, private val graph: AppGraph) {
    private val dir = File(context.filesDir, "offline").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val gate = Semaphore(2)

    private val _tracks = MutableStateFlow(load())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    /** Track ids being downloaded right now. */
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private fun load(): List<Track> = runCatching {
        JSON.decodeFromString(ListSerializer(Track.serializer()), index.readText())
    }.getOrDefault(emptyList()).filter { fileFor(it.id) != null }

    private fun saveIndex(list: List<Track>) {
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(JSON.encodeToString(ListSerializer(Track.serializer()), list))
        tmp.renameTo(index)
    }

    /** The local audio file of a track, or null. */
    fun fileFor(id: String): File? {
        if (!ID.matches(id)) return null
        return dir.listFiles { f -> f.name.startsWith("$id.") && !f.name.endsWith(".part") }?.firstOrNull()
    }

    fun has(id: String): Boolean = _tracks.value.any { it.id == id }

    fun download(tracks: List<Track>) {
        for (t in tracks) {
            if (has(t.id) || t.id in _busy.value) continue
            _busy.value = _busy.value + t.id
            graph.scope.launch {
                try {
                    gate.withPermit { fetch(t) }
                } catch (_: Exception) {
                    // stays not-downloaded; the button can be pressed again
                } finally {
                    _busy.value = _busy.value - t.id
                }
            }
        }
    }

    private suspend fun fetch(t: Track) = withContext(Dispatchers.IO) {
        val api = graph.api.value
        // ?download=1 waits on the server until the whole file is in its library, then sends it.
        val url = api.streamUrl(t.id).newBuilder().addQueryParameter("download", "1").build()
        val req = Request.Builder().url(url).apply { api.authHeaders().forEach { (k, v) -> header(k, v) } }.build()
        graph.http.newBuilder().readTimeout(5, java.util.concurrent.TimeUnit.MINUTES).build().newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val ext = when ((res.header("Content-Type") ?: "").substringBefore(';')) {
                "audio/webm" -> "webm"
                "audio/ogg" -> "ogg"
                "audio/mpeg" -> "mp3"
                else -> "m4a"
            }
            val part = File(dir, "${t.id}.$ext.part")
            part.outputStream().use { out -> res.body.byteStream().copyTo(out) }
            if (part.length() < 10_000) {
                part.delete()
                throw IOException("too small")
            }
            part.renameTo(File(dir, "${t.id}.$ext"))
        }
        synchronized(this@OfflineStore) {
            val list = _tracks.value.filterNot { it.id == t.id } + t.copy(cached = true)
            saveIndex(list)
            _tracks.value = list
        }
    }

    fun remove(id: String) {
        fileFor(id)?.delete()
        synchronized(this) {
            val list = _tracks.value.filterNot { it.id == id }
            saveIndex(list)
            _tracks.value = list
        }
    }

    fun bytesUsed(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0

    private companion object {
        val ID = Regex("^[A-Za-z0-9]{22}$")
    }
}
