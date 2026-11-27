package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApiMedia
import com.example.data.PreferencesManager
import com.example.data.search.DateSearchQueryHelper
import com.example.data.search.RangeSearch
import com.example.data.search.SearchQueryParser
import com.example.data.search.SearchQueryTypes
import com.example.ui.GalleryScreen
import com.example.ui.GalleryViewMode
import com.example.ui.GalleryViewModel
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DateViewOptimizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = PreferencesManager(context)
        prefs.clear()
        prefs.isLoggedIn = true
        prefs.serverUrl = ""
    }

    @Test
    fun `DateSearchQueryHelper builds timezone-padded month and year DATE queries with optional directory scope`() {
        // Leap year February 2024 has 29 days; non-leap February 2025 has 28 days
        val (feb2024Start, feb2024End) = DateSearchQueryHelper.monthUtcBounds(2024, 2)
        val (feb2025Start, feb2025End) = DateSearchQueryHelper.monthUtcBounds(2025, 2)
        assertEquals(29L * 24 * 60 * 60 * 1000L - 1L, feb2024End - feb2024Start)
        assertEquals(28L * 24 * 60 * 60 * 1000L - 1L, feb2025End - feb2025Start)

        // Plain month query (no rootPath) -> type 10 DATE RangeSearch padded by ±14h
        val monthQuery = DateSearchQueryHelper.buildMonthQuery(2025, 7)
        assertEquals(SearchQueryTypes.DATE.value, monthQuery["type"])
        val (julyStart, julyEnd) = DateSearchQueryHelper.monthUtcBounds(2025, 7)
        assertEquals(julyStart - DateSearchQueryHelper.TIMEZONE_PADDING_MS, monthQuery["min"])
        assertEquals(julyEnd + DateSearchQueryHelper.TIMEZONE_PADDING_MS, monthQuery["max"])

        // Scoped year query with defaultRootPath -> AND(DATE, DIRECTORY)
        val scopedYearQuery = DateSearchQueryHelper.buildYearQuery(2025, rootPath = "/Family/Trips/")
        assertEquals(SearchQueryTypes.AND.value, scopedYearQuery["type"])
        val list = scopedYearQuery["list"] as? List<*>
        assertNotNull(list)
        assertEquals(2, list!!.size)
        val datePart = list[0] as Map<*, *>
        val dirPart = list[1] as Map<*, *>
        assertEquals(SearchQueryTypes.DATE.value, datePart["type"])
        assertEquals(SearchQueryTypes.DIRECTORY.value, dirPart["type"])
        assertEquals("Family/Trips", dirPart["value"])

        // matchesRootPath enforces exact directory prefix boundary
        assertTrue(DateSearchQueryHelper.matchesRootPath(ApiMedia(1, "a.jpg", null, "Family/Trips/2025"), "Family/Trips"))
        assertFalse(DateSearchQueryHelper.matchesRootPath(ApiMedia(2, "b.jpg", null, "Family/TripsExtra"), "Family/Trips"))
    }

    @Test
    fun `DateSearchQueryHelper date paths and search text round-trip cleanly with SearchQueryParser`() {
        val yearPath = DateSearchQueryHelper.buildDatePath(2025)
        val monthPath = DateSearchQueryHelper.buildDatePath(2025, 7)
        assertEquals("year:2025", yearPath)
        assertEquals("year:2025/month:7", monthPath)
        assertEquals(2025, DateSearchQueryHelper.parseYearFromPath(monthPath))
        assertEquals(7, DateSearchQueryHelper.parseMonthFromPath(monthPath))
        assertNull(DateSearchQueryHelper.parseMonthFromPath(yearPath))

        val searchText = DateSearchQueryHelper.buildSearchTextForDate(2025, 7)
        assertEquals("date:2025-07", searchText)
        val parsed = SearchQueryParser.parse(searchText) as? RangeSearch
        assertNotNull(parsed)
        assertEquals(SearchQueryTypes.DATE, parsed!!.type)
        assertTrue((parsed.min ?: 0L) > 0L)
        assertTrue((parsed.max ?: 0L) > (parsed.min ?: 0L))
    }

    @Test
    fun `DateQuickJumpBar jumps directly to year and month and opens period search pre-filled with date parameter`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.setGalleryViewMode(GalleryViewMode.DATE)

        composeTestRule.setContent {
            MyApplicationTheme {
                GalleryScreen(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("date_quick_jump_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_quick_year_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_quick_month_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_search_in_period_chip").assertDoesNotExist()

        // Jump directly to July 2025 via ViewModel
        vm.jumpToDate(2025, 7)
        composeTestRule.waitForIdle()
        assertEquals(listOf("", "year:2025", "year:2025/month:7"), vm.pathHistory.value)
        assertEquals(GalleryViewMode.DATE, vm.galleryViewMode.value)

        // Search in period chip appears and pre-fills `date:2025-07 ` in the unified search bar
        composeTestRule.onNodeWithTag("date_search_in_period_chip").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(vm.isSearchActive.value)
        assertEquals("date:2025-07 ", vm.searchQuery.value)
    }
}
