package io.github.revocx35.reddify.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.github.revocx35.reddify.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUi(
    val current: Track? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val error: String? = null,
    val contextName: String? = null,
    val contextId: String? = null,
)

/** The UI's handle on [PlaybackService]: a MediaController plus a [PlayerUi] snapshot for Compose. */
class PlayerConnection(context: Context, private val scope: CoroutineScope) {
    private var controller: MediaController? = null
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()
    private var contextName: String? = null
    private var contextId: String? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    init {
        // Robolectric's service shadow hands MediaController a null ComponentName: screenshot tests run without it.
        if (android.os.Build.FINGERPRINT != "robolectric") connect(context)
        scope.launch {
            while (isActive) {
                delay(500)
                if (controller?.isPlaying == true) refresh()
            }
        }
    }

    private fun connect(context: Context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            })
            pending.forEach { it(c) }
            pending.clear()
            refresh()
        }, MoreExecutors.directExecutor())
    }

    private fun withController(fn: (MediaController) -> Unit) {
        controller?.let(fn) ?: pending.add(fn)
    }

    private fun refresh() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).track() }
        _ui.value = PlayerUi(
            current = c.currentMediaItem?.track(),
            playing = c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING),
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (c.currentMediaItem?.track()?.durationMs ?: 0),
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            queue = queue,
            index = c.currentMediaItemIndex,
            error = c.playerError?.let { "Could not play this song." },
            contextName = contextName,
            contextId = contextId,
        )
    }

    fun playList(tracks: List<Track>, start: Int, name: String?, id: String?) {
        if (tracks.isEmpty()) return
        contextName = name
        contextId = id
        withController { c ->
            c.setMediaItems(tracks.map { it.toMediaItem() }, start.coerceIn(0, tracks.size - 1), 0)
            c.prepare()
            c.play()
        }
    }

    fun playNext(t: Track) = withController { c ->
        if (c.mediaItemCount == 0) playList(listOf(t), 0, t.name, t.id)
        else c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
    }

    fun addToQueue(t: Track) = withController { c ->
        if (c.mediaItemCount == 0) playList(listOf(t), 0, t.name, t.id) else c.addMediaItem(t.toMediaItem())
    }

    fun toggle() = withController { c ->
        if (c.playerError != null) c.prepare()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = withController { it.seekToNext() }
    fun prev() = withController { it.seekToPrevious() }
    fun seek(ms: Long) = withController { it.seekTo(ms) }
    fun jumpTo(i: Int) = withController { it.seekTo(i, 0); it.play() }
    fun shuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun repeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
