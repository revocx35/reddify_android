package io.github.revocx35.reddify

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsDisplayed
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

    /** Real home answer plus what Spotify sometimes sends: a shelf title twice and the same card twice in a row in one shelf. */
    private fun homeWithDuplicates(): String {
        val home = kotlinx.serialization.json.Json.parseToJsonElement(res("home.json")).jsonObject
        val sections = home["sections"]!!.jsonArray
        val first = sections[0].jsonObject
        val firstItems = first["items"]!!.jsonArray
        val dupCards = kotlinx.serialization.json.JsonObject(first + ("items" to kotlinx.serialization.json.JsonArray(listOf(firstItems[0], firstItems[0]) + firstItems.drop(1))))
        val all = kotlinx.serialization.json.JsonArray(listOf(dupCards) + sections.drop(1) + first)
        return kotlinx.serialization.json.JsonObject(home + ("sections" to all)).toString()
    }


    /** Opens the full-screen player with a song "playing" (the MediaController does not connect under Robolectric). */
    private fun openNowPlaying(app: ReddifyApp, file: String) {
        val t = io.github.revocx35.reddify.core.Track(
            id = "0DiWol3AO6WpXZgp0goxAV", name = "One More Time", durationMs = 320_000,
            artists = listOf(io.github.revocx35.reddify.core.ArtistRef("4tZwfgrHOc3mvqYlEYSvVi", "Daft Punk")),
            album = io.github.revocx35.reddify.core.AlbumRef("2noRn2Aes5aoNVsU6iWThc", "Discovery"),
        )
        app.player.showForTests(io.github.revocx35.reddify.playback.PlayerUi(current = t, positionMs = 95_000, durationMs = 320_000, queue = listOf(t), index = 0, contextName = "Discovery"))
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("One More Time")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(androidx.compose.ui.test.hasText("One More Time"))[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("5:20")).fetchSemanticsNodes().isNotEmpty() }
        // The controls must be on screen, not pushed below it by the cover.
        compose.onNodeWithText("5:20").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/screens/$file")
    }

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val body = when {
                    path == "/api/auth/state" -> """{"setupNeeded":false,"localLogin":true,"oidc":{"enabled":true,"label":"authentik"},"me":{"id":1,"username":"salih","displayName":"salih","role":"admin"}}"""
                    path == "/api/home" -> homeWithDuplicates()
                    path == "/api/search" -> res("search.json")
                    path.startsWith("/api/album/") -> res("album.json")
                    path == "/api/library/downloads" -> res("downloads.json")
                    path == "/api/library/liked/ids" -> "[]"
                    path == "/api/library" -> """{"liked":12,"downloaded":3,"playlists":[{"kind":"playlist","id":"PPPPPPPPPPPPPPPPPPPPPP","name":"Road trip","subtitle":"By you","image":null},{"kind":"playlist","id":"PPPPPPPPPPPPPPPPPPPPPP","name":"Road trip","subtitle":"By you","image":null}],"hidden":[{"kind":"album","id":"QQQQQQQQQQQQQQQQQQQQQQ","name":"An old album","subtitle":"2019 • Someone","image":null}]}"""
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
        // Scroll the whole home: every shelf (with its duplicate title / duplicate card) gets composed.
        val homeList = compose.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction())[0]
        for (i in 0..7) homeList.performScrollToIndex(i)
        compose.waitForIdle()
        compose.onNodeWithText("Search").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/search-empty.png")
        compose.onNodeWithText("Your Library").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("On this phone")).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/screens/library.png")
        compose.onNodeWithContentDescription("Edit Your Library").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Removed (1)")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Remove Road trip from Your Library").assertExists()
        compose.onRoot().captureRoboImage("build/screens/library-edit.png")
        openNowPlaying(app, "nowplaying.png")
    }

    @Test fun album() {
        val app = ApplicationProvider.getApplicationContext<ReddifyApp>()
        compose.setContent { ReddifyRoot(app.graph, app.player) }
        compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Trending songs")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(androidx.compose.ui.test.hasText("Manifesto"))[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("One More Time")).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/screens/album.png")
    }

    /** Unfolded Galaxy Z Fold7-class inner screen (about 884 x 980 dp, nearly square). */
    @Config(qualifiers = "w884dp-h980dp-xhdpi")
    @Test fun foldable() = bigScreen("fold")

    /** Phone turned sideways: very little height. */
    @Config(qualifiers = "w844dp-h390dp-xxhdpi")
    @Test fun landscape() {
        val app = ApplicationProvider.getApplicationContext<ReddifyApp>()
        compose.setContent { ReddifyRoot(app.graph, app.player) }
        compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Trending songs")).fetchSemanticsNodes().isNotEmpty() }
        openNowPlaying(app, "landscape-nowplaying.png")
    }

    private fun bigScreen(prefix: String) {
        val app = ApplicationProvider.getApplicationContext<ReddifyApp>()
        compose.setContent { ReddifyRoot(app.graph, app.player) }
        compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Trending songs")).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/screens/$prefix-home.png")
        compose.onAllNodes(androidx.compose.ui.test.hasText("Manifesto"))[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("One More Time")).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/screens/$prefix-album.png")
        compose.onNodeWithText("Your Library").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("On this phone")).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("build/screens/$prefix-library.png")
        openNowPlaying(app, "$prefix-nowplaying.png")
    }
}
