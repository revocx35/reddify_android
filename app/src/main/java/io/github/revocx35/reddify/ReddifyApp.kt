package io.github.revocx35.reddify

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.revocx35.reddify.data.AppGraph
import io.github.revocx35.reddify.playback.PlayerConnection

class ReddifyApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set
    val player: PlayerConnection by lazy { PlayerConnection(this, graph.scope) { graph.api.value } }

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    // Covers: Spotify's CDN directly, the server's /api/cover through the signed-in client.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.http })) }.build()
}
