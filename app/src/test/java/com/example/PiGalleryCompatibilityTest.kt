package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PiGalleryApi
import com.example.data.PreferencesManager
import com.example.data.UserRoles
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PiGalleryCompatibilityTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var api: PiGalleryApi

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = PreferencesManager(context)
        prefs.clear()
        api = PiGalleryApi(context)
    }

    @Test
    fun `serializeQuery cleans whole Doubles inside nested list structures`() {
        val rawAlbumQuery = mapOf<String, Any?>(
            "type" to 2.0,
            "list" to listOf(
                mapOf(
                    "type" to 105.0,
                    "value" to "Alice",
                    "matchType" to 1.0
                ),
                mapOf(
                    "type" to 10.0,
                    "min" to 1700000000000.0
                )
            )
        )

        val json = api.serializeQuery(rawAlbumQuery)
        assertFalse("Should not serialize integer values with .0: $json", json.contains(".0"))
        assertTrue(json.contains("\"type\":2"))
        assertTrue(json.contains("\"type\":105"))
        assertTrue(json.contains("\"matchType\":1"))
        assertTrue(json.contains("\"min\":1700000000000"))
    }

    @Test
    fun `login prefers pgapi, handles special chars in password, and extracts session cookies and csrfToken`() {
        val seenPaths = mutableListOf<String>()
        var seenBody = ""

        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                seenPaths.add(req.url.encodedPath)
                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                seenBody = buffer.readUtf8()

                val json = """
                    {
                      "error": null,
                      "result": {
                        "username": "admin",
                        "role": 3,
                        "csrfToken": "csrf-abc-123"
                      }
                    }
                """.trimIndent()

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .addHeader("Set-Cookie", "pigallery2-session=xyz%3D1; Path=/; HttpOnly")
                    .addHeader("Set-Cookie", "pigallery2-session.sig=sig123; Path=/; HttpOnly")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val (cookies, prefix) = api.login("https://example.org/", "admin", "p@ss\"with\\quotes")

        assertEquals("/pgapi", prefix)
        assertEquals(listOf("/pgapi/user/login"), seenPaths)
        assertTrue(cookies.contains("pigallery2-session=xyz%3D1"))
        assertTrue(cookies.contains("pigallery2-session.sig=sig123"))
        assertEquals("csrf-abc-123", prefs.csrfToken)
        assertTrue(seenBody.contains("p@ss\\\"with\\\\quotes"))
    }

    @Test
    fun `login falls back from HTML pgapi response to api and supports generic session cookie`() {
        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.url.encodedPath == "/pgapi/user/login") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("<!doctype html><html></html>".toResponseBody("text/html".toMediaType()))
                        .build()
                } else {
                    val json = """{"error":null,"result":{"username":"guest","role":1}}"""
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .addHeader("Set-Cookie", "connect.sid=s%3A999; Path=/")
                        .body(json.toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        val (cookies, prefix) = api.login("https://example.org", "guest", "secret")
        assertEquals("/api", prefix)
        assertEquals("connect.sid=s%3A999", cookies)
    }

    @Test
    fun `search parses packed ContentWrapper metadata, string map directories, and subfolder full path`() {
        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                val json = """
                    {
                      "error": null,
                      "result": {
                        "searchResult": {
                          "directories": [
                            { "id": 7, "name": "Summer", "path": "./2024", "mediaCount": 12 }
                          ],
                          "media": [
                            {
                              "id": 42,
                              "n": "IMG_001.jpg",
                              "d": 0,
                              "s": 2048,
                              "m": {
                                "d": [4000, 3000],
                                "t": 1700000000000,
                                "o": 120,
                                "k": [0]
                              }
                            }
                          ]
                        },
                        "map": {
                          "directories": ["./2024/Summer"],
                          "keywords": ["Beach"]
                        }
                      }
                    }
                """.trimIndent()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val dir = api.search("https://example.org", "{\"type\":100,\"value\":\"Beach\"}", "c=1", "/pgapi")
        assertEquals(1, dir.directories?.size)
        assertEquals("2024/Summer", dir.directories?.first()?.path)

        assertEquals(1, dir.media?.size)
        val media = dir.media!!.first()
        assertEquals("IMG_001.jpg", media.name)
        assertEquals("2024/Summer", media.parentPath)
        assertEquals(4000, media.metadata?.size?.width)
        assertEquals("+02:00", media.metadata?.creationDateOffset)
        assertEquals(listOf("Beach"), media.metadata?.keywords)
        assertEquals(2048L, media.metadata?.fileSize)
    }

    @Test
    fun `UserRoles requires Admin role or higher to manage albums`() {
        assertFalse(UserRoles.canManageAlbums(-1))
        assertFalse(UserRoles.canManageAlbums(UserRoles.LIMITED_GUEST))
        assertFalse(UserRoles.canManageAlbums(UserRoles.GUEST))
        assertFalse(UserRoles.canManageAlbums(UserRoles.USER))
        assertTrue(UserRoles.canManageAlbums(UserRoles.ADMIN))
        assertTrue(UserRoles.canManageAlbums(UserRoles.DEVELOPER))
    }

    @Test
    fun `login persists admin role and canManageAlbums returns true`() {
        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                val json = """{"error":null,"result":{"username":"admin","role":4,"csrfToken":"tok-4"}}"""
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .addHeader("Set-Cookie", "pigallery2-session=adm1; Path=/")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        api.login("https://example.org", "admin", "pw")
        assertEquals(4, prefs.userRole)
        assertTrue(UserRoles.canManageAlbums(prefs.userRole))
    }

    @Test
    fun `anonymous fallback checks user me and keeps unauthorized when user me returns 401`() {
        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                when (req.url.encodedPath) {
                    "/pgapi/gallery/content/" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"error":null,"result":{"directory":{"id":1,"name":"/"}}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                    "/pgapi/user/me" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized")
                        .body("""{"error":"Unauthorized"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                    else -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized")
                        .body("""{"error":"Unauthorized"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        api.login("https://example.org", "", "")
        assertEquals(-1, prefs.userRole)
        assertFalse(UserRoles.canManageAlbums(prefs.userRole))
    }

    @Test
    fun `createSavedSearchAlbum sends PUT with CSRF token, cleaned query, and handles 404 fallback`() {
        prefs.csrfToken = "csrf-save-99"
        val attempts = mutableListOf<String>()
        var capturedBody = ""
        var capturedCsrf: String? = null
        var capturedCookie: String? = null

        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                attempts.add("${req.method} ${req.url.encodedPath}")
                if (req.url.encodedPath == "/pgapi/albums/saved-searches") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    val buffer = okio.Buffer()
                    req.body?.writeTo(buffer)
                    capturedBody = buffer.readUtf8()
                    capturedCsrf = req.header("CSRF-Token")
                    capturedCookie = req.header("Cookie")
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """{"error":null,"result":{"id":12,"name":"Beach 2024","locked":false,"searchQuery":{"type":100,"value":"Beach"}}}"""
                                .toResponseBody("application/json".toMediaType())
                        )
                        .build()
                }
            }
            .build()

        val album = api.createSavedSearchAlbum(
            serverUrl = "https://example.org/",
            name = "Beach 2024",
            searchQuery = mapOf("type" to 100.0, "value" to "Beach"),
            cookies = "pigallery2-session=abc",
            apiPrefix = "/pgapi"
        )

        assertNotNull(album)
        assertEquals(12, album?.id)
        assertEquals("Beach 2024", album?.name)
        assertEquals(listOf("PUT /pgapi/albums/saved-searches", "PUT /pgapi/albums"), attempts)
        assertEquals("csrf-save-99", capturedCsrf)
        assertEquals("pigallery2-session=abc", capturedCookie)
        assertFalse(capturedBody.contains(".0"))
        assertTrue(capturedBody.contains("\"type\":100"))
        assertTrue(capturedBody.contains("\"name\":\"Beach 2024\""))
    }

    @Test
    fun `createSavedSearchAlbum stops immediately on 403 forbidden without trying fallbacks`() {
        prefs.csrfToken = "csrf-user"
        val attempts = mutableListOf<String>()

        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                attempts.add("${req.method} ${req.url.encodedPath}")
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(403)
                    .message("Forbidden")
                    .body("""{"error":"Not enough rights"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        try {
            api.createSavedSearchAlbum(
                serverUrl = "https://example.org",
                name = "Unauthorized Album",
                searchQuery = mapOf("type" to 100, "value" to "cat"),
                cookies = "pigallery2-session=user",
                apiPrefix = "/pgapi"
            )
            fail("Expected IOException on 403 Forbidden")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Not enough rights") == true)
            assertEquals(listOf("PUT /pgapi/albums/saved-searches"), attempts)
        }
    }

    @Test
    fun `UserRoles allows normal users when Admin lowers albumWriteMinRole or grants user override`() {
        assertFalse(UserRoles.canManageAlbums(UserRoles.USER, albumWriteMinRole = UserRoles.ADMIN))
        assertTrue(UserRoles.canManageAlbums(UserRoles.USER, albumWriteMinRole = UserRoles.USER))
        assertFalse(UserRoles.canManageAlbums(UserRoles.GUEST, albumWriteMinRole = UserRoles.USER))
        assertFalse(UserRoles.canManageAlbums(UserRoles.USER, albumWriteMinRole = UserRoles.USER, albumFeatureEnabled = false))
        assertTrue(UserRoles.canManageAlbums(UserRoles.USER, albumWriteMinRole = UserRoles.ADMIN, userCanCreateAlbumsOverride = 1))
        assertFalse(UserRoles.canManageAlbums(UserRoles.USER, albumWriteMinRole = UserRoles.USER, userCanCreateAlbumsOverride = 0))
    }

    @Test
    fun `fetchCurrentUserSession allows normal User when settings Album writeAccessMinRole is User`() {
        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                when (req.url.encodedPath) {
                    "/pgapi/user/me" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"error":null,"result":{"username":"alice","role":3,"csrfToken":"csrf-alice"}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                    "/pgapi/settings" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"error":null,"result":{"Album":{"enabled":true,"writeAccessMinRole":"User"}}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                    else -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            }
            .build()

        api.fetchCurrentUserSession("https://example.org", "pigallery2-session=u1", "/pgapi")

        assertEquals(UserRoles.USER, prefs.userRole)
        assertEquals(UserRoles.USER, prefs.albumWriteMinRole)
        assertTrue(prefs.albumFeatureEnabled)
        assertTrue(
            UserRoles.canManageAlbums(
                role = prefs.userRole,
                albumWriteMinRole = prefs.albumWriteMinRole,
                albumFeatureEnabled = prefs.albumFeatureEnabled,
                userCanCreateAlbumsOverride = prefs.userCanCreateAlbumsOverride
            )
        )
    }

    @Test
    fun `fetchCurrentUserSession falls back to HTML ServerInject ConfigInject when settings returns 403`() {
        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                when (req.url.encodedPath) {
                    "/pgapi/user/me" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"error":null,"result":{"username":"bob","role":3}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                    "/pgapi/settings" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(403)
                        .message("Forbidden")
                        .body("""{"error":"Admin required"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                    "/" -> {
                        val html = """
                            <!doctype html>
                            <html>
                            <head>
                            <script>
                            var ServerInject = {
                              user: {"username":"bob","role":3},
                              ConfigInject: {"Users":{"allowUserToCreateAlbums":true},"Album":{"enabled":true}}
                            };
                            </script>
                            </head>
                            <body></body>
                            </html>
                        """.trimIndent()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(html.toResponseBody("text/html".toMediaType()))
                            .build()
                    }
                    else -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            }
            .build()

        api.fetchCurrentUserSession("https://example.org", "pigallery2-session=u2", "/pgapi")

        assertEquals(UserRoles.USER, prefs.userRole)
        assertEquals(UserRoles.USER, prefs.albumWriteMinRole)
        assertTrue(
            UserRoles.canManageAlbums(
                role = prefs.userRole,
                albumWriteMinRole = prefs.albumWriteMinRole,
                albumFeatureEnabled = prefs.albumFeatureEnabled,
                userCanCreateAlbumsOverride = prefs.userCanCreateAlbumsOverride
            )
        )
    }

    @Test
    fun `deleteAlbum sends DELETE with CSRF token and cookie and handles 404 fallback`() {
        prefs.csrfToken = "csrf-del-1"
        val attempts = mutableListOf<String>()
        var capturedCsrf: String? = null
        var capturedCookie: String? = null

        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                attempts.add("${req.method} ${req.url.encodedPath}")
                if (req.url.encodedPath == "/pgapi/albums/42") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    capturedCsrf = req.header("CSRF-Token")
                    capturedCookie = req.header("Cookie")
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"error":null,"result":true}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        api.deleteAlbum(
            serverUrl = "https://example.org/",
            albumId = 42,
            cookies = "pigallery2-session=del",
            apiPrefix = "/pgapi"
        )

        assertEquals(listOf("DELETE /pgapi/albums/42", "DELETE /pgapi/albums/saved-searches/42"), attempts)
        assertEquals("csrf-del-1", capturedCsrf)
        assertEquals("pigallery2-session=del", capturedCookie)
    }

    @Test
    fun `deleteAlbum stops immediately on 403 forbidden without trying fallbacks`() {
        prefs.csrfToken = "csrf-user"
        val attempts = mutableListOf<String>()

        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                attempts.add("${req.method} ${req.url.encodedPath}")
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(403)
                    .message("Forbidden")
                    .body("""{"error":"Not authorized"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        try {
            api.deleteAlbum(
                serverUrl = "https://example.org",
                albumId = 99,
                cookies = "pigallery2-session=user",
                apiPrefix = "/pgapi"
            )
            fail("Expected IOException on 403 Forbidden")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Not authorized") == true)
            assertEquals(listOf("DELETE /pgapi/albums/99"), attempts)
        }
    }
}
