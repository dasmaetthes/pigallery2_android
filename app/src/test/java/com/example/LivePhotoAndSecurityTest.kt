package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApiMedia
import com.example.data.ApiMediaMetadata
import com.example.data.LivePhotoHelper
import com.example.data.PiGalleryApi
import com.example.data.PreferencesManager
import com.example.data.UnauthorizedSessionException
import com.example.ui.GalleryViewModel
import com.example.util.SecurePrefsCipher
import kotlinx.coroutines.runBlocking
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LivePhotoAndSecurityTest {

    private lateinit var app: Application
    private lateinit var prefs: PreferencesManager
    private lateinit var api: PiGalleryApi

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        prefs = PreferencesManager(app)
        prefs.clear()
        api = PiGalleryApi(app)
    }

    @Test
    fun `PreferencesManager encrypts sensitive credentials at rest and migrates legacy plaintext`() {
        prefs.password = "SuperSecret!42"
        prefs.cookies = "pigallery2-session=abc123; pigallery2-session.sig=sig999"
        prefs.csrfToken = "csrf-token-xyz"

        val rawPrefs = app.getSharedPreferences("pigallery_prefs", Context.MODE_PRIVATE)
        val rawPassword = rawPrefs.getString("password", "") ?: ""
        val rawCookies = rawPrefs.getString("cookies", "") ?: ""
        val rawCsrf = rawPrefs.getString("csrf_token", "") ?: ""

        assertTrue("Password should be stored encrypted", SecurePrefsCipher.isEncrypted(rawPassword))
        assertTrue("Cookies should be stored encrypted", SecurePrefsCipher.isEncrypted(rawCookies))
        assertTrue("CSRF token should be stored encrypted", SecurePrefsCipher.isEncrypted(rawCsrf))

        assertFalse(rawPassword.contains("SuperSecret"))
        assertFalse(rawCookies.contains("abc123"))
        assertFalse(rawCsrf.contains("csrf-token-xyz"))

        assertEquals("SuperSecret!42", prefs.password)
        assertEquals("pigallery2-session=abc123; pigallery2-session.sig=sig999", prefs.cookies)
        assertEquals("csrf-token-xyz", prefs.csrfToken)

        // Legacy plaintext migration test
        rawPrefs.edit().putString("password", "LegacyPlaintextPass").commit()
        assertFalse(SecurePrefsCipher.isEncrypted(rawPrefs.getString("password", "") ?: ""))

        // Reading should decrypt (pass through) and immediately migrate to encrypted storage
        assertEquals("LegacyPlaintextPass", prefs.password)
        val migratedRaw = rawPrefs.getString("password", "") ?: ""
        assertTrue("Legacy plaintext should be migrated on read", SecurePrefsCipher.isEncrypted(migratedRaw))
        assertEquals("LegacyPlaintextPass", prefs.password)
    }

    @Test
    fun `LivePhotoHelper pairs companion mov, hides duplicate, and ignores standalone heic and long videos`() {
        val liveStill = ApiMedia(
            id = 1,
            name = "IMG_1001.HEIC",
            parentPath = "Trips/2026",
            metadata = ApiMediaMetadata(creationDate = 1710000000000L)
        )
        val companionMov = ApiMedia(
            id = 2,
            name = "IMG_1001.MOV",
            parentPath = "Trips/2026",
            metadata = ApiMediaMetadata(duration = 2850.0, creationDate = 1710000000000L)
        )
        val standaloneHeic = ApiMedia(
            id = 3,
            name = "IMG_1002.HEIC",
            parentPath = "Trips/2026",
            metadata = ApiMediaMetadata(creationDate = 1710000050000L)
        )
        val longStill = ApiMedia(
            id = 4,
            name = "IMG_1003.JPG",
            parentPath = "Trips/2026",
            metadata = ApiMediaMetadata(creationDate = 1710000100000L)
        )
        val longVideo = ApiMedia(
            id = 5,
            name = "IMG_1003.MOV",
            parentPath = "Trips/2026",
            metadata = ApiMediaMetadata(duration = 15200.0, creationDate = 1710000100000L)
        )

        val paired = LivePhotoHelper.pairLivePhotos(
            listOf(liveStill, companionMov, standaloneHeic, longStill, longVideo)
        )

        // Companion .mov should be folded into IMG_1001.HEIC and hidden from grid list
        assertEquals(4, paired.size)
        assertFalse(paired.any { it.name == "IMG_1001.MOV" })

        val pairedLive = paired.first { it.name == "IMG_1001.HEIC" }
        assertTrue("Paired HEIC+MOV should be marked as Live Photo", pairedLive.isLivePhoto)
        assertEquals("IMG_1001.MOV", pairedLive.metadata?.liveVideoPath)
        assertEquals("Trips/2026/IMG_1001.MOV", LivePhotoHelper.resolveCompanionVideoPath(pairedLive))

        val plainHeic = paired.first { it.name == "IMG_1002.HEIC" }
        assertFalse("Standalone HEIC without companion MOV must not be marked Live Photo", plainHeic.isLivePhoto)

        val nonLiveJpg = paired.first { it.name == "IMG_1003.JPG" }
        assertFalse("Photo matching a long video (> 6.5s) must not be marked Live Photo", nonLiveJpg.isLivePhoto)
        assertTrue("Long video must remain visible", paired.any { it.name == "IMG_1003.MOV" })
    }

    @Test
    fun `PiGalleryApi throws UnauthorizedSessionException on HTTP 401 across endpoints`() {
        val server = "https://gallery.example.com"
        val expiredCookies = "pigallery2-session=expired"
        prefs.serverUrl = server
        prefs.cookies = expiredCookies

        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("""{"error":{"code":401,"message":"Not authenticated"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        runBlocking {
            try {
                api.getGalleryContent(server, "", expiredCookies, "/pgapi")
                fail("Expected UnauthorizedSessionException for getGalleryContent")
            } catch (_: UnauthorizedSessionException) {
                // expected
            }

            try {
                api.getAlbums(server, expiredCookies, "/pgapi")
                fail("Expected UnauthorizedSessionException for getAlbums")
            } catch (_: UnauthorizedSessionException) {
                // expected
            }

            try {
                api.getPersons(server, expiredCookies, "/pgapi")
                fail("Expected UnauthorizedSessionException for getPersons")
            } catch (_: UnauthorizedSessionException) {
                // expected
            }

            try {
                api.search(server, """{"type":100,"value":"vacation"}""", expiredCookies, "/pgapi")
                fail("Expected UnauthorizedSessionException for search")
            } catch (_: UnauthorizedSessionException) {
                // expected
            }
        }
    }

    @Test
    fun `clearSession preserves user settings and server credentials while clearing sensitive auth state`() {
        prefs.serverUrl = "https://gallery.example.com"
        prefs.username = "alice"
        prefs.password = "secret123"
        prefs.cookies = "pigallery2-session=tok"
        prefs.csrfToken = "csrf-1"
        prefs.themeMode = "Black"
        prefs.itemsPerRowPortrait = 4
        prefs.rediscoverDays = 7

        prefs.clearSession()

        // Settings and non-secret login hints remain intact
        assertEquals("https://gallery.example.com", prefs.serverUrl)
        assertEquals("alice", prefs.username)
        assertEquals("Black", prefs.themeMode)
        assertEquals(4, prefs.itemsPerRowPortrait)
        assertEquals(7, prefs.rediscoverDays)

        // Sensitive session credentials are wiped
        assertEquals("", prefs.password)
        assertEquals("", prefs.cookies)
        assertEquals("", prefs.csrfToken)
    }

    @Test
    fun `GalleryViewModel executeAuthenticated recovers via silent re-login on 401 and logs out preserving settings on failure`() {
        val server = "https://gallery.example.com"
        prefs.serverUrl = server
        prefs.username = "alice"
        prefs.password = "my-pass"
        prefs.cookies = "pigallery2-session=old"
        prefs.isLoggedIn = true
        prefs.themeMode = "Dark"
        prefs.itemsPerRowPortrait = 5

        val viewModel = GalleryViewModel(app)
        var albumCalls = 0
        var loginCalls = 0
        var allowLoginSuccess = true

        viewModel.api.client = viewModel.api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                when {
                    path.endsWith("/user/login") -> {
                        loginCalls++
                        if (allowLoginSuccess) {
                            Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .addHeader("Set-Cookie", "pigallery2-session=refreshed; Path=/")
                                .body("""{"error":null,"result":{"username":"alice","role":3,"csrfToken":"new-csrf"}}""".toResponseBody("application/json".toMediaType()))
                                .build()
                        } else {
                            Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(401)
                                .message("Unauthorized")
                                .body("""{"error":{"code":401,"message":"Invalid credentials"}}""".toResponseBody("application/json".toMediaType()))
                                .build()
                        }
                    }
                    path.endsWith("/albums") -> {
                        albumCalls++
                        val cookieHeader = req.header("Cookie") ?: ""
                        if (cookieHeader.contains("refreshed") && allowLoginSuccess) {
                            Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body("""{"error":null,"result":[]}""".toResponseBody("application/json".toMediaType()))
                                .build()
                        } else {
                            Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(401)
                                .message("Unauthorized")
                                .body("""{"error":{"code":401,"message":"Session expired"}}""".toResponseBody("application/json".toMediaType()))
                                .build()
                        }
                    }
                    else -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"error":null,"result":{"name":"","path":"","directories":[],"media":[]}}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                }
            }
            .build()

        // 1) Silent re-login succeeds and retries request transparently
        var localAttempts = 0
        runBlocking {
            val albums = viewModel.executeAuthenticated { activeCookies, activePrefix ->
                localAttempts++
                viewModel.api.getAlbums(server, activeCookies, activePrefix)
            }
            assertNotNull(albums)
            assertEquals(2, localAttempts)
            assertEquals(1, loginCalls)
            assertTrue(viewModel.isLoggedIn.value)
            assertTrue(prefs.cookies.contains("refreshed"))
        }

        // 2) Silent re-login fails -> clean logout, clears secrets, preserves settings
        allowLoginSuccess = false
        runBlocking {
            try {
                viewModel.executeAuthenticated { activeCookies, activePrefix ->
                    viewModel.api.getAlbums(server, activeCookies, activePrefix)
                }
                fail("Expected UnauthorizedSessionException when silent re-login fails")
            } catch (_: UnauthorizedSessionException) {
                // expected
            }
        }

        assertFalse("User should be logged out after failed re-auth", viewModel.isLoggedIn.value)
        assertEquals("", prefs.password)
        assertEquals("", prefs.cookies)
        assertEquals("", prefs.csrfToken)
        assertEquals("Dark", prefs.themeMode)
        assertEquals(5, prefs.itemsPerRowPortrait)
        assertEquals(server, prefs.serverUrl)
        assertEquals("alice", prefs.username)
    }
}
