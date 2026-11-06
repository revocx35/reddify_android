package io.github.revocx35.reddify.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.revocx35.reddify.core.JSON
import io.github.revocx35.reddify.core.Track

/** Queue items carry the whole Track (for the UI) and a reddify://track/<id> URI resolved in the service. */
const val TRACK_EXTRA = "reddify.track"

fun trackUri(id: String): Uri = Uri.parse("reddify://track/$id")

fun Track.toMediaItem(): MediaItem {
    val extras = Bundle().apply { putString(TRACK_EXTRA, JSON.encodeToString(Track.serializer(), this@toMediaItem)) }
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(trackUri(id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(artistLine)
                .setAlbumTitle(album?.name)
                .setArtworkUri(cover?.let { Uri.parse(bigCover(it)) })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(extras)
                .build(),
        )
        .build()
}

fun MediaItem.track(): Track? = mediaMetadata.extras?.getString(TRACK_EXTRA)?.let {
    runCatching { JSON.decodeFromString(Track.serializer(), it) }.getOrNull()
}

/** Spotify's 300 px cover id → the 640 px one (same image, bigger). */
fun bigCover(url: String): String = url.replace("ab67616d00001e02", "ab67616d0000b273")
