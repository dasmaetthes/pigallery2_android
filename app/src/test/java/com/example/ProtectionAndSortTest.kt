package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProtectionAndSortTest {

    private lateinit var prefs: PreferencesManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = PreferencesManager(context)
        prefs.clear()
    }

    @Test
    fun `test protected folder isolation - parent of locked folder is never locked`() {
        // User locks "Holiday/Secret"
        prefs.toggleProtectedFolder("Holiday/Secret")

        // "Holiday/Secret" must be protected
        assertTrue(prefs.isFolderProtected("Holiday/Secret"))
        // Subfolders of "Holiday/Secret" must be protected
        assertTrue(prefs.isFolderProtected("Holiday/Secret/Day1"))
        assertTrue(prefs.isFolderProtected("Holiday/Secret/Day1/Morning"))

        // CRITICAL: Parent folder "Holiday" MUST NOT be locked!
        assertFalse(prefs.isFolderProtected("Holiday"))

        // Sibling folder "Holiday/Public" MUST NOT be locked!
        assertFalse(prefs.isFolderProtected("Holiday/Public"))

        // Root must not be locked
        assertFalse(prefs.isFolderProtected(""))
        assertFalse(prefs.isFolderProtected("."))
    }

    @Test
    fun `test root folder sorting does not leak to subfolders`() {
        val defaultFSort = "name"
        val defaultFDir = "asc"

        // Set global default sort
        prefs.setFolderSortBy("global", "name")
        prefs.setFolderSortDirection("global", "asc")

        // User sets "Only root folder" to count desc
        prefs.setFolderSortBy("root", "count")
        prefs.setFolderSortDirection("root", "desc")

        // Root must have specific sort
        assertTrue(prefs.hasFolderSort("root"))
        assertEquals("count", prefs.getFolderSortBy("root", defaultFSort))
        assertEquals("desc", prefs.getFolderSortDirection("root", defaultFDir))

        // Subfolder "Vacation" has NO specific sort
        assertFalse(prefs.hasFolderSort("Vacation"))
        // Subfolder must NOT get "count", it must fallback to global "name"
        val subfolderSort = if (prefs.hasFolderSort("Vacation")) {
            prefs.getFolderSortBy("Vacation", prefs.getFolderSortBy("global", defaultFSort))
        } else {
            prefs.getFolderSortBy("global", defaultFSort)
        }
        assertEquals("name", subfolderSort)
    }

    @Test
    fun `test subfolder specific sort does not leak to siblings or sub-subfolders`() {
        prefs.setFolderSortBy("global", "name")
        prefs.setFolderSortBy("Vacation", "date")

        assertTrue(prefs.hasFolderSort("Vacation"))
        assertFalse(prefs.hasFolderSort("Vacation/Beach"))
        assertFalse(prefs.hasFolderSort("Work"))
    }

    @Test
    fun `test removing folder sort reverts to global`() {
        prefs.setFolderSortBy("global", "name")
        prefs.setFolderSortBy("Vacation", "random")
        assertTrue(prefs.hasFolderSort("Vacation"))

        prefs.removeFolderSort("Vacation")
        assertFalse(prefs.hasFolderSort("Vacation"))
    }

    @Test
    fun `test clearSession preserves settings and connection info while clearing auth session`() {
        prefs.serverUrl = "https://gallery.example.com"
        prefs.localServerUrl = "http://192.168.1.50:3000"
        prefs.allowInsecureSsl = true
        prefs.username = "alice"
        prefs.password = "secret"
        prefs.cookies = "pigallery2-session=abc"
        prefs.csrfToken = "csrf-123"
        prefs.isLoggedIn = true
        prefs.userRole = 3
        prefs.albumWriteMinRole = 3
        prefs.albumFeatureEnabled = true
        prefs.userCanCreateAlbumsOverride = 1

        prefs.themeColor = "Blue"
        prefs.themeMode = "Dark"
        prefs.itemsPerRow = 4
        prefs.itemsPerRowPortrait = 4
        prefs.itemsPerRowLandscape = 7
        prefs.cornerRadius = 12
        prefs.spacing = 8
        prefs.aspectRatio = 1.5f
        prefs.showDirectoryItemCount = false
        prefs.galleryViewMode = "DATE"
        prefs.defaultRootPath = "Photos/2024"
        prefs.thumbnailPathSuffix = "480"
        prefs.preloadPathSuffix = "1080"
        prefs.videoPathSuffix = "720"
        prefs.slideshowDuration = 5
        prefs.rediscoverDays = 3
        prefs.dismissGestureEnabled = false
        prefs.showMetadataGestureEnabled = false
        prefs.biometricLockEnabled = true
        prefs.hideProtectedItems = true
        prefs.autoCheckUpdates = false
        prefs.maxBrightnessEnabled = true
        prefs.toggleProtectedFolder("Private")
        prefs.setFolderSortBy("global", "date")

        prefs.clearSession()

        // Session/auth data is cleared
        assertFalse(prefs.isLoggedIn)
        assertEquals("", prefs.cookies)
        assertEquals("", prefs.csrfToken)
        assertEquals("", prefs.password)
        assertEquals(-1, prefs.userRole)
        assertEquals(-1, prefs.userCanCreateAlbumsOverride)

        // Connection details and all user settings are preserved
        assertEquals("https://gallery.example.com", prefs.serverUrl)
        assertEquals("http://192.168.1.50:3000", prefs.localServerUrl)
        assertTrue(prefs.allowInsecureSsl)
        assertEquals("alice", prefs.username)
        assertEquals("Blue", prefs.themeColor)
        assertEquals("Dark", prefs.themeMode)
        assertEquals(4, prefs.itemsPerRow)
        assertEquals(4, prefs.itemsPerRowPortrait)
        assertEquals(7, prefs.itemsPerRowLandscape)
        assertEquals(12, prefs.cornerRadius)
        assertEquals(8, prefs.spacing)
        assertEquals(1.5f, prefs.aspectRatio, 0.001f)
        assertFalse(prefs.showDirectoryItemCount)
        assertEquals("DATE", prefs.galleryViewMode)
        assertEquals("Photos/2024", prefs.defaultRootPath)
        assertEquals("480", prefs.thumbnailPathSuffix)
        assertEquals("1080", prefs.preloadPathSuffix)
        assertEquals("720", prefs.videoPathSuffix)
        assertEquals(5, prefs.slideshowDuration)
        assertEquals(3, prefs.rediscoverDays)
        assertFalse(prefs.dismissGestureEnabled)
        assertFalse(prefs.showMetadataGestureEnabled)
        assertTrue(prefs.biometricLockEnabled)
        assertTrue(prefs.hideProtectedItems)
        assertFalse(prefs.autoCheckUpdates)
        assertTrue(prefs.maxBrightnessEnabled)
        assertTrue(prefs.isFolderProtected("Private"))
        assertEquals("date", prefs.getFolderSortBy("global", "name"))
    }
}
