package io.github.revocx35.reddify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.revocx35.reddify.core.MyPlaylist
import io.github.revocx35.reddify.core.Track
import io.github.revocx35.reddify.core.userMessage
import io.github.revocx35.reddify.ui.Cover
import io.github.revocx35.reddify.ui.ErrorBox
import io.github.revocx35.reddify.ui.LocalEnv
import io.github.revocx35.reddify.ui.Loading
import io.github.revocx35.reddify.ui.MenuAction
import io.github.revocx35.reddify.ui.PlayFab
import io.github.revocx35.reddify.ui.PlaylistCover
import io.github.revocx35.reddify.ui.RIcon
import io.github.revocx35.reddify.ui.TrackRow
import io.github.revocx35.reddify.ui.theme.R
import io.github.revocx35.reddify.ui.theme.RIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun lengthText(ms: Long): String {
    val min = ((ms + 30_000) / 60_000).toInt()
    return if (min >= 60) "${min / 60} hr ${min % 60} min" else "$min min"
}

/** A playlist the user made in Reddify: play, rename, describe, reorder (move up/down), remove, delete, add songs. */
@Composable
fun MyPlaylistScreen(id: Long) {
    val env = LocalEnv.current
    val scope = rememberCoroutineScope()
    val res = rememberLoad("my", id) { env.graph.api.value.myPlaylist(id) }
    var p by remember(id) { mutableStateOf<MyPlaylist?>(null) }
    var tracks by remember(id) { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(res.data) { res.data?.let { p = it.playlist; tracks = it.tracks } }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var finding by remember(id) { mutableStateOf(false) }
    val ui by env.player.ui.collectAsState()
    val toast = env.graph.playlists::toast

    fun changed(np: MyPlaylist) {
        p = np
        env.graph.playlists.upsert(np)
    }
    fun move(from: Int, to: Int) {
        val t = tracks.getOrNull(from) ?: return
        if (to !in tracks.indices) return
        tracks = tracks.toMutableList().apply { removeAt(from); add(to, t) }
        scope.launch { runCatching { env.graph.api.value.movePlaylistTrack(id, t.id, to) }.onSuccess(::changed).onFailure { toast(it.userMessage()); res.reload() } }
    }
    fun remove(t: Track) {
        tracks = tracks.filter { it.id != t.id }
        scope.launch {
            runCatching { env.graph.api.value.removeFromPlaylist(id, t.id) }
                .onSuccess { changed(it); toast("Removed from ${it.name}") }
                .onFailure { toast(it.userMessage()); res.reload() }
        }
    }

    val pl = p
    val ctxId = "my:$id"
    LazyColumn(contentPadding = bottomPad) {
        when {
            res.error != null && pl == null -> item { BackRow(); ErrorBox(res.error, res.reload) }
            pl == null -> item { Loading() }
            else -> {
                item {
                    Column(
                        Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color(0x99E8283B), 1f to R.panel)).statusBarsPadding().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BackRow()
                        Box(Modifier.clickable { editing = true }) { PlaylistCover(pl.covers, 240.dp) }
                        Spacer(Modifier.height(16.dp))
                        Column(Modifier.fillMaxWidth().clickable { editing = true }) {
                            Text("Playlist", color = R.text, style = MaterialTheme.typography.bodySmall)
                            Text(pl.name, color = R.text, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (pl.description.isNotEmpty()) Text(pl.description, color = R.sub, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Text(if (pl.count > 0) "${pl.count} songs, ${lengthText(pl.durationMs)}" else "No songs yet", color = R.sub, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    val onPhone by env.graph.offline.tracks.collectAsState()
                    val all = tracks.isNotEmpty() && tracks.all { t -> onPhone.any { it.id == t.id } }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (tracks.isNotEmpty()) IconButton(onClick = { if (!all) env.graph.offline.download(tracks) }) {
                            RIcon(if (all) RIcons.downloaded else RIcons.download, 26.dp, tint = if (all) R.accent else R.sub)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { RIcon(RIcons.more, 26.dp, tint = R.sub) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = R.elev2) {
                                @Composable fun item(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, fn: () -> Unit) = DropdownMenuItem(
                                    text = { Text(label, color = R.text) }, leadingIcon = { RIcon(icon, 18.dp, tint = R.sub) }, onClick = { menu = false; fn() },
                                )
                                item("Edit details", RIcons.edit) { editing = true }
                                item("Add songs", RIcons.search) { finding = true }
                                if (tracks.isNotEmpty()) item("Add to queue", RIcons.queue) { tracks.forEach { env.player.addToQueue(it) }; toast("Added to queue") }
                                item("Delete", RIcons.trash) { confirmDelete = true }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (tracks.isNotEmpty()) {
                            IconButton(onClick = { env.player.playList(tracks.shuffled(), 0, pl.name, ctxId) }) { RIcon(RIcons.shuffle, 26.dp, tint = R.sub) }
                            Spacer(Modifier.width(8.dp))
                            PlayFab(ui.contextId == ctxId && ui.playing) { if (ui.contextId == ctxId) env.player.toggle() else env.player.playList(tracks, 0, pl.name, ctxId) }
                        }
                    }
                }
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                    TrackRow(
                        t, tracks, i, pl.name, ctxId,
                        extra = listOfNotNull(
                            MenuAction("Remove from this playlist", RIcons.trash) { remove(t) },
                            if (i > 0) MenuAction("Move up", RIcons.back) { move(i, i - 1) } else null,
                            if (i < tracks.size - 1) MenuAction("Move down", RIcons.down) { move(i, i + 1) } else null,
                        ),
                    )
                }
                if (tracks.isEmpty() || finding) item {
                    FindSongs(pl, tracks.map { it.id }.toSet()) { np, t ->
                        changed(np)
                        if (tracks.none { it.id == t.id }) tracks = tracks + t
                    }
                } else item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { finding = true }) { Text("Find more", color = R.text, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
    if (editing && pl != null) EditDetailsDialog(pl, onDismiss = { editing = false }) { name, desc ->
        scope.launch {
            runCatching { env.graph.api.value.updatePlaylist(id, name, desc) }.onSuccess { changed(it); editing = false }.onFailure { toast(it.userMessage()) }
        }
    }
    if (confirmDelete && pl != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = R.elev2,
        title = { Text("Delete “${pl.name}”?", color = R.text) },
        text = { Text("This cannot be undone.", color = R.sub) },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch {
                    runCatching { env.graph.api.value.deletePlaylist(id) }
                        .onSuccess { env.graph.playlists.remove(id); toast("Removed from Your Library"); env.back() }
                        .onFailure { toast(it.userMessage()) }
                }
            }) { Text("Delete", color = R.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = R.text) } },
    )
}

/** "Let's find something for your playlist": Spotify search with an Add button per song. */
@Composable
private fun FindSongs(p: MyPlaylist, have: Set<String>, onAdded: (MyPlaylist, Track) -> Unit) {
    val env = LocalEnv.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(text) {
        val q = text.trim()
        if (q.isEmpty()) { results = null; return@LaunchedEffect }
        delay(350)
        runCatching { env.graph.api.value.search(q).tracks }.onSuccess { results = it }
    }
    Column(Modifier.padding(top = 16.dp)) {
        Text("Let's find something for your playlist", color = R.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(R.elev2).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RIcon(RIcons.search, 18.dp, tint = R.sub)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("Search for songs", color = R.sub, fontSize = 15.sp)
                BasicTextField(text, { text = it }, singleLine = true, textStyle = TextStyle(color = R.text, fontSize = 15.sp), cursorBrush = SolidColor(R.accent), modifier = Modifier.fillMaxWidth())
            }
        }
        val r = results
        if (r != null && r.isEmpty()) Text("No songs found.", color = R.sub, modifier = Modifier.padding(16.dp))
        r?.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(t.cover, t.id, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, color = R.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artistLine, color = R.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                if (t.id in have) RIcon(RIcons.check, 22.dp, tint = R.accent, modifier = Modifier.padding(horizontal = 12.dp))
                else TextButton(onClick = {
                    scope.launch {
                        runCatching { env.graph.api.value.addToPlaylist(p.id, listOf(t.id)) }
                            .onSuccess { onAdded(it.playlist, t) }
                            .onFailure { env.graph.playlists.toast(it.userMessage()) }
                    }
                }) { Text("Add", color = R.text, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun EditDetailsDialog(p: MyPlaylist, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var desc by remember { mutableStateOf(p.description) }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = R.text, unfocusedTextColor = R.text, focusedBorderColor = R.sub, unfocusedBorderColor = R.elev3,
        cursorColor = R.accent, focusedLabelColor = R.sub, unfocusedLabelColor = R.sub,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = R.elev2,
        title = { Text("Edit details", color = R.text) },
        text = {
            Column {
                OutlinedTextField(name, { if (it.length <= 100) name = it }, label = { Text("Name") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(desc, { if (it.length <= 300) desc = it }, label = { Text("Description (optional)") }, minLines = 3, colors = colors, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), desc.trim()) }, enabled = name.isNotBlank()) { Text("Save", color = R.accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = R.text) } },
    )
}

/** The user's playlists as library rows (Library screen). */
@Composable
fun MyPlaylistRows(list: List<MyPlaylist>, onRemove: ((MyPlaylist) -> Unit)? = null) {
    val env = LocalEnv.current
    Column {
        for (p in list) {
            Row(Modifier.fillMaxWidth().clickable { env.nav("my/${p.id}") }.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlaylistCover(p.covers, 64.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = R.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Playlist • ${p.count} songs", color = R.sub, style = MaterialTheme.typography.bodySmall)
                }
                if (onRemove != null) RemoveButton("Delete ${p.name}") { onRemove(p) }
            }
        }
    }
}
