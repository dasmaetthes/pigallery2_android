package com.example

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApiMedia
import com.example.data.PreferencesManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.ActiveTab
import com.example.ui.CollectionCard
import com.example.ui.GalleryCardDefaults
import com.example.ui.GalleryScreen
import com.example.ui.GalleryViewModel
import com.example.ui.MediaGridCard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.getDarkColorSchemeForOption
import com.example.ui.theme.getLightColorSchemeForOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UnifiedUiConsistencyTest {

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
    fun `Albums and Persons tabs expose unified TopAppBar search icon and inline search field`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.setActiveTab(ActiveTab.ALBUMS)

        composeTestRule.setContent {
            MaterialTheme {
                GalleryScreen(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("topbar_search_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("topbar_search_field").assertIsDisplayed().performTextInput("Summer")
        composeTestRule.waitForIdle()
        assertEquals("Summer", vm.albumsFilterQuery.value)

        // First close click clears text; second closes search
        composeTestRule.onNodeWithTag("topbar_search_close_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("", vm.albumsFilterQuery.value)
        assertTrue(vm.isAlbumsSearchActive.value)

        composeTestRule.onNodeWithTag("topbar_search_close_button").performClick()
        composeTestRule.waitForIdle()
        assertFalse(vm.isAlbumsSearchActive.value)

        // Switch to Persons tab and verify identical TopAppBar search behavior
        composeTestRule.onNodeWithTag("nav_tab_persons").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("topbar_search_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("topbar_search_field").assertIsDisplayed().performTextInput("Alex")
        composeTestRule.waitForIdle()
        assertEquals("Alex", vm.personsFilterQuery.value)
    }

    @Test
    fun `3-dot menu omits Refresh and Select items, shows global entries cleanly, and is hidden on Settings with Back navigation`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.setActiveTab(ActiveTab.ALBUMS)

        composeTestRule.setContent {
            MaterialTheme {
                GalleryScreen(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_options_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_refresh").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_select_items").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_global_divider").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_about").assertIsDisplayed()

        // Navigate to Settings via ViewModel (avoiding menu 200ms delay flakiness) and verify 3-dot menu is hidden
        vm.setActiveTab(ActiveTab.SETTINGS)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_options_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("topbar_back_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Back from Settings returns to the previous non-Settings tab (ALBUMS)
        assertEquals(ActiveTab.ALBUMS, vm.activeTab.value)
    }

    @Test
    fun `toggleSelectAllMedia selects and clears all visible items and goBackFolder clears active tab search first`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val media = listOf(
            ApiMedia(id = 1, name = "a.jpg", metadata = null, parentPath = "p"),
            ApiMedia(id = 2, name = "b.jpg", metadata = null, parentPath = "p")
        )

        vm.toggleSelectAllMedia(media)
        assertTrue(vm.isSelectMode.value)
        assertEquals(2, vm.selectedMediaForShare.value.size)

        vm.toggleSelectAllMedia(media)
        assertEquals(0, vm.selectedMediaForShare.value.size)

        // Exiting select mode via back
        assertTrue(vm.goBackFolder())
        assertFalse(vm.isSelectMode.value)

        // Back on Albums tab clears search/filter before leaving tab
        vm.setActiveTab(ActiveTab.ALBUMS)
        vm.setAlbumsSearchActive(true)
        vm.setAlbumsFilterQuery("Vacation")
        assertTrue(vm.goBackFolder())
        assertFalse(vm.isAlbumsSearchActive.value)
        assertEquals("", vm.albumsFilterQuery.value)
        assertEquals(ActiveTab.ALBUMS, vm.activeTab.value)
    }

    @Test
    fun `theme color schemes populate distinct Material 3 surface container hierarchy across light, dark, and OLED modes`() {
        val colorOptions = listOf("Purple", "Blue", "Green", "Orange", "Teal", "Pink", "Grey", "White")

        colorOptions.forEach { option ->
            val light = getLightColorSchemeForOption(option)
            assertNotEquals("Light $option low vs default", light.surfaceContainerLow, light.surfaceContainer)
            assertNotEquals("Light $option default vs high", light.surfaceContainer, light.surfaceContainerHigh)
            assertNotEquals("Light $option high vs highest", light.surfaceContainerHigh, light.surfaceContainerHighest)

            val dark = getDarkColorSchemeForOption(option, isOled = false)
            assertNotEquals("Dark $option low vs default", dark.surfaceContainerLow, dark.surfaceContainer)
            assertNotEquals("Dark $option default vs high", dark.surfaceContainer, dark.surfaceContainerHigh)
            assertNotEquals("Dark $option high vs highest", dark.surfaceContainerHigh, dark.surfaceContainerHighest)
        }

        val oled = getDarkColorSchemeForOption("Blue", isOled = true)
        assertEquals(Color(0xFF000000), oled.background)
        assertEquals(Color(0xFF000000), oled.surfaceContainerLowest)
        assertNotEquals(oled.surfaceContainerLowest, oled.surfaceContainerLow)
        assertNotEquals(oled.surfaceContainerLow, oled.surfaceContainer)
        assertNotEquals(oled.surfaceContainer, oled.surfaceContainerHigh)
        assertNotEquals(oled.surfaceContainerHigh, oled.surfaceContainerHighest)
    }

    @Test
    fun `GalleryCardDefaults defines uniform Material 3 Surface elevation and shape configuration`() {
        assertTrue(GalleryCardDefaults.PreviewElevation > GalleryCardDefaults.CollectionElevation)
        assertTrue(GalleryCardDefaults.CollectionElevation > GalleryCardDefaults.MediaElevation)
        assertTrue(GalleryCardDefaults.MediaSelectedElevation > GalleryCardDefaults.MediaElevation)
        assertTrue(GalleryCardDefaults.MediaPressedElevation > GalleryCardDefaults.MediaSelectedElevation)
        assertTrue(GalleryCardDefaults.CollectionPressedElevation > GalleryCardDefaults.CollectionElevation)

        val vm = GalleryViewModel(context.applicationContext as Application)
        val media = ApiMedia(id = 42, name = "sunset.jpg", metadata = null, parentPath = "vacation")
        var albumClicked = false
        var mediaClicked = false

        lateinit var restingMediaStyle: com.example.ui.MediaCardSurfaceStyle
        lateinit var selectedMediaStyle: com.example.ui.MediaCardSurfaceStyle
        lateinit var pressedMediaStyle: com.example.ui.MediaCardSurfaceStyle
        lateinit var defaultShape: androidx.compose.ui.graphics.Shape
        lateinit var themeMediumShape: androidx.compose.ui.graphics.Shape

        composeTestRule.setContent {
            MyApplicationTheme(themeColorOption = "Blue", darkTheme = true, isOledMode = false) {
                themeMediumShape = MaterialTheme.shapes.medium
                defaultShape = GalleryCardDefaults.mediaCardShape()
                restingMediaStyle = GalleryCardDefaults.mediaCardSurfaceStyle(cornerRadius = 12.dp)
                selectedMediaStyle = GalleryCardDefaults.mediaCardSurfaceStyle(cornerRadius = 12.dp, isSelected = true)
                pressedMediaStyle = GalleryCardDefaults.mediaCardSurfaceStyle(cornerRadius = 12.dp, isPressed = true)

                androidx.compose.foundation.layout.Column {
                    CollectionCard(
                        name = "Summer 2026",
                        count = "18",
                        coverUrl = null,
                        cacheKey = "album_1",
                        cookies = "",
                        placeholderIcon = Icons.Outlined.PhotoLibrary,
                        cornerRadius = 12.dp,
                        aspectRatio = 1f,
                        onClick = { albumClicked = true },
                        modifier = Modifier.testTag("test_collection_card")
                    )
                    MediaGridCard(
                        media = media,
                        aspectRatio = 1f,
                        cornerRadiusDp = 12.dp,
                        isSelectMode = false,
                        isSelected = false,
                        cookies = "",
                        viewModel = vm,
                        onMediaClick = { mediaClicked = true },
                        onToggleSelect = {},
                        modifier = Modifier.testTag("test_media_card")
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(themeMediumShape, defaultShape)
        assertEquals(GalleryCardDefaults.MediaTonalElevation, restingMediaStyle.tonalElevation)
        assertEquals(GalleryCardDefaults.MediaElevation, restingMediaStyle.shadowElevation)
        assertTrue(selectedMediaStyle.tonalElevation > restingMediaStyle.tonalElevation)
        assertTrue(selectedMediaStyle.shadowElevation > restingMediaStyle.shadowElevation)
        assertTrue(pressedMediaStyle.shadowElevation > selectedMediaStyle.shadowElevation)

        composeTestRule.onNodeWithTag("test_collection_card").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("test_media_card").assertIsDisplayed().performClick()
        assertTrue(albumClicked)
        assertTrue(mediaClicked)
    }

    @Test
    fun `Appearance settings displays language options including Ukrainian and persists selection`() {
        val previousLocale = java.util.Locale.getDefault()
        try {
            val vm = GalleryViewModel(context.applicationContext as Application)
            vm.setActiveTab(ActiveTab.SETTINGS)
            vm.setSelectedSettingsTab(0)

            composeTestRule.setContent {
                MyApplicationTheme {
                    GalleryScreen(viewModel = vm)
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("settings_language_card").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("language_option_system").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("language_option_en").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("language_option_de").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("language_option_uk").performScrollTo().assertIsDisplayed().performClick()

            composeTestRule.waitForIdle()
            assertEquals(com.example.util.LocaleHelper.LANGUAGE_UKRAINIAN, vm.appLanguage.value)
            assertEquals(com.example.util.LocaleHelper.LANGUAGE_UKRAINIAN, prefs.appLanguage)

            val ukContext = com.example.util.LocaleHelper.wrapContext(context)
            assertEquals("Мова", ukContext.getString(R.string.language))
            assertEquals("Оформлення", ukContext.getString(R.string.settings_tab_appearance))
            assertEquals("Галерея", ukContext.getString(R.string.gallery))
        } finally {
            java.util.Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `UnifiedGlobalTopAppBar keeps consistent search placement across Gallery, Albums, People, and Rediscover`() {
        val vm = GalleryViewModel(context.applicationContext as Application)

        composeTestRule.setContent {
            MyApplicationTheme {
                GalleryScreen(viewModel = vm)
            }
        }

        // Verify unified TopAppBar and Search icon across all 4 primary screens
        listOf(
            ActiveTab.GALLERY,
            ActiveTab.ALBUMS,
            ActiveTab.PERSONS,
            ActiveTab.REDISCOVER
        ).forEach { tab ->
            vm.setActiveTab(tab)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("unified_top_app_bar").assertIsDisplayed()
            composeTestRule.onNodeWithTag("topbar_search_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("more_options_button").assertIsDisplayed()
        }

        // Clicking Search on Rediscover opens global search in Gallery
        composeTestRule.onNodeWithTag("topbar_search_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ActiveTab.GALLERY, vm.activeTab.value)
        assertTrue(vm.isSearchActive.value)
        composeTestRule.onNodeWithTag("topbar_search_field").assertIsDisplayed()
    }

    @Test
    fun `EmptyStateView renders Material 3 illustration, guidance tip, and primary and secondary actions`() {
        var primaryClicked = false
        var secondaryClicked = false

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.EmptyStateView(
                    title = "No matching items found",
                    subtitle = "No media matches your current search criteria.",
                    illustration = com.example.ui.EmptyStateIllustration.SEARCH,
                    guidanceText = "Try broader keywords or adjust filters in Advanced Search.",
                    actionLabel = "Clear search",
                    onAction = { primaryClicked = true },
                    secondaryActionLabel = "Advanced search",
                    onSecondaryAction = { secondaryClicked = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_state_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_state_illustration").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_state_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_state_subtitle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_state_guidance").assertIsDisplayed()

        composeTestRule.onNodeWithTag("empty_state_action_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("empty_state_secondary_action_button").performScrollTo().assertIsDisplayed().performClick()

        assertTrue(primaryClicked)
        assertTrue(secondaryClicked)
    }

    @Test
    @Config(qualifiers = "w840dp-h600dp")
    fun `GalleryScreen adapts to NavigationRail on wide screens and uses custom Manrope and Inter typography`() {
        val vm = GalleryViewModel(context.applicationContext as Application)

        assertEquals(com.example.ui.theme.DisplayFontFamily, com.example.ui.theme.Typography.titleLarge.fontFamily)
        assertEquals(com.example.ui.theme.DisplayFontFamily, com.example.ui.theme.Typography.headlineMedium.fontFamily)
        assertEquals(com.example.ui.theme.BodyFontFamily, com.example.ui.theme.Typography.bodyMedium.fontFamily)
        assertEquals(com.example.ui.theme.BodyFontFamily, com.example.ui.theme.Typography.labelLarge.fontFamily)

        composeTestRule.setContent {
            MyApplicationTheme {
                GalleryScreen(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("adaptive_navigation_rail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adaptive_navigation_bar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_tab_albums").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ActiveTab.ALBUMS, vm.activeTab.value)
    }

    @Test
    fun `TopAppBar displays Map button next to Sort for Album and Person detail views`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val album = com.example.data.ApiAlbum(
            id = 1,
            name = "Vacation",
            locked = false,
            searchQuery = mapOf("type" to 100, "value" to "Vacation"),
            cache = null
        )
        val person = com.example.data.ApiPerson(
            id = 2,
            name = "Alex",
            isFavourite = false,
            cache = com.example.data.ApiPersonCache(count = 1)
        )
        val sampleDir = com.example.data.ApiDirectory(
            id = 10,
            name = "Results",
            path = "",
            directories = emptyList(),
            media = listOf(
                ApiMedia(
                    id = 101,
                    name = "photo.jpg",
                    metadata = com.example.data.ApiMediaMetadata(
                        gps = com.example.data.ApiGPSData(48.1351, 11.5820)
                    ),
                    parentPath = "2025/Trip"
                )
            )
        )

        val selectedAlbumField = GalleryViewModel::class.java.getDeclaredField("_selectedAlbum")
        selectedAlbumField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val selectedAlbumFlow = selectedAlbumField.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<com.example.data.ApiAlbum?>

        val albumContentField = GalleryViewModel::class.java.getDeclaredField("_albumContentState")
        albumContentField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val albumContentFlow = albumContentField.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<com.example.ui.GalleryUiState?>

        val selectedPersonField = GalleryViewModel::class.java.getDeclaredField("_selectedPerson")
        selectedPersonField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val selectedPersonFlow = selectedPersonField.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<com.example.data.ApiPerson?>

        val personContentField = GalleryViewModel::class.java.getDeclaredField("_personContentState")
        personContentField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val personContentFlow = personContentField.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<com.example.ui.GalleryUiState?>

        composeTestRule.setContent {
            MyApplicationTheme {
                GalleryScreen(viewModel = vm)
            }
        }

        // Album detail: Map button is displayed directly to the right of Sort
        vm.setActiveTab(ActiveTab.ALBUMS)
        selectedAlbumFlow.value = album
        albumContentFlow.value = com.example.ui.GalleryUiState.Success(sampleDir)
        composeTestRule.waitForIdle()

        val albumSortBounds = composeTestRule.onNodeWithTag("topbar_sort_button").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val albumMapBounds = composeTestRule.onNodeWithTag("topbar_map_button").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Map button should be positioned to the right of Sort in Album detail", albumMapBounds.left >= albumSortBounds.right - 1f)

        // Person detail: Map button is displayed directly to the right of Sort
        vm.setActiveTab(ActiveTab.PERSONS)
        selectedPersonFlow.value = person
        personContentFlow.value = com.example.ui.GalleryUiState.Success(sampleDir)
        composeTestRule.waitForIdle()

        val personSortBounds = composeTestRule.onNodeWithTag("topbar_sort_button").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val personMapBounds = composeTestRule.onNodeWithTag("topbar_map_button").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        composeTestRule.onNodeWithTag("topbar_person_favorite_button").assertDoesNotExist()
        assertTrue("Map button should be positioned to the right of Sort in Person detail", personMapBounds.left >= personSortBounds.right - 1f)
    }

    @Test
    fun `LoginScreen renders unified Material 3 logo badge, inputs, and demo server action`() {
        prefs.isLoggedIn = false
        val vm = GalleryViewModel(context.applicationContext as Application)

        composeTestRule.setContent {
            MyApplicationTheme(themeColorOption = "Blue", darkTheme = false, isOledMode = false) {
                com.example.ui.LoginScreen(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("login_app_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("server_url_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("local_server_url_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("username_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("password_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("demo_server_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("connect_button").assertIsDisplayed()
    }

    @Test
    fun `SettingsTabContent and AboutScreen expose unified sections, buttons, and navigation`() {
        prefs.autoCheckUpdates = false
        val vm = GalleryViewModel(context.applicationContext as Application)
        var aboutOpened = false
        var aboutBackClicked = false
        val showAbout = androidx.compose.runtime.mutableStateOf(false)

        composeTestRule.setContent {
            MyApplicationTheme {
                if (showAbout.value) {
                    com.example.ui.AboutScreen(
                        viewModel = vm,
                        onBack = {
                            aboutBackClicked = true
                            showAbout.value = false
                        }
                    )
                } else {
                    com.example.ui.SettingsTabContent(
                        viewModel = vm,
                        onOpenAbout = {
                            aboutOpened = true
                            showAbout.value = true
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_tab_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_tab_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_tab_2").assertIsDisplayed()

        // Switch to Server tab and navigate to AboutScreen
        composeTestRule.onNodeWithTag("settings_tab_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_local_server_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_disconnect_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_about_card").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTrue(aboutOpened)
        composeTestRule.onNodeWithTag("about_top_app_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_hero_logo").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_check_updates_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_github_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_issues_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_back_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTrue(aboutBackClicked)
    }

    @Test
    fun `MapBrowserDialog displays unified empty state when no geotagged media exists`() {
        prefs.autoCheckUpdates = false
        val vm = GalleryViewModel(context.applicationContext as Application)
        var dismissed = false

        composeTestRule.setContent {
            MyApplicationTheme(themeColorOption = "Teal", darkTheme = false, isOledMode = false) {
                com.example.ui.MapBrowserDialog(
                    mediaList = listOf(
                        ApiMedia(id = 1, name = "no_gps.jpg", metadata = null, parentPath = "p")
                    ),
                    viewModel = vm,
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("map_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_state_action_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    @Config(qualifiers = "w840dp-h480dp")
    fun `MapBrowserDialog adapts side by side in wide landscape and renders unified floating controls, preview card, and timeline`() {
        prefs.autoCheckUpdates = false
        val vm = GalleryViewModel(context.applicationContext as Application)
        var dismissed = false
        val geoMedia = listOf(
            ApiMedia(
                id = 10,
                name = "alps.jpg",
                metadata = com.example.data.ApiMediaMetadata(
                    gps = com.example.data.ApiGPSData(47.2692, 11.4041)
                ),
                parentPath = "trip"
            ),
            ApiMedia(
                id = 11,
                name = "lake.jpg",
                metadata = com.example.data.ApiMediaMetadata(
                    gps = com.example.data.ApiGPSData(47.3769, 8.5417)
                ),
                parentPath = "trip"
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(themeColorOption = "Blue", darkTheme = true, isOledMode = false) {
                com.example.ui.MapBrowserDialog(
                    mediaList = geoMedia,
                    viewModel = vm,
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("map_close_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_travel_track_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_menu_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_zoom_in_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_zoom_out_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_center_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_preview_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_play_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_timeline_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_timeline_item_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_timeline_item_1").assertIsDisplayed()

        // Side-by-side layout in landscape: preview card is positioned to the right of the map pane and its controls
        val zoomInBounds = composeTestRule.onNodeWithTag("map_zoom_in_button").fetchSemanticsNode().boundsInRoot
        val layersBounds = composeTestRule.onNodeWithTag("map_menu_button").fetchSemanticsNode().boundsInRoot
        val previewBounds = composeTestRule.onNodeWithTag("map_preview_card").fetchSemanticsNode().boundsInRoot
        assertTrue("Preview card should be side-by-side to the right of the map in landscape", previewBounds.left >= zoomInBounds.right - 1f)
        assertTrue("Layer button should sit on the map pane to the left of the preview card", previewBounds.left >= layersBounds.right - 1f)

        // Controls remain permanently visible after advancing time past 5 seconds and during playback
        composeTestRule.mainClock.advanceTimeBy(5000L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("map_close_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_travel_track_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_menu_button").assertIsDisplayed()

        // Toggle travel track directly in 1 tap and verify accessibility label updates
        composeTestRule.onNodeWithTag("map_travel_track_toggle").performClick()
        composeTestRule.waitForIdle()
        val toggledSemantics = composeTestRule.onNodeWithTag("map_travel_track_toggle").fetchSemanticsNode()
        val contentDesc = toggledSemantics.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { emptyList() }
        assertTrue("Route toggle should now offer showing travel track", contentDesc.contains("Show Travel Track"))

        // Open Map layer selector menu and select a different layer
        composeTestRule.onNodeWithTag("map_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("map_provider_base").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_provider_dark_matter").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("map_menu_button").assertIsDisplayed()

        // Close button remains clickable
        composeTestRule.onNodeWithTag("map_close_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }
}
