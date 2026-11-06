package io.github.revocx35.reddify.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.revocx35.reddify.MainActivity
import io.github.revocx35.reddify.ReddifyApp
import io.github.revocx35.reddify.data.AppGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background playback: ExoPlayer in a MediaSessionService (notification, lock screen, headset buttons, Android
 * Auto/Wear controls). reddify://track/<id> resolves to the phone's offline copy when there is one, else to the
 * server's /api/stream/<id> (the server streams while it downloads on the first play). It also:
 *  - asks the server to get the next queued track ready (prepare) so it starts instantly,
 *  - appends Spotify's recommendations when the queue runs out (autoplay),
 *  - reports a play after 30 s, and skips a track the server cannot find.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var graph: AppGraph
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private var ticker: Job? = null
    private var reportedFor: String? = null
    private var autoplayFor: String? = null
    var cast: CastBridge? = null

    override fun onCreate() {
        super.onCreate()
        graph = (application as ReddifyApp).graph
        val http = OkHttpDataSource.Factory(graph.http)
        val upstream = DefaultDataSource.Factory(this, http)
        val resolving = ResolvingDataSource.Factory(upstream) { spec ->
            if (spec.uri.scheme != "reddify") return@Factory spec
            val id = spec.uri.lastPathSegment ?: return@Factory spec
            val local = graph.offline.fileFor(id)
            if (local != null) {
                spec.withUri(Uri.fromFile(local))
            } else {
                val api = graph.api.value
                spec.withUri(Uri.parse(api.streamUrl(id).toString())).withAdditionalHeaders(api.authHeaders())
            }
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                // Controllers never see the URI: rebuild it from the media id.
                override fun onAddMediaItems(s: MediaSession, c: MediaSession.ControllerInfo, items: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
                    Futures.immediateFuture(items.map { it.buildUpon().setUri(trackUri(it.mediaId)).build() }.toMutableList())
            })
            .build()
        cast = CastBridge(this, player, graph)
        ticker = graph.scope.launch {
            while (isActive) {
                delay(5_000)
                tick()
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            prefetchNext()
            maybeAutoplay()
        }

        override fun onPlayerError(error: PlaybackException) {
            // A track the server cannot match/download: skip it instead of stopping the whole queue.
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private fun tick() {
        val item = player.currentMediaItem ?: return
        if (player.isPlaying && reportedFor != item.mediaId && player.currentPosition > 30_000) {
            reportedFor = item.mediaId
            val api = graph.api.value
            graph.scope.launch { api.played(item.mediaId) }
        }
        if (player.isPlaying && player.duration > 0 && player.duration - player.currentPosition < 60_000) maybeAutoplay()
    }

    private fun prefetchNext() {
        val i = player.nextMediaItemIndex
        if (i == C.INDEX_UNSET) return
        val id = player.getMediaItemAt(i).mediaId
        if (graph.offline.fileFor(id) != null) return
        val api = graph.api.value
        graph.scope.launch { runCatching { api.prepare(id) } }
    }

    private fun maybeAutoplay() {
        val cur = player.currentMediaItem ?: return
        if (player.repeatMode != Player.REPEAT_MODE_OFF || player.nextMediaItemIndex != C.INDEX_UNSET || autoplayFor == cur.mediaId) return
        autoplayFor = cur.mediaId
        val api = graph.api.value
        graph.scope.launch {
            val recs = runCatching { api.recommendations(cur.mediaId) }.getOrDefault(emptyList())
            val have = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            val add = recs.filter { it.id !in have }.map { it.toMediaItem() }
            if (add.isNotEmpty()) {
                player.addMediaItems(add)
                prefetchNext()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        ticker?.cancel()
        cast?.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
