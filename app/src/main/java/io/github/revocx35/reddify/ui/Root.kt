package io.github.revocx35.reddify.ui

import android.view.ContextThemeWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.media3.common.Player
import androidx.mediarouter.app.MediaRouteButton
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.cast.framework.CastButtonFactory
import io.github.revocx35.reddify.core.ApiException
import io.github.revocx35.reddify.core.AuthState
import io.github.revocx35.reddify.data.AppGraph
import io.github.revocx35.reddify.playback.CastState
import io.github.revocx35.reddify.playback.PlayerConnection
import io.github.revocx35.reddify.playback.bigCover
import io.github.revocx35.reddify.ui.screens.AlbumScreen
import io.github.revocx35.reddify.ui.screens.ArtistScreen
import io.github.revocx35.reddify.ui.screens.HomeScreen
import io.github.revocx35.reddify.ui.screens.LibraryScreen
import io.github.revocx35.reddify.ui.screens.LikedScreen
import io.github.revocx35.reddify.ui.screens.MyPlaylistScreen
import io.github.revocx35.reddify.ui.screens.OnPhoneScreen
import io.github.revocx35.reddify.ui.screens.PlaylistScreen
import io.github.revocx35.reddify.ui.screens.RecentScreen
import io.github.revocx35.reddify.ui.screens.SearchScreen
import io.github.revocx35.reddify.ui.screens.ServerLibraryScreen
import io.github.revocx35.reddify.ui.screens.SettingsScreen
import io.github.revocx35.reddify.ui.screens.SignInScreen
import io.github.revocx35.reddify.ui.screens.SpotifyLikedScreen
import io.github.revocx35.reddify.ui.theme.R
import io.github.revocx35.reddify.ui.theme.RIcons
import io.github.revocx35.reddify.ui.theme.ReddifyTheme

/** Reddify's mark: red disc with three sound arcs (same geometry as web/public/logo.svg). */
@Composable
fun Logo(size: Dp) = Canvas(Modifier.size(size)) {
    val s = this.size.width / 24f
    drawCircle(R.accent, radius = 12f * s, center = Offset(12f * s, 12f * s))
    fun arc(x0: Float, y0: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float, x1: Float, y1: Float, w: Float) {
        val p = Path().apply { moveTo(x0 * s, y0 * s); cubicTo(c1x * s, c1y * s, c2x * s, c2y * s, x1 * s, y1 * s) }
        drawPath(p, Color.Black, style = Stroke(width = w * s, cap = StrokeCap.Round))
    }
    arc(5.4f, 9.3f, 9.6f, 7.9f, 14.6f, 8.2f, 18.6f, 10.4f, 2.1f)
    arc(6.2f, 12.7f, 9.8f, 11.6f, 13.9f, 11.9f, 17.3f, 13.7f, 1.75f)
    arc(6.9f, 15.9f, 9.9f, 15.1f, 13.1f, 15.3f, 15.9f, 16.7f, 1.45f)
}

@Composable
fun ReddifyRoot(graph: AppGraph, player: PlayerConnection) = ReddifyTheme {
    var auth by remember { mutableStateOf<AuthState?>(null) }
    var checking by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var offlineMode by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (graph.api.value.base.host == "reddify.invalid") {
            checking = false
            return@LaunchedEffect
        }
        try {
            val st = graph.api.value.state()
            if (st.me != null) auth = st
        } catch (e: ApiException) {
            if (e.code == ApiException.NETWORK && graph.offline.tracks.value.isNotEmpty()) offlineMode = true
            if (e.code != ApiException.AUTHENTIK) error = e.message
        } finally {
            checking = false
        }
    }
    LaunchedEffect(auth) { if (auth?.me != null) { graph.likes.load(); graph.playlists.refresh() } }
    Box(Modifier.fillMaxSize().background(R.panel)) {
        when {
            checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Logo(72.dp) }
            auth?.me != null || offlineMode -> MainShell(graph, player, auth?.me?.displayName ?: "", offlineMode) {
                graph.signOut()
                auth = null
                offlineMode = false
            }
            else -> SignInScreen(graph, error) { auth = it; error = null }
        }
    }
}

@Composable
private fun MainShell(graph: AppGraph, player: PlayerConnection, displayName: String, offline: Boolean, signOut: () -> Unit) {
    val nav = rememberNavController()
    var expanded by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(false) }
    val env = remember(nav) {
        Env(graph, player, nav = { route -> expanded = false; nav.navigate(route) { launchSingleTop = true } }, back = { nav.popBackStack() })
    }
    CompositionLocalProvider(LocalEnv provides env) {
        Box(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = if (offline) "onphone" else "home", modifier = Modifier.fillMaxSize()) {
                composable("home") { HomeScreen(displayName) }
                composable("search") { SearchScreen() }
                composable("library") { LibraryScreen() }
                composable("liked") { LikedScreen() }
                composable("onphone") { OnPhoneScreen() }
                composable("serverlib") { ServerLibraryScreen() }
                composable("recent") { RecentScreen() }
                composable("spotifyliked") { SpotifyLikedScreen() }
                composable("settings") { SettingsScreen(displayName, signOut) }
                composable("album/{id}", listOf(navArgument("id") { type = NavType.StringType })) { AlbumScreen(it.arguments?.getString("id") ?: "") }
                composable("artist/{id}", listOf(navArgument("id") { type = NavType.StringType })) { ArtistScreen(it.arguments?.getString("id") ?: "") }
                composable("playlist/{id}", listOf(navArgument("id") { type = NavType.StringType })) { PlaylistScreen(it.arguments?.getString("id") ?: "") }
                composable("my/{id}", listOf(navArgument("id") { type = NavType.LongType })) { MyPlaylistScreen(it.arguments?.getLong("id") ?: 0L) }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                MiniPlayer { expanded = true }
                BottomNav(nav.currentBackStackEntryAsState().value?.destination?.route) { route ->
                    nav.navigate(route) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            if (expanded) NowPlaying(onClose = { expanded = false }, onQueue = { queue = true })
            if (queue) QueueSheet { queue = false }
        }
    }
}

@Composable
private fun BottomNav(route: String?, go: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Black))).navigationBarsPadding().height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable fun tab(r: String, label: String, on: androidx.compose.ui.graphics.vector.ImageVector, off: androidx.compose.ui.graphics.vector.ImageVector) {
            val active = route == r
            Column(Modifier.weight(1f).clickable { go(r) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RIcon(if (active) on else off, 24.dp, tint = if (active) R.text else R.sub)
                Text(label, color = if (active) R.text else R.sub, style = MaterialTheme.typography.labelSmall)
            }
        }
        tab("home", "Home", RIcons.homeFill, RIcons.home)
        tab("search", "Search", RIcons.searchFill, RIcons.search)
        tab("library", "Your Library", RIcons.libraryFill, RIcons.library)
    }
}

@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            MediaRouteButton(ContextThemeWrapper(ctx, androidx.mediarouter.R.style.Theme_MediaRouter)).also {
                runCatching { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, it) }
            }
        },
        modifier = modifier.size(48.dp),
    )
}

@Composable
private fun MiniPlayer(onExpand: () -> Unit) {
    val env = LocalEnv.current
    val ui by env.player.ui.collectAsState()
    val device by CastState.deviceFlow.collectAsState()
    val t = ui.current ?: return
    Box(Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(6.dp)).background(R.mini).clickable(onClick = onExpand)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(t.cover, t.id, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, color = R.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(device?.let { "Casting to $it" } ?: t.artistLine, color = if (device != null) R.accent else R.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            LikeButton(t.id)
            IconButton(onClick = { env.player.toggle() }) {
                if (ui.buffering && ui.playing) CircularProgressIndicator(Modifier.size(22.dp), color = R.text, strokeWidth = 2.dp)
                else RIcon(if (ui.playing) RIcons.pause else RIcons.play, 26.dp)
            }
        }
        val frac = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp).height(2.dp),
            color = R.text,
            trackColor = Color(0x40FFFFFF),
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun NowPlaying(onClose: () -> Unit, onQueue: () -> Unit) {
    val env = LocalEnv.current
    val ui by env.player.ui.collectAsState()
    val device by CastState.deviceFlow.collectAsState()
    BackHandler(onBack = onClose)
    val t = ui.current ?: run { onClose(); return }
    var drag by remember { mutableStateOf<Float?>(null) }
    // Large or nearly square screens (unfolded foldables, tablets, landscape): the controls keep a phone-like width in the
    // middle, and the cover takes only the height that is left, so title, seek bar and buttons always stay on screen.
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xFF6B1520), 0.55f to Color(0xFF1A0508), 1f to Color.Black))
            .statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
    Column(
        Modifier.fillMaxHeight().widthIn(max = 560.dp).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { RIcon(RIcons.down, 28.dp) }
            Text(ui.contextName ?: "Reddify", color = R.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onQueue) { RIcon(RIcons.queue, 24.dp) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Cover(t.cover?.let(::bigCover), t.id, null, RoundedCornerShape(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, color = R.text, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { t.album?.let { env.nav("album/${it.id}") } })
                Text(t.artistLine, color = R.sub, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { t.artists.firstOrNull()?.let { env.nav("artist/${it.id}") } })
            }
            LikeButton(t.id, 28.dp)
        }
        val dur = ui.durationMs.coerceAtLeast(1)
        Column {
            Slider(
                value = drag ?: (ui.positionMs.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { env.player.seek((it * dur).toLong()) }; drag = null },
                colors = SliderDefaults.colors(thumbColor = R.text, activeTrackColor = R.text, inactiveTrackColor = Color(0x40FFFFFF)),
            )
            Row {
                Text(fmtTime(drag?.let { (it * dur).toLong() } ?: ui.positionMs), color = R.sub, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(fmtTime(ui.durationMs), color = R.sub, style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { env.player.shuffle() }) { RIcon(RIcons.shuffle, 26.dp, tint = if (ui.shuffle) R.accent else R.text) }
            IconButton(onClick = { env.player.prev() }) { RIcon(RIcons.prev, 34.dp) }
            Box(Modifier.size(68.dp).clip(CircleShape).background(R.text).clickable { env.player.toggle() }, contentAlignment = Alignment.Center) {
                if (ui.buffering && ui.playing) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
                else RIcon(if (ui.playing) RIcons.pause else RIcons.play, 30.dp, tint = Color.Black)
            }
            IconButton(onClick = { env.player.next() }) { RIcon(RIcons.next, 34.dp) }
            IconButton(onClick = { env.player.repeat() }) {
                RIcon(RIcons.repeat, 26.dp, tint = if (ui.repeat != Player.REPEAT_MODE_OFF) R.accent else R.text)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CastButton()
            Text(device?.let { "Playing on $it" } ?: ui.error ?: "", color = if (ui.error != null && device == null) R.danger else R.accent, style = MaterialTheme.typography.bodySmall)
        }
    }
    }
}

@Composable
private fun QueueSheet(onClose: () -> Unit) {
    val env = LocalEnv.current
    val ui by env.player.ui.collectAsState()
    BackHandler(onBack = onClose)
    LazyColumn(Modifier.fillMaxSize().background(R.panel).statusBarsPadding().navigationBarsPadding()) {
        item {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { RIcon(RIcons.down, 26.dp) }
                Text("Queue", style = MaterialTheme.typography.titleLarge, color = R.text)
            }
        }
        itemsIndexed(ui.queue) { i, t ->
            Row(
                Modifier.fillMaxWidth().clickable { env.player.jumpTo(i) }.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(t.cover, t.id, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, color = if (i == ui.index) R.accent else if (i < ui.index) R.muted else R.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artistLine, color = R.sub, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Suppress("unused")
@Composable
private fun Square(content: @Composable () -> Unit) = Box(Modifier.aspectRatio(1f)) { content() }
