package io.github.revocx35.reddify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.revocx35.reddify.core.Card
import io.github.revocx35.reddify.core.Track
import io.github.revocx35.reddify.core.userMessage
import io.github.revocx35.reddify.ui.CardTile
import io.github.revocx35.reddify.ui.Chip
import io.github.revocx35.reddify.ui.Cover
import io.github.revocx35.reddify.ui.ErrorBox
import io.github.revocx35.reddify.ui.LocalEnv
import io.github.revocx35.reddify.ui.Loading
import io.github.revocx35.reddify.ui.OfflineBanner
import io.github.revocx35.reddify.ui.PlayFab
import io.github.revocx35.reddify.ui.RIcon
import io.github.revocx35.reddify.ui.SectionTitle
import io.github.revocx35.reddify.ui.Shelf
import io.github.revocx35.reddify.ui.TrackRow
import io.github.revocx35.reddify.ui.cardRoute
import io.github.revocx35.reddify.ui.theme.R
import io.github.revocx35.reddify.ui.theme.RIcons
import kotlinx.coroutines.delay
import java.util.Calendar

/** Loads data once per [key], with error + retry. */
class Loaded<T>(val data: T?, val error: String?, val reload: () -> Unit)

@Composable
fun <T> rememberLoad(vararg key: Any?, fn: suspend () -> T): Loaded<T> {
    var data by remember(*key) { mutableStateOf<T?>(null) }
    var error by remember(*key) { mutableStateOf<String?>(null) }
    var n by remember(*key) { mutableIntStateOf(0) }
    LaunchedEffect(*key, n) {
        error = null
        try {
            data = fn()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.userMessage()
        }
    }
    return Loaded(data, error) { n++ }
}

val bottomPad = PaddingValues(bottom = 150.dp)

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..4 -> "Good night"
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
fun HomeScreen(displayName: String) {
    val env = LocalEnv.current
    val home = rememberLoad("home") { env.graph.api.value.home() }
    val offlineTracks by env.graph.offline.tracks.collectAsState()
    LazyColumn(
        Modifier.background(Brush.verticalGradient(0f to Color(0x66E8283B), 0.25f to R.panel)),
        contentPadding = bottomPad,
    ) {
        item {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${greeting()}${if (displayName.isNotEmpty()) ", $displayName" else ""}", style = MaterialTheme.typography.headlineMedium, color = R.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { env.nav("settings") }) { RIcon(RIcons.settings, 24.dp) }
            }
        }
        val h = home.data
        when {
            home.error != null && h == null -> {
                item { ErrorBox(home.error, home.reload) }
                if (offlineTracks.isNotEmpty()) {
                    item { SectionTitle("On this phone") }
                    itemsIndexed(offlineTracks) { i, t -> TrackRow(t, offlineTracks, i, "On this phone", "offline") }
                }
            }
            h == null -> item { Loading() }
            else -> {
                if (h.offline) item { OfflineBanner() }
                val quick = h.sections.flatMap { it.items }.filter { it.kind == "album" || it.kind == "playlist" }.take(6)
                if (quick.isNotEmpty()) item { QuickGrid(quick) }
                for (s in h.sections) item(key = s.title) { Shelf(s.title, s.items) }
            }
        }
    }
}

@Composable
private fun QuickGrid(items: List<Card>) {
    val env = LocalEnv.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in items.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in row) {
                    Row(
                        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x1FFFFFFF)).clickable { cardRoute(c)?.let(env.nav) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(c.image, null, 56.dp, RoundedCornerShape(0.dp))
                        Text(c.name, color = R.text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SearchScreen() {
    val env = LocalEnv.current
    var text by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("all") }
    LaunchedEffect(text) {
        delay(350)
        q = text.trim()
    }
    val res = rememberLoad(q) { if (q.isEmpty()) null else env.graph.api.value.search(q) }
    LazyColumn(contentPadding = bottomPad) {
        item {
            Text("Search", style = MaterialTheme.typography.headlineMedium, color = R.text, modifier = Modifier.statusBarsPadding().padding(16.dp))
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.White).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RIcon(RIcons.search, 22.dp, tint = Color.Black)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text("What do you want to listen to?", color = Color(0xFF555555), fontSize = 16.sp)
                    BasicTextField(text, { text = it }, singleLine = true, textStyle = TextStyle(color = Color.Black, fontSize = 16.sp), cursorBrush = SolidColor(R.accent), modifier = Modifier.fillMaxWidth())
                }
                if (text.isNotEmpty()) RIcon(RIcons.close, 18.dp, tint = Color.Black, modifier = Modifier.clickable { text = "" })
            }
        }
        if (q.isEmpty()) {
            item { Text("Search Spotify's catalogue: songs, artists, albums and playlists.", color = R.sub, modifier = Modifier.padding(24.dp)) }
            return@LazyColumn
        }
        val r = res.data
        when {
            res.error != null -> item { ErrorBox(res.error, res.reload) }
            r == null -> item { Loading() }
            else -> {
                if (r.offline) item { OfflineBanner() }
                item {
                    LazyRow(Modifier.padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("all", "songs", "artists", "albums", "playlists")) { t -> Chip(t.replaceFirstChar { it.uppercase() }, tab == t) { tab = t } }
                    }
                }
                when (tab) {
                    "all" -> {
                        itemsIndexed(r.tracks.take(5)) { i, t -> TrackRow(t, r.tracks, i, "Search: $q", "search:$q") }
                        item { Shelf("Artists", r.artists) }
                        item { Shelf("Albums", r.albums) }
                        item { Shelf("Playlists", r.playlists) }
                    }
                    "songs" -> itemsIndexed(r.tracks) { i, t -> TrackRow(t, r.tracks, i, "Search: $q", "search:$q") }
                    else -> cardGrid(if (tab == "artists") r.artists else if (tab == "albums") r.albums else r.playlists)
                }
            }
        }
    }
}

fun LazyListScope.cardGrid(cards: List<Card>) {
    items(cards.chunked(2)) { row ->
        Row(Modifier.padding(horizontal = 8.dp)) {
            for (c in row) Box(Modifier.weight(1f)) { CardTile(c, 180.dp) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Header(kind: String, title: String, image: String?, round: Boolean = false, meta: String) {
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color(0x99E8283B), 1f to R.panel)).statusBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BackRow()
        Cover(image, null, 240.dp, if (round) CircleShape else RoundedCornerShape(4.dp))
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(kind, color = R.text, style = MaterialTheme.typography.bodySmall)
            Text(title, color = R.text, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(meta, color = R.sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun BackRow() {
    val env = LocalEnv.current
    Row(Modifier.fillMaxWidth()) {
        IconButton(onClick = env.back) { RIcon(RIcons.back, 24.dp) }
    }
}

@Composable
private fun ActionBar(tracks: List<Track>, name: String, id: String) {
    val env = LocalEnv.current
    val ui by env.player.ui.collectAsState()
    val onPhone by env.graph.offline.tracks.collectAsState()
    val all = tracks.isNotEmpty() && tracks.all { t -> onPhone.any { it.id == t.id } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (!all) env.graph.offline.download(tracks) }) {
            RIcon(if (all) RIcons.downloaded else RIcons.download, 26.dp, tint = if (all) R.accent else R.sub)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { env.player.playList(tracks.shuffled(), 0, name, id) }) { RIcon(RIcons.shuffle, 26.dp, tint = R.sub) }
        Spacer(Modifier.width(8.dp))
        val playing = ui.contextId == id && ui.playing
        PlayFab(playing) { if (ui.contextId == id) env.player.toggle() else env.player.playList(tracks, 0, name, id) }
    }
}

@Composable
fun AlbumScreen(id: String) {
    val env = LocalEnv.current
    val res = rememberLoad(id) { env.graph.api.value.album(id) }
    val a = res.data
    LazyColumn(contentPadding = bottomPad) {
        when {
            res.error != null -> item { BackRow(); ErrorBox(res.error, res.reload) }
            a == null -> item { Loading() }
            else -> {
                val mins = a.tracks.sumOf { it.durationMs } / 60000
                item {
                    Header(
                        a.album.type.replaceFirstChar { it.uppercase() }, a.album.name, a.album.cover,
                        meta = listOf(a.album.artists.joinToString(", ") { it.name }, a.album.year, "${a.tracks.size} songs, $mins min").filter { it.isNotEmpty() }.joinToString(" • "),
                    )
                }
                item { ActionBar(a.tracks, a.album.name, a.album.id) }
                itemsIndexed(a.tracks) { i, t -> TrackRow(t, a.tracks, i, a.album.name, a.album.id, showCover = false) }
                if (a.album.label.isNotEmpty()) item { Text("℗ ${a.album.label}", color = R.sub, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
fun ArtistScreen(id: String) {
    val env = LocalEnv.current
    val res = rememberLoad(id) { env.graph.api.value.artist(id) }
    val a = res.data
    var more by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = bottomPad) {
        when {
            res.error != null -> item { BackRow(); ErrorBox(res.error, res.reload) }
            a == null -> item { Loading() }
            else -> {
                item {
                    Header("Artist", a.artist.name, a.artist.image, round = true, meta = a.artist.monthlyListeners?.let { "%,d monthly listeners".format(it) } ?: "")
                }
                item { ActionBar(a.topTracks, a.artist.name, a.artist.id) }
                item { SectionTitle("Popular") }
                val shown = if (more) a.topTracks else a.topTracks.take(5)
                itemsIndexed(shown) { i, t -> TrackRow(t, a.topTracks, i, a.artist.name, a.artist.id, number = i + 1) }
                if (a.topTracks.size > 5) item { TextButton(onClick = { more = !more }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(if (more) "Show less" else "See more", color = R.sub, fontWeight = FontWeight.Bold) } }
                item { Shelf("Albums", a.albums) }
                item { Shelf("Singles and EPs", a.singles) }
                item { Shelf("Fans also like", a.related) }
            }
        }
    }
}

@Composable
fun PlaylistScreen(id: String) {
    val env = LocalEnv.current
    val res = rememberLoad(id) { env.graph.api.value.playlist(id) }
    var extra by remember(id) { mutableStateOf<List<Track>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val p = res.data
    LaunchedEffect(busy) {
        if (busy && p != null) {
            runCatching { env.graph.api.value.playlist(id, p.tracks.size + extra.size) }.onSuccess { extra = extra + it.tracks }
            busy = false
        }
    }
    LazyColumn(contentPadding = bottomPad) {
        when {
            res.error != null -> item { BackRow(); ErrorBox(res.error, res.reload) }
            p == null -> item { Loading() }
            else -> {
                val tracks = p.tracks + extra
                item { Header("Playlist", p.playlist.name, p.playlist.image, meta = listOf(p.playlist.owner, "${p.total} songs").filter { it.isNotEmpty() }.joinToString(" • ")) }
                if (p.playlist.description.isNotEmpty()) item { Text(p.playlist.description, color = R.sub, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
                item { ActionBar(tracks, p.playlist.name, p.playlist.id) }
                itemsIndexed(tracks) { i, t -> TrackRow(t, tracks, i, p.playlist.name, p.playlist.id) }
                if (tracks.size < p.total) item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        if (busy) Loading() else TextButton(onClick = { busy = true }) { Text("Load more", color = R.text, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
fun Dot() = Box(Modifier.size(4.dp).clip(CircleShape).background(R.sub))
