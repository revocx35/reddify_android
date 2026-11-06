package io.github.revocx35.reddify.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.common.images.WebImage
import com.google.android.gms.cast.CastMediaControlIntent
import io.github.revocx35.reddify.data.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Google Cast with the Default Media Receiver (no custom receiver app needed). */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()

    override fun getAdditionalSessionProviders(context: Context): MutableList<SessionProvider>? = null
}

/** Which Cast device is playing (null = this phone). Read by the UI. */
object CastState {
    val device = MutableStateFlow<String?>(null)
    val deviceFlow: StateFlow<String?> = device.asStateFlow()
}

/**
 * Casting by mirroring: while a Cast session is up, the local ExoPlayer keeps running muted (so queue, position,
 * notification and lock-screen controls work exactly as before), and every track change, play/pause and seek is
 * repeated on the Chromecast, which fetches the track from Reddify's signed /cast/<token> URL.
 */
class CastBridge(context: Context, private val player: Player, private val graph: AppGraph) {
    private var castContext: CastContext? = null
    private var session: CastSession? = null
    private var savedVolume = 1f

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, id: String) = start(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = start(s)
        override fun onSessionEnded(s: CastSession, error: Int) = stop()
        override fun onSessionSuspended(s: CastSession, reason: Int) = Unit
        override fun onSessionStarting(s: CastSession) = Unit
        override fun onSessionStartFailed(s: CastSession, error: Int) = Unit
        override fun onSessionEnding(s: CastSession) = Unit
        override fun onSessionResuming(s: CastSession, id: String) = Unit
        override fun onSessionResumeFailed(s: CastSession, error: Int) = Unit
    }

    private val mirror = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (session != null) loadCurrent()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val c = session?.remoteMediaClient ?: return
            if (playWhenReady) c.play() else c.pause()
        }

        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK && old.mediaItemIndex == new.mediaItemIndex) {
                session?.remoteMediaClient?.seek(MediaSeekOptions.Builder().setPosition(new.positionMs).build())
            }
        }
    }

    init {
        runCatching {
            CastContext.getSharedInstance(context, Executors.newSingleThreadExecutor()).addOnSuccessListener { ctx ->
                castContext = ctx
                ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                ctx.sessionManager.currentCastSession?.let { start(it) }
            }
        }
        player.addListener(mirror)
    }

    private fun start(s: CastSession) {
        session = s
        CastState.device.value = s.castDevice?.friendlyName ?: "Chromecast"
        savedVolume = player.volume.takeIf { it > 0f } ?: 1f
        player.volume = 0f
        loadCurrent()
    }

    private fun stop() {
        session = null
        CastState.device.value = null
        player.volume = savedVolume
        player.pause()
    }

    private fun loadCurrent() {
        val item = player.currentMediaItem ?: return
        val t = item.track()
        val api = graph.api.value
        val position = player.currentPosition
        graph.scope.launch {
            val c = runCatching { api.cast(item.mediaId) }.getOrNull() ?: return@launch
            val md = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_TITLE, t?.name ?: "")
                putString(MediaMetadata.KEY_ARTIST, t?.artistLine ?: "")
                putString(MediaMetadata.KEY_ALBUM_TITLE, t?.album?.name ?: "")
                c.cover?.let { addImage(WebImage(Uri.parse(bigCover(it)))) }
            }
            val info = MediaInfo.Builder(c.url).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(c.contentType).setMetadata(md).build()
            session?.remoteMediaClient?.load(
                MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(player.playWhenReady).setCurrentTime(position).build(),
            )
        }
    }

    fun release() {
        player.removeListener(mirror)
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
    }
}
