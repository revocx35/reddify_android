package io.github.revocx35.reddify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.revocx35.reddify.core.Track
import io.github.revocx35.reddify.ui.Cover
import io.github.revocx35.reddify.ui.ErrorBox
import io.github.revocx35.reddify.ui.LocalEnv
import io.github.revocx35.reddify.ui.Loading
import io.github.revocx35.reddify.ui.PlayFab
import io.github.revocx35.reddify.ui.RIcon
import io.github.revocx35.reddify.ui.TrackRow
import io.github.revocx35.reddify.ui.cardRoute
import io.github.revocx35.reddify.ui.theme.R
import io.github.revocx35.reddify.ui.theme.RIcons
import kotlinx.coroutines.delay

@Composable
private fun LibRow(icon: ImageVector, brush: Brush, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)).background(brush), contentAlignment = Alignment.Center) { RIcon(icon, 28.dp) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = R.text, style = MaterialTheme.typography.titleMedium)
            Text(sub, color = R.sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val red = Brush.linearGradient(listOf(Color(0xFF3A0810), R.accent, Color(0xFFFFB3BA)))
private val dark = Brush.linearGradient(listOf(Color(0xFF111111), Color(0xFF4A1219)))
private val grey = Brush.linearGradient(listOf(Color(0xFF222222), Color(0xFF555555)))

@Composable
fun LibraryScreen() {
    val env = LocalEnv.current
    val lib = rememberLoad("lib") { env.graph.api.value.library() }
    val onPhone by env.graph.offline.tracks.collectAsState()
    val mine by env.graph.playlists.list.collectAsState()
    LaunchedEffect(Unit) { env.graph.playlists.refresh() }
    LazyColumn(contentPadding = bottomPad) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your Library", style = MaterialTheme.typography.headlineMedium, color = R.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { env.graph.playlists.create { env.nav("my/${it.id}") } }) { RIcon(RIcons.plus, 26.dp) }
            }
        }
        item { LibRow(RIcons.heartFill, red, "Liked Songs", "Playlist • ${lib.data?.liked ?: 0} songs") { env.nav("liked") } }
        item { LibRow(RIcons.downloaded, dark, "On this phone", "${onPhone.size} songs • plays offline") { env.nav("onphone") } }
        item { LibRow(RIcons.library, dark, "Server library", "${lib.data?.downloaded ?: 0} songs anyone played") { env.nav("serverlib") } }
        item { LibRow(RIcons.clock, grey, "Recently played", "Your history") { env.nav("recent") } }
        item { LibRow(RIcons.heart, dark, "Spotify Liked Songs", "From your Spotify account") { env.nav("spotifyliked") } }
        item { MyPlaylistRows(mine) }
        items(lib.data?.playlists ?: emptyList()) { c ->
            Row(Modifier.fillMaxWidth().clickable { cardRoute(c)?.let(env.nav) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(c.image, null, 64.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(c.name, color = R.text, style = MaterialTheme.typography.titleMedium)
                    Text(c.subtitle, color = R.sub, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun TrackListScreen(title: String, contextId: String, tracks: List<Track>?, error: String?, reload: () -> Unit, empty: String, header: (@Composable () -> Unit)? = null) {
    val env = LocalEnv.current
    LazyColumn(contentPadding = bottomPad) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color(0x99E8283B), 1f to R.panel)).statusBarsPadding().padding(bottom = 8.dp)) {
                BackRow()
                Text(title, style = MaterialTheme.typography.headlineMedium, color = R.text, modifier = Modifier.padding(horizontal = 16.dp))
                if (tracks != null) Text("${tracks.size} songs", color = R.sub, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        header?.let { h -> item { h() } }
        when {
            error != null && tracks == null -> item { ErrorBox(error, reload) }
            tracks == null -> item { Loading() }
            tracks.isEmpty() -> item { Text(empty, color = R.sub, modifier = Modifier.padding(24.dp)) }
            else -> {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Spacer(Modifier.weight(1f))
                        PlayFab(false) { env.player.playList(tracks, 0, title, contextId) }
                    }
                }
                itemsIndexed(tracks) { i, t -> TrackRow(t, tracks, i, title, contextId) }
            }
        }
    }
}

@Composable
fun LikedScreen() {
    val env = LocalEnv.current
    val r = rememberLoad("liked", env.graph.likes.ids.collectAsState().value.size) { env.graph.api.value.liked() }
    TrackListScreen("Liked Songs", "liked", r.data, r.error, r.reload, "Songs you like will appear here. Tap the heart on any song.")
}

@Composable
fun RecentScreen() {
    val env = LocalEnv.current
    val r = rememberLoad("recent") { env.graph.api.value.recent() }
    TrackListScreen("Recently played", "recent", r.data, r.error, r.reload, "Nothing yet.")
}

@Composable
fun SpotifyLikedScreen() {
    val env = LocalEnv.current
    val r = rememberLoad("spliked") { env.graph.api.value.spotifyLiked(0).tracks }
    TrackListScreen("Spotify Liked Songs", "spotify-liked", r.data, r.error?.let { "$it Log Reddify's browser in to Spotify on the web page (Settings → Spotify connection)." }, r.reload, "No liked songs on Spotify.")
}

@Composable
fun OnPhoneScreen() {
    val env = LocalEnv.current
    val tracks by env.graph.offline.tracks.collectAsState()
    val busy by env.graph.offline.busy.collectAsState()
    TrackListScreen("On this phone", "offline", tracks, null, {}, "Download albums, playlists or songs (↓ button or a song's menu) to play them without a connection.") {
        if (busy.isNotEmpty()) Text("Downloading ${busy.size} songs…", color = R.accent, modifier = Modifier.padding(horizontal = 16.dp))
        Text("Uses ${env.graph.offline.bytesUsed() / 1_048_576} MB", color = R.sub, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
fun ServerLibraryScreen() {
    val env = LocalEnv.current
    var text by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(text) {
        delay(300)
        q = text.trim()
    }
    val r = rememberLoad("serverlib", q) { env.graph.api.value.downloads(q, 0).tracks }
    TrackListScreen("Server library", "serverlib", r.data, r.error, r.reload, "Every song anyone played is kept here and plays without Spotify.") {
        Row(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(R.elev2).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RIcon(RIcons.search, 18.dp, tint = R.sub)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("Find in the library", color = R.sub, fontSize = 15.sp)
                BasicTextField(text, { text = it }, singleLine = true, textStyle = TextStyle(color = R.text, fontSize = 15.sp), cursorBrush = SolidColor(R.accent), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
