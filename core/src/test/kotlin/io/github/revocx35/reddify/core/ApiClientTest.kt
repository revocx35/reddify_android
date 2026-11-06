package io.github.revocx35.reddify.core

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun client(token: String? = null) = ApiClient(OkHttpClient(), server.url("/"), token)

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test fun `parses a real home and search answer from the server`() = runBlocking {
        val home = javaClass.getResource("/home.json")!!.readText()
        val search = javaClass.getResource("/search.json")!!.readText()
        server.enqueue(json(home))
        server.enqueue(json(search))
        val c = client()
        val h = c.home()
        assertTrue(h.sections.size >= 3)
        assertTrue(h.sections.any { s -> s.items.any { it.kind == "track" && it.track != null } })
        val s = c.search("tarkan kuzu kuzu")
        assertEquals("Kuzu Kuzu", s.tracks.first().name)
        assertEquals("Tarkan", s.tracks.first().artists.first().name)
        assertTrue(s.tracks.first().durationMs > 60_000)
    }

    @Test fun `sends the app token and maps errors to readable messages`() = runBlocking {
        server.enqueue(json("""{"error":"unauthorized","message":"Please sign in."}""", 401))
        server.enqueue(json("""{"error":"rate_limited","message":"Too many attempts. Try again in 30 s."}""", 429))
        val c = client("rdf_abc")
        try { c.home(); fail() } catch (e: ApiException) { assertEquals(401, e.status); assertEquals("Please sign in.", e.message) }
        assertEquals("Bearer rdf_abc", server.takeRequest().headers["Authorization"])
        try { c.login("a", "b", null); fail() } catch (e: ApiException) { assertEquals("rate_limited", e.code) }
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"client\":\"app\""))
    }

    @Test fun `own playlists - requests and answers match the server contract`() = runBlocking {
        val pl = """{"id":7,"name":"Road trip","description":"","count":1,"durationMs":200000,"covers":[{"url":null,"trackId":"AAAAAAAAAAAAAAAAAAAAAA"}],"createdAt":1,"updatedAt":2}"""
        server.enqueue(json("""{"playlist":$pl,"tracks":[]}"""))
        server.enqueue(json("""{"added":1,"playlist":$pl}"""))
        server.enqueue(json(pl))
        server.enqueue(json(pl))
        server.enqueue(json("[7]"))
        val c = client("rdf_abc")
        assertEquals(7L, c.createPlaylist("Road trip", listOf("AAAAAAAAAAAAAAAAAAAAAA")).playlist.id)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/playlists", create.url.encodedPath)
        assertEquals("""{"name":"Road trip","tracks":["AAAAAAAAAAAAAAAAAAAAAA"]}""", create.body!!.utf8())
        assertEquals(1, c.addToPlaylist(7, listOf("BBBBBBBBBBBBBBBBBBBBBB")).added)
        assertEquals("""{"ids":["BBBBBBBBBBBBBBBBBBBBBB"]}""", server.takeRequest().body!!.utf8())
        c.movePlaylistTrack(7, "BBBBBBBBBBBBBBBBBBBBBB", 0)
        val move = server.takeRequest()
        assertEquals("PUT", move.method)
        assertEquals("/api/playlists/7/tracks/BBBBBBBBBBBBBBBBBBBBBB/position", move.url.encodedPath)
        assertEquals("""{"to":0}""", move.body!!.utf8())
        assertEquals("Road trip", c.updatePlaylist(7, "Road trip", "").name)
        assertEquals("PATCH", server.takeRequest().method)
        assertEquals(listOf(7L), c.trackPlaylists("BBBBBBBBBBBBBBBBBBBBBB"))
    }

    @Test fun `library - removing an item only calls Reddify's hidden routes`() = runBlocking {
        server.enqueue(json("""{"liked":1,"downloaded":2,"playlists":[],"mine":[],"hidden":[{"kind":"album","id":"QQQQQQQQQQQQQQQQQQQQQQ","name":"An album","subtitle":"","image":null}]}"""))
        server.enqueue(json("""{"ok":true}"""))
        server.enqueue(json("""{"ok":true}"""))
        val c = client("rdf_abc")
        assertEquals("An album", c.library().hidden.single().name)
        server.takeRequest()
        c.hideLibraryItem("playlist", "PPPPPPPPPPPPPPPPPPPPPP")
        val hide = server.takeRequest()
        assertEquals("PUT", hide.method)
        assertEquals("/api/library/hidden/playlist/PPPPPPPPPPPPPPPPPPPPPP", hide.url.encodedPath)
        c.unhideLibraryItem("album", "QQQQQQQQQQQQQQQQQQQQQQ")
        val restore = server.takeRequest()
        assertEquals("DELETE", restore.method)
        assertEquals("/api/library/hidden/album/QQQQQQQQQQQQQQQQQQQQQQ", restore.url.encodedPath)
    }

    @Test fun `refuses a non Reddify server`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<html>").build())
        try { client().state(); fail() } catch (e: ApiException) { assertEquals(ApiException.NOT_REDDIFY, e.code) }
    }

    @Test fun `unknown fields and missing ones do not break parsing`() {
        val t = JSON.decodeFromString(Track.serializer(), """{"id":"x","name":"n","brandNewField":1}""")
        assertEquals("n", t.name)
        assertNull(t.album)
    }

    @Test fun `server address must be https`() {
        assertEquals("https://reddify.example/", parseServerUrl("reddify.example/abc?x=1").toString())
        assertNull(parseServerUrl("http://reddify.example"))
        assertNull(parseServerUrl("https://user:pw@reddify.example"))
        assertNull(parseServerUrl("not a url"))
    }
}
