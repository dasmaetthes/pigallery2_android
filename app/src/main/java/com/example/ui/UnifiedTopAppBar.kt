package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.data.ApiAlbum
import com.example.data.ApiMedia
import com.example.data.ApiPerson
import com.example.data.ShareTarget
import com.example.data.ShareTargetKind
import com.example.findActivity
import com.example.util.BiometricHelper
import com.example.util.TvFeatureCapabilities
import com.example.util.TvModeHelper
import com.example.util.rememberIsTvMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Unified Material 3 TopAppBar shared across all primary screens
 * (Gallery, Albums, People, Rediscover) and Settings.
 *
 * Maintains a consistent action bar slot layout:
 * 1. Search / Close-Search (+ Advanced Query Builder when Gallery search is active)
 * 2. Contextual quick action (Favorite toggle for Person detail)
 * 3. Sort options (Gallery, Albums, People)
 * 4. Map view when geotagged items exist (positioned to the right of Sort)
 * 5. Context-sensitive 3-dot Overflow Menu (View/Content -> Share -> Global Settings & About)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedGlobalTopAppBar(
    viewModel: GalleryViewModel,
    activeTab: ActiveTab,
    isSelectMode: Boolean,
    selectedMediaForShare: Set<ApiMedia>,
    currentVisibleMediaList: List<ApiMedia>,
    mapMediaList: List<ApiMedia>,
    isTv: Boolean,
    isTopBarSearchExpanded: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    showSearchSuggestions: Boolean,
    isAlbumsSearchActive: Boolean,
    albumsFilterQuery: String,
    isPersonsSearchActive: Boolean,
    personsFilterQuery: String,
    galleryState: GalleryUiState,
    albumsState: AlbumsUiState,
    personsState: PersonsUiState,
    albumContentState: GalleryUiState?,
    personContentState: GalleryUiState?,
    rediscoverState: RediscoverUiState,
    pathHistory: List<String>,
    defaultRootPath: String,
    galleryViewMode: GalleryViewMode,
    isFlattened: Boolean,
    hideProtectedItems: Boolean,
    selectedAlbum: ApiAlbum?,
    selectedPerson: ApiPerson?,
    canManageAlbums: Boolean,
    isSharingAvailable: Boolean,
    isCreatingShare: Boolean,
    shareDialogTarget: ShareTarget?,
    searchFieldFocusRequester: FocusRequester,
    searchSuggestionsFocusRequester: FocusRequester,
    galleryGridFirstItemFocusRequester: FocusRequester,
    albumsGridFirstItemFocusRequester: FocusRequester,
    personsGridFirstItemFocusRequester: FocusRequester,
    rediscoverGridFirstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    settingsFirstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    onOpenQueryBuilder: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSaveAlbum: (String) -> Unit,
    onOpenDeleteAlbum: (ApiAlbum) -> Unit,
    onOpenShareDialog: (ShareTarget) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val resolvedIsTv = isTv || rememberIsTvMode()
    val tvCapabilities = remember(resolvedIsTv) { TvModeHelper.capabilitiesFor(resolvedIsTv) }
    val effectiveIsSelectMode = tvCapabilities.isSelectModeActive(isSelectMode)
    val effectiveHideProtectedItems = tvCapabilities.shouldEnforceItemLocking(hideProtectedItems)
    val topBarMode = when {
        effectiveIsSelectMode -> UnifiedTopBarMode.SELECT
        isTopBarSearchExpanded -> UnifiedTopBarMode.SEARCH
        else -> UnifiedTopBarMode.NORMAL
    }
    val targetContainerColor = when (topBarMode) {
        UnifiedTopBarMode.SELECT -> MaterialTheme.colorScheme.surfaceContainerHigh
        UnifiedTopBarMode.SEARCH -> MaterialTheme.colorScheme.surfaceContainerLow
        UnifiedTopBarMode.NORMAL -> MaterialTheme.colorScheme.surface
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 180),
        label = "unifiedTopBarContainerColor"
    )

    val activeDownTarget = when (activeTab) {
        ActiveTab.GALLERY -> if (isSearchActive && showSearchSuggestions) {
            searchSuggestionsFocusRequester
        } else {
            galleryGridFirstItemFocusRequester
        }
        ActiveTab.ALBUMS -> albumsGridFirstItemFocusRequester
        ActiveTab.PERSONS -> personsGridFirstItemFocusRequester
        ActiveTab.REDISCOVER -> rediscoverGridFirstItemFocusRequester
        ActiveTab.SETTINGS -> settingsFirstItemFocusRequester
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .onKeyEvent { event ->
                if (resolvedIsTv && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> true
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down) ||
                                runCatching { activeDownTarget.requestFocus() }.isSuccess
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        TopAppBar(
            modifier = Modifier.testTag("unified_top_app_bar"),
            title = {
                AnimatedContent(
                    targetState = topBarMode,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180)) +
                            slideInVertically(animationSpec = tween(180)) { it / 6 })
                            .togetherWith(fadeOut(animationSpec = tween(130)))
                    },
                    label = "unifiedTopBarTitleTransition"
                ) { mode ->
                    UnifiedTopBarTitleContent(
                        viewModel = viewModel,
                        activeTab = activeTab,
                        isSelectMode = mode == UnifiedTopBarMode.SELECT,
                        selectedCount = selectedMediaForShare.size,
                        isTopBarSearchExpanded = mode == UnifiedTopBarMode.SEARCH,
                        searchQuery = searchQuery,
                        textFieldValue = textFieldValue,
                        onTextFieldValueChange = onTextFieldValueChange,
                        showSearchSuggestions = showSearchSuggestions,
                        albumsFilterQuery = albumsFilterQuery,
                        personsFilterQuery = personsFilterQuery,
                        galleryState = galleryState,
                        albumsState = albumsState,
                        personsState = personsState,
                        albumContentState = albumContentState,
                        personContentState = personContentState,
                        rediscoverState = rediscoverState,
                        pathHistory = pathHistory,
                        defaultRootPath = defaultRootPath,
                        galleryViewMode = galleryViewMode,
                        hideProtectedItems = effectiveHideProtectedItems,
                        selectedAlbum = selectedAlbum,
                        selectedPerson = selectedPerson,
                        searchFieldFocusRequester = searchFieldFocusRequester,
                        searchSuggestionsFocusRequester = searchSuggestionsFocusRequester,
                        galleryGridFirstItemFocusRequester = galleryGridFirstItemFocusRequester,
                        albumsGridFirstItemFocusRequester = albumsGridFirstItemFocusRequester,
                        personsGridFirstItemFocusRequester = personsGridFirstItemFocusRequester
                    )
                }
            },
            navigationIcon = {
                UnifiedTopBarNavigationIcon(
                    viewModel = viewModel,
                    activeTab = activeTab,
                    isSelectMode = effectiveIsSelectMode,
                    isSearchActive = isSearchActive,
                    pathHistorySize = pathHistory.size,
                    selectedAlbum = selectedAlbum,
                    selectedPerson = selectedPerson
                )
            },
            actions = {
                if (effectiveIsSelectMode) {
                    if (currentVisibleMediaList.isNotEmpty()) {
                        val allSelected = selectedMediaForShare.size >= currentVisibleMediaList.size &&
                            currentVisibleMediaList.all { it in selectedMediaForShare }
                        IconButton(
                            onClick = { viewModel.toggleSelectAllMedia(currentVisibleMediaList) },
                            modifier = Modifier
                                .tvFocus(shape = CircleShape)
                                .testTag("select_all_button")
                        ) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = stringResource(if (allSelected) R.string.deselect_all else R.string.select_all)
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.shareSelectedMedia(context) },
                        enabled = selectedMediaForShare.isNotEmpty(),
                        modifier = Modifier
                            .tvFocus(shape = CircleShape)
                            .testTag("select_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share_selected)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.downloadSelectedMedia(context) },
                        enabled = selectedMediaForShare.isNotEmpty(),
                        modifier = Modifier
                            .tvFocus(shape = CircleShape)
                            .testTag("select_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.download_selected)
                        )
                    }
                } else if (activeTab != ActiveTab.SETTINGS) {
                    // Slot 1: Consistent Search placement across ALL 4 primary screens
                    // (Gallery, Albums, People, Rediscover)
                    if (activeTab == ActiveTab.GALLERY && isSearchActive) {
                        if (tvCapabilities.isTvMode && searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    viewModel.executeSearch()
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier
                                    .tvFocus(shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.execute_search),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = onOpenQueryBuilder,
                            modifier = Modifier
                                .tvFocus(shape = CircleShape)
                                .testTag("topbar_query_builder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.advanced_search_builder),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            when (activeTab) {
                                ActiveTab.GALLERY -> {
                                    if (isSearchActive && searchQuery.isNotEmpty()) {
                                        viewModel.updateSearchQueryText("")
                                    } else {
                                        viewModel.toggleSearchActive()
                                    }
                                }
                                ActiveTab.ALBUMS -> {
                                    if (selectedAlbum != null) {
                                        viewModel.openGlobalSearch()
                                    } else if (isAlbumsSearchActive && albumsFilterQuery.isNotEmpty()) {
                                        viewModel.setAlbumsFilterQuery("")
                                    } else {
                                        viewModel.toggleAlbumsSearchActive()
                                    }
                                }
                                ActiveTab.PERSONS -> {
                                    if (selectedPerson != null) {
                                        viewModel.openGlobalSearch()
                                    } else if (isPersonsSearchActive && personsFilterQuery.isNotEmpty()) {
                                        viewModel.setPersonsFilterQuery("")
                                    } else {
                                        viewModel.togglePersonsSearchActive()
                                    }
                                }
                                ActiveTab.REDISCOVER -> {
                                    viewModel.openGlobalSearch()
                                }
                                ActiveTab.SETTINGS -> {}
                            }
                        },
                        modifier = Modifier
                            .tvFocus(shape = CircleShape)
                            .testTag(if (isTopBarSearchExpanded) "topbar_search_close_button" else "topbar_search_button")
                    ) {
                        Icon(
                            imageVector = if (isTopBarSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (isTopBarSearchExpanded) stringResource(R.string.close_search) else stringResource(R.string.search)
                        )
                    }

                    val isGalleryShowingSuggestions = activeTab == ActiveTab.GALLERY && isSearchActive && showSearchSuggestions

                    // Slot 2: Sort action (Gallery, Albums, People)
                    if ((activeTab == ActiveTab.GALLERY || activeTab == ActiveTab.ALBUMS || activeTab == ActiveTab.PERSONS) &&
                        !isGalleryShowingSuggestions
                    ) {
                        var showSortDialog by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showSortDialog = true },
                            modifier = Modifier
                                .tvFocus(shape = CircleShape)
                                .testTag("topbar_sort_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort_options)
                            )
                        }
                        if (showSortDialog) {
                            SortDialog(
                                viewModel = viewModel,
                                onDismiss = { showSortDialog = false }
                            )
                        }
                    }

                    // Slot 4: Map View action (positioned directly to the right of Sort)
                    val hasGeotagged = remember(mapMediaList) {
                        mapMediaList.any { it.metadata?.gps != null }
                    }
                    val showMapButton = tvCapabilities.allowMapView && !isGalleryShowingSuggestions && when (activeTab) {
                        ActiveTab.GALLERY -> mapMediaList.isNotEmpty()
                        ActiveTab.ALBUMS -> selectedAlbum != null && (albumContentState is GalleryUiState.Loading || mapMediaList.isNotEmpty())
                        ActiveTab.PERSONS -> selectedPerson != null && (personContentState is GalleryUiState.Loading || mapMediaList.isNotEmpty())
                        else -> false
                    }
                    val isMapButtonEnabled = when (activeTab) {
                        ActiveTab.GALLERY -> galleryState is GalleryUiState.Success && mapMediaList.isNotEmpty()
                        ActiveTab.ALBUMS -> albumContentState is GalleryUiState.Success && mapMediaList.isNotEmpty()
                        ActiveTab.PERSONS -> personContentState is GalleryUiState.Success && mapMediaList.isNotEmpty()
                        else -> false
                    }
                    if (showMapButton) {
                        IconButton(
                            onClick = onOpenMap,
                            enabled = isMapButtonEnabled,
                            modifier = Modifier
                                .tvFocus(shape = CircleShape)
                                .testTag("topbar_map_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = stringResource(R.string.map_view),
                                tint = if (isMapButtonEnabled && hasGeotagged) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                }
                            )
                        }
                    }

                    // Slot 5: Context-Sensitive Material 3 Overflow Menu
                    UnifiedOverflowMenu(
                        viewModel = viewModel,
                        activeTab = activeTab,
                        tvCapabilities = tvCapabilities,
                        isSelectMode = effectiveIsSelectMode,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        galleryViewMode = galleryViewMode,
                        isFlattened = isFlattened,
                        pathHistory = pathHistory,
                        selectedAlbum = selectedAlbum,
                        selectedPerson = selectedPerson,
                        canManageAlbums = tvCapabilities.canManageAlbums(canManageAlbums),
                        isSharingAvailable = tvCapabilities.canCreateShareLinks(isSharingAvailable),
                        isCreatingShare = isCreatingShare,
                        shareDialogTarget = shareDialogTarget,
                        onOpenSaveAlbum = onOpenSaveAlbum,
                        onOpenDeleteAlbum = onOpenDeleteAlbum,
                        onOpenShareDialog = onOpenShareDialog,
                        onOpenAbout = onOpenAbout
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = containerColor,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun UnifiedTopBarNavigationIcon(
    viewModel: GalleryViewModel,
    activeTab: ActiveTab,
    isSelectMode: Boolean,
    isSearchActive: Boolean,
    pathHistorySize: Int,
    selectedAlbum: ApiAlbum?,
    selectedPerson: ApiPerson?
) {
    when {
        isSelectMode -> {
            IconButton(
                onClick = { viewModel.exitSelectMode() },
                modifier = Modifier
                    .tvFocus(shape = CircleShape)
                    .testTag("exit_select_mode_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.exit_selection)
                )
            }
        }
        activeTab == ActiveTab.SETTINGS -> {
            IconButton(
                onClick = { viewModel.navigateBackFromSettings() },
                modifier = Modifier
                    .tvFocus(shape = CircleShape)
                    .testTag("topbar_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.go_back)
                )
            }
        }
        activeTab == ActiveTab.GALLERY && !isSearchActive && pathHistorySize > 1 -> {
            IconButton(
                onClick = { viewModel.goBackFolder() },
                modifier = Modifier
                    .tvFocus(shape = CircleShape)
                    .testTag("topbar_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.go_back)
                )
            }
        }
        activeTab == ActiveTab.ALBUMS && selectedAlbum != null -> {
            IconButton(
                onClick = { viewModel.selectAlbum(null) },
                modifier = Modifier
                    .tvFocus(shape = CircleShape)
                    .testTag("topbar_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_to_albums)
                )
            }
        }
        activeTab == ActiveTab.PERSONS && selectedPerson != null -> {
            IconButton(
                onClick = { viewModel.clearSelectedPerson() },
                modifier = Modifier
                    .tvFocus(shape = CircleShape)
                    .testTag("topbar_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_to_persons)
                )
            }
        }
    }
}

@Composable
private fun UnifiedTopBarTitleContent(
    viewModel: GalleryViewModel,
    activeTab: ActiveTab,
    isSelectMode: Boolean,
    selectedCount: Int,
    isTopBarSearchExpanded: Boolean,
    searchQuery: String,
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    showSearchSuggestions: Boolean,
    albumsFilterQuery: String,
    personsFilterQuery: String,
    galleryState: GalleryUiState,
    albumsState: AlbumsUiState,
    personsState: PersonsUiState,
    albumContentState: GalleryUiState?,
    personContentState: GalleryUiState?,
    rediscoverState: RediscoverUiState,
    pathHistory: List<String>,
    defaultRootPath: String,
    galleryViewMode: GalleryViewMode,
    hideProtectedItems: Boolean,
    selectedAlbum: ApiAlbum?,
    selectedPerson: ApiPerson?,
    searchFieldFocusRequester: FocusRequester,
    searchSuggestionsFocusRequester: FocusRequester,
    galleryGridFirstItemFocusRequester: FocusRequester,
    albumsGridFirstItemFocusRequester: FocusRequester,
    personsGridFirstItemFocusRequester: FocusRequester
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    if (isSelectMode) {
        Text(
            text = stringResource(R.string.selected_count, selectedCount),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else if (isTopBarSearchExpanded) {
        LaunchedEffect(activeTab, isTopBarSearchExpanded) {
            if (isTopBarSearchExpanded) {
                try {
                    delay(100)
                    searchFieldFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }
        if (activeTab == ActiveTab.GALLERY) {
            TextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    onTextFieldValueChange(newValue)
                    if (searchQuery != newValue.text) {
                        viewModel.updateSearchQueryText(newValue.text)
                        viewModel.fetchSearchSuggestions(newValue.text)
                        viewModel.showSearchSuggestions.value = true
                    }
                },
                placeholder = { Text(stringResource(R.string.search)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.executeSearch()
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("topbar_search_field")
                    .focusRequester(searchFieldFocusRequester)
                    .focusProperties {
                        down = if (showSearchSuggestions) {
                            searchSuggestionsFocusRequester
                        } else {
                            galleryGridFirstItemFocusRequester
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionDown -> {
                                    keyboardController?.hide()
                                    val target = if (showSearchSuggestions) {
                                        searchSuggestionsFocusRequester
                                    } else {
                                        galleryGridFirstItemFocusRequester
                                    }
                                    try {
                                        target.requestFocus()
                                        true
                                    } catch (_: Exception) {
                                        focusManager.moveFocus(FocusDirection.Down)
                                    }
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    viewModel.executeSearch()
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
            )
        } else {
            val isAlbums = activeTab == ActiveTab.ALBUMS
            val filterValue = if (isAlbums) albumsFilterQuery else personsFilterQuery
            val filterPlaceholder = stringResource(if (isAlbums) R.string.search_albums_hint else R.string.search_persons_hint)
            val downTarget = if (isAlbums) albumsGridFirstItemFocusRequester else personsGridFirstItemFocusRequester

            TextField(
                value = filterValue,
                onValueChange = { updated ->
                    if (isAlbums) viewModel.setAlbumsFilterQuery(updated)
                    else viewModel.setPersonsFilterQuery(updated)
                },
                placeholder = { Text(filterPlaceholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    try { downTarget.requestFocus() } catch (_: Exception) {}
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("topbar_search_field")
                    .focusRequester(searchFieldFocusRequester)
                    .focusProperties { down = downTarget }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionDown || event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            keyboardController?.hide()
                            try {
                                downTarget.requestFocus()
                                true
                            } catch (_: Exception) {
                                focusManager.moveFocus(FocusDirection.Down)
                            }
                        } else {
                            false
                        }
                    }
            )
        }
    } else {
        val titleText = when (activeTab) {
            ActiveTab.GALLERY -> {
                if (galleryViewMode == GalleryViewMode.DATE) {
                    val currentPath = pathHistory.lastOrNull() ?: ""
                    when {
                        currentPath.isEmpty() || currentPath == defaultRootPath -> stringResource(R.string.gallery)
                        currentPath.startsWith("year:") && !currentPath.contains("month:") -> currentPath.substringAfter("year:")
                        currentPath.contains("month:") -> {
                            val year = currentPath.substringAfter("year:").substringBefore("/")
                            val monthNum = currentPath.substringAfter("month:").toIntOrNull() ?: 1
                            "${viewModel.getMonthName(monthNum)} $year"
                        }
                        else -> stringResource(R.string.gallery)
                    }
                } else {
                    val currentFolder = pathHistory.lastOrNull()?.substringAfterLast('/') ?: stringResource(R.string.root)
                    if (currentFolder.isEmpty()) stringResource(R.string.gallery) else currentFolder
                }
            }
            ActiveTab.ALBUMS -> selectedAlbum?.name ?: stringResource(R.string.albums)
            ActiveTab.PERSONS -> selectedPerson?.name ?: stringResource(R.string.persons)
            ActiveTab.REDISCOVER -> stringResource(R.string.rediscover)
            ActiveTab.SETTINGS -> stringResource(R.string.settings)
        }

        val subtitleText: String? = when (activeTab) {
            ActiveTab.GALLERY -> {
                if (galleryState is GalleryUiState.Success) {
                    val visibleFoldersCount = (galleryState.directory.directories ?: emptyList()).count {
                        if (hideProtectedItems && viewModel.isFolderProtected(it.path)) false
                        else if (galleryViewMode == GalleryViewMode.DATE) (it.mediaCount ?: 0) > 0
                        else it.mediaCount == null || it.mediaCount > 0
                    }
                    val mediaCount = galleryState.directory.media?.size ?: 0
                    when {
                        visibleFoldersCount > 0 && mediaCount > 0 ->
                            stringResource(R.string.folders_and_items_count, visibleFoldersCount, mediaCount)
                        visibleFoldersCount > 0 ->
                            stringResource(R.string.folders_count, visibleFoldersCount)
                        mediaCount > 0 ->
                            stringResource(R.string.items_count, mediaCount)
                        else -> null
                    }
                } else null
            }
            ActiveTab.ALBUMS -> {
                if (selectedAlbum != null) {
                    if (albumContentState is GalleryUiState.Success) {
                        stringResource(R.string.items_count, albumContentState.directory.media?.size ?: 0)
                    } else null
                } else if (albumsState is AlbumsUiState.Success) {
                    val total = albumsState.albums.size
                    if (albumsFilterQuery.isNotBlank()) {
                        val matched = albumsState.albums.count { it.name.contains(albumsFilterQuery, ignoreCase = true) }
                        stringResource(R.string.albums_filtered_count, matched, total)
                    } else if (total > 0) {
                        stringResource(R.string.albums_count, total)
                    } else null
                } else null
            }
            ActiveTab.PERSONS -> {
                if (selectedPerson != null) {
                    if (personContentState is GalleryUiState.Success) {
                        stringResource(R.string.items_count, personContentState.directory.media?.size ?: 0)
                    } else null
                } else if (personsState is PersonsUiState.Success) {
                    val total = personsState.persons.size
                    if (personsFilterQuery.isNotBlank()) {
                        val matched = personsState.persons.count { it.name.contains(personsFilterQuery, ignoreCase = true) }
                        stringResource(R.string.persons_filtered_count, matched, total)
                    } else if (total > 0) {
                        stringResource(R.string.persons_count, total)
                    } else null
                } else null
            }
            ActiveTab.REDISCOVER -> {
                if (rediscoverState is RediscoverUiState.Success) {
                    val total = rediscoverState.groupedMedia.values.sumOf { it.size }
                    if (total > 0) stringResource(R.string.memories_count, total) else null
                } else null
            }
            ActiveTab.SETTINGS -> null
        }

        Column {
            Text(
                text = titleText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitleText.isNullOrBlank()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("topbar_subtitle")
                )
            }
        }
    }
}

@Composable
private fun UnifiedOverflowMenu(
    viewModel: GalleryViewModel,
    activeTab: ActiveTab,
    tvCapabilities: TvFeatureCapabilities,
    isSelectMode: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    galleryViewMode: GalleryViewMode,
    isFlattened: Boolean,
    pathHistory: List<String>,
    selectedAlbum: ApiAlbum?,
    selectedPerson: ApiPerson?,
    canManageAlbums: Boolean,
    isSharingAvailable: Boolean,
    isCreatingShare: Boolean,
    shareDialogTarget: ShareTarget?,
    onOpenSaveAlbum: (String) -> Unit,
    onOpenDeleteAlbum: (ApiAlbum) -> Unit,
    onOpenShareDialog: (ShareTarget) -> Unit,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
    val menuCoroutineScope = rememberCoroutineScope()
    LaunchedEffect(activeTab, isSelectMode) {
        showMoreMenu = false
    }
    val dismissMenuAndRun: (() -> Unit) -> Unit = { action ->
        if (showMoreMenu) {
            showMoreMenu = false
            menuCoroutineScope.launch {
                delay(200)
                action()
            }
        }
    }

    Box {
        IconButton(
            onClick = {
                viewModel.refreshUserPermissions(force = false)
                showMoreMenu = true
            },
            modifier = Modifier
                .tvFocus(shape = CircleShape)
                .testTag("more_options_button")
        ) {
            if (isCreatingShare && shareDialogTarget == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .testTag("topbar_share_loading_indicator"),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
            }
        }

        DropdownMenu(
            expanded = showMoreMenu,
            onDismissRequest = { showMoreMenu = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            // --- Group 1: Context-Sensitive View & Content Actions ---
            if (activeTab == ActiveTab.GALLERY) {
                val nextViewMode = if (galleryViewMode == GalleryViewMode.DATE) GalleryViewMode.FOLDER else GalleryViewMode.DATE
                DropdownMenuItem(
                    text = { Text(if (galleryViewMode == GalleryViewMode.DATE) stringResource(R.string.view_by_folder) else stringResource(R.string.view_by_date)) },
                    onClick = {
                        dismissMenuAndRun { viewModel.setGalleryViewMode(nextViewMode) }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (galleryViewMode == GalleryViewMode.DATE) Icons.Default.Folder else Icons.Default.DateRange,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .tvFocus(shape = RoundedCornerShape(8.dp))
                        .testTag("menu_toggle_view_mode")
                )
                DropdownMenuItem(
                    text = { Text(if (isFlattened) stringResource(R.string.unflatten_directory) else stringResource(R.string.flatten_directory)) },
                    onClick = {
                        dismissMenuAndRun { viewModel.toggleFlattened() }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isFlattened) Icons.Default.GridView else Icons.Default.Layers,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .tvFocus(shape = RoundedCornerShape(8.dp))
                        .testTag("menu_toggle_flatten")
                )
                if (tvCapabilities.canManageAlbums(canManageAlbums) && isSearchActive && searchQuery.isNotBlank()) {
                    val querySnapshot = searchQuery
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save_search_as_album)) },
                        onClick = {
                            dismissMenuAndRun { onOpenSaveAlbum(querySnapshot) }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .tvFocus(shape = RoundedCornerShape(8.dp))
                            .testTag("menu_save_search_as_album")
                    )
                }
                if (tvCapabilities.allowItemLocking && galleryViewMode == GalleryViewMode.FOLDER && (pathHistory.lastOrNull() ?: "").isNotEmpty()) {
                    val currentFolder = pathHistory.last()
                    val isProtected = viewModel.isFolderProtected(currentFolder)
                    val isDirectlyProtected = viewModel.isFolderDirectlyProtected(currentFolder)
                    val isInheritedProtected = isProtected && !isDirectlyProtected

                    if (isInheritedProtected) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.protected_inherited)) },
                            onClick = {
                                dismissMenuAndRun {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.protected_inherited_toast),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier
                                .tvFocus(shape = RoundedCornerShape(8.dp))
                                .testTag("menu_protect_folder")
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(if (isDirectlyProtected) stringResource(R.string.unprotect_folder) else stringResource(R.string.protect_folder)) },
                            onClick = {
                                dismissMenuAndRun {
                                    val activity = context.findActivity() as? FragmentActivity
                                    if (activity != null) {
                                        BiometricHelper.authenticate(
                                            activity = activity,
                                            title = context.getString(if (isDirectlyProtected) R.string.unprotect_folder else R.string.protect_folder),
                                            subtitle = context.getString(R.string.confirm_folder_protection_subtitle),
                                            onSuccess = {
                                                viewModel.toggleProtectedFolder(currentFolder)
                                            },
                                            onError = { err ->
                                                if (err != BiometricHelper.ERROR_CANCELED) {
                                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    } else {
                                        viewModel.toggleProtectedFolder(currentFolder)
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .tvFocus(shape = RoundedCornerShape(8.dp))
                                .testTag("menu_protect_folder")
                        )
                    }
                }
            }

            // Destructive album deletion inside Album detail
            val showDeleteAlbum = tvCapabilities.canManageAlbums(canManageAlbums) && activeTab == ActiveTab.ALBUMS && selectedAlbum != null && !selectedAlbum.locked
            if (showDeleteAlbum) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.delete_album),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        dismissMenuAndRun { onOpenDeleteAlbum(selectedAlbum) }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier
                        .tvFocus(shape = RoundedCornerShape(8.dp))
                        .testTag("menu_delete_album")
                )
            }

            val hasGroup1Items = activeTab == ActiveTab.GALLERY || showDeleteAlbum

            // --- Group 2: Share Link Action ---
            val currentShareTarget: ShareTarget? = if (!tvCapabilities.allowShareLinks) null else when (activeTab) {
                ActiveTab.GALLERY -> {
                    if (isSearchActive && searchQuery.isNotBlank()) {
                        val parsed = viewModel.parseSearchStringToQuery(searchQuery)
                        if (parsed.isNotEmpty()) {
                            ShareTarget.Search(
                                label = searchQuery.trim(),
                                searchQuery = parsed,
                                kind = ShareTargetKind.SEARCH
                            )
                        } else null
                    } else if (galleryViewMode == GalleryViewMode.FOLDER) {
                        ShareTarget.Directory(path = pathHistory.lastOrNull() ?: "")
                    } else null
                }
                ActiveTab.ALBUMS -> {
                    val query = selectedAlbum?.searchQuery
                    if (selectedAlbum != null && !query.isNullOrEmpty()) {
                        ShareTarget.Search(
                            label = selectedAlbum.name,
                            searchQuery = query,
                            kind = ShareTargetKind.ALBUM
                        )
                    } else null
                }
                ActiveTab.PERSONS -> {
                    if (selectedPerson != null) {
                        ShareTarget.Search(
                            label = selectedPerson.name,
                            searchQuery = viewModel.buildPersonSearchQuery(selectedPerson),
                            kind = ShareTargetKind.PERSON
                        )
                    } else null
                }
                else -> null
            }
            val showShareAction = tvCapabilities.canCreateShareLinks(isSharingAvailable) && currentShareTarget != null
            if (showShareAction) {
                if (hasGroup1Items) {
                    HorizontalDivider(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag("menu_share_divider")
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(
                                if (isCreatingShare) R.string.share_creating_button else R.string.create_share_link
                            )
                        )
                    },
                    onClick = {
                        dismissMenuAndRun { onOpenShareDialog(currentShareTarget) }
                    },
                    enabled = !isCreatingShare,
                    leadingIcon = {
                        if (isCreatingShare) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .testTag("menu_share_loading_indicator"),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier
                        .tvFocus(shape = RoundedCornerShape(8.dp))
                        .testTag("menu_create_share_link")
                )
            }

            // --- Group 3: Global App Section ---
            if (hasGroup1Items || showShareAction) {
                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .testTag("menu_global_divider")
                )
            }

            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                onClick = {
                    dismissMenuAndRun { viewModel.setActiveTab(ActiveTab.SETTINGS) }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null
                    )
                },
                modifier = Modifier
                    .tvFocus(shape = RoundedCornerShape(8.dp))
                    .testTag("menu_settings")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.about)) },
                onClick = {
                    dismissMenuAndRun { onOpenAbout() }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null
                    )
                },
                modifier = Modifier
                    .tvFocus(shape = RoundedCornerShape(8.dp))
                    .testTag("menu_about")
            )
        }
    }
}

private enum class UnifiedTopBarMode {
    NORMAL,
    SEARCH,
    SELECT
}
