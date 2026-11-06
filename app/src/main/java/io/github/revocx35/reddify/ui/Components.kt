package io.github.revocx35.reddify.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import io.github.revocx35.reddify.core.Card
import io.github.revocx35.reddify.core.CoverRef
import io.github.revocx35.reddify.core.Track
import io.github.revocx35.reddify.data.AppGraph
import io.github.revocx35.reddify.playback.PlayerConnection
import io.github.revocx35.reddify.ui.theme.R
import io.github.revocx35.reddify.ui.theme.RIcons

/** What every screen needs. */
class Env(val graph: AppGraph, val player: PlayerConnection, val nav: (String) -> Unit, val back: () -> Unit)

val LocalEnv = staticCompositionLocalOf<Env> { error("no Env") }

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Cover art: Spotify CDN, else the server's cached cover (works when Spotify is down), else a note tile. */
@Composable
fun Cover(url: String?, trackId: String?, size: Dp?, shape: Shape = RoundedCornerShape(4.dp), modifier: Modifier = Modifier) {
    val env = LocalEnv.current
    val fallback = trackId?.let { env.graph.api.value.url("/api/cover/$it").toString() }
    val m = (if (size != null) modifier.size(size) else modifier.aspectRatio(1f)).clip(shape).background(R.elev2)
    var useFallback by remember(url, trackId) { mutableStateOf(url == null) }
    val src = if (useFallback) fallback else url
    if (src == null) {
        Box(m, contentAlignment = Alignment.Center) { Icon(RIcons.music, null, tint = R.muted, modifier = Modifier.size(24.dp)) }
        return
    }
    SubcomposeAsyncImage(
        model = src,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = m,
        error = {
            if (!useFallback && fallback != null) useFallback = true
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(RIcons.music, null, tint = R.muted, modifier = Modifier.size(24.dp)) }
        },
    )
}

@Composable
fun RIcon(icon: ImageVector, size: Dp = 24.dp, tint: androidx.compose.ui.graphics.Color = R.text, modifier: Modifier = Modifier) =
    Icon(icon, null, tint = tint, modifier = modifier.size(size))

@Composable
fun PlayFab(playing: Boolean, size: Dp = 56.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(R.accent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { RIcon(if (playing) RIcons.pause else RIcons.play, size * 0.45f, tint = androidx.compose.ui.graphics.Color.Black) }
}

@Composable
fun LikeButton(id: String, size: Dp = 22.dp) {
    val env = LocalEnv.current
    val liked = id in env.graph.likes.ids.collectAsState().value
    IconButton(onClick = { env.graph.likes.toggle(id) }) {
        RIcon(if (liked) RIcons.heartFill else RIcons.heart, size, tint = if (liked) R.accent else R.sub)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleLarge, color = R.text, modifier = modifier.padding(start = 16.dp, top = 22.dp, bottom = 10.dp, end = 16.dp))

@Composable
fun Loading() = Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = R.accent)
}

@Composable
fun ErrorBox(text: String, retry: (() -> Unit)? = null) = Column(
    Modifier.fillMaxWidth().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    RIcon(RIcons.warning, 32.dp, tint = R.sub)
    Text(text, color = R.sub, style = MaterialTheme.typography.bodyMedium)
    if (retry != null) TextButton(onClick = retry) { Text("Try again", color = R.text, fontWeight = FontWeight.Bold) }
}

@Composable
fun OfflineBanner() = Row(
    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(R.accentDim).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    RIcon(RIcons.offline, 18.dp)
    Spacer(Modifier.width(10.dp))
    Text("Spotify is not reachable. Showing songs from Reddify's library; they still play.", color = R.text, style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(t: Track, list: List<Track>, index: Int, contextName: String?, contextId: String?, showCover: Boolean = true, number: Int? = null, extra: List<MenuAction> = emptyList()) {
    val env = LocalEnv.current
    val ui by env.player.ui.collectAsState()
    val active = ui.current?.id == t.id
    val offline = env.graph.offline.tracks.collectAsState().value.any { it.id == t.id }
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = { env.player.playList(list, index, contextName, contextId) }, onLongClick = { menu = true })
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text("$number", color = if (active) R.accent else R.sub, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium)
        }
        if (showCover) {
            Cover(t.cover, t.id, 48.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(t.name, color = if (active) R.accent else R.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (offline) {
                    RIcon(RIcons.downloaded, 14.dp, tint = R.accent)
                    Spacer(Modifier.width(4.dp))
                }
                if (t.explicit) {
                    Text("E", color = androidx.compose.ui.graphics.Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(R.sub).padding(horizontal = 4.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(t.artistLine, color = R.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { RIcon(RIcons.more, 20.dp, tint = R.sub) }
            TrackMenu(t, menu, extra) { menu = false }
        }
    }
}

/** An extra entry for a track's menu (e.g. "Remove from this playlist" on the user's own playlists). */
class MenuAction(val label: String, val icon: ImageVector, val run: () -> Unit)

@Composable
fun TrackMenu(t: Track, open: Boolean, extra: List<MenuAction> = emptyList(), onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val liked = t.id in env.graph.likes.ids.collectAsState().value
    val onPhone = env.graph.offline.tracks.collectAsState().value.any { it.id == t.id }
    var picker by remember { mutableStateOf(false) }
    if (picker) AddToPlaylistDialog(listOf(t.id)) { picker = false }
    DropdownMenu(expanded = open, onDismissRequest = onDismiss, containerColor = R.elev2) {
        @Composable fun item(label: String, icon: ImageVector, fn: () -> Unit) = DropdownMenuItem(
            text = { Text(label, color = R.text) },
            leadingIcon = { RIcon(icon, 18.dp, tint = R.sub) },
            onClick = { onDismiss(); fn() },
        )
        item("Add to playlist", RIcons.playlistAdd) { picker = true }
        for (x in extra) item(x.label, x.icon, x.run)
        item("Play next", RIcons.queue) { env.player.playNext(t) }
        item("Add to queue", RIcons.plus) { env.player.addToQueue(t) }
        item(if (liked) "Remove from Liked Songs" else "Save to Liked Songs", if (liked) RIcons.heartFill else RIcons.heart) { env.graph.likes.toggle(t.id) }
        if (onPhone) item("Remove from this phone", RIcons.close) { env.graph.offline.remove(t.id) }
        else item("Download to this phone", RIcons.download) { env.graph.offline.download(listOf(t)) }
        t.album?.let { a -> item("Go to album", RIcons.music) { env.nav("album/${a.id}") } }
        t.artists.firstOrNull()?.let { a -> item("Go to artist", RIcons.user) { env.nav("artist/${a.id}") } }
    }
}

fun cardRoute(c: Card): String? = when (c.kind) {
    "album", "artist", "playlist" -> "${c.kind}/${c.id}"
    "track" -> c.track?.album?.id?.let { "album/$it" }
    else -> null
}

@Composable
fun CardTile(c: Card, width: Dp = 150.dp) {
    val env = LocalEnv.current
    Column(
        Modifier.width(width).clip(RoundedCornerShape(6.dp)).clickable {
            if (c.kind == "track" && c.track != null) env.player.playList(listOf(c.track!!), 0, c.name, c.id)
            else cardRoute(c)?.let(env.nav)
        }.padding(8.dp),
    ) {
        Cover(c.image, if (c.kind == "track") c.id else null, width - 16.dp, if (c.kind == "artist") CircleShape else RoundedCornerShape(6.dp))
        Spacer(Modifier.height(8.dp))
        Text(c.name, color = R.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(c.subtitle, color = R.sub, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Shelf(title: String, items: List<Card>) {
    if (items.isEmpty()) return
    SectionTitle(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) { items(items, key = { "${it.kind}:${it.id}" }) { CardTile(it) } }
}

@Composable
fun Chip(label: String, on: Boolean, onClick: () -> Unit) = Text(
    label,
    color = if (on) androidx.compose.ui.graphics.Color.Black else R.text,
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) R.text else R.elev2).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
)

/** A Reddify playlist's cover: 2x2 mosaic of four album covers, else the first cover, else a note tile. */
@Composable
fun PlaylistCover(covers: List<CoverRef>, size: Dp) {
    if (covers.size >= 4) {
        val half = size / 2
        Column(Modifier.size(size).clip(RoundedCornerShape(4.dp))) {
            for (row in covers.take(4).chunked(2)) Row { for (c in row) Cover(c.url, c.trackId, half, RoundedCornerShape(0.dp)) }
        }
    } else {
        val c = covers.firstOrNull()
        Cover(c?.url, c?.trackId, size)
    }
}

/** "Add to playlist": a new playlist, then the user's playlists (check = the song is already in it). */
@Composable
fun AddToPlaylistDialog(ids: List<String>, onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val lists by env.graph.playlists.list.collectAsState()
    var has by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(ids) {
        env.graph.playlists.refresh()
        if (ids.size == 1) runCatching { has = env.graph.api.value.trackPlaylists(ids[0]).toSet() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = R.elev2,
        title = { Text("Add to playlist", color = R.text) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { onDismiss(); env.graph.playlists.create(ids) { env.nav("my/${it.id}") } }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(R.elev3), contentAlignment = Alignment.Center) { RIcon(RIcons.plus, 24.dp) }
                        Spacer(Modifier.width(12.dp))
                        Text("New playlist", color = R.text, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = R.elev3)
                }
                items(lists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onDismiss(); env.graph.playlists.add(p, ids) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistCover(p.covers, 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = R.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.count} songs", color = R.sub, style = MaterialTheme.typography.bodySmall)
                        }
                        if (p.id in has) RIcon(RIcons.check, 20.dp, tint = R.accent)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = R.text) } },
    )
}
