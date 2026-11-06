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
}
