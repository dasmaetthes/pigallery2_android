package com.example

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.AlbumBackupManager
import com.example.data.ApiAlbum
import com.example.data.BackedUpAlbum
import com.example.data.PiGalleryApi
import com.example.data.PreferencesManager
import com.example.data.UserRoles
import com.example.ui.AlbumRestoreUiState
import com.example.ui.AlbumsTabContent
import com.example.ui.AlbumsUiState
import com.example.ui.DeleteAlbumUiState
import com.example.ui.GalleryViewModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlbumBackupAndPermissionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var backupManager: AlbumBackupManager
    private lateinit var api: PiGalleryApi

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = PreferencesManager(context)
        prefs.clear()
        backupManager = AlbumBackupManager(context)
        backupManager.clearAll()
        api = PiGalleryApi(context)
    }

    private fun waitUntil(timeoutMs: Long = 3000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", condition())
    }

    @Test
    fun `fetchCurrentUserSession allows User role by default when ConfigInject has Album enabled without explicit Admin lock`() {
        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                when (req.url.encodedPath) {
                    "/pgapi/user/me" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"error":null,"result":{"username":"max","role":3,"csrfToken":"csrf-u3"}}""".toResponseBody("application/json".toMediaType()))
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
                            <head><script>
                            var ServerInject = {
                              user: {"username":"max","role":3},
                              ConfigInject: {"Album":{"enabled":true}}
                            };
                            </script></head><body></body></html>
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

        api.fetchCurrentUserSession("https://example.org", "pigallery2-session=u3", "/pgapi")

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
    fun `fetchCurrentUserSession still blocks User when server explicitly sets Album readOnly or Admin writeAccessMinRole`() {
        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                when (req.url.encodedPath) {
                    "/pgapi/user/me" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"error":null,"result":{"username":"max","role":3}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                    "/pgapi/settings" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"error":null,"result":{"Album":{"enabled":true,"readOnly":true}}}""".toResponseBody("application/json".toMediaType()))
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

        api.fetchCurrentUserSession("https://example.org", "pigallery2-session=u3", "/pgapi")

        assertEquals(UserRoles.USER, prefs.userRole)
        assertEquals(UserRoles.ADMIN, prefs.albumWriteMinRole)
        assertFalse(
            UserRoles.canManageAlbums(
                role = prefs.userRole,
                albumWriteMinRole = prefs.albumWriteMinRole,
                albumFeatureEnabled = prefs.albumFeatureEnabled,
                userCanCreateAlbumsOverride = prefs.userCanCreateAlbumsOverride
            )
        )
    }

    @Test
    fun `AlbumBackupManager saves only unlocked albums, normalizes server URL, and survives clearSession`() {
        val unlocked1 = ApiAlbum(
            id = 10,
            name = "Summer 2024",
            locked = false,
            searchQuery = mapOf("type" to 100.0, "value" to "Summer"),
            cache = null
        )
        val lockedFromPg2Conf = ApiAlbum(
            id = 99,
            name = "Locked Config Album",
            locked = true,
            searchQuery = mapOf("type" to 100.0, "value" to "Config"),
            cache = null
        )

        backupManager.replaceUnlockedSnapshot("https://Example.ORG/", listOf(unlocked1, lockedFromPg2Conf), nowMs = 123456L)

        // Normalized lookup (different casing & no trailing slash)
        val stored = backupManager.getBackups("https://example.org")
        assertEquals(1, stored.size)
        assertEquals("Summer 2024", stored[0].name)
        assertEquals(10, stored[0].lastKnownId)
        assertEquals(123456L, stored[0].savedAtMs)
        assertEquals(100L, (stored[0].searchQuery["type"] as Number).toLong())

        // Logging out must NOT wipe local album backups
        prefs.isLoggedIn = true
        prefs.cookies = "cookie=abc"
        prefs.clearSession()
        assertEquals(1, backupManager.getBackups("https://example.org").size)
    }

    @Test
    fun `GalleryViewModel distinguishes WebUI deletion, in-app deletion, and total update loss`() {
        val serverAlbums = CopyOnWriteArrayList(
            listOf(
                ApiAlbum(1, "Trips", false, mapOf("type" to 100, "value" to "trip"), null),
                ApiAlbum(2, "Family", false, mapOf("type" to 105, "value" to "Anna"), null),
                ApiAlbum(99, "From pg2conf", true, mapOf("type" to 100, "value" to "locked"), null)
            )
        )
        val createdRequests = CopyOnWriteArrayList<String>()
        var nextId = 10

        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.api.client = vm.api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                when {
                    req.method == "GET" && req.url.encodedPath == "/pgapi/albums" -> {
                        val jsonItems = serverAlbums.joinToString(",") { a ->
                            val qJson = vm.api.serializeQuery(a.searchQuery ?: emptyMap())
                            """{"id":${a.id},"name":"${a.name}","locked":${a.locked},"searchQuery":$qJson}"""
                        }
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"error":null,"result":[$jsonItems]}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    req.method == "DELETE" && req.url.encodedPath.startsWith("/pgapi/albums/") -> {
                        val id = req.url.encodedPath.substringAfterLast("/").toIntOrNull()
                        serverAlbums.removeAll { it.id == id }
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"error":null,"result":true}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    req.method == "PUT" && req.url.encodedPath == "/pgapi/albums/saved-searches" -> {
                        val buf = okio.Buffer()
                        req.body?.writeTo(buf)
                        val raw = buf.readUtf8()
                        createdRequests.add(raw)
                        val newId = nextId++
                        val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(raw)?.groupValues?.get(1) ?: "Restored"
                        serverAlbums.add(
                            ApiAlbum(newId, nameMatch, false, mapOf("type" to 100, "value" to nameMatch), null)
                        )
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"error":null,"result":{"id":$newId,"name":"$nameMatch","locked":false,"searchQuery":{"type":100,"value":"$nameMatch"}}}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    else -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"error":null,"result":{}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        vm.prefs.serverUrl = "https://example.org"
        vm.prefs.cookies = "pigallery2-session=u3"
        vm.prefs.csrfToken = "csrf-1"
        vm.prefs.userRole = UserRoles.USER
        vm.prefs.albumWriteMinRole = UserRoles.USER
        vm.syncPermissionStateFlows()

        // 1. Initial load saves the 2 unlocked albums (ignoring locked album)
        vm.loadAlbums()
        waitUntil { (vm.albumsState.value as? AlbumsUiState.Success)?.albums?.size == 3 }
        assertEquals(2, vm.albumBackupManager.getBackups("https://example.org").size)
        assertTrue(vm.recoverableAlbums.value.isEmpty())

        // 2. User deletes "Family" in WebUI -> server now returns 1 unlocked + 1 locked
        // Backup should silently sync to ["Trips"] WITHOUT offering restore
        serverAlbums.removeAll { it.id == 2 }
        vm.refreshAlbums()
        waitUntil { (vm.albumsState.value as? AlbumsUiState.Success)?.albums?.size == 2 }
        assertEquals(listOf("Trips"), vm.albumBackupManager.getBackups("https://example.org").map { it.name })
        assertTrue(vm.recoverableAlbums.value.isEmpty())

        // 3. Simulate PiGallery2 update/DB reset wiping all unlocked albums (only locked pg2conf remains)
        serverAlbums.removeAll { !it.locked }
        vm.loadAlbums()
        waitUntil { (vm.albumsState.value as? AlbumsUiState.Success)?.albums?.size == 1 }
        assertEquals(listOf("Trips"), vm.recoverableAlbums.value.map { it.name })

        // 4. Restore via ViewModel recreates "Trips" and clears recoverable prompt
        vm.restoreBackedUpAlbums(vm.recoverableAlbums.value)
        waitUntil { vm.albumRestoreState.value is AlbumRestoreUiState.Success }
        assertEquals(1, createdRequests.size)
        assertTrue(vm.recoverableAlbums.value.isEmpty())
        val restoredAlbum = (vm.albumsState.value as AlbumsUiState.Success).albums.first { it.name == "Trips" }

        // 5. Intentional in-app deletion of the ONLY remaining unlocked album must remove backup
        // and MUST NOT offer restore afterwards
        vm.deleteAlbum(restoredAlbum)
        waitUntil {
            vm.deleteAlbumState.value is DeleteAlbumUiState.Success &&
                (vm.albumsState.value as? AlbumsUiState.Success)?.albums?.none { !it.locked } == true
        }
        assertTrue(vm.albumBackupManager.getBackups("https://example.org").isEmpty())
        assertTrue(vm.recoverableAlbums.value.isEmpty())
    }

    @Test
    fun `AlbumsTabContent displays restore banner and handles selective restore dialog and dismiss confirmation`() {
        val serverAlbums = CopyOnWriteArrayList<ApiAlbum>()
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.api.client = vm.api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                when {
                    req.method == "GET" && req.url.encodedPath == "/pgapi/albums" -> {
                        val jsonItems = serverAlbums.joinToString(",") { a ->
                            val qJson = vm.api.serializeQuery(a.searchQuery ?: emptyMap())
                            """{"id":${a.id},"name":"${a.name}","locked":${a.locked},"searchQuery":$qJson}"""
                        }
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"error":null,"result":[$jsonItems]}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    req.method == "PUT" && req.url.encodedPath == "/pgapi/albums/saved-searches" -> {
                        serverAlbums.add(ApiAlbum(50, "Beach", false, mapOf("type" to 100, "value" to "Beach"), null))
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"error":null,"result":{"id":50,"name":"Beach","locked":false,"searchQuery":{"type":100,"value":"Beach"}}}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    else -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"error":null,"result":{}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }
            .build()

        vm.prefs.serverUrl = "https://example.org"
        vm.prefs.cookies = "pigallery2-session=u3"
        vm.prefs.csrfToken = "csrf-1"
        vm.prefs.userRole = UserRoles.USER
        vm.prefs.albumWriteMinRole = UserRoles.USER
        vm.syncPermissionStateFlows()

        vm.albumBackupManager.upsertAlbum(
            "https://example.org",
            "Beach",
            mapOf("type" to 100, "value" to "Beach"),
            albumId = 5
        )
        vm.loadAlbums()
        waitUntil { vm.recoverableAlbums.value.size == 1 && vm.albumsState.value is AlbumsUiState.Success }

        composeTestRule.setContent {
            MaterialTheme {
                AlbumsTabContent(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_restore_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_restore_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_restore_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_restore_item_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_restore_confirm_button").performClick()

        waitUntil { vm.recoverableAlbums.value.isEmpty() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_restore_banner").assertDoesNotExist()
        composeTestRule.onNodeWithText("Beach").assertIsDisplayed()
    }

    @Test
    fun `AlbumsTabContent dismiss confirmation clears local backup and hides restore banner`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.api.client = vm.api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"error":null,"result":[]}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        vm.prefs.serverUrl = "https://example.org"
        vm.prefs.cookies = "pigallery2-session=u3"
        vm.prefs.userRole = UserRoles.USER
        vm.prefs.albumWriteMinRole = UserRoles.USER
        vm.syncPermissionStateFlows()

        vm.albumBackupManager.upsertAlbum(
            "https://example.org",
            "Deleted On Web",
            mapOf("type" to 100, "value" to "old"),
            albumId = 8
        )
        vm.loadAlbums()
        waitUntil { vm.recoverableAlbums.value.size == 1 && vm.albumsState.value is AlbumsUiState.Success }

        composeTestRule.setContent {
            MaterialTheme {
                AlbumsTabContent(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_restore_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_restore_dismiss_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_backup_dismiss_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_backup_dismiss_confirm_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(vm.recoverableAlbums.value.isEmpty())
        assertTrue(vm.albumBackupManager.getBackups("https://example.org").isEmpty())
        composeTestRule.onNodeWithTag("album_restore_banner").assertDoesNotExist()
    }

    @Test
    fun `AlbumBackupManager resolves backups across whitespace, default ports, and http-https switch without mixing hosts`() {
        val album = ApiAlbum(7, "Roadtrip", false, mapOf("type" to 100.0, "value" to "roadtrip"), null)
        backupManager.replaceUnlockedSnapshot(" http://MyServer.local:80/ ", listOf(album), nowMs = 5000L)

        // Normalized same host with HTTPS + default :443 + whitespace matches seamlessly
        val found = backupManager.getBackups(" https://myserver.local:443/ ")
        assertEquals(1, found.size)
        assertEquals("Roadtrip", found[0].name)

        // Different port or different host must NOT see those backups
        assertTrue(backupManager.getBackups("https://myserver.local:8443").isEmpty())
        assertTrue(backupManager.getBackups("https://other-server.org").isEmpty())

        // Clearing via the HTTPS alias removes the underlying HTTP key too
        backupManager.clearBackups("https://myserver.local")
        assertTrue(backupManager.getBackups("http://myserver.local").isEmpty())
    }

    @Test
    fun `album recovery works after disconnect logout and reconnect via connectAndLogin including local URL switch`() {
        val serverAlbums = CopyOnWriteArrayList(
            listOf(
                ApiAlbum(1, "Mountains", false, mapOf("type" to 100, "value" to "mountain"), null),
                ApiAlbum(2, "City", false, mapOf("type" to 105, "value" to "Berlin"), null)
            )
        )
        val createdRequests = CopyOnWriteArrayList<String>()
        var nextId = 20

        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.api.client = vm.api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                when {
                    req.method == "POST" && req.url.encodedPath == "/pgapi/user/login" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .addHeader("Set-Cookie", "pigallery2-session=fresh-session; Path=/")
                            .body("""{"error":null,"result":{"username":"max","role":3,"csrfToken":"fresh-csrf"}}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    req.method == "GET" && req.url.encodedPath == "/pgapi/user/me" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"error":null,"result":{"username":"max","role":3,"csrfToken":"fresh-csrf"}}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    req.method == "GET" && req.url.encodedPath == "/pgapi/settings" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"error":null,"result":{"Album":{"enabled":true}}}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    req.method == "GET" && req.url.encodedPath == "/pgapi/albums" -> {
                        val jsonItems = serverAlbums.joinToString(",") { a ->
                            val qJson = vm.api.serializeQuery(a.searchQuery ?: emptyMap())
                            """{"id":${a.id},"name":"${a.name}","locked":${a.locked},"searchQuery":$qJson}"""
                        }
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"error":null,"result":[$jsonItems]}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    req.method == "PUT" && req.url.encodedPath == "/pgapi/albums/saved-searches" -> {
                        val buf = okio.Buffer()
                        req.body?.writeTo(buf)
                        val raw = buf.readUtf8()
                        createdRequests.add(raw)
                        val newId = nextId++
                        val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(raw)?.groupValues?.get(1) ?: "Restored"
                        serverAlbums.add(
                            ApiAlbum(newId, nameMatch, false, mapOf("type" to 100, "value" to nameMatch), null)
                        )
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"error":null,"result":{"id":$newId,"name":"$nameMatch","locked":false,"searchQuery":{"type":100,"value":"$nameMatch"}}}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    else -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"error":null,"result":{"directory":{"id":1,"name":"root","path":"","directories":[],"media":[]}}}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                }
            }
            .build()

        // Initial session with primary + local URL configured
        vm.prefs.serverUrl = "https://nas.example.org"
        vm.prefs.localServerUrl = "http://192.168.178.50:3000"
        vm.prefs.cookies = "pigallery2-session=initial"
        vm.prefs.csrfToken = "csrf-init"
        vm.prefs.userRole = UserRoles.USER
        vm.prefs.albumWriteMinRole = UserRoles.USER
        vm.prefs.isLoggedIn = true
        vm.syncPermissionStateFlows()

        vm.loadAlbums()
        waitUntil { (vm.albumsState.value as? AlbumsUiState.Success)?.albums?.size == 2 }
        assertEquals(2, vm.albumBackupManager.getBackups("https://nas.example.org").size)

        // User disconnects the app and PiGallery2 is updated/reset (0 albums left on server)
        vm.logout()
        assertFalse(vm.isLoggedIn.value)
        assertTrue(vm.albumsState.value is AlbumsUiState.Loading)
        assertTrue(vm.recoverableAlbums.value.isEmpty())
        serverAlbums.clear()

        // User reconnects via the previously configured local URL (with trailing slash/spaces)
        vm.connectAndLogin(" http://192.168.178.50:3000/ ", "max", "secret")
        waitUntil {
            vm.isLoggedIn.value &&
                vm.canManageAlbums.value &&
                vm.albumsState.value is AlbumsUiState.Success &&
                vm.recoverableAlbums.value.size == 2
        }

        // Restore all backed-up albums on the newly connected session
        vm.restoreBackedUpAlbums(vm.recoverableAlbums.value)
        waitUntil { vm.albumRestoreState.value is AlbumRestoreUiState.Success }

        assertEquals(2, createdRequests.size)
        assertTrue(vm.recoverableAlbums.value.isEmpty())
        assertEquals(
            setOf("Mountains", "City"),
            (vm.albumsState.value as AlbumsUiState.Success).albums.map { it.name }.toSet()
        )
    }
}
