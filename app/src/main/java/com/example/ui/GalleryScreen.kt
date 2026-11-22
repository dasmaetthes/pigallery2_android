package com.example.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.ApiAlbum
import com.example.data.search.SearchQueryParser
import com.example.findActivity
import com.example.util.BiometricHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val isAppLocked by viewModel.isAppLocked.collectAsState()
    if (isAppLocked) {
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
    val searchFieldFocusRequester = remember { FocusRequester() }
    val searchSuggestionsFocusRequester = remember { FocusRequester() }
    val galleryGridFirstItemFocusRequester = remember { FocusRequester() }
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
    var showMapDialog by remember { mutableStateOf(false) }
    var mapInitialMediaId by remember { mutableStateOf<String?>(null) }
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
    val canManageAlbums by viewModel.canManageAlbums.collectAsState()
    val saveAlbumState by viewModel.saveAlbumState.collectAsState()
    val deleteAlbumState by viewModel.deleteAlbumState.collectAsState()

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
    val albumContentState by viewModel.albumContentState.collectAsState()
    val personContentState by viewModel.personContentState.collectAsState()

    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedPerson by viewModel.selectedPerson.collectAsState()
    val protectedFolders by viewModel.protectedFolders.collectAsState()

    val mapMediaList = remember(activeTab, galleryState, albumContentState, personContentState, selectedAlbum, selectedPerson, protectedFolders) {
        when (activeTab) {
            ActiveTab.GALLERY -> {
                if (galleryState is GalleryUiState.Success) {
                    val media = (galleryState as GalleryUiState.Success).directory.media ?: emptyList()
                    if (viewModel.isFolderProtected(viewModel.currentPath)) {
                        media
                    } else {
                        media.filter { !viewModel.isMediaProtected(it) }
                    }
                } else emptyList()
            }
            ActiveTab.ALBUMS -> {
                if (selectedAlbum != null && albumContentState is GalleryUiState.Success) {
                    val media = (albumContentState as GalleryUiState.Success).directory.media ?: emptyList()
                    media.filter { !viewModel.isMediaProtected(it) }
                } else emptyList()
            }
            ActiveTab.PERSONS -> {
                if (selectedPerson != null && personContentState is GalleryUiState.Success) {
                    val media = (personContentState as GalleryUiState.Success).directory.media ?: emptyList()
                    media.filter { !viewModel.isMediaProtected(it) }
                } else emptyList()
            }
            else -> emptyList()
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (isSelectMode) {
                            Text(
                                text = stringResource(R.string.selected_count, selectedMediaForShare.size),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        } else if (activeTab == ActiveTab.GALLERY && isSearchActive) {
                            LaunchedEffect(isSearchActive) {
                                if (isSearchActive) {
                                    try {
                                        delay(100)
                                        searchFieldFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                }
                            }
                            TextField(
                                value = textFieldValue,
                                onValueChange = { newValue ->
                                    textFieldValue = newValue
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
                            Text(
                                text = titleText,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    navigationIcon = {
                        if (isSelectMode) {
                            IconButton(onClick = { viewModel.exitSelectMode() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.exit_selection)
                                )
                            }
                        } else if (activeTab == ActiveTab.GALLERY && pathHistory.size > 1) {
                            IconButton(onClick = { viewModel.goBackFolder() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.go_back)
                                )
                            }
                        } else if (activeTab == ActiveTab.ALBUMS && selectedAlbum != null) {
                            IconButton(onClick = { viewModel.selectAlbum(null) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back_to_albums)
                                )
                            }
                        } else if (activeTab == ActiveTab.PERSONS && selectedPerson != null) {
                            IconButton(onClick = { viewModel.clearSelectedPerson() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back_to_persons)
                                )
                            }
                        }
                    },
                    actions = {
                        if (isSelectMode) {
                            IconButton(onClick = { viewModel.shareSelectedMedia(context) }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share_selected)
                                )
                            }
                            IconButton(onClick = { viewModel.downloadSelectedMedia(context) }) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = stringResource(R.string.download_selected)
                                )
                            }
                        } else {
                            if (activeTab == ActiveTab.GALLERY) {
                                val searchDownTarget = if (showSearchSuggestions) {
                                    searchSuggestionsFocusRequester
                                } else {
                                    galleryGridFirstItemFocusRequester
                                }
                                if (isTv && isSearchActive && searchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            viewModel.executeSearch()
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        },
                                        modifier = Modifier.focusProperties { down = searchDownTarget }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = stringResource(R.string.execute_search),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (isSearchActive) {
                                    if (canManageAlbums && searchQuery.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                queryToSaveAsAlbum = searchQuery
                                                viewModel.resetSaveAlbumState()
                                                showSaveAlbumDialog = true
                                            },
                                            modifier = Modifier
                                                .testTag("save_search_as_album_button")
                                                .focusProperties { down = searchDownTarget }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BookmarkAdd,
                                                contentDescription = stringResource(R.string.save_search_as_album),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { showQueryBuilder = true },
                                        modifier = Modifier.focusProperties { down = searchDownTarget }
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
                                        if (isSearchActive && searchQuery.isNotEmpty()) {
                                            viewModel.updateSearchQueryText("")
                                        } else {
                                            viewModel.toggleSearchActive()
                                        }
                                    },
                                    modifier = if (isSearchActive) {
                                        Modifier.focusProperties { down = searchDownTarget }
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = if (isSearchActive) stringResource(R.string.close_search) else stringResource(R.string.search)
                                    )
                                }
                            }

                            if (activeTab == ActiveTab.PERSONS && selectedPerson != null) {
                                IconButton(onClick = { selectedPerson?.let { viewModel.togglePersonFavourite(it) } }) {
                                    val isFav = selectedPerson?.isFavourite == true
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = stringResource(R.string.toggle_favorite),
                                        tint = if (isFav) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }

                            val hasGeotagged = remember(mapMediaList) {
                                mapMediaList.any { it.metadata?.gps != null }
                            }
                            val showMapButton = !isTv && when (activeTab) {
                                ActiveTab.GALLERY -> hasGeotagged
                                ActiveTab.ALBUMS -> selectedAlbum != null && hasGeotagged
                                ActiveTab.PERSONS -> selectedPerson != null && hasGeotagged
                                else -> false
                            }
                            if (showMapButton) {
                                IconButton(onClick = { showMapDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = stringResource(R.string.map_view)
                                    )
                                }
                            }

                            if (activeTab == ActiveTab.GALLERY || activeTab == ActiveTab.ALBUMS || activeTab == ActiveTab.PERSONS) {
                                var showSortDialog by remember { mutableStateOf(false) }
                                IconButton(onClick = { showSortDialog = true }) {
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
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    if (activeTab == ActiveTab.GALLERY) {
                                        val nextViewMode = if (galleryViewMode == GalleryViewMode.DATE) GalleryViewMode.FOLDER else GalleryViewMode.DATE
                                        DropdownMenuItem(
                                            text = { Text(if (galleryViewMode == GalleryViewMode.DATE) stringResource(R.string.view_by_folder) else stringResource(R.string.view_by_date)) },
                                            onClick = {
                                                dismissMenuAndRun {
                                                    viewModel.setGalleryViewMode(nextViewMode)
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (galleryViewMode == GalleryViewMode.DATE) Icons.Default.Folder else Icons.Default.DateRange,
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isFlattened) stringResource(R.string.unflatten_directory) else stringResource(R.string.flatten_directory)) },
                                            onClick = {
                                                dismissMenuAndRun {
                                                    viewModel.toggleFlattened()
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (isFlattened) Icons.Default.GridView else Icons.Default.Layers,
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                        if (isSearchActive && searchQuery.isNotBlank() && canManageAlbums) {
                                            val querySnapshot = searchQuery
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.save_search_as_album)) },
                                                onClick = {
                                                    dismissMenuAndRun {
                                                        queryToSaveAsAlbum = querySnapshot
                                                        viewModel.resetSaveAlbumState()
                                                        showSaveAlbumDialog = true
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.BookmarkAdd,
                                                        contentDescription = null
                                                    )
                                                },
                                                modifier = Modifier.testTag("menu_save_search_as_album")
                                            )
                                        }
                                    }
                                    if (activeTab == ActiveTab.GALLERY && galleryViewMode == GalleryViewMode.FOLDER && (pathHistory.lastOrNull() ?: "").isNotEmpty()) {
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
                                                }
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
                                                }
                                            )
                                        }
                                    }
                                    val openedAlbum = selectedAlbum
                                    if (activeTab == ActiveTab.ALBUMS && openedAlbum != null && canManageAlbums && !openedAlbum.locked) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.delete_album)) },
                                            onClick = {
                                                dismissMenuAndRun {
                                                    viewModel.resetDeleteAlbumState()
                                                    albumToDelete = openedAlbum
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null
                                                )
                                            },
                                            modifier = Modifier.testTag("menu_delete_album")
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.settings)) },
                                        onClick = {
                                            dismissMenuAndRun {
                                                viewModel.setActiveTab(ActiveTab.SETTINGS)
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.about)) },
                                        onClick = {
                                            dismissMenuAndRun {
                                                showAboutDialog = true
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                if (!isSelectMode) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = activeTab == ActiveTab.GALLERY,
                            onClick = { viewModel.setActiveTab(ActiveTab.GALLERY) },
                            icon = { Icon(Icons.Default.Image, contentDescription = stringResource(R.string.gallery)) },
                            label = { Text(stringResource(R.string.gallery)) }
                        )
                        NavigationBarItem(
                            selected = activeTab == ActiveTab.ALBUMS,
                            onClick = { viewModel.setActiveTab(ActiveTab.ALBUMS) },
                            icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = stringResource(R.string.albums)) },
                            label = { Text(stringResource(R.string.albums)) }
                        )
                        NavigationBarItem(
                            selected = activeTab == ActiveTab.PERSONS,
                            onClick = { viewModel.setActiveTab(ActiveTab.PERSONS) },
                            icon = { Icon(Icons.Default.Person, contentDescription = stringResource(R.string.persons)) },
                            label = { Text(stringResource(R.string.persons)) }
                        )
                        NavigationBarItem(
                            selected = activeTab == ActiveTab.REDISCOVER,
                            onClick = { viewModel.setActiveTab(ActiveTab.REDISCOVER) },
                            icon = { Icon(Icons.Default.History, contentDescription = stringResource(R.string.rediscover)) },
                            label = { Text(stringResource(R.string.rediscover)) }
                        )
                    }
                }
            },
            modifier = modifier.fillMaxSize()
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

                    when (activeTab) {
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
                                }
                            )
                        }
                        ActiveTab.ALBUMS -> AlbumsTabContent(viewModel = viewModel)
                        ActiveTab.PERSONS -> PersonsTabContent(viewModel = viewModel)
                        ActiveTab.REDISCOVER -> RediscoverTabContent(viewModel = viewModel)
                        ActiveTab.SETTINGS -> SettingsTabContent(viewModel = viewModel)
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
                canManageAlbums = canManageAlbums,
                onDismissRequest = { showQueryBuilder = false },
                onSearchApplied = { query ->
                    viewModel.updateSearchQueryText(query)
                    viewModel.executeSearch()
                    showQueryBuilder = false
                },
                onSaveAsAlbum = { query ->
                    showQueryBuilder = false
                    queryToSaveAsAlbum = query
                    viewModel.resetSaveAlbumState()
                    showSaveAlbumDialog = true
                }
            )
        }
        if (showSaveAlbumDialog && canManageAlbums) {
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
            if (canManageAlbums) {
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
        if (showMapDialog && !isTv) {
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
                onOpenMap = { clickedMedia ->
                    mapInitialMediaId = clickedMedia.id?.toString()
                    showMapDialog = true
                    viewModel.selectMedia(null)
                },
                isMapOpen = showMapDialog
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
    onSaveSearchAsAlbum: () -> Unit = {}
) {
    val galleryState by viewModel.galleryState.collectAsState()
    val pathHistory by viewModel.pathHistory.collectAsState()
    val galleryViewMode by viewModel.galleryViewMode.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val showSearchSuggestions by viewModel.showSearchSuggestions.collectAsState()
    val localSearchHistory by viewModel.localSearchHistory.collectAsState(initial = emptyList())
    val canManageAlbums by viewModel.canManageAlbums.collectAsState()
    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isSearchActive && showSearchSuggestions) {
            SearchSuggestionsOverlay(
                searchQuery = searchQuery,
                searchSuggestions = searchSuggestions,
                localSearchHistory = localSearchHistory,
                canManageAlbums = canManageAlbums,
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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
                            ErrorStateView(message = state.message) {
                                viewModel.refreshCurrentDirectory()
                            }
                        }
                        is GalleryUiState.Success -> {
                            val directory = state.directory
                            val rawSubfolders = directory.directories ?: emptyList()
                            val hideProtectedItems by viewModel.hideProtectedItems.collectAsState()
                            val protectedFolders by viewModel.protectedFolders.collectAsState()
                            val subfolders = remember(rawSubfolders, galleryViewMode, hideProtectedItems, protectedFolders) {
                                rawSubfolders.filter {
                                    if (hideProtectedItems && viewModel.isFolderProtected(it.path)) {
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
                                EmptyStateView(
                                    if (isSearchActive) stringResource(R.string.no_search_results) else stringResource(R.string.folder_empty),
                                    if (isSearchActive) stringResource(R.string.no_search_results_subtitle) else stringResource(R.string.folder_empty_subtitle)
                                )
                            } else {
                                GalleryContentGrid(
                                    subfolders = subfolders,
                                    mediaList = mediaList,
                                    viewModel = viewModel,
                                    onFolderClick = { folder -> viewModel.enterFolder(folder.path) },
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

@OptIn(ExperimentalLayoutApi::class)
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
    onExecuteSearch: () -> Unit = {},
    onSaveAsAlbum: () -> Unit = {},
    onRemoveToken: (String) -> Unit
) {
    val tokens = SearchQueryParser.tokenize(searchQuery)
    val hasListItems = searchSuggestions.isNotEmpty() || localSearchHistory.isNotEmpty()
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
                    if (canManageAlbums) {
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
                    item {
                        Text(
                            text = stringResource(R.string.recent_searches),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                    }
                    itemsIndexed(localSearchHistory) { index, historyItem ->
                        val focusMod = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .then(focusMod)
                                .tvFocus(shape = RoundedCornerShape(12.dp))
                                .clickable { onHistoryClick(historyItem) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .then(focusMod)
                                .tvFocus(shape = RoundedCornerShape(12.dp))
                                .clickable { onSuggestionClick(suggestion) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
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
