package com.example

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApiDirectory
import com.example.data.ApiMedia
import com.example.data.ApiMediaMetadata
import com.example.data.ApiSize
import com.example.data.ApiSubFolder
import com.example.data.PreferencesManager
import com.example.ui.AdaptivePullToRefreshBox
import com.example.ui.DateUtils
import com.example.ui.GalleryContentGrid
import com.example.ui.GalleryUiState
import com.example.ui.GalleryViewModel
import com.example.ui.rememberPersistedLazyGridState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GalleryScrollBehaviorTest {

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

    private fun sampleMedia(count: Int, includeSizes: Boolean = false): List<ApiMedia> {
        val baseMs = 1_735_689_600_000L // 2025-01-01 UTC
        return (0 until count).map { idx ->
            val size = if (includeSizes) {
                when (idx % 3) {
                    0 -> ApiSize(4000, 3000)
                    1 -> ApiSize(3000, 4000)
                    else -> null
                }
            } else {
                null
            }
            ApiMedia(
                id = idx + 1,
                name = "IMG_${String.format("%04d", idx)}.jpg",
                metadata = ApiMediaMetadata(
                    size = size,
                    creationDate = baseMs - idx * 86_400_000L
                ),
                parentPath = "vacation"
            )
        }
    }

    private fun sampleSubfolders(count: Int): List<ApiSubFolder> =
        (1..count).map { idx ->
            ApiSubFolder(id = idx, name = "Album $idx", path = "vacation/Album $idx", mediaCount = 5)
        }

    @Test
    fun `GalleryContentGrid stays pinned to top when subfolders prepend above cached media`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val gridState = LazyGridState(0, 0)
        var subfolders by mutableStateOf<List<ApiSubFolder>>(emptyList())
        val media = sampleMedia(180) // > 150 items also verifies synchronous grouping (no empty frame)

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize().testTag("grid_host")) {
                    GalleryContentGrid(
                        subfolders = subfolders,
                        mediaList = media,
                        viewModel = vm,
                        onFolderClick = {},
                        onMediaClick = {},
                        state = gridState
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(0, gridState.firstVisibleItemIndex)
        assertTrue(gridState.layoutInfo.totalItemsCount > 180)

        // Simulate fresh network response prepending 9 subfolders above cached media
        subfolders = sampleSubfolders(9)
        composeTestRule.waitForIdle()

        // Must remain pinned to (0, 0) so topmost subfolders are immediately reachable
        assertEquals(0, gridState.firstVisibleItemIndex)
        assertEquals(0, gridState.firstVisibleItemScrollOffset)
    }

    @Test
    fun `Scrolling back and forth with subfolders inside PullToRefresh reaches top row of albums`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val gridState = LazyGridState(0, 0)
        val subfolders = sampleSubfolders(9)
        val media = sampleMedia(90)
        var isRefreshing by mutableStateOf(false)

        composeTestRule.setContent {
            MyApplicationTheme {
                AdaptivePullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { isRefreshing = false },
                    modifier = Modifier.fillMaxSize().testTag("grid_host")
                ) {
                    GalleryContentGrid(
                        subfolders = subfolders,
                        mediaList = media,
                        viewModel = vm,
                        onFolderClick = {},
                        onMediaClick = {},
                        state = gridState
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Album 1").assertIsDisplayed()

        // Scroll back and forth multiple times (including right-column touch zone)
        repeat(3) { cycle ->
            composeTestRule.onNodeWithTag("grid_host").performTouchInput {
                swipeUp(startY = bottom * 0.8f, endY = top + 40f, durationMillis = 150)
            }
            composeTestRule.waitForIdle()
            assertTrue(
                "Expected grid to scroll down on cycle $cycle, got index=${gridState.firstVisibleItemIndex}",
                gridState.firstVisibleItemIndex > 0
            )

            // Swipe back up along right column (outside the flush 32dp edge handle)
            repeat(5) {
                composeTestRule.onNodeWithTag("grid_host").performTouchInput {
                    val rightColumnX = (right - 50f).coerceAtLeast(centerX)
                    swipeDown(
                        startY = top + height * 0.2f,
                        endY = bottom * 0.85f,
                        durationMillis = 150
                    )
                    // Also drag specifically at rightColumnX
                    down(Offset(rightColumnX, top + height * 0.25f))
                    moveTo(Offset(rightColumnX, top + height * 0.55f), delayMillis = 40)
                    moveTo(Offset(rightColumnX, bottom * 0.85f), delayMillis = 40)
                    up()
                }
                composeTestRule.waitForIdle()
            }
        }

        // Topmost row of albums MUST be visible and reachable at (0, 0)
        assertEquals(
            "Expected top row (0), got index=${gridState.firstVisibleItemIndex}, offset=${gridState.firstVisibleItemScrollOffset}",
            0,
            gridState.firstVisibleItemIndex
        )
        assertEquals(0, gridState.firstVisibleItemScrollOffset)
        composeTestRule.onNodeWithText("Album 1").assertIsDisplayed()
    }

    @Test
    fun `Dragging FastScrollIndicator thumb down and back up reaches top row of albums`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val gridState = LazyGridState(0, 0)
        val subfolders = sampleSubfolders(9)
        val media = sampleMedia(120)

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize().testTag("grid_host")) {
                    GalleryContentGrid(
                        subfolders = subfolders,
                        mediaList = media,
                        viewModel = vm,
                        onFolderClick = {},
                        onMediaClick = {},
                        state = gridState
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Scroll down first so FastScrollIndicator appears
        composeTestRule.onNodeWithTag("grid_host").performTouchInput {
            swipeUp(startY = bottom * 0.85f, endY = top + 30f, durationMillis = 120)
        }
        composeTestRule.waitForIdle()
        assertTrue("Expected grid to scroll down first", gridState.firstVisibleItemIndex > 0)

        // Drag fast_scroll_thumb downward toward middle
        composeTestRule.onNodeWithTag("fast_scroll_thumb").performTouchInput {
            down(center)
            moveBy(Offset(0f, 180f), delayMillis = 50)
            up()
        }
        composeTestRule.waitForIdle()
        val afterDownDrag = gridState.firstVisibleItemIndex
        assertTrue("Expected dragging thumb down to move deeper, got $afterDownDrag", afterDownDrag > 5)

        // Now drag fast_scroll_thumb all the way back UP to the top
        composeTestRule.onNodeWithTag("fast_scroll_thumb").performTouchInput {
            down(center)
            moveBy(Offset(0f, -60f), delayMillis = 40)
            moveBy(Offset(0f, -120f), delayMillis = 40)
            moveBy(Offset(0f, -240f), delayMillis = 40)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "Expected dragging thumb to top to reach 0, got index=${gridState.firstVisibleItemIndex}, offset=${gridState.firstVisibleItemScrollOffset}",
            0,
            gridState.firstVisibleItemIndex
        )
        assertEquals(0, gridState.firstVisibleItemScrollOffset)
        composeTestRule.onNodeWithText("Album 1").assertIsDisplayed()
    }

    @Test
    fun `Scrolling back and forth in Original aspect ratio mode reaches top row of albums without drift`() {
        prefs.aspectRatio = 0f // "Original" aspect ratio
        val vm = GalleryViewModel(context.applicationContext as Application)
        val gridState = LazyGridState(0, 0)
        val subfolders = sampleSubfolders(6)
        val media = sampleMedia(60, includeSizes = true)

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize().testTag("grid_host")) {
                    GalleryContentGrid(
                        subfolders = subfolders,
                        mediaList = media,
                        viewModel = vm,
                        onFolderClick = {},
                        onMediaClick = {},
                        state = gridState
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Album 1").assertIsDisplayed()

        // Scroll down then back up repeatedly
        repeat(2) {
            composeTestRule.onNodeWithTag("grid_host").performTouchInput {
                swipeUp(startY = bottom * 0.8f, endY = top + 40f, durationMillis = 150)
            }
            composeTestRule.waitForIdle()
            assertTrue(gridState.firstVisibleItemIndex > 0)

            repeat(5) {
                composeTestRule.onNodeWithTag("grid_host").performTouchInput {
                    swipeDown(startY = top + height * 0.2f, endY = bottom * 0.85f, durationMillis = 150)
                }
                composeTestRule.waitForIdle()
            }
        }

        assertEquals(0, gridState.firstVisibleItemIndex)
        assertEquals(0, gridState.firstVisibleItemScrollOffset)
        composeTestRule.onNodeWithText("Album 1").assertIsDisplayed()
    }

    @Test
    fun `DateUtils groupMediaByMonthYear preserves order and handles undated media`() {
        val media = listOf(
            ApiMedia(1, "a.jpg", ApiMediaMetadata(creationDate = 1_735_689_600_000L), "p"),
            ApiMedia(2, "b.jpg", ApiMediaMetadata(creationDate = 1_735_776_000_000L), "p"),
            ApiMedia(3, "c.jpg", ApiMediaMetadata(creationDate = null), "p")
        )
        val grouped = DateUtils.groupMediaByMonthYear(media, "Unknown")
        assertEquals(2, grouped.size)
        assertEquals(2, grouped.values.first().size)
        assertEquals(listOf(media[2]), grouped["Unknown"])
    }

    @Test
    fun `Returning from subfolder synchronously restores parent folders without disappearing`() {
        prefs.serverUrl = "http://127.0.0.1:1"
        val vm = GalleryViewModel(context.applicationContext as Application)

        val rootSubfolders = sampleSubfolders(9)
        val rootMedia = sampleMedia(20)
        val rootDir = ApiDirectory(
            id = 1,
            name = "Root",
            path = "",
            directories = rootSubfolders,
            media = rootMedia
        )
        val childDir = ApiDirectory(
            id = 2,
            name = "Album 1",
            path = "vacation/Album 1",
            directories = listOf(ApiSubFolder(id = 99, name = "Day 1", path = "vacation/Album 1/Day 1", mediaCount = 4)),
            media = sampleMedia(10)
        )
        val grandChildDir = ApiDirectory(
            id = 3,
            name = "Day 1",
            path = "vacation/Album 1/Day 1",
            directories = emptyList(),
            media = sampleMedia(4)
        )

        vm.cacheFolderDirectory("", rootDir)
        vm.cacheFolderDirectory("vacation/Album 1", childDir)
        vm.cacheFolderDirectory("vacation/Album 1/Day 1", grandChildDir)

        // Enter child -> grandchild -> back to child -> breadcrumb to root
        vm.enterFolder("vacation/Album 1")
        val childState = vm.galleryState.value as GalleryUiState.Success
        assertEquals("gallery:FOLDER:false:vacation/Album 1", childState.contextKey)
        assertEquals(1, childState.directory.directories?.size)

        vm.enterFolder("vacation/Album 1/Day 1")
        val grandChildState = vm.galleryState.value as GalleryUiState.Success
        assertEquals("gallery:FOLDER:false:vacation/Album 1/Day 1", grandChildState.contextKey)

        // Return one level up: must IMMEDIATELY restore child folder with its subfolder intact
        assertTrue(vm.goBackFolder())
        val backToChild = vm.galleryState.value as GalleryUiState.Success
        assertEquals("gallery:FOLDER:false:vacation/Album 1", backToChild.contextKey)
        assertEquals(1, backToChild.directory.directories?.size)
        assertEquals("Day 1", backToChild.directory.directories?.first()?.name)

        // Jump via breadcrumb straight to root: must IMMEDIATELY restore all 9 root folders
        vm.navigateToBreadcrumb(0)
        val backToRoot = vm.galleryState.value as GalleryUiState.Success
        assertEquals("gallery:FOLDER:false:", backToRoot.contextKey)
        assertEquals(9, backToRoot.directory.directories?.size)
        assertEquals(20, backToRoot.directory.media?.size)
    }

    @Test
    fun `Returning from subfolder restores exact parent scroll position`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val parentSubfolders = sampleSubfolders(12)
        val parentMedia = sampleMedia(90)
        val childMedia = sampleMedia(60)

        var activeKey by mutableStateOf("gallery:FOLDER:false:")
        var activeSubfolders by mutableStateOf(parentSubfolders)
        var activeMedia by mutableStateOf(parentMedia)
        var currentGridState: LazyGridState? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize().testTag("grid_host")) {
                    key(activeKey) {
                        val gridState = rememberPersistedLazyGridState(vm, activeKey)
                        currentGridState = gridState
                        GalleryContentGrid(
                            subfolders = activeSubfolders,
                            mediaList = activeMedia,
                            viewModel = vm,
                            onFolderClick = {},
                            onMediaClick = {},
                            state = gridState
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Scroll parent down to a mid-list position
        composeTestRule.onNodeWithTag("grid_host").performTouchInput {
            swipeUp(startY = bottom * 0.75f, endY = top + height * 0.35f, durationMillis = 200)
        }
        composeTestRule.waitForIdle()

        val parentSavedIndex = currentGridState!!.firstVisibleItemIndex
        val parentSavedOffset = currentGridState!!.firstVisibleItemScrollOffset
        assertTrue(
            "Expected parent to have scrolled down, got ($parentSavedIndex, $parentSavedOffset)",
            parentSavedIndex > 0 || parentSavedOffset > 0
        )

        // Navigate into subfolder and scroll to a different position
        activeKey = "gallery:FOLDER:false:vacation/Album 1"
        activeSubfolders = emptyList()
        activeMedia = childMedia
        composeTestRule.waitForIdle()

        assertEquals(0, currentGridState!!.firstVisibleItemIndex)
        composeTestRule.onNodeWithTag("grid_host").performTouchInput {
            swipeUp(startY = bottom * 0.85f, endY = top + 30f, durationMillis = 120)
        }
        composeTestRule.waitForIdle()
        assertTrue(currentGridState!!.firstVisibleItemIndex > 0)

        // Return to parent folder (with all 12 subfolders immediately present, matching RAM cache restore)
        activeKey = "gallery:FOLDER:false:"
        activeSubfolders = parentSubfolders
        activeMedia = parentMedia
        composeTestRule.waitForIdle()

        assertEquals(parentSavedIndex, currentGridState!!.firstVisibleItemIndex)
        assertEquals(parentSavedOffset, currentGridState!!.firstVisibleItemScrollOffset)
    }

    @Test
    fun `Pending scroll restore survives partial initial list and restores exact offset once full list loads`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val scrollKey = "gallery:DATE:false:"
        vm.saveScrollPosition(scrollKey, 18, 95)

        var activeMedia by mutableStateOf(sampleMedia(4))
        var currentGridState: LazyGridState? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize().testTag("partial_grid_host")) {
                    key(scrollKey) {
                        val gridState = rememberPersistedLazyGridState(vm, scrollKey)
                        currentGridState = gridState
                        GalleryContentGrid(
                            subfolders = emptyList(),
                            mediaList = activeMedia,
                            viewModel = vm,
                            onFolderClick = {},
                            onMediaClick = {},
                            state = gridState
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        // Saved target must NOT have been overwritten by the clamped 4-item layout
        assertEquals(18, vm.getScrollPosition(scrollKey).index)
        assertEquals(95, vm.getScrollPosition(scrollKey).offset)

        // Full list arrives — grid must restore exact saved (18, 95)
        activeMedia = sampleMedia(80)
        composeTestRule.waitForIdle()

        assertEquals(18, currentGridState!!.firstVisibleItemIndex)
        assertEquals(95, currentGridState!!.firstVisibleItemScrollOffset)
    }

    @Test
    fun `Stale scroll key owner cannot overwrite newer grid scroll position during Crossfade transitions`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val key = "albums_list:"

        val oldOwner = vm.claimScrollKeyOwner(key)
        vm.saveScrollPosition(key, 12, 80, oldOwner)
        assertEquals(GalleryViewModel.SavedScrollPosition(12, 80), vm.getScrollPosition(key))

        val newOwner = vm.claimScrollKeyOwner(key)
        vm.saveScrollPosition(key, 24, 140, newOwner)

        // Simulated delayed onDispose from outgoing Crossfade composition with oldOwner
        vm.saveScrollPosition(key, 0, 0, oldOwner)
        assertEquals(GalleryViewModel.SavedScrollPosition(24, 140), vm.getScrollPosition(key))
    }

    @Test
    fun `Returning from Album detail view preserves Albums LazyVerticalGrid scroll position`() {
        val vm = GalleryViewModel(context.applicationContext as Application)
        val albums = (1..48).map { i ->
            com.example.data.ApiAlbum(
                id = i,
                name = "Album $i",
                locked = false,
                searchQuery = mapOf("type" to 100, "value" to "Album $i"),
                cache = com.example.data.ApiAlbumCache(
                    itemCount = 12,
                    oldestMedia = null,
                    youngestMedia = null,
                    coverName = null,
                    coverDirectory = null
                )
            )
        }

        val albumsStateField = GalleryViewModel::class.java.getDeclaredField("_albumsState")
        albumsStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val albumsStateFlow = albumsStateField.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<com.example.ui.AlbumsUiState>
        albumsStateFlow.value = com.example.ui.AlbumsUiState.Success(albums)

        val selectedAlbumField = GalleryViewModel::class.java.getDeclaredField("_selectedAlbum")
        selectedAlbumField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val selectedAlbumFlow = selectedAlbumField.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<com.example.data.ApiAlbum?>

        val albumContentField = GalleryViewModel::class.java.getDeclaredField("_albumContentState")
        albumContentField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val albumContentFlow = albumContentField.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<GalleryUiState?>

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize().testTag("albums_tab_host")) {
                    com.example.ui.AlbumsTabContent(viewModel = vm)
                }
            }
        }

        composeTestRule.waitForIdle()

        // Scroll Albums list down
        composeTestRule.onNodeWithTag("albums_tab_host").performTouchInput {
            swipeUp(startY = bottom * 0.8f, endY = top + height * 0.2f, durationMillis = 200)
        }
        composeTestRule.waitForIdle()

        val savedBeforeDetail = vm.getScrollPosition("albums_list:")
        assertTrue(
            "Expected albums list to scroll down, got $savedBeforeDetail",
            savedBeforeDetail.index > 0 || savedBeforeDetail.offset > 0
        )

        // Open Album detail view
        selectedAlbumFlow.value = albums[10]
        albumContentFlow.value = GalleryUiState.Success(
            ApiDirectory(10, "Album 11", "", emptyList(), sampleMedia(30))
        )
        composeTestRule.waitForIdle()

        // Navigate back to Albums list
        selectedAlbumFlow.value = null
        albumContentFlow.value = null
        composeTestRule.waitForIdle()

        val savedAfterReturn = vm.getScrollPosition("albums_list:")
        assertEquals(savedBeforeDetail.index, savedAfterReturn.index)
        assertEquals(savedBeforeDetail.offset, savedAfterReturn.offset)
    }
}
