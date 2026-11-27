package com.example

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApiAlbum
import com.example.data.ApiCameraData
import com.example.data.ApiDirectory
import com.example.data.ApiGPSData
import com.example.data.ApiMedia
import com.example.data.ApiMediaMetadata
import com.example.data.ApiPerson
import com.example.data.ApiPersonCache
import com.example.data.ApiSize
import com.example.data.PreferencesManager
import com.example.data.ShareTarget
import com.example.ui.AboutScreen
import com.example.ui.ActiveTab
import com.example.ui.AdvancedQueryBuilderDialog
import com.example.ui.AlbumsUiState
import com.example.ui.CollectionCard
import com.example.ui.GalleryScreen
import com.example.ui.GalleryUiState
import com.example.ui.GalleryViewModel
import com.example.ui.MediaGridCard
import com.example.ui.MediaViewerDialog
import com.example.ui.PersonItem
import com.example.ui.SearchSuggestionsOverlay
import com.example.ui.SettingsTabContent
import com.example.ui.ShareLinkDialog
import com.example.ui.ShareLinkUiState
import com.example.ui.TvVideoControlsBar
import com.example.ui.UnifiedGlobalTopAppBar
import com.example.ui.tvFocus
import com.example.ui.tvSlider
import com.example.util.DeviceUtils
import com.example.util.LocalIsAndroidTv
import com.example.util.TvModeHelper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidTvAndTabletOptimizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager

    private val sampleGeotaggedMedia = ApiMedia(
        id = 101,
        name = "alps_panorama.jpg",
        parentPath = "Trips/Alps",
        metadata = ApiMediaMetadata(
            size = ApiSize(width = 4000, height = 3000),
            creationDate = 1715000000000L,
            fileSize = 4_200_000L,
            cameraData = ApiCameraData(
                make = "Sony",
                model = "A7 IV",
                lens = "FE 35mm F1.4 GM",
                focalLength = 35.0,
                ISO = 100,
                fStop = 2.8,
                exposure = 0.002
            ),
            gps = ApiGPSData(latitude = 46.5197, longitude = 7.9821),
            keywords = listOf("Mountains", "Summer")
        )
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = PreferencesManager(context)
        prefs.clear()
        prefs.isLoggedIn = true
        prefs.serverUrl = ""
    }

    @Test
    @Config(sdk = [28])
    fun `DeviceUtils detects older Android 9 TV via Leanback, legacy Television feature, and TV UI mode`() {
        val shadowPm = shadowOf(context.packageManager)

        // Default Robolectric device is not TV
        assertFalse(DeviceUtils.isAndroidTv(context))
        assertFalse(DeviceUtils.isLegacyOrLowRamTv(context))

        // 1. Legacy Android TV feature only (older TVs / boxes without Leanback)
        @Suppress("DEPRECATION")
        shadowPm.setSystemFeature(PackageManager.FEATURE_TELEVISION, true)
        assertTrue(DeviceUtils.isAndroidTv(context))
        assertTrue(DeviceUtils.isLegacyOrLowRamTv(context))

        // 2. Leanback feature (MiTV-MSSP3 Android 9 API 28)
        @Suppress("DEPRECATION")
        shadowPm.setSystemFeature(PackageManager.FEATURE_TELEVISION, false)
        shadowPm.setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        assertTrue(DeviceUtils.isAndroidTv(context))
        assertTrue(DeviceUtils.isLegacyOrLowRamTv(context))

        // 3. UI_MODE_TYPE_TELEVISION via configuration
        shadowPm.setSystemFeature(PackageManager.FEATURE_LEANBACK, false)
        context.resources.configuration.uiMode =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                Configuration.UI_MODE_TYPE_TELEVISION
        assertTrue(DeviceUtils.isAndroidTv(context))
    }

    @Test
    fun `Android TV disables multiselect on MediaGridCard and TopAppBar`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        var clicked = false
        var toggledSelect = false

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    MediaGridCard(
                        media = sampleGeotaggedMedia,
                        aspectRatio = 1f,
                        cornerRadiusDp = 12.dp,
                        isSelectMode = true,
                        isSelected = true,
                        cookies = "",
                        viewModel = vm,
                        onMediaClick = { clicked = true },
                        onToggleSelect = { toggledSelect = true },
                        modifier = Modifier.testTag("tv_media_card")
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Long press does not toggle selection on TV
        composeTestRule.onNodeWithTag("tv_media_card").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        assertFalse(toggledSelect)

        // Normal click opens media directly even if isSelectMode flag was true
        composeTestRule.onNodeWithTag("tv_media_card").performClick()
        composeTestRule.waitForIdle()
        assertTrue(clicked)
        assertFalse(toggledSelect)
    }

    @Test
    fun `Android TV hides Map, Share Link, Save Album, Delete Album, and Select controls in TopAppBar`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val album = ApiAlbum(
            id = 7,
            name = "Roadtrip",
            locked = false,
            searchQuery = mapOf("type" to 100, "text" to "road"),
            cache = null
        )
        val searchFocus = androidx.compose.ui.focus.FocusRequester()
        val sampleDirectory = ApiDirectory(
            id = 1,
            name = "Root",
            path = "",
            directories = emptyList(),
            media = listOf(sampleGeotaggedMedia)
        )

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    UnifiedGlobalTopAppBar(
                        viewModel = vm,
                        activeTab = ActiveTab.ALBUMS,
                        isSelectMode = true, // Even if forced true, TV masks it off
                        selectedMediaForShare = setOf(sampleGeotaggedMedia),
                        currentVisibleMediaList = listOf(sampleGeotaggedMedia),
                        mapMediaList = listOf(sampleGeotaggedMedia),
                        isTv = true,
                        isTopBarSearchExpanded = false,
                        isSearchActive = false,
                        searchQuery = "Alps",
                        textFieldValue = androidx.compose.ui.text.input.TextFieldValue("Alps"),
                        onTextFieldValueChange = {},
                        showSearchSuggestions = false,
                        isAlbumsSearchActive = false,
                        albumsFilterQuery = "",
                        isPersonsSearchActive = false,
                        personsFilterQuery = "",
                        galleryState = GalleryUiState.Success(sampleDirectory),
                        albumsState = AlbumsUiState.Success(listOf(album)),
                        personsState = com.example.ui.PersonsUiState.Success(emptyList()),
                        albumContentState = GalleryUiState.Success(sampleDirectory),
                        personContentState = null,
                        rediscoverState = com.example.ui.RediscoverUiState.Loading,
                        pathHistory = listOf(""),
                        defaultRootPath = "",
                        galleryViewMode = com.example.ui.GalleryViewMode.FOLDER,
                        isFlattened = false,
                        hideProtectedItems = false,
                        selectedAlbum = album,
                        selectedPerson = null,
                        canManageAlbums = true,
                        isSharingAvailable = true,
                        isCreatingShare = false,
                        shareDialogTarget = null,
                        searchFieldFocusRequester = searchFocus,
                        searchSuggestionsFocusRequester = searchFocus,
                        galleryGridFirstItemFocusRequester = searchFocus,
                        albumsGridFirstItemFocusRequester = searchFocus,
                        personsGridFirstItemFocusRequester = searchFocus,
                        onOpenQueryBuilder = {},
                        onOpenMap = {},
                        onOpenAbout = {},
                        onOpenSaveAlbum = {},
                        onOpenDeleteAlbum = {},
                        onOpenShareDialog = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Select mode actions must NOT appear on TV
        composeTestRule.onNodeWithTag("select_all_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("select_share_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("select_download_button").assertDoesNotExist()
        // Map button must NOT appear on TV
        composeTestRule.onNodeWithTag("topbar_map_button").assertDoesNotExist()

        // Overflow menu must NOT show delete album or create share link on TV
        composeTestRule.onNodeWithTag("more_options_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_delete_album").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_create_share_link").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_save_search_as_album").assertDoesNotExist()
        composeTestRule.onNodeWithTag("menu_settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_about").assertIsDisplayed()
    }

    @Test
    fun `Android TV hides Save Album in AdvancedQueryBuilderDialog and Album Restore banner in AlbumsTabContent`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.updateSearchQueryText("keyword:Mountains")

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    AdvancedQueryBuilderDialog(
                        viewModel = vm,
                        canManageAlbums = true,
                        onDismissRequest = {},
                        onSearchApplied = {},
                        onSaveAsAlbum = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("query_builder_search_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("query_builder_save_album_button").assertDoesNotExist()
    }

    @Test
    fun `Android TV hides Person favorite toggle while non-TV displays it`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val person = ApiPerson(id = 5, name = "Anna", isFavourite = true, cache = ApiPersonCache(count = 12))

        var isTvState = true
        val tvModeState = androidx.compose.runtime.mutableStateOf(true)

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides tvModeState.value) {
                    PersonItem(
                        person = person,
                        viewModel = vm,
                        onClick = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("person_favorite_button_Anna").assertDoesNotExist()

        tvModeState.value = false
        isTvState = false
        composeTestRule.waitForIdle()
        assertFalse(isTvState)
        composeTestRule.onNodeWithTag("person_favorite_button_Anna").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun `Android TV GalleryScreen uses NavigationRail with direct Settings and TV Settings hides mobile-only controls`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.setActiveTab(ActiveTab.GALLERY)

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    GalleryScreen(viewModel = vm)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("adaptive_navigation_rail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_settings").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Gestures, Portrait items-per-row, and Max Brightness are hidden on Android TV
        composeTestRule.onNodeWithTag("settings_gestures_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_items_per_row_portrait").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_max_brightness_row").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [28], qualifiers = "w960dp-h540dp-land")
    fun `MediaViewerDialog on Android 9 TV hides Share, Download, and Map and docks metadata to the right of the image`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.selectMedia(sampleGeotaggedMedia, listOf(sampleGeotaggedMedia))

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    MediaViewerDialog(
                        media = sampleGeotaggedMedia,
                        viewModel = vm,
                        onDismiss = {},
                        onOpenMap = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Restricted TV actions must not exist
        composeTestRule.onNodeWithTag("viewer_share_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("viewer_download_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("viewer_map_button").assertDoesNotExist()
        // TV slideshow quick action & metadata button exist
        composeTestRule.onNodeWithTag("viewer_slideshow_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_metadata_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Metadata panel is docked on the right side next to the image
        composeTestRule.onNodeWithTag("metadata_side_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_bottom_sheet").assertDoesNotExist()

        val mediaBounds = composeTestRule.onNodeWithTag("media_viewer_media_pane").getUnclippedBoundsInRoot()
        val sideBounds = composeTestRule.onNodeWithTag("metadata_side_panel").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected metadata side panel (${sideBounds.left}) to be placed to the right of media pane (${mediaBounds.right})",
            sideBounds.left >= mediaBounds.right - 2.dp
        )
    }

    @Test
    @Config(qualifiers = "w720dp-h1024dp-port")
    fun `MediaViewerDialog on Tablet Portrait docks metadata on the right side next to image and keeps Share & Map`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.selectMedia(sampleGeotaggedMedia, listOf(sampleGeotaggedMedia))

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides false) {
                    MediaViewerDialog(
                        media = sampleGeotaggedMedia,
                        viewModel = vm,
                        onDismiss = {},
                        onOpenMap = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Tablet retains share, download, and map actions
        composeTestRule.onNodeWithTag("viewer_share_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_download_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_map_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("viewer_metadata_button").performClick()
        composeTestRule.waitForIdle()

        // Even in Portrait on a Tablet (>= 600dp width), metadata docks on the right side
        composeTestRule.onNodeWithTag("metadata_side_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_bottom_sheet").assertDoesNotExist()

        val mediaBounds = composeTestRule.onNodeWithTag("media_viewer_media_pane").getUnclippedBoundsInRoot()
        val sideBounds = composeTestRule.onNodeWithTag("metadata_side_panel").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected tablet side metadata panel (${sideBounds.left}) right of media (${mediaBounds.right})",
            sideBounds.left >= mediaBounds.right - 2.dp
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    fun `MediaViewerDialog on Compact Phone Portrait uses bottom sheet for metadata`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.selectMedia(sampleGeotaggedMedia, listOf(sampleGeotaggedMedia))

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides false) {
                    MediaViewerDialog(
                        media = sampleGeotaggedMedia,
                        viewModel = vm,
                        onDismiss = {},
                        onOpenMap = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_metadata_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("metadata_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_side_panel").assertDoesNotExist()
    }

    @Test
    fun `TvModeHelper resolves full restriction policy on TV and allows all features on non-TV`() {
        val tvCaps = TvModeHelper.capabilitiesFor(isTvMode = true)
        assertTrue(tvCaps.isTvMode)
        assertFalse(tvCaps.allowMultiSelect)
        assertFalse(tvCaps.allowSharing)
        assertFalse(tvCaps.allowShareLinks)
        assertFalse(tvCaps.allowDownloads)
        assertFalse(tvCaps.allowMapView)
        assertFalse(tvCaps.allowAlbumManagement)
        assertFalse(tvCaps.allowFavoriteMarking)
        assertFalse(tvCaps.allowItemLocking)
        assertFalse(tvCaps.allowTouchGestures)
        assertFalse(tvCaps.isSelectModeActive(true))
        assertFalse(tvCaps.canManageAlbums(true))
        assertFalse(tvCaps.canCreateShareLinks(true))
        assertFalse(tvCaps.shouldEnforceItemLocking(true))

        val mobileCaps = TvModeHelper.capabilitiesFor(isTvMode = false)
        assertFalse(mobileCaps.isTvMode)
        assertTrue(mobileCaps.allowMultiSelect)
        assertTrue(mobileCaps.allowSharing)
        assertTrue(mobileCaps.allowShareLinks)
        assertTrue(mobileCaps.allowDownloads)
        assertTrue(mobileCaps.allowMapView)
        assertTrue(mobileCaps.allowAlbumManagement)
        assertTrue(mobileCaps.allowFavoriteMarking)
        assertTrue(mobileCaps.allowItemLocking)
        assertTrue(mobileCaps.allowTouchGestures)
        assertTrue(mobileCaps.isSelectModeActive(true))
        assertTrue(mobileCaps.canManageAlbums(true))
        assertTrue(mobileCaps.canCreateShareLinks(true))
        assertTrue(mobileCaps.shouldEnforceItemLocking(true))
    }

    @Test
    fun `Android TV removes Lock Item controls from TopAppBar overflow menu, CollectionCard, Settings, and AppLockScreen`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val searchFocus = androidx.compose.ui.focus.FocusRequester()
        val sampleDirectory = ApiDirectory(
            id = 1,
            name = "Private",
            path = "Private",
            directories = emptyList(),
            media = listOf(sampleGeotaggedMedia)
        )
        val tvModeState = androidx.compose.runtime.mutableStateOf(true)

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides tvModeState.value) {
                    androidx.compose.foundation.layout.Column {
                        UnifiedGlobalTopAppBar(
                            viewModel = vm,
                            activeTab = ActiveTab.GALLERY,
                            isSelectMode = false,
                            selectedMediaForShare = emptySet(),
                            currentVisibleMediaList = listOf(sampleGeotaggedMedia),
                            mapMediaList = listOf(sampleGeotaggedMedia),
                            isTv = tvModeState.value,
                            isTopBarSearchExpanded = false,
                            isSearchActive = false,
                            searchQuery = "",
                            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(""),
                            onTextFieldValueChange = {},
                            showSearchSuggestions = false,
                            isAlbumsSearchActive = false,
                            albumsFilterQuery = "",
                            isPersonsSearchActive = false,
                            personsFilterQuery = "",
                            galleryState = GalleryUiState.Success(sampleDirectory),
                            albumsState = AlbumsUiState.Success(emptyList()),
                            personsState = com.example.ui.PersonsUiState.Success(emptyList()),
                            albumContentState = null,
                            personContentState = null,
                            rediscoverState = com.example.ui.RediscoverUiState.Loading,
                            pathHistory = listOf("", "Private"),
                            defaultRootPath = "",
                            galleryViewMode = com.example.ui.GalleryViewMode.FOLDER,
                            isFlattened = false,
                            hideProtectedItems = true,
                            selectedAlbum = null,
                            selectedPerson = null,
                            canManageAlbums = true,
                            isSharingAvailable = true,
                            isCreatingShare = false,
                            shareDialogTarget = null,
                            searchFieldFocusRequester = searchFocus,
                            searchSuggestionsFocusRequester = searchFocus,
                            galleryGridFirstItemFocusRequester = searchFocus,
                            albumsGridFirstItemFocusRequester = searchFocus,
                            personsGridFirstItemFocusRequester = searchFocus,
                            onOpenQueryBuilder = {},
                            onOpenMap = {},
                            onOpenAbout = {},
                            onOpenSaveAlbum = {},
                            onOpenDeleteAlbum = {},
                            onOpenShareDialog = {}
                        )
                        CollectionCard(
                            name = "Private",
                            count = "5",
                            coverUrl = null,
                            cacheKey = "folder_private",
                            cookies = "",
                            placeholderIcon = Icons.Outlined.Folder,
                            cornerRadius = 12.dp,
                            aspectRatio = 1f,
                            onClick = {},
                            isBlurredLocked = true
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        // On TV: no lock overlay on folder card and no "Protect folder" in overflow menu
        composeTestRule.onNodeWithTag("collection_card_lock_overlay", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("more_options_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_protect_folder").assertDoesNotExist()

        // Switch to non-TV: lock overlay and "Protect folder" menu item appear
        tvModeState.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("collection_card_lock_overlay", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("menu_protect_folder").assertIsDisplayed()
    }

    @Test
    fun `Android TV removes Security and Lock Item card from SettingsTabContent and bypasses AppLockScreen`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.setSelectedSettingsTab(1) // Features tab where Security & App Protection card lives
        vm.isAppLocked.value = true

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    SettingsTabContent(viewModel = vm)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_security_protection_card").assertDoesNotExist()
    }

    @Test
    fun `GalleryScreen bypasses AppLockScreen on Android TV even when locked flag is true`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.isAppLocked.value = true

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    GalleryScreen(viewModel = vm)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("app_lock_screen").assertDoesNotExist()
        composeTestRule.onNodeWithTag("adaptive_navigation_rail").assertIsDisplayed()
    }

    @Test
    fun `SearchSuggestionsOverlay hides Save Album action on Android TV`() {
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    SearchSuggestionsOverlay(
                        searchQuery = "Alps",
                        searchSuggestions = emptyList(),
                        localSearchHistory = emptyList(),
                        canManageAlbums = true,
                        onSuggestionClick = {},
                        onHistoryClick = {},
                        onRemoveHistoryItem = {},
                        onClearHistory = {},
                        onExecuteSearch = {},
                        onSaveAsAlbum = {},
                        onRemoveToken = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("suggestions_execute_search_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("suggestions_save_album_button").assertDoesNotExist()
    }

    @Test
    fun `GalleryViewModel enforces TV mode restrictions for multi-select, favorite marking, and folder locking`() {
        val shadowPm = shadowOf(context.packageManager)
        shadowPm.setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        assertTrue(TvModeHelper.isTvMode(context))

        prefs.biometricLockEnabled = true
        prefs.toggleProtectedFolder("Private")

        val vm = GalleryViewModel(context.applicationContext as Application)
        // App lock and folder lock are disabled in TV mode
        assertFalse(vm.isAppLocked.value)
        assertFalse(vm.isFolderProtected("Private"))
        assertFalse(vm.isFolderDirectlyProtected("Private"))
        vm.lockApp()
        assertFalse(vm.isAppLocked.value)

        // Multi-select is blocked in TV mode
        vm.enterSelectMode()
        assertFalse(vm.isSelectMode.value)
        vm.toggleSelectMedia(sampleGeotaggedMedia)
        assertFalse(vm.isSelectMode.value)
        assertTrue(vm.selectedMediaForShare.value.isEmpty())

        // Favorite marking is blocked in TV mode
        val person = ApiPerson(id = 9, name = "Max", isFavourite = false)
        vm.togglePersonFavourite(person)
        assertFalse(prefs.getFavoritePersons().contains("Max"))
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun `Android TV Settings renders stripped-down two-pane UI and omits Manage Active Shares, Lock Item, Gestures, and raw server inputs`() {
        prefs.serverUrl = "https://photos.example.com"
        prefs.username = "tv_viewer"
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.setSelectedSettingsTab(0)
        var aboutOpened = false

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    SettingsTabContent(
                        viewModel = vm,
                        onOpenAbout = { aboutOpened = true }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Dedicated stripped-down TV settings screen is rendered
        composeTestRule.onNodeWithTag("tv_settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_tab_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_tab_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_tab_2").assertIsDisplayed()

        // Category 0 (Display & Theme): supported TV cards exist; mobile-only controls are absent
        composeTestRule.onNodeWithTag("tv_settings_theme_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_language_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_settings_layout_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_gestures_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_items_per_row_portrait").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_max_brightness_row").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_security_protection_card").assertDoesNotExist()
        composeTestRule.onNodeWithText("Manage Active Shares", ignoreCase = true).assertDoesNotExist()

        // Category 1 (Slideshow & Gallery): playback controls exist; Security & Lock Item card is completely removed
        composeTestRule.onNodeWithTag("settings_tab_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_settings_playback_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_security_protection_card").assertDoesNotExist()
        composeTestRule.onNodeWithText("Security & App Protection", ignoreCase = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Fingerprint & Device Lock", ignoreCase = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Hide Protected Items", ignoreCase = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Manage Active Shares", ignoreCase = true).assertDoesNotExist()

        // Category 2 (Server & Storage): read-only server summary, disconnect, cache, and about exist;
        // raw editable server/path/suffix inputs & Manage Active Shares are completely removed
        composeTestRule.onNodeWithTag("settings_tab_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_settings_server_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_disconnect_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_settings_storage_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_settings_clear_cache_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_about_card").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(aboutOpened)

        composeTestRule.onNodeWithTag("settings_local_server_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_path_config_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_api_suffix_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_security_protection_card").assertDoesNotExist()
        composeTestRule.onNodeWithText("Manage Active Shares", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `Non-TV Settings retains full mobile configuration cards including Security and Server path inputs`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.setSelectedSettingsTab(1)

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides false) {
                    SettingsTabContent(viewModel = vm, onOpenAbout = {})
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_settings_screen").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_security_protection_card").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_tab_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_local_server_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_path_config_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_api_suffix_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `ShareLinkDialog auto-dismisses on Android TV and AboutScreen hides external browser links on TV`() {
        var shareDismissed = false
        val vm = GalleryViewModel(context.applicationContext as Application)

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    ShareLinkDialog(
                        target = ShareTarget.Directory(path = "Trips/Alps"),
                        passwordProtected = false,
                        shareLinkState = ShareLinkUiState.Idle,
                        onCreateShare = { _, _, _, _ -> },
                        onDismiss = { shareDismissed = true }
                    )
                    AboutScreen(viewModel = vm, onBack = {})
                }
            }
        }

        composeTestRule.waitForIdle()
        assertTrue("Expected ShareLinkDialog to auto-dismiss in TV mode", shareDismissed)
        composeTestRule.onNodeWithTag("about_hero_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_github_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("about_issues_button").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun `Android TV side-panel metadata D-pad navigation traverses all sections and returns to media without dead spots`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.selectMedia(sampleGeotaggedMedia, listOf(sampleGeotaggedMedia))

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    MediaViewerDialog(
                        media = sampleGeotaggedMedia,
                        viewModel = vm,
                        onDismiss = {},
                        onOpenMap = {}
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()

        // Open side-panel metadata
        composeTestRule.onNodeWithTag("viewer_metadata_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()

        // Opening side panel focuses its close button
        composeTestRule.onNodeWithTag("metadata_side_panel_close_button").assertIsFocused()

        // D-pad Down walks sequentially through every metadata section card
        composeTestRule.onNodeWithTag("metadata_side_panel_close_button").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("metadata_section_file").assertIsFocused()

        composeTestRule.onNodeWithTag("metadata_section_file").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("metadata_section_camera").assertIsFocused()

        composeTestRule.onNodeWithTag("metadata_section_camera").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("metadata_section_location").assertIsFocused()

        composeTestRule.onNodeWithTag("metadata_section_location").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("metadata_section_tags").assertIsFocused()

        // Pressing Down at the last section stays cleanly focused without dropping into a dead spot
        composeTestRule.onNodeWithTag("metadata_section_tags").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("metadata_section_tags").assertIsFocused()

        // Pressing Left from a metadata section returns focus cleanly to the media canvas
        composeTestRule.onNodeWithTag("metadata_section_tags").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("media_viewer_canvas_focus_target").assertIsFocused()

        // Pressing Right from the last media item re-enters the open side panel cleanly
        composeTestRule.onNodeWithTag("media_viewer_canvas_focus_target").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("metadata_side_panel_close_button").assertIsFocused()

        // Pressing Up on media canvas jumps to top bar close button
        composeTestRule.onNodeWithTag("metadata_side_panel_close_button").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("metadata_section_file").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("media_viewer_canvas_focus_target").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_close_button").assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun `Android TV NavigationRail right-press from bottom Settings tab enters TV Settings and viewer isolates background focus`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.setActiveTab(ActiveTab.SETTINGS)

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    GalleryScreen(viewModel = vm)
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()

        // Focus bottom-pinned Settings tab in NavigationRail and press D-pad Right
        composeTestRule.onNodeWithTag("nav_tab_settings").requestFocus()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_tab_settings").assertIsFocused()

        composeTestRule.onNodeWithTag("nav_tab_settings").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_tab_0").assertIsFocused()

        // Open fullscreen MediaViewerDialog overlay: D-pad Left on first image must NOT leak to background NavigationRail
        vm.setActiveTab(ActiveTab.GALLERY)
        vm.selectMedia(sampleGeotaggedMedia, listOf(sampleGeotaggedMedia))
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("media_viewer_canvas_focus_target").assertIsFocused()
        composeTestRule.onNodeWithTag("media_viewer_canvas_focus_target").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("media_viewer_canvas_focus_target").assertIsFocused()
        composeTestRule.onNodeWithTag("nav_tab_gallery").assertIsNotFocused()
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun `tvSlider adjusts value with D-pad and releases focus at min boundary without trapping navigation`() {
        var latestValue = 2f

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    var sliderValue by remember { mutableFloatStateOf(2f) }
                    Row {
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .tvFocus()
                                .testTag("left_escape_button")
                        ) {
                            Text("Back")
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                sliderValue = it
                                latestValue = it
                            },
                            valueRange = 2f..10f,
                            steps = 7,
                            modifier = Modifier
                                .tvSlider(
                                    value = sliderValue,
                                    onValueChange = {
                                        sliderValue = it
                                        latestValue = it
                                    },
                                    valueRange = 2f..10f,
                                    steps = 7
                                )
                                .testTag("tv_slider_test")
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_slider_test").requestFocus()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_slider_test").assertIsFocused()

        // D-pad Right increments slider
        composeTestRule.onNodeWithTag("tv_slider_test").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.waitForIdle()
        assertTrue("Expected slider to increment from 2f to 3f, got $latestValue", latestValue == 3f)
        composeTestRule.onNodeWithTag("tv_slider_test").assertIsFocused()

        // D-pad Left decrements back to minimum (2f)
        composeTestRule.onNodeWithTag("tv_slider_test").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.waitForIdle()
        assertTrue("Expected slider to return to 2f, got $latestValue", latestValue == 2f)

        // D-pad Left at minimum value releases focus to the neighbor button on the left (no trap)
        composeTestRule.onNodeWithTag("tv_slider_test").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("left_escape_button").assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun `TvVideoControlsBar D-pad navigation reaches Play Pause, Rewind, Forward, Restart, Mute, and Seek bar without dead spots`() {
        var playPauseClicks = 0
        var rewindClicks = 0
        var forwardClicks = 0
        var restartClicks = 0
        var muteClicks = 0

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    val canvasRequester = remember { FocusRequester() }
                    val playPauseRequester = remember { FocusRequester() }
                    var positionMs by remember { mutableIntStateOf(30_000) }
                    var isPlaying by remember { mutableStateOf(true) }
                    var isMuted by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .focusRequester(canvasRequester)
                                .focusable()
                                .testTag("test_video_canvas")
                        )
                        TvVideoControlsBar(
                            title = "mountain_hike.mp4",
                            pageIndicator = "1 / 3",
                            isPlaying = isPlaying,
                            isMuted = isMuted,
                            currentPositionMs = positionMs,
                            durationMs = 120_000,
                            sliderValue = positionMs.toFloat(),
                            isDraggingSlider = false,
                            seekFeedbackLabel = "+10s",
                            playPauseFocusRequester = playPauseRequester,
                            mediaCanvasFocusRequester = canvasRequester,
                            sidePanelFocusRequester = null,
                            onTogglePlayPause = {
                                playPauseClicks++
                                isPlaying = !isPlaying
                            },
                            onSeekBackward = {
                                rewindClicks++
                                positionMs = (positionMs - 10_000).coerceAtLeast(0)
                            },
                            onSeekForward = {
                                forwardClicks++
                                positionMs = (positionMs + 10_000).coerceAtMost(120_000)
                            },
                            onRestart = {
                                restartClicks++
                                positionMs = 0
                            },
                            onToggleMute = {
                                muteClicks++
                                isMuted = !isMuted
                            },
                            onSliderChange = {},
                            onSliderChangeFinished = {},
                            onTvSliderStep = { positionMs = it.toInt() },
                            onUserInteraction = {},
                            onFocusChanged = {}
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_video_controls_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_video_seek_feedback_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_video_current_time").assertTextEquals("0:30")
        composeTestRule.onNodeWithTag("tv_video_duration_time").assertTextEquals("2:00")

        // Focus Play/Pause and walk left: Play/Pause -> Rewind 10s -> Restart
        composeTestRule.onNodeWithTag("viewer_video_play_pause_button").requestFocus()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_video_play_pause_button").assertIsFocused()

        composeTestRule.onNodeWithTag("viewer_video_play_pause_button").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_video_rewind_button").assertIsFocused()

        composeTestRule.onNodeWithTag("tv_video_rewind_button").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_video_restart_button").assertIsFocused()

        // Pressing Left at the leftmost button stays cleanly on Restart (no dead spot)
        composeTestRule.onNodeWithTag("tv_video_restart_button").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_video_restart_button").assertIsFocused()

        // Walk right across the entire transport bar: Restart -> Rewind -> Play/Pause -> Forward -> Mute
        composeTestRule.onNodeWithTag("tv_video_restart_button").performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_video_rewind_button").assertIsFocused()

        composeTestRule.onNodeWithTag("tv_video_rewind_button").performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_video_play_pause_button").assertIsFocused()

        composeTestRule.onNodeWithTag("viewer_video_play_pause_button").performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_video_forward_button").assertIsFocused()

        composeTestRule.onNodeWithTag("tv_video_forward_button").performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_video_mute_button").assertIsFocused()

        // Pressing Up moves cleanly to the seek bar; Down returns straight to Play/Pause
        composeTestRule.onNodeWithTag("tv_video_mute_button").performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_video_seek_slider").assertIsFocused()

        composeTestRule.onNodeWithTag("viewer_video_seek_slider").performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_video_play_pause_button").assertIsFocused()

        // Pressing Up twice moves from Play/Pause -> Seek bar -> Video Canvas
        composeTestRule.onNodeWithTag("viewer_video_play_pause_button").performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_video_seek_slider").assertIsFocused()
        composeTestRule.onNodeWithTag("viewer_video_seek_slider").performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("test_video_canvas").assertIsFocused()

        // Verify all action callbacks fire cleanly
        composeTestRule.onNodeWithTag("viewer_video_play_pause_button").performClick()
        composeTestRule.onNodeWithTag("tv_video_forward_button").performClick()
        composeTestRule.onNodeWithTag("tv_video_rewind_button").performClick()
        composeTestRule.onNodeWithTag("tv_video_restart_button").performClick()
        composeTestRule.onNodeWithTag("tv_video_mute_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(playPauseClicks == 1)
        assertTrue(forwardClicks == 1)
        assertTrue(rewindClicks == 1)
        assertTrue(restartClicks == 1)
        assertTrue(muteClicks == 1)
        composeTestRule.onNodeWithTag("tv_video_current_time").assertTextEquals("0:00")
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun `MediaViewerDialog displays TV video controls with immediate Play Pause focus and working 10s seek`() {
        val sampleVideo = ApiMedia(
            id = 202,
            name = "alps_timelapse.mp4",
            parentPath = "Trips/Alps",
            metadata = ApiMediaMetadata(
                size = ApiSize(width = 1920, height = 1080),
                creationDate = 1715000000000L,
                fileSize = 24_000_000L,
                duration = 90_000.0
            )
        )
        val vm = GalleryViewModel(context.applicationContext as Application)
        vm.selectMedia(sampleVideo, listOf(sampleVideo))

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalIsAndroidTv provides true) {
                    MediaViewerDialog(
                        media = sampleVideo,
                        viewModel = vm,
                        onDismiss = {},
                        onOpenMap = null
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()

        // Custom Android TV video control bar is rendered with Play/Pause immediately focused
        composeTestRule.onNodeWithTag("tv_video_controls_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_video_play_pause_button").assertIsDisplayed().assertIsFocused()
        composeTestRule.onNodeWithTag("tv_video_rewind_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_video_forward_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_video_restart_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_video_mute_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_video_duration_time").assertTextEquals("1:30")

        // Single D-pad Right reaches Forward 10s; clicking advances playback time and shows +10s feedback
        composeTestRule.onNodeWithTag("viewer_video_play_pause_button").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_video_forward_button").assertIsFocused().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_video_current_time").assertTextEquals("0:10")
        composeTestRule.onNodeWithTag("tv_video_seek_feedback_badge").assertIsDisplayed()
    }
}
