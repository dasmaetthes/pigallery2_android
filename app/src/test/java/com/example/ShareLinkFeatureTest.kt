package com.example

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApiPerson
import com.example.data.ApiShareLink
import com.example.data.PiGalleryApi
import com.example.data.PreferencesManager
import com.example.data.ShareTarget
import com.example.data.ShareTargetKind
import com.example.data.ShareValidity
import com.example.data.ShareValidityUnit
import com.example.data.UserRoles
import com.example.ui.GalleryScreen
import com.example.ui.GalleryViewModel
import com.example.ui.SettingsTabContent
import com.example.ui.ShareLinkDialog
import com.example.ui.ShareLinkUiState
import com.example.ui.SharingConfigState
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareLinkFeatureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun `UserRoles canCreateShareLinks enforces sharing enabled and minimum User role`() {
        assertFalse(UserRoles.canCreateShareLinks(-1, sharingFeatureEnabled = true))
        assertFalse(UserRoles.canCreateShareLinks(UserRoles.LIMITED_GUEST, sharingFeatureEnabled = true))
        assertFalse(UserRoles.canCreateShareLinks(UserRoles.GUEST, sharingFeatureEnabled = true))
        assertTrue(UserRoles.canCreateShareLinks(UserRoles.USER, sharingFeatureEnabled = true))
        assertTrue(UserRoles.canCreateShareLinks(UserRoles.ADMIN, sharingFeatureEnabled = true))
        assertTrue(UserRoles.canCreateShareLinks(UserRoles.DEVELOPER, sharingFeatureEnabled = true))

        // Admin disabled Sharing in PiGallery2 settings -> hidden for everyone
        assertFalse(UserRoles.canCreateShareLinks(UserRoles.USER, sharingFeatureEnabled = false))
        assertFalse(UserRoles.canCreateShareLinks(UserRoles.ADMIN, sharingFeatureEnabled = false))
        assertFalse(UserRoles.canCreateShareLinks(UserRoles.DEVELOPER, sharingFeatureEnabled = false))
    }

    @Test
    fun `applyClientConfigMap parses Client Sharing settings when admin disables feature or passwords`() {
        val disabledConfig = mapOf(
            "Client" to mapOf(
                "Sharing" to mapOf(
                    "enabled" to false,
                    "passwordProtected" to false
                )
            )
        )
        assertTrue(api.applyClientConfigMap(disabledConfig))
        assertTrue(prefs.sharingConfigLoaded)
        assertTrue(prefs.sharingSupportedByServer)
        assertFalse(prefs.sharingFeatureEnabled)
        assertFalse(prefs.sharingPasswordProtected)

        val reEnabledConfig = mapOf(
            "Sharing" to mapOf(
                "enabled" to true,
                "passwordProtected" to true,
                "minRole" to "Admin"
            )
        )
        assertTrue(api.applyClientConfigMap(reEnabledConfig))
        assertTrue(prefs.sharingConfigLoaded)
        assertTrue(prefs.sharingSupportedByServer)
        assertTrue(prefs.sharingFeatureEnabled)
        assertTrue(prefs.sharingPasswordProtected)
        assertEquals(UserRoles.ADMIN, prefs.sharingMinRole)

        // When authenticationRequired is false, PiGallery2 restricts sharing
        val noAuthConfig = mapOf(
            "Client" to mapOf(
                "Users" to mapOf("authenticationRequired" to false),
                "Sharing" to mapOf("enabled" to true)
            )
        )
        assertTrue(api.applyClientConfigMap(noAuthConfig))
        assertFalse(prefs.sharingAuthenticationRequired)
        assertFalse(prefs.sharingFeatureEnabled)

        // When server config omits Sharing completely, mark sharing unsupported by server
        val unsupportedConfig = mapOf(
            "Client" to mapOf(
                "Gallery" to mapOf("enabled" to true)
            )
        )
        assertTrue(api.applyClientConfigMap(unsupportedConfig))
        assertTrue(prefs.sharingConfigLoaded)
        assertFalse(prefs.sharingSupportedByServer)
        assertFalse(prefs.sharingFeatureEnabled)
    }

    @Test
    fun `SharingConfigState fails closed when unconfigured, unsupported, disabled, or restricted`() {
        // Default unconfigured state hides share even for Admin
        assertFalse(SharingConfigState().isAvailableForRole(UserRoles.ADMIN))

        // Unsupported server instance hides share
        assertFalse(
            SharingConfigState(
                isConfigLoaded = true,
                isSupportedByServer = false,
                isEnabled = true
            ).isAvailableForRole(UserRoles.ADMIN)
        )

        // Admin disabled sharing hides share
        assertFalse(
            SharingConfigState(
                isConfigLoaded = true,
                isSupportedByServer = true,
                isEnabled = false
            ).isAvailableForRole(UserRoles.ADMIN)
        )

        // Authentication disabled restricts sharing
        assertFalse(
            SharingConfigState(
                isConfigLoaded = true,
                isSupportedByServer = true,
                isEnabled = true,
                isAuthenticationRequired = false
            ).isAvailableForRole(UserRoles.ADMIN)
        )

        // Supported and enabled enforces minRole
        val adminOnlyConfig = SharingConfigState(
            isConfigLoaded = true,
            isSupportedByServer = true,
            isEnabled = true,
            minRole = UserRoles.ADMIN
        )
        assertFalse(adminOnlyConfig.isAvailableForRole(UserRoles.GUEST))
        assertFalse(adminOnlyConfig.isAvailableForRole(UserRoles.USER))
        assertTrue(adminOnlyConfig.isAvailableForRole(UserRoles.ADMIN))
    }

    @Test
    fun `probeSharingSupport detects unsupported 404, restricted 403, and enabled 200 servers`() {
        var responseCode = 404
        var responseJson = """{"error": "Not found"}"""
        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message("Mock")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        api.probeSharingSupport("https://photos.example.org", "c=1", "/pgapi")
        assertTrue(prefs.sharingConfigLoaded)
        assertFalse(prefs.sharingSupportedByServer)
        assertFalse(prefs.sharingFeatureEnabled)

        responseCode = 403
        responseJson = """{"error": "Forbidden"}"""
        api.probeSharingSupport("https://photos.example.org", "c=1", "/pgapi")
        assertTrue(prefs.sharingConfigLoaded)
        assertTrue(prefs.sharingSupportedByServer)
        assertFalse(prefs.sharingFeatureEnabled)

        responseCode = 200
        responseJson = """{"error": null, "result": []}"""
        api.probeSharingSupport("https://photos.example.org", "c=1", "/pgapi")
        assertTrue(prefs.sharingConfigLoaded)
        assertTrue(prefs.sharingSupportedByServer)
        assertTrue(prefs.sharingFeatureEnabled)
    }

    @Test
    fun `createShareLink sends createSharing payload with CSRF token and parses result`() {
        prefs.csrfToken = "csrf-share-token"
        var capturedMethod = ""
        var capturedPath = ""
        var capturedCsrf = ""
        var capturedBody = ""

        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedMethod = req.method
                capturedPath = req.url.encodedPath
                capturedCsrf = req.header("CSRF-Token").orEmpty()
                val buf = okio.Buffer()
                req.body?.writeTo(buf)
                capturedBody = buf.readUtf8()

                val json = """
                    {
                      "error": null,
                      "result": {
                        "id": 11,
                        "sharingKey": "sKey99",
                        "path": "./Vacation 2025",
                        "password": "***",
                        "expires": 1800000000000,
                        "includeSubfolders": true,
                        "timeStamp": 1700000000000
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

        val created = api.createShareLink(
            serverUrl = "https://photos.example.org/",
            path = "Vacation 2025",
            includeSubfolders = true,
            validity = ShareValidity(amount = 12, unit = ShareValidityUnit.HOURS),
            password = "secret",
            cookies = "pigallery2-session=abc",
            apiPrefix = "/pgapi",
            preventDownload = true
        )

        assertEquals("POST", capturedMethod)
        assertEquals("/pgapi/share/Vacation%202025", capturedPath)
        assertEquals("csrf-share-token", capturedCsrf)
        assertTrue(capturedBody.contains("\"createSharing\""))
        assertTrue(capturedBody.contains("\"includeSubfolders\":true"))
        assertTrue(capturedBody.contains("\"preventDownload\":true"))
        assertTrue(capturedBody.contains("\"password\":\"secret\""))
        assertTrue(capturedBody.contains("\"amount\":12"))
        assertTrue(capturedBody.contains("\"type\":2"))
        assertEquals("sKey99", created.sharingKey)
        assertEquals("Vacation 2025", created.path)
        assertTrue(created.hasPassword)
        assertTrue(created.includeSubfolders)
        assertTrue(created.preventDownload)
        assertEquals(1800000000000L, created.expires)
    }

    @Test
    fun `createSearchShareLink sends SearchQueryDTO to share search endpoint and parses searchQuery`() {
        prefs.csrfToken = "csrf-search-share"
        var capturedMethod = ""
        var capturedPath = ""
        var capturedBody = ""

        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedMethod = req.method
                capturedPath = req.url.encodedPath
                val buf = okio.Buffer()
                req.body?.writeTo(buf)
                capturedBody = buf.readUtf8()

                val json = """
                    {
                      "error": null,
                      "result": {
                        "id": 21,
                        "sharingKey": "searchKey1",
                        "searchQuery": {"type": 105, "value": "Anna", "matchType": 1},
                        "expires": -1
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

        val personQuery = mapOf<String, Any?>("type" to 105, "value" to "Anna", "matchType" to 1)
        val created = api.createSearchShareLink(
            serverUrl = "https://photos.example.org",
            searchQuery = personQuery,
            validity = ShareValidity(amount = 3, unit = ShareValidityUnit.MONTHS),
            password = null,
            cookies = "pigallery2-session=abc",
            apiPrefix = "/pgapi",
            preventDownload = true
        )

        assertEquals("POST", capturedMethod)
        assertTrue(capturedPath.startsWith("/pgapi/share/search/"))
        assertTrue(capturedBody.contains("\"searchQuery\""))
        assertTrue(capturedBody.contains("\"Anna\""))
        assertTrue(capturedBody.contains("\"preventDownload\":true"))
        assertTrue(capturedBody.contains("\"amount\":3"))
        assertTrue(capturedBody.contains("\"type\":4"))
        assertEquals("searchKey1", created.sharingKey)
        assertTrue(
            api.matchesShareTarget(
                created,
                ShareTarget.Search("Anna", personQuery, ShareTargetKind.PERSON)
            )
        )
    }

    @Test
    fun `createSearchShareLink auto-revokes accidental root share on older server`() {
        prefs.csrfToken = "csrf-safety"
        val seenRequests = mutableListOf<String>()

        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                seenRequests.add("${req.method} ${req.url.encodedPath}")
                val (code, bodyJson) = when {
                    req.url.encodedPath.startsWith("/pgapi/share/search") -> {
                        404 to """{"error": "Not found"}"""
                    }
                    req.method == "POST" && req.url.encodedPath == "/pgapi/share/" -> {
                        // Older server ignored searchQuery and created a root-directory share!
                        200 to """
                            {
                              "error": null,
                              "result": {
                                "id": 99,
                                "sharingKey": "accidentalRootKey",
                                "path": "./",
                                "includeSubfolders": true,
                                "expires": -1
                              }
                            }
                        """.trimIndent()
                    }
                    req.method == "DELETE" && req.url.encodedPath == "/pgapi/share/accidentalRootKey" -> {
                        200 to """{"error": null, "result": true}"""
                    }
                    else -> 500 to """{"error": "Unexpected request"}"""
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Error")
                    .body(bodyJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        try {
            api.createSearchShareLink(
                serverUrl = "https://photos.example.org",
                searchQuery = mapOf("type" to 104, "value" to "sunset"),
                validity = ShareValidity(amount = 7, unit = ShareValidityUnit.DAYS),
                password = null,
                cookies = "c=1",
                apiPrefix = "/pgapi"
            )
            fail("Expected IOException when older server ignores searchQuery and creates a root share")
        } catch (_: IOException) {
            // Expected
        }

        assertTrue(
            "Accidental root share MUST be revoked immediately",
            seenRequests.contains("DELETE /pgapi/share/accidentalRootKey")
        )
    }

    @Test
    fun `matchesShareTarget matches directory path, directory SearchQueryDTO, and canonicalized search queries`() {
        val legacyFolderShare = ApiShareLink(sharingKey = "k1", path = "./Trips/2025")
        val queryFolderShare = ApiShareLink(
            sharingKey = "k2",
            path = "",
            searchQuery = mapOf("type" to 102, "value" to "/Trips/2025", "matchType" to 2)
        )
        val albumShare = ApiShareLink(
            sharingKey = "k3",
            path = "",
            // Deliberately different key order vs target query
            searchQuery = mapOf("value" to "beach", "matchType" to 2, "type" to 104)
        )

        val folderTarget = ShareTarget.Directory("Trips/2025")
        val otherFolderTarget = ShareTarget.Directory("Trips/2024")
        val albumTarget = ShareTarget.Search(
            label = "Beach Album",
            searchQuery = mapOf("type" to 104, "value" to "beach", "matchType" to 2),
            kind = ShareTargetKind.ALBUM
        )

        assertTrue(api.matchesShareTarget(legacyFolderShare, folderTarget))
        assertTrue(api.matchesShareTarget(queryFolderShare, folderTarget))
        assertFalse(api.matchesShareTarget(legacyFolderShare, otherFolderTarget))
        assertFalse(api.matchesShareTarget(albumShare, folderTarget))
        assertTrue(api.matchesShareTarget(albumShare, albumTarget))
        assertFalse(api.matchesShareTarget(legacyFolderShare, albumTarget))
    }

    @Test
    fun `buildPersonSearchQuery respects peopleFallbackToKeywords setting`() {
        val person = ApiPerson(id = 7, name = "Marie")
        val vm = GalleryViewModel(context.applicationContext as Application)

        prefs.peopleFallbackToKeywords = false
        val directQuery = vm.buildPersonSearchQuery(person)
        assertEquals(105, directQuery["type"])
        assertEquals("Marie", directQuery["value"])
        assertEquals(1, directQuery["matchType"])

        prefs.peopleFallbackToKeywords = true
        val fallbackQuery = vm.buildPersonSearchQuery(person)
        assertEquals(2, fallbackQuery["type"])
        val list = fallbackQuery["list"] as List<*>
        assertEquals(2, list.size)
    }

    @Test
    fun `listShareLinks and deleteShareLink call expected endpoints`() {
        prefs.csrfToken = "csrf-del-1"
        val seenRequests = mutableListOf<String>()

        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                seenRequests.add("${req.method} ${req.url.encodedPath}")
                val bodyJson = when {
                    req.method == "GET" && req.url.encodedPath == "/pgapi/share/list" -> """
                        {
                          "error": null,
                          "result": [
                            {
                              "id": 1,
                              "sharingKey": "keyOne",
                              "path": "./Trips",
                              "expires": -1,
                              "includeSubfolders": false
                            }
                          ]
                        }
                    """.trimIndent()
                    req.method == "DELETE" && req.url.encodedPath == "/pgapi/share/keyOne" -> """
                        {"error": null, "result": true}
                    """.trimIndent()
                    else -> """{"error": "Unexpected"}"""
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val shares = api.listShareLinks("https://photos.example.org", "c=1", "/pgapi")
        assertEquals(1, shares.size)
        assertEquals("keyOne", shares.first().sharingKey)
        assertEquals("Trips", shares.first().path)
        assertFalse(shares.first().includeSubfolders)

        api.deleteShareLink("https://photos.example.org", "keyOne", "c=1", "/pgapi")
        assertEquals(listOf("GET /pgapi/share/list", "DELETE /pgapi/share/keyOne"), seenRequests)
    }

    @Test
    fun `buildPublicShareUrl prefers external serverUrl over localServerUrl`() {
        prefs.serverUrl = "https://gallery.public.org/"
        prefs.localServerUrl = "http://192.168.1.50:8080"
        val vm = GalleryViewModel(context.applicationContext as Application)

        assertEquals("https://gallery.public.org/share/abc123", vm.buildPublicShareUrl("abc123"))
    }

    @Test
    fun `resolveShareUrl uses server returned url from Retrofit POST response and falls back to sharingKey`() {
        prefs.serverUrl = "https://gallery.public.org/"
        val vm = GalleryViewModel(context.applicationContext as Application)

        val absoluteShare = ApiShareLink(
            sharingKey = "kAbs",
            path = "Trips",
            shareUrl = "https://cdn.public.org/s/kAbs"
        )
        assertEquals("https://cdn.public.org/s/kAbs", vm.resolveShareUrl(absoluteShare))

        val relativeShare = ApiShareLink(
            sharingKey = "kRel",
            path = "Trips",
            shareUrl = "/share/custom/kRel"
        )
        assertEquals("https://gallery.public.org/share/custom/kRel", vm.resolveShareUrl(relativeShare))

        val keyOnlyShare = ApiShareLink(
            sharingKey = "kOnly",
            path = "Trips",
            shareUrl = null
        )
        assertEquals("https://gallery.public.org/share/kOnly", vm.resolveShareUrl(keyOnlyShare))
    }

    @Test
    fun `Retrofit ShareApiService POST response parses explicit server share url`() {
        prefs.csrfToken = "csrf-url-test"
        api.client = api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                val json = """
                    {
                      "error": null,
                      "result": {
                        "id": 42,
                        "sharingKey": "retroKey",
                        "path": "./Events",
                        "url": "https://photos.example.org/share/retroKey",
                        "expires": -1,
                        "includeSubfolders": true
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

        val created = api.createShareLink(
            serverUrl = "https://photos.example.org",
            path = "Events",
            includeSubfolders = true,
            validity = ShareValidity(amount = 0, unit = ShareValidityUnit.NONE),
            password = null,
            cookies = "c=1",
            apiPrefix = "/pgapi"
        )

        assertEquals("retroKey", created.sharingKey)
        assertEquals("https://photos.example.org/share/retroKey", created.shareUrl)
    }

    @Test
    fun `ShareLinkDialog collects preventDownload, custom expiry number and unit, and password`() {
        var submittedSubfolders: Boolean? = null
        var submittedPreventDownload: Boolean? = null
        var submittedValidity: ShareValidity? = null
        var submittedPassword: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                ShareLinkDialog(
                    target = ShareTarget.Directory("Holidays"),
                    passwordProtected = true,
                    shareLinkState = ShareLinkUiState.Success(
                        shareUrl = "https://gallery.public.org/share/newKey",
                        share = ApiShareLink(sharingKey = "newKey", path = "Holidays")
                    ),
                    onCreateShare = { subfolders, preventDownload, validity, pw ->
                        submittedSubfolders = subfolders
                        submittedPreventDownload = preventDownload
                        submittedValidity = validity
                        submittedPassword = pw
                    },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("share_target_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_include_subfolders_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_prevent_download_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_result_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_no_active_links_text").assertDoesNotExist()

        // Toggle prevent downloading ON
        composeTestRule.onNodeWithTag("share_prevent_download_switch").performScrollTo().performClick()

        // Enter custom duration number "45" and choose "Minutes" unit
        composeTestRule.onNodeWithTag("share_validity_amount_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("share_validity_amount_input").performScrollTo().performTextInput("45")
        composeTestRule.onNodeWithTag("share_validity_unit_dropdown").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("share_unit_option_MINUTES").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("share_password_input").performScrollTo().performTextInput("pass123")
        composeTestRule.onNodeWithTag("share_create_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, submittedSubfolders)
        assertEquals(true, submittedPreventDownload)
        assertEquals(ShareValidity(amount = 45, unit = ShareValidityUnit.MINUTES), submittedValidity)
        assertEquals("pass123", submittedPassword)

        // Switching unit to NONE disables number input and submits ShareValidity(0, NONE)
        composeTestRule.onNodeWithTag("share_validity_unit_dropdown").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("share_unit_option_NONE").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("share_validity_amount_input").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("share_create_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(ShareValidity(amount = 0, unit = ShareValidityUnit.NONE), submittedValidity)
    }

    @Test
    fun `ShareLinkDialog hides subfolders switch for Search, Album, and Person targets while keeping prevent download`() {
        val albumTarget = ShareTarget.Search(
            label = "Summer Highlights",
            searchQuery = mapOf("type" to 104, "value" to "summer"),
            kind = ShareTargetKind.ALBUM
        )

        composeTestRule.setContent {
            MaterialTheme {
                ShareLinkDialog(
                    target = albumTarget,
                    passwordProtected = false,
                    shareLinkState = ShareLinkUiState.Idle,
                    onCreateShare = { _, _, _, _ -> },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("share_target_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_include_subfolders_switch").assertDoesNotExist()
        composeTestRule.onNodeWithTag("share_prevent_download_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_password_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("share_no_active_links_text").assertDoesNotExist()
    }

    @Test
    fun `GalleryScreen 3-dot menu hides Share item when server config disables or does not support sharing`() {
        prefs.isLoggedIn = true
        prefs.userRole = UserRoles.ADMIN
        prefs.sharingConfigLoaded = true
        prefs.sharingSupportedByServer = false
        prefs.sharingFeatureEnabled = false
        val vm = GalleryViewModel(context.applicationContext as Application)

        composeTestRule.setContent {
            MaterialTheme {
                GalleryScreen(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_options_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_create_share_link").assertDoesNotExist()
    }

    @Test
    fun `GalleryScreen 3-dot menu renders Share item when server config supports and enables sharing`() {
        prefs.isLoggedIn = true
        prefs.userRole = UserRoles.USER
        prefs.sharingConfigLoaded = true
        prefs.sharingSupportedByServer = true
        prefs.sharingFeatureEnabled = true
        prefs.sharingAuthenticationRequired = true
        prefs.sharingMinRole = UserRoles.USER
        val vm = GalleryViewModel(context.applicationContext as Application)

        composeTestRule.setContent {
            MaterialTheme {
                GalleryScreen(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_options_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_create_share_link").assertIsDisplayed()
    }

    @Test
    fun `SettingsTabContent does not show Manage Active Shares section on Server tab`() {
        prefs.isLoggedIn = true
        prefs.serverUrl = "https://gallery.public.org"
        prefs.userRole = UserRoles.ADMIN
        prefs.sharingConfigLoaded = true
        prefs.sharingSupportedByServer = true
        prefs.sharingFeatureEnabled = true
        prefs.sharingAuthenticationRequired = true
        prefs.sharingMinRole = UserRoles.USER
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.setSelectedSettingsTab(2)

        composeTestRule.setContent {
            MaterialTheme {
                SettingsTabContent(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("manage_active_shares_card").assertDoesNotExist()
    }

    @Test
    fun `Share menu action shows loading indicator and disables clicks while share request is in progress`() {
        prefs.isLoggedIn = true
        prefs.serverUrl = "https://gallery.public.org"
        prefs.userRole = UserRoles.USER
        prefs.sharingConfigLoaded = true
        prefs.sharingSupportedByServer = true
        prefs.sharingFeatureEnabled = true
        prefs.sharingAuthenticationRequired = true
        prefs.sharingMinRole = UserRoles.USER
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.setShareLinkStateForTesting(ShareLinkUiState.Creating)

        composeTestRule.setContent {
            MaterialTheme {
                GalleryScreen(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("topbar_share_loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_options_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_create_share_link").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_create_share_link").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("menu_share_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `GalleryScreen shows Toast notification on share link creation success and failure`() {
        ShadowToast.reset()
        prefs.isLoggedIn = true
        prefs.serverUrl = "https://gallery.public.org"
        prefs.cookies = "session=ok"
        prefs.csrfToken = "csrf-toast"
        prefs.apiPrefix = "/pgapi"
        prefs.userRole = UserRoles.USER
        prefs.sharingConfigLoaded = true
        prefs.sharingSupportedByServer = true
        prefs.sharingFeatureEnabled = true
        prefs.sharingAuthenticationRequired = true
        prefs.sharingMinRole = UserRoles.USER

        val vm = GalleryViewModel(context.applicationContext as Application)
        var shouldFail = false
        vm.api.client = vm.api.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                val (code, body) = when {
                    req.method == "POST" && !shouldFail -> 200 to """
                        {
                          "error": null,
                          "result": {
                            "id": 77,
                            "sharingKey": "toastKey1",
                            "path": "./Trips",
                            "url": "https://gallery.public.org/share/toastKey1",
                            "expires": -1,
                            "includeSubfolders": true
                          }
                        }
                    """.trimIndent()
                    req.method == "POST" && shouldFail -> 500 to """
                        {"error": {"message": "Share generation failed on server"}}
                    """.trimIndent()
                    else -> 200 to """{"error": null, "result": []}"""
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Error")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        composeTestRule.setContent {
            MaterialTheme {
                GalleryScreen(viewModel = vm)
            }
        }
        composeTestRule.waitForIdle()

        // Success case -> emits Created Toast
        vm.createShareLink(
            target = ShareTarget.Directory("Trips"),
            includeSubfolders = true,
            validity = ShareValidity(amount = 7, unit = ShareValidityUnit.DAYS),
            password = null,
            preventDownload = true
        )
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            vm.shareLinkState.value is ShareLinkUiState.Success
        }
        composeTestRule.waitForIdle()
        assertEquals(
            context.getString(R.string.share_link_created_toast),
            ShadowToast.getTextOfLatestToast()
        )

        // Failure case -> emits Failed Toast with server error message
        shouldFail = true
        vm.createShareLink(
            target = ShareTarget.Directory("Trips"),
            includeSubfolders = true,
            validity = ShareValidity(amount = 7, unit = ShareValidityUnit.DAYS),
            password = null
        )
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            vm.shareLinkState.value is ShareLinkUiState.Error
        }
        composeTestRule.waitForIdle()
        assertEquals(
            "Share generation failed on server",
            ShadowToast.getTextOfLatestToast()
        )
    }
}
