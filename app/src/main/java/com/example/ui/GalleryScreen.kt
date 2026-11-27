package com.example.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.ApiAlbum
import com.example.data.ShareTarget
import com.example.data.ShareTargetKind
import com.example.data.search.SearchQueryParser
import com.example.findActivity
import com.example.util.BiometricHelper
import com.example.util.rememberTvCapabilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tvCapabilities = rememberTvCapabilities()
    val isTv = tvCapabilities.isTvMode
    val isAppLocked by viewModel.isAppLocked.collectAsState()
    if (tvCapabilities.allowItemLocking && isAppLocked) {
        AppLockScreen(
            viewModel = viewModel,
            onUnlockSuccess = { viewModel.unlockApp() }
        )
        return
    }

    val activeTab by viewModel.activeTab.collectAsState()
    val pathHistory by viewModel.pathHistory.collectAsState()
    val selectedMedia by viewModel.selectedMedia.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedMediaForShare by viewModel.selectedMediaForShare.collectAsState()

    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val showSearchSuggestions by viewModel.showSearchSuggestions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isAlbumsSearchActive by viewModel.isAlbumsSearchActive.collectAsState()
    val albumsFilterQuery by viewModel.albumsFilterQuery.collectAsState()
    val isPersonsSearchActive by viewModel.isPersonsSearchActive.collectAsState()
    val personsFilterQuery by viewModel.personsFilterQuery.collectAsState()

    val searchFieldFocusRequester = remember { FocusRequester() }
    val searchSuggestionsFocusRequester = remember { FocusRequester() }
    val galleryGridFirstItemFocusRequester = remember { FocusRequester() }
    val albumsGridFirstItemFocusRequester = remember { FocusRequester() }
    val personsGridFirstItemFocusRequester = remember { FocusRequester() }
    val rediscoverFirstItemFocusRequester = remember { FocusRequester() }
    val settingsFirstItemFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var textFieldValue by remember { mutableStateOf(TextFieldValue(searchQuery)) }
    LaunchedEffect(searchQuery) {
        if (searchQuery != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = searchQuery,
                selection = TextRange(searchQuery.length)
            )
        }
    }
    val isFlattened by viewModel.isFlattened.collectAsState()
    val galleryViewMode by viewModel.galleryViewMode.collectAsState()
    val defaultRootPath by viewModel.defaultRootPath.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }
    var showQueryBuilder by remember { mutableStateOf(false) }
    var showSaveAlbumDialog by remember { mutableStateOf(false) }
    var queryToSaveAsAlbum by remember { mutableStateOf("") }
    var albumToDelete by remember { mutableStateOf<ApiAlbum?>(null) }
    var shareDialogTarget by remember { mutableStateOf<ShareTarget?>(null) }
    var showMapDialog by remember { mutableStateOf(false) }
    var mapInitialMediaId by remember { mutableStateOf<String?>(null) }
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
    val canManageAlbums by viewModel.canManageAlbums.collectAsState()
    val saveAlbumState by viewModel.saveAlbumState.collectAsState()
    val deleteAlbumState by viewModel.deleteAlbumState.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val sharingConfig by viewModel.sharingConfig.collectAsState()
    val canCreateShareLink by viewModel.canCreateShareLink.collectAsState()
    val sharingPasswordProtected by viewModel.sharingPasswordProtected.collectAsState()
    val shareLinkState by viewModel.shareLinkState.collectAsState()
    val isSharingAvailable = sharingConfig.isAvailableForRole(userRole) && canCreateShareLink
    val isCreatingShare = shareLinkState is ShareLinkUiState.Creating
    val effectiveIsSelectMode = tvCapabilities.isSelectModeActive(isSelectMode)
    val effectiveCanManageAlbums = tvCapabilities.canManageAlbums(canManageAlbums)
    val effectiveIsSharingAvailable = tvCapabilities.canCreateShareLinks(isSharingAvailable)

    LaunchedEffect(tvCapabilities.allowMultiSelect, isSelectMode) {
        if (!tvCapabilities.allowMultiSelect && isSelectMode) {
            viewModel.exitSelectMode()
        }
    }

    LaunchedEffect(effectiveIsSharingAvailable) {
        if (!effectiveIsSharingAvailable && shareDialogTarget != null) {
            shareDialogTarget = null
            viewModel.resetShareLinkState()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareLinkToastEvents.collect { event ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                when (event) {
                    is ShareLinkToastEvent.Created -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.share_link_created_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is ShareLinkToastEvent.Failed -> {
                        Toast.makeText(
                            context,
                            event.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(saveAlbumState) {
        if (saveAlbumState is SaveAlbumUiState.Success) {
            val savedName = (saveAlbumState as SaveAlbumUiState.Success).albumName
            Toast.makeText(
                context,
                context.getString(R.string.album_saved_toast, savedName),
                Toast.LENGTH_SHORT
            ).show()
            showSaveAlbumDialog = false
            viewModel.resetSaveAlbumState()
        }
    }

    LaunchedEffect(deleteAlbumState) {
        if (deleteAlbumState is DeleteAlbumUiState.Success) {
            val deletedName = (deleteAlbumState as DeleteAlbumUiState.Success).albumName
            Toast.makeText(
                context,
                context.getString(R.string.album_deleted_toast, deletedName),
                Toast.LENGTH_SHORT
            ).show()
            albumToDelete = null
            viewModel.resetDeleteAlbumState()
        }
    }

    val galleryState by viewModel.galleryState.collectAsState()
    val albumsState by viewModel.albumsState.collectAsState()
    val personsState by viewModel.personsState.collectAsState()
    val rediscoverState by viewModel.rediscoverState.collectAsState()
    val albumContentState by viewModel.albumContentState.collectAsState()
    val personContentState by viewModel.personContentState.collectAsState()

    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedPerson by viewModel.selectedPerson.collectAsState()
    val protectedFolders by viewModel.protectedFolders.collectAsState()
    val hideProtectedItems by viewModel.hideProtectedItems.collectAsState()
    val effectiveHideProtectedItems = tvCapabilities.shouldEnforceItemLocking(hideProtectedItems)

    val mapMediaList = remember(activeTab, galleryState, albumContentState, personContentState, rediscoverState, selectedAlbum, selectedPerson, protectedFolders, pathHistory, tvCapabilities.allowItemLocking) {
        when (activeTab) {
            ActiveTab.GALLERY -> {
                if (galleryState is GalleryUiState.Success) {
                    val media = (galleryState as GalleryUiState.Success).directory.media ?: emptyList()
                    if (!tvCapabilities.allowItemLocking || viewModel.isFolderProtected(viewModel.currentPath)) {
                        media
                    } else {
                        media.filter { !viewModel.isMediaProtected(it) }
                    }
                } else emptyList()
            }
            ActiveTab.ALBUMS -> {
                if (selectedAlbum != null && albumContentState is GalleryUiState.Success) {
                    val media = (albumContentState as GalleryUiState.Success).directory.media ?: emptyList()
                    if (!tvCapabilities.allowItemLocking) media else media.filter { !viewModel.isMediaProtected(it) }
                } else emptyList()
            }
            ActiveTab.PERSONS -> {
                if (selectedPerson != null && personContentState is GalleryUiState.Success) {
                    val media = (personContentState as GalleryUiState.Success).directory.media ?: emptyList()
                    if (!tvCapabilities.allowItemLocking) media else media.filter { !viewModel.isMediaProtected(it) }
                } else emptyList()
            }
            ActiveTab.REDISCOVER -> {
                if (rediscoverState is RediscoverUiState.Success) {
                    (rediscoverState as RediscoverUiState.Success).groupedMedia.values.flatten()
                } else emptyList()
            }
            else -> emptyList()
        }
    }

    val currentVisibleMediaList = remember(activeTab, mapMediaList, rediscoverState) {
        when (activeTab) {
            ActiveTab.REDISCOVER -> {
                if (rediscoverState is RediscoverUiState.Success) {
                    (rediscoverState as RediscoverUiState.Success).groupedMedia.values.flatten()
                } else emptyList()
            }
            else -> mapMediaList
        }
    }

    val isTopBarSearchExpanded = when (activeTab) {
        ActiveTab.GALLERY -> isSearchActive
        ActiveTab.ALBUMS -> selectedAlbum == null && isAlbumsSearchActive
        ActiveTab.PERSONS -> selectedPerson == null && isPersonsSearchActive
        else -> false
    }

    BackHandler(enabled = true) {
        if (activeTab == ActiveTab.GALLERY && isSearchActive) {
            if (showSearchSuggestions || !viewModel.goBackFolder()) {
                viewModel.toggleSearchActive()
            }
        } else {
            val handled = viewModel.goBackFolder()
            if (!handled) {
                if (activeTab != ActiveTab.GALLERY) {
                    viewModel.setActiveTab(ActiveTab.GALLERY)
                } else {
                    (context as? Activity)?.finish()
                }
            }
        }
    }

    val isFullscreenOverlayOpen = selectedMedia != null || showAboutDialog
    val activeTabFocusRequester = when (activeTab) {
        ActiveTab.GALLERY -> if (isSearchActive && showSearchSuggestions) {
            searchSuggestionsFocusRequester
        } else {
            galleryGridFirstItemFocusRequester
        }
        ActiveTab.ALBUMS -> albumsGridFirstItemFocusRequester
        ActiveTab.PERSONS -> personsGridFirstItemFocusRequester
        ActiveTab.REDISCOVER -> rediscoverFirstItemFocusRequester
        ActiveTab.SETTINGS -> settingsFirstItemFocusRequester
    }

    var wasOverlayOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isFullscreenOverlayOpen, isTv) {
        if (wasOverlayOpen && !isFullscreenOverlayOpen && isTv) {
            delay(80)
            runCatching { activeTabFocusRequester.requestFocus() }
        }
        wasOverlayOpen = isFullscreenOverlayOpen
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavigationRail = isTv || maxWidth >= 600.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .focusGroup()
                .focusProperties {
                    if (isFullscreenOverlayOpen) {
                        enter = { FocusRequester.Cancel }
                    }
                }
                .then(
                    if (isTv) Modifier.padding(horizontal = 16.dp, vertical = 10.dp) else Modifier
                )
        ) {
            if (useNavigationRail && !effectiveIsSelectMode) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    windowInsets = NavigationRailDefaults.windowInsets,
                    modifier = Modifier
                        .focusGroup()
                        .onPreviewKeyEvent { event ->
                            if (isTv && event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionLeft -> true
                                    Key.DirectionRight -> {
                                        if (activeTab == ActiveTab.SETTINGS) {
                                            runCatching { settingsFirstItemFocusRequester.requestFocus() }.isSuccess ||
                                                focusManager.moveFocus(FocusDirection.Right)
                                        } else {
                                            focusManager.moveFocus(FocusDirection.Right) ||
                                                runCatching { activeTabFocusRequester.requestFocus() }.isSuccess
                                        }
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .testTag("adaptive_navigation_rail")
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = activeTab == ActiveTab.GALLERY,
                        onClick = { viewModel.setActiveTab(ActiveTab.GALLERY) },
                        icon = {
                            Icon(
                                imageVector = if (activeTab == ActiveTab.GALLERY) Icons.Filled.Image else Icons.Outlined.Image,
                                contentDescription = stringResource(R.string.gallery)
                            )
                        },
                        label = { Text(stringResource(R.string.gallery)) },
                        modifier = Modifier
                            .focusProperties { up = FocusRequester.Cancel }
                            .tvFocus(shape = RoundedCornerShape(16.dp))
                            .testTag("nav_tab_gallery")
                    )
                    NavigationRailItem(
                        selected = activeTab == ActiveTab.ALBUMS,
                        onClick = { viewModel.setActiveTab(ActiveTab.ALBUMS) },
                        icon = {
                            Icon(
                                imageVector = if (activeTab == ActiveTab.ALBUMS) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                                contentDescription = stringResource(R.string.albums)
                            )
                        },
                        label = { Text(stringResource(R.string.albums)) },
                        modifier = Modifier
                            .tvFocus(shape = RoundedCornerShape(16.dp))
                            .testTag("nav_tab_albums")
                    )
                    NavigationRailItem(
                        selected = activeTab == ActiveTab.PERSONS,
                        onClick = { viewModel.setActiveTab(ActiveTab.PERSONS) },
                        icon = {
                            Icon(
                                imageVector = if (activeTab == ActiveTab.PERSONS) Icons.Filled.Person else Icons.Outlined.Person,
                                contentDescription = stringResource(R.string.persons)
                            )
                        },
                        label = { Text(stringResource(R.string.persons)) },
                        modifier = Modifier
                            .tvFocus(shape = RoundedCornerShape(16.dp))
                            .testTag("nav_tab_persons")
                    )
                    NavigationRailItem(
                        selected = activeTab == ActiveTab.REDISCOVER,
                        onClick = { viewModel.setActiveTab(ActiveTab.REDISCOVER) },
                        icon = {
                            Icon(
                                imageVector = if (activeTab == ActiveTab.REDISCOVER) Icons.Filled.History else Icons.Outlined.History,
                                contentDescription = stringResource(R.string.rediscover)
                            )
                        },
                        label = { Text(stringResource(R.string.rediscover)) },
                        modifier = Modifier
                            .focusProperties {
                                if (!isTv) down = FocusRequester.Cancel
                            }
                            .tvFocus(shape = RoundedCornerShape(16.dp))
                            .testTag("nav_tab_rediscover")
                    )
                    if (isTv) {
                        Spacer(modifier = Modifier.weight(1f))
                        NavigationRailItem(
                            selected = activeTab == ActiveTab.SETTINGS,
                            onClick = { viewModel.setActiveTab(ActiveTab.SETTINGS) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == ActiveTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.settings)
                                )
                            },
                            label = { Text(stringResource(R.string.settings)) },
                            modifier = Modifier
                                .focusProperties { down = FocusRequester.Cancel }
                                .tvFocus(shape = RoundedCornerShape(16.dp))
                                .testTag("nav_tab_settings")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                VerticalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }

            Scaffold(
                topBar = {
                    UnifiedGlobalTopAppBar(
                        viewModel = viewModel,
                        activeTab = activeTab,
                        isSelectMode = effectiveIsSelectMode,
                        selectedMediaForShare = selectedMediaForShare,
                        currentVisibleMediaList = currentVisibleMediaList,
                        mapMediaList = mapMediaList,
                        isTv = isTv,
                        isTopBarSearchExpanded = isTopBarSearchExpanded,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        textFieldValue = textFieldValue,
                        onTextFieldValueChange = { textFieldValue = it },
                        showSearchSuggestions = showSearchSuggestions,
                        isAlbumsSearchActive = isAlbumsSearchActive,
                        albumsFilterQuery = albumsFilterQuery,
                        isPersonsSearchActive = isPersonsSearchActive,
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
                        isFlattened = isFlattened,
                        hideProtectedItems = effectiveHideProtectedItems,
                        selectedAlbum = selectedAlbum,
                        selectedPerson = selectedPerson,
                        canManageAlbums = effectiveCanManageAlbums,
                        isSharingAvailable = effectiveIsSharingAvailable,
                        isCreatingShare = isCreatingShare,
                        shareDialogTarget = shareDialogTarget,
                        searchFieldFocusRequester = searchFieldFocusRequester,
                        searchSuggestionsFocusRequester = searchSuggestionsFocusRequester,
                        galleryGridFirstItemFocusRequester = galleryGridFirstItemFocusRequester,
                        albumsGridFirstItemFocusRequester = albumsGridFirstItemFocusRequester,
                        personsGridFirstItemFocusRequester = personsGridFirstItemFocusRequester,
                        rediscoverGridFirstItemFocusRequester = rediscoverFirstItemFocusRequester,
                        settingsFirstItemFocusRequester = settingsFirstItemFocusRequester,
                        onOpenQueryBuilder = { showQueryBuilder = true },
                        onOpenMap = { if (tvCapabilities.allowMapView) showMapDialog = true },
                        onOpenAbout = { showAboutDialog = true },
                        onOpenSaveAlbum = { query ->
                            if (effectiveCanManageAlbums) {
                                queryToSaveAsAlbum = query
                                viewModel.resetSaveAlbumState()
                                showSaveAlbumDialog = true
                            }
                        },
                        onOpenDeleteAlbum = { album ->
                            if (effectiveCanManageAlbums) {
                                viewModel.resetDeleteAlbumState()
                                albumToDelete = album
                            }
                        },
                        onOpenShareDialog = { target ->
                            if (effectiveIsSharingAvailable) {
                                viewModel.openShareDialog(target)
                                shareDialogTarget = target
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useNavigationRail && !effectiveIsSelectMode) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 0.dp,
                            windowInsets = NavigationBarDefaults.windowInsets,
                            modifier = Modifier.testTag("adaptive_navigation_bar")
                        ) {
                            NavigationBarItem(
                                selected = activeTab == ActiveTab.GALLERY,
                                onClick = { viewModel.setActiveTab(ActiveTab.GALLERY) },
                                icon = {
                                    Icon(
                                        imageVector = if (activeTab == ActiveTab.GALLERY) Icons.Filled.Image else Icons.Outlined.Image,
                                        contentDescription = stringResource(R.string.gallery)
                                    )
                                },
                                label = { Text(stringResource(R.string.gallery)) },
                                modifier = Modifier.testTag("nav_tab_gallery")
                            )
                            NavigationBarItem(
                                selected = activeTab == ActiveTab.ALBUMS,
                                onClick = { viewModel.setActiveTab(ActiveTab.ALBUMS) },
                                icon = {
                                    Icon(
                                        imageVector = if (activeTab == ActiveTab.ALBUMS) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                                        contentDescription = stringResource(R.string.albums)
                                    )
                                },
                                label = { Text(stringResource(R.string.albums)) },
                                modifier = Modifier.testTag("nav_tab_albums")
                            )
                            NavigationBarItem(
                                selected = activeTab == ActiveTab.PERSONS,
                                onClick = { viewModel.setActiveTab(ActiveTab.PERSONS) },
                                icon = {
                                    Icon(
                                        imageVector = if (activeTab == ActiveTab.PERSONS) Icons.Filled.Person else Icons.Outlined.Person,
                                        contentDescription = stringResource(R.string.persons)
                                    )
                                },
                                label = { Text(stringResource(R.string.persons)) },
                                modifier = Modifier.testTag("nav_tab_persons")
                            )
                            NavigationBarItem(
                                selected = activeTab == ActiveTab.REDISCOVER,
                                onClick = { viewModel.setActiveTab(ActiveTab.REDISCOVER) },
                                icon = {
                                    Icon(
                                        imageVector = if (activeTab == ActiveTab.REDISCOVER) Icons.Filled.History else Icons.Outlined.History,
                                        contentDescription = stringResource(R.string.rediscover)
                                    )
                                },
                                label = { Text(stringResource(R.string.rediscover)) },
                                modifier = Modifier.testTag("nav_tab_rediscover")
                            )
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val shareProgress by viewModel.shareProgress.collectAsState()

                    Column {
                        if (shareProgress >= 0f) {
                            LinearProgressIndicator(
                                progress = { shareProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Crossfade(
                            targetState = activeTab,
                            animationSpec = tween(durationMillis = 180),
                            label = "tab_content_crossfade"
                        ) { tab ->
                            when (tab) {
                                ActiveTab.GALLERY -> {
                                    GalleryTabContent(
                                        viewModel = viewModel,
                                        searchFieldFocusRequester = searchFieldFocusRequester,
                                        searchSuggestionsFocusRequester = searchSuggestionsFocusRequester,
                                        galleryGridFirstItemFocusRequester = galleryGridFirstItemFocusRequester,
                                        onSaveSearchAsAlbum = {
                                            queryToSaveAsAlbum = searchQuery
                                            viewModel.resetSaveAlbumState()
                                            showSaveAlbumDialog = true
                                        },
                                        onOpenQueryBuilder = {
                                            showQueryBuilder = true
                                        }
                                    )
                                }
                                ActiveTab.ALBUMS -> AlbumsTabContent(
                                    viewModel = viewModel,
                                    firstItemFocusRequester = albumsGridFirstItemFocusRequester
                                )
                                ActiveTab.PERSONS -> PersonsTabContent(
                                    viewModel = viewModel,
                                    firstItemFocusRequester = personsGridFirstItemFocusRequester
                                )
                                ActiveTab.REDISCOVER -> RediscoverTabContent(
                                    viewModel = viewModel,
                                    firstItemFocusRequester = rediscoverFirstItemFocusRequester
                                )
                                ActiveTab.SETTINGS -> SettingsTabContent(
                                    viewModel = viewModel,
                                    onOpenAbout = { showAboutDialog = true },
                                    firstItemFocusRequester = settingsFirstItemFocusRequester
                                )
                            }
                        }
                    }
                }
            }
        }

        val updateState by viewModel.updateState.collectAsState()
        (updateState as? GalleryViewModel.UpdateState.NewRelease)?.let { newRelease ->
            if (dismissedUpdateVersion != newRelease.tagName) {
                NewUpdateDialog(
                    newRelease = newRelease,
                    onDismiss = { dismissedUpdateVersion = newRelease.tagName },
                    onUpdate = {
                        dismissedUpdateVersion = newRelease.tagName
                        val intent = Intent(Intent.ACTION_VIEW, newRelease.releaseUrl.toUri())
                        context.startActivity(intent)
                    }
                )
            }
        }

        if (showAboutDialog) {
            AboutScreen(viewModel = viewModel, onBack = { showAboutDialog = false })
        }
        if (showQueryBuilder) {
            AdvancedQueryBuilderDialog(
                viewModel = viewModel,
                canManageAlbums = effectiveCanManageAlbums,
                onDismissRequest = { showQueryBuilder = false },
                onSearchApplied = { query ->
                    viewModel.updateSearchQueryText(query)
                    viewModel.executeSearch()
                    showQueryBuilder = false
                },
                onSaveAsAlbum = { query ->
                    if (effectiveCanManageAlbums) {
                        showQueryBuilder = false
                        queryToSaveAsAlbum = query
                        viewModel.resetSaveAlbumState()
                        showSaveAlbumDialog = true
                    }
                }
            )
        }
        if (showSaveAlbumDialog && effectiveCanManageAlbums) {
            SaveSearchAsAlbumDialog(
                searchQuery = queryToSaveAsAlbum,
                saveAlbumState = saveAlbumState,
                onSave = { albumName ->
                    viewModel.saveSearchAsAlbum(albumName, queryToSaveAsAlbum)
                },
                onDismiss = {
                    showSaveAlbumDialog = false
                    viewModel.resetSaveAlbumState()
                }
            )
        }
        albumToDelete?.let { targetAlbum ->
            if (effectiveCanManageAlbums) {
                DeleteAlbumDialog(
                    album = targetAlbum,
                    deleteAlbumState = deleteAlbumState,
                    onConfirmDelete = {
                        viewModel.deleteAlbum(targetAlbum)
                    },
                    onDismiss = {
                        albumToDelete = null
                        viewModel.resetDeleteAlbumState()
                    }
                )
            }
        }
        shareDialogTarget?.let { target ->
            if (effectiveIsSharingAvailable) {
                ShareLinkDialog(
                    target = target,
                    passwordProtected = sharingPasswordProtected,
                    shareLinkState = shareLinkState,
                    onCreateShare = { includeSubfolders, preventDownload, validity, password ->
                        viewModel.createShareLink(
                            target = target,
                            includeSubfolders = includeSubfolders,
                            validity = validity,
                            password = password,
                            preventDownload = preventDownload
                        )
                    },
                    onDismiss = {
                        shareDialogTarget = null
                        if (shareLinkState !is ShareLinkUiState.Creating) {
                            viewModel.resetShareLinkState()
                        }
                    }
                )
            }
        }
        if (showMapDialog && tvCapabilities.allowMapView) {
            MapBrowserDialog(
                mediaList = mapMediaList,
                viewModel = viewModel,
                initialMediaId = mapInitialMediaId,
                onDismiss = {
                    showMapDialog = false
                    mapInitialMediaId = null
                }
            )
        }

        selectedMedia?.let { media ->
            MediaViewerDialog(
                media = media,
                viewModel = viewModel,
                onDismiss = { viewModel.selectMedia(null) },
                onOpenMap = if (!tvCapabilities.allowMapView) {
                    null
                } else {
                    { clickedMedia ->
                        mapInitialMediaId = clickedMedia.id?.toString()
                        showMapDialog = true
                        viewModel.selectMedia(null)
                    }
                },
                isMapOpen = tvCapabilities.allowMapView && showMapDialog
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryTabContent(
    viewModel: GalleryViewModel,
    searchFieldFocusRequester: FocusRequester = remember { FocusRequester() },
    searchSuggestionsFocusRequester: FocusRequester = remember { FocusRequester() },
    galleryGridFirstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    onSaveSearchAsAlbum: () -> Unit = {},
    onOpenQueryBuilder: () -> Unit = {}
) {
    val galleryState by viewModel.galleryState.collectAsState()
    val pathHistory by viewModel.pathHistory.collectAsState()
    val galleryViewMode by viewModel.galleryViewMode.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val showSearchSuggestions by viewModel.showSearchSuggestions.collectAsState()
    val localSearchHistory by viewModel.localSearchHistory.collectAsState(initial = emptyList())
    val tvCapabilities = rememberTvCapabilities()
    val canManageAlbums by viewModel.canManageAlbums.collectAsState()
    val availableDateYears by viewModel.availableDateYears.collectAsState()
    val isDateIndexLoading by viewModel.isDateIndexLoading.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isSearchActive && showSearchSuggestions) {
            SearchSuggestionsOverlay(
                searchQuery = searchQuery,
                searchSuggestions = searchSuggestions,
                localSearchHistory = localSearchHistory,
                canManageAlbums = tvCapabilities.canManageAlbums(canManageAlbums),
                firstItemFocusRequester = searchSuggestionsFocusRequester,
                onSuggestionClick = { clickedSuggestion ->
                    viewModel.setSearchQueryFromSuggestion(clickedSuggestion)
                    try {
                        searchFieldFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                },
                onHistoryClick = { query ->
                    viewModel.updateSearchQueryText(query)
                    viewModel.executeSearch()
                },
                onRemoveHistoryItem = { query ->
                    viewModel.removeSearchHistoryItem(query)
                },
                onClearHistory = {
                    viewModel.clearSearchHistory()
                },
                onExecuteSearch = { viewModel.executeSearch() },
                onSaveAsAlbum = onSaveSearchAsAlbum,
                onRemoveToken = { token -> viewModel.removeTokenFromSearch(token) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!isSearchActive) {
                    BreadcrumbBar(
                        pathHistory = pathHistory,
                        galleryViewMode = galleryViewMode,
                        onBreadcrumbClick = { index -> viewModel.navigateToBreadcrumb(index) }
                    )

                    if (galleryViewMode == GalleryViewMode.DATE) {
                        DateQuickJumpBar(
                            currentPath = pathHistory.lastOrNull() ?: "",
                            availableYears = availableDateYears,
                            getMonthName = { month -> viewModel.getMonthName(month) },
                            onJumpToDate = { year, month -> viewModel.jumpToDate(year, month) },
                            onSearchInPeriod = { viewModel.searchInCurrentDateScope() }
                        )
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    if (galleryViewMode == GalleryViewMode.DATE && isDateIndexLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .testTag("date_index_progress")
                        )
                    }
                }

                val isRefreshing by viewModel.isRefreshing.collectAsState()
                AdaptivePullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshCurrentDirectory() },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    when (val state = galleryState) {
                        is GalleryUiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is GalleryUiState.Error -> {
                            ErrorStateView(
                                message = state.message,
                                actionFocusRequester = galleryGridFirstItemFocusRequester
                            ) {
                                viewModel.refreshCurrentDirectory()
                            }
                        }
                        is GalleryUiState.Success -> {
                            val directory = state.directory
                            val rawSubfolders = directory.directories ?: emptyList()
                            val hideProtectedItems by viewModel.hideProtectedItems.collectAsState()
                            val protectedFolders by viewModel.protectedFolders.collectAsState()
                            val effectiveHideProtected = tvCapabilities.shouldEnforceItemLocking(hideProtectedItems)
                            val subfolders = remember(rawSubfolders, galleryViewMode, effectiveHideProtected, protectedFolders) {
                                rawSubfolders.filter {
                                    if (effectiveHideProtected && viewModel.isFolderProtected(it.path)) {
                                        false
                                    } else if (galleryViewMode == GalleryViewMode.DATE) {
                                        (it.mediaCount ?: 0) > 0
                                    } else {
                                        it.mediaCount == null || it.mediaCount > 0
                                    }
                                }
                            }
                            val mediaList = directory.media ?: emptyList()

                            if (subfolders.isEmpty() && mediaList.isEmpty()) {
                                if (isSearchActive) {
                                    EmptyStateView(
                                        title = stringResource(R.string.no_search_results),
                                        subtitle = stringResource(R.string.no_search_results_subtitle),
                                        illustration = EmptyStateIllustration.SEARCH,
                                        guidanceText = stringResource(R.string.empty_guidance_search),
                                        actionLabel = stringResource(R.string.empty_action_clear_search),
                                        onAction = { viewModel.toggleSearchActive() },
                                        secondaryActionLabel = stringResource(R.string.empty_action_advanced_search),
                                        onSecondaryAction = onOpenQueryBuilder,
                                        actionFocusRequester = galleryGridFirstItemFocusRequester
                                    )
                                } else if (galleryViewMode == GalleryViewMode.DATE) {
                                    EmptyStateView(
                                        title = stringResource(R.string.empty_date_period_title),
                                        subtitle = stringResource(R.string.empty_date_period_subtitle),
                                        illustration = EmptyStateIllustration.MEMORIES,
                                        guidanceText = stringResource(R.string.empty_guidance_date),
                                        actionLabel = stringResource(R.string.refresh),
                                        onAction = { viewModel.refreshCurrentDirectory() },
                                        actionFocusRequester = galleryGridFirstItemFocusRequester
                                    )
                                } else {
                                    EmptyStateView(
                                        title = stringResource(R.string.folder_empty),
                                        subtitle = stringResource(R.string.folder_empty_subtitle),
                                        illustration = EmptyStateIllustration.MEMORIES,
                                        guidanceText = stringResource(R.string.empty_guidance_folder),
                                        actionLabel = stringResource(R.string.refresh),
                                        onAction = { viewModel.refreshCurrentDirectory() },
                                        actionFocusRequester = galleryGridFirstItemFocusRequester
                                    )
                                }
                            } else {
                                val scrollKey = state.contextKey.ifEmpty { viewModel.buildGalleryContextKey() }
                                key(scrollKey) {
                                    val gridState = rememberPersistedLazyGridState(viewModel, scrollKey)
                                    GalleryContentGrid(
                                        subfolders = subfolders,
                                        mediaList = mediaList,
                                        viewModel = viewModel,
                                        onFolderClick = { folder ->
                                            if (gridState.layoutInfo.totalItemsCount > 0) {
                                                viewModel.saveScrollPosition(
                                                    scrollKey,
                                                    gridState.firstVisibleItemIndex,
                                                    gridState.firstVisibleItemScrollOffset
                                                )
                                            }
                                            viewModel.enterFolder(folder.path)
                                        },
                                        onMediaClick = { media -> viewModel.selectMedia(media, mediaList) },
                                        state = gridState,
                                        firstItemFocusRequester = galleryGridFirstItemFocusRequester
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchSuggestionsOverlay(
    searchQuery: String,
    searchSuggestions: List<String>,
    localSearchHistory: List<String>,
    modifier: Modifier = Modifier,
    canManageAlbums: Boolean = false,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    onSuggestionClick: (String) -> Unit,
    onHistoryClick: (String) -> Unit,
    onRemoveHistoryItem: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onExecuteSearch: () -> Unit = {},
    onSaveAsAlbum: () -> Unit = {},
    onRemoveToken: (String) -> Unit
) {
    val tokens = SearchQueryParser.tokenize(searchQuery)
    val hasListItems = searchSuggestions.isNotEmpty() || localSearchHistory.isNotEmpty()
    val tvCapabilities = rememberTvCapabilities()
    val effectiveCanManageAlbums = tvCapabilities.canManageAlbums(canManageAlbums)
    val showQuickSearchButton = searchQuery.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (tokens.isNotEmpty() || showQuickSearchButton) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showQuickSearchButton) {
                    val quickSearchFocusMod = if (!hasListItems) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                    Button(
                        onClick = onExecuteSearch,
                        modifier = quickSearchFocusMod
                            .testTag("suggestions_execute_search_button")
                            .tvFocus(shape = RoundedCornerShape(20.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.search))
                    }
                    if (effectiveCanManageAlbums) {
                        FilledTonalButton(
                            onClick = onSaveAsAlbum,
                            modifier = Modifier
                                .testTag("suggestions_save_album_button")
                                .tvFocus(shape = RoundedCornerShape(20.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.save_album))
                        }
                    }
                }
                tokens.forEachIndexed { index, token ->
                    val tokenFocusMod = if (!hasListItems && !showQuickSearchButton && index == 0) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                    InputChip(
                        selected = false,
                        onClick = { onRemoveToken(token) },
                        label = { Text(token) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), modifier = Modifier.size(16.dp))
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            enabled = true,
                            selected = false
                        ),
                        modifier = tokenFocusMod.tvFocus(shape = RoundedCornerShape(8.dp))
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        }

        if (searchSuggestions.isEmpty() && localSearchHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_suggestions),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (searchSuggestions.isEmpty() && localSearchHistory.isNotEmpty()) {
                    item(key = "recent_searches_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp, start = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.recent_searches),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = onClearHistory,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("clear_search_history_button")
                                    .tvFocus(shape = RoundedCornerShape(12.dp)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = stringResource(R.string.clear_search_history_cd),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(R.string.clear_history))
                            }
                        }
                    }
                    itemsIndexed(localSearchHistory, key = { _, item -> "history_$item" }) { index, historyItem ->
                        val focusMod = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    onRemoveHistoryItem(historyItem)
                                    true
                                } else {
                                    false
                                }
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            modifier = Modifier.animateItem(),
                            backgroundContent = {
                                val isSwiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSwiping) MaterialTheme.colorScheme.errorContainer
                                            else Color.Transparent
                                        )
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    if (isSwiping) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        ) {
                            Card(
                                onClick = { onHistoryClick(historyItem) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(focusMod)
                                    .tvFocus(shape = RoundedCornerShape(12.dp))
                                    .testTag("search_history_item_$index"),
                                shape = RoundedCornerShape(12.dp),
                                colors = GalleryCardDefaults.sectionCardColors(),
                                elevation = GalleryCardDefaults.sectionCardElevation(),
                                border = GalleryCardDefaults.sectionCardBorder()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = stringResource(R.string.history_cd),
                                        modifier = Modifier.padding(end = 16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = historyItem,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onRemoveHistoryItem(historyItem) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("remove_search_history_item_$index")
                                            .tvFocus(shape = RoundedCornerShape(24.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.remove_search_history_item_cd, historyItem),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (searchSuggestions.isNotEmpty()) {
                    itemsIndexed(searchSuggestions) { index, suggestion ->
                        val focusMod = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }
                        Card(
                            onClick = { onSuggestionClick(suggestion) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(focusMod)
                                .tvFocus(shape = RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = GalleryCardDefaults.sectionCardColors(),
                            elevation = GalleryCardDefaults.sectionCardElevation(),
                            border = GalleryCardDefaults.sectionCardBorder()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search),
                                    modifier = Modifier.padding(end = 16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
