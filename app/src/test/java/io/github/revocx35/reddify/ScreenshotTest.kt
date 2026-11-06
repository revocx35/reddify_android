package io.github.revocx35.reddify

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.revocx35.reddify.core.ApiClient
import io.github.revocx35.reddify.ui.ReddifyRoot
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the real screens against a mock Reddify server (answers recorded from a live server). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val server = MockWebServer()

    private fun res(name: String) = javaClass.getResource("/$name")!!.readText()

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val body = when {
                    path == "/api/auth/state" -> """{"setupNeeded":false,"localLogin":true,"oidc":{"enabled":true,"label":"authentik"},"me":{"id":1,"username":"salih","displayName":"salih","role":"admin"}}"""
                    path == "/api/home" -> res("home.json")
                    path == "/api/search" -> res("search.json")
                    path.startsWith("/api/album/") -> res("album.json")
                    path == "/api/library/downloads" -> res("downloads.json")
                    path == "/api/library/liked/ids" -> "[]"
                    path == "/api/library" -> """{"liked":12,"downloaded":3,"playlists":[]}"""
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()
            }
        }
        server.start()
        val app = ApplicationProvider.getApplicationContext<ReddifyApp>()
        app.graph.useApiForTests(ApiClient(OkHttpClient(), server.url("/"), null))
    }

    @After fun stop() = server.close()

    @Test fun home_search_album() {
        val app = ApplicationProvider.getApplicationContext<ReddifyApp>()
        compose.setContent { ReddifyRoot(app.graph, app.player) }
        compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Trending songs")).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/screens/home.png")
        compose.onNodeWithText("Search").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/search-empty.png")
        compose.onNodeWithText("Your Library").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("On this phone")).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/screens/library.png")
    }

    @Test fun album() {
        val app = ApplicationProvider.getApplicationContext<ReddifyApp>()
        compose.setContent { ReddifyRoot(app.graph, app.player) }
        compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Trending songs")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(androidx.compose.ui.test.hasText("Manifesto"))[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("One More Time")).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/screens/album.png")
    }
}
