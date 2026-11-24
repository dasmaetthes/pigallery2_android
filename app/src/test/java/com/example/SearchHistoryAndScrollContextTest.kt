package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApiAlbum
import com.example.data.ApiPerson
import com.example.data.SearchHistoryManager
import com.example.ui.ActiveTab
import com.example.ui.GalleryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchHistoryAndScrollContextTest {

    private lateinit var application: Application
    private lateinit var historyManager: SearchHistoryManager
    private lateinit var viewModel: GalleryViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        historyManager = SearchHistoryManager(application)
        runBlocking { historyManager.clearHistory() }
        viewModel = GalleryViewModel(application)
    }

    @Test
    fun `search history supports removing single query and clearing all`() = runBlocking {
        historyManager.addSearchQuery("mountains")
        historyManager.addSearchQuery("beach")
        historyManager.addSearchQuery("city")

        assertEquals(listOf("city", "beach", "mountains"), historyManager.searchHistory.first())

        historyManager.removeSearchQuery("beach")
        assertEquals(listOf("city", "mountains"), historyManager.searchHistory.first())

        historyManager.removeSearchQuery("non_existent")
        assertEquals(listOf("city", "mountains"), historyManager.searchHistory.first())

        historyManager.clearHistory()
        assertEquals(emptyList<String>(), historyManager.searchHistory.first())
    }

    @Test
    fun `scroll positions and tab filter states are preserved and cleared on logout`() {
        viewModel.saveScrollPosition("gallery:FOLDER:false:Trips", 18, 240)
        viewModel.saveScrollPosition("albums_list:", 7, 96)

        val galleryPos = viewModel.getScrollPosition("gallery:FOLDER:false:Trips")
        assertEquals(18, galleryPos.index)
        assertEquals(240, galleryPos.offset)

        val albumsPos = viewModel.getScrollPosition("albums_list:")
        assertEquals(7, albumsPos.index)
        assertEquals(96, albumsPos.offset)

        viewModel.setAlbumsFilterQuery("summer")
        viewModel.setPersonsFilterQuery("alice")
        viewModel.toggleRediscoverYear(2022)
        viewModel.setSelectedSettingsTab(2)

        assertEquals("summer", viewModel.albumsFilterQuery.value)
        assertEquals("alice", viewModel.personsFilterQuery.value)
        assertTrue(2022 in viewModel.rediscoverExpandedYears.value)
        assertEquals(2, viewModel.selectedSettingsTab.value)

        viewModel.logout()

        val clearedPos = viewModel.getScrollPosition("gallery:FOLDER:false:Trips")
        assertEquals(0, clearedPos.index)
        assertEquals(0, clearedPos.offset)
        assertEquals("", viewModel.albumsFilterQuery.value)
        assertEquals("", viewModel.personsFilterQuery.value)
        assertFalse(2022 in viewModel.rediscoverExpandedYears.value)
        assertEquals(0, viewModel.selectedSettingsTab.value)
    }

    @Test
    fun `switching tabs preserves open album and person and re-tapping active tab closes detail`() {
        val album = ApiAlbum(id = 10, name = "Alps", locked = false, searchQuery = null, cache = null)
        val person = ApiPerson(id = 5, name = "Alice")

        viewModel.setActiveTab(ActiveTab.ALBUMS)
        viewModel.selectAlbum(album)
        assertEquals(album, viewModel.selectedAlbum.value)

        // Switch away to Settings and back to Albums: open album stays preserved
        viewModel.setActiveTab(ActiveTab.SETTINGS)
        assertEquals(ActiveTab.SETTINGS, viewModel.activeTab.value)
        assertEquals(album, viewModel.selectedAlbum.value)

        viewModel.setActiveTab(ActiveTab.ALBUMS)
        assertEquals(ActiveTab.ALBUMS, viewModel.activeTab.value)
        assertEquals(album, viewModel.selectedAlbum.value)

        // Tapping Albums tab again pops back to overview list
        viewModel.setActiveTab(ActiveTab.ALBUMS)
        assertNull(viewModel.selectedAlbum.value)

        // Same behavior for Persons tab
        viewModel.setActiveTab(ActiveTab.PERSONS)
        viewModel.selectPerson(person)
        assertEquals(person, viewModel.selectedPerson.value)

        viewModel.setActiveTab(ActiveTab.GALLERY)
        viewModel.setActiveTab(ActiveTab.PERSONS)
        assertEquals(person, viewModel.selectedPerson.value)

        viewModel.setActiveTab(ActiveTab.PERSONS)
        assertNull(viewModel.selectedPerson.value)
    }
}
