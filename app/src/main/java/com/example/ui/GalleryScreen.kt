package com.example.ui
import androidx.compose.ui.text.style.TextAlign

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material.icons.filled.Clear
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.ApiAlbum
import com.example.data.ApiDirectory
import com.example.data.ApiMedia
import com.example.data.ApiSubFolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeTab by viewModel.activeTab.collectAsState()
    val pathHistory by viewModel.pathHistory.collectAsState()
    val selectedMedia by viewModel.selectedMedia.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedMediaForShare by viewModel.selectedMediaForShare.collectAsState()
    
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var textFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(searchQuery)) }
    LaunchedEffect(searchQuery) {
        if (searchQuery != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = searchQuery,
                selection = androidx.compose.ui.text.TextRange(searchQuery.length)
            )
        }
    }
    val isFlattened by viewModel.isFlattened.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }
    
    // Album Tab sub-navigation states
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedPerson by viewModel.selectedPerson.collectAsState()

    // Handle system back navigation nicely
    BackHandler(enabled = true) {
        val handled = viewModel.goBackFolder()
        if (!handled) {
            // If we are on Gallery, do nothing or exit app, but let's allow going back to root folder or gallery if we are in other tabs
            if (activeTab != ActiveTab.GALLERY) {
                viewModel.setActiveTab(ActiveTab.GALLERY)
            } else {
                (context as? android.app.Activity)?.finish()
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
                            text = "${selectedMediaForShare.size} selected",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    } else if (activeTab == ActiveTab.GALLERY && isSearchActive) {
                            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                            val focusRequester = remember { FocusRequester() }
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
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
                                placeholder = { Text("Search") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                                    viewModel.executeSearch()
                                    keyboardController?.hide()
                                }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                    } else {
                        val titleText = when (activeTab) {
                            ActiveTab.GALLERY -> {
                                val currentFolder = pathHistory.lastOrNull()?.substringAfterLast('/') ?: "Root"
                                if (currentFolder.isEmpty()) "Gallery" else currentFolder
                            }
                            ActiveTab.ALBUMS -> {
                                selectedAlbum?.name ?: "Albums"
                            }
                            ActiveTab.PERSONS -> {
                                selectedPerson?.name ?: "Persons"
                            }
                            ActiveTab.REDISCOVER -> "Rediscover"
                            ActiveTab.SETTINGS -> "Settings"
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
                                contentDescription = "Exit selection"
                            )
                        }
                    } else if (activeTab == ActiveTab.GALLERY && pathHistory.size > 1) {
                        IconButton(onClick = { viewModel.goBackFolder() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go back"
                            )
                        }
                    } else if (activeTab == ActiveTab.ALBUMS && selectedAlbum != null) {
                        IconButton(onClick = { viewModel.selectAlbum(null) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to albums"
                            )
                        }
                    } else if (activeTab == ActiveTab.PERSONS && selectedPerson != null) {
                        IconButton(onClick = { viewModel.clearSelectedPerson() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to persons"
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectMode) {
                        IconButton(onClick = { viewModel.shareSelectedMedia(context) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share selected items"
                            )
                        }
                        IconButton(onClick = { viewModel.downloadSelectedMedia(context) }) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download selected items"
                            )
                        }
                    } else {
                        if (activeTab == ActiveTab.GALLERY) {
                            // Search Button
                            IconButton(onClick = { 
                                if (isSearchActive && searchQuery.isNotEmpty()) {
                                    viewModel.updateSearchQueryText("")
                                } else {
                                    viewModel.toggleSearchActive() 
                                }
                            }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = if (isSearchActive) "Close Search" else "Search"
                                )
                            }

                        }
                        
                        if (activeTab == ActiveTab.PERSONS && selectedPerson != null) {
                            IconButton(onClick = { selectedPerson?.let { viewModel.togglePersonFavourite(it) } }) {
                                val isFav = selectedPerson?.isFavourite == true
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Toggle favorite",
                                    tint = if (isFav) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                        
                        if (activeTab == ActiveTab.GALLERY || activeTab == ActiveTab.ALBUMS || activeTab == ActiveTab.PERSONS) {
                            // Sorting Menu Button
                            var showSortDialog by remember { mutableStateOf(false) }
                            IconButton(onClick = { showSortDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort Options"
                                )
                            }
                            if (showSortDialog) {
                                val showFolderSort = when(activeTab) {
                                    ActiveTab.GALLERY -> true
                                    ActiveTab.ALBUMS -> selectedAlbum == null
                                    ActiveTab.PERSONS -> selectedPerson == null
                                    else -> false
                                }
                                val showMediaSort = when(activeTab) {
                                    ActiveTab.GALLERY -> true
                                    ActiveTab.ALBUMS -> selectedAlbum != null
                                    ActiveTab.PERSONS -> selectedPerson != null
                                    else -> false
                                }
                                val folderSortTitle = when(activeTab) {
                                    ActiveTab.ALBUMS -> "Sort albums by"
                                    ActiveTab.PERSONS -> "Sort persons by"
                                    else -> "Sort folders by"
                                }
                                val showFolderSortOptions = activeTab == ActiveTab.GALLERY
                                SortDialog(
                                    viewModel = viewModel,
                                    showFolderSort = showFolderSort,
                                    showMediaSort = showMediaSort,
                                    showFolderSortOptions = showFolderSortOptions,
                                    folderSortTitle = folderSortTitle,
                                    onDismiss = { showSortDialog = false }
                                )
                            }
                        }
                        
                        if (activeTab == ActiveTab.SETTINGS) {
                        }
                        
                        var showMoreMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            if (activeTab == ActiveTab.GALLERY) {
                                DropdownMenuItem(
                                    text = { Text(if (isFlattened) "Unflatten Directory" else "Flatten Directory") },
                                    onClick = {
                                        viewModel.toggleFlattened()
                                        showMoreMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isFlattened) Icons.Default.GridView else Icons.Default.Layers,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    viewModel.setActiveTab(ActiveTab.SETTINGS)
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    showAboutDialog = true
                                    showMoreMenu = false
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            // Bottom navigation tabs (hidden when in select mode to keep UI clean)
            if (!isSelectMode) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = activeTab == ActiveTab.GALLERY,
                        onClick = { viewModel.setActiveTab(ActiveTab.GALLERY) },
                        icon = { Icon(Icons.Default.Image, contentDescription = "Gallery") },
                        label = { Text("Gallery") }
                    )
                    NavigationBarItem(
                        selected = activeTab == ActiveTab.ALBUMS,
                        onClick = { viewModel.setActiveTab(ActiveTab.ALBUMS) },
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Albums") },
                        label = { Text("Albums") }
                    )
                    NavigationBarItem(
                        selected = activeTab == ActiveTab.PERSONS,
                        onClick = { viewModel.setActiveTab(ActiveTab.PERSONS) },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Persons") },
                        label = { Text("Persons") }
                    )
                    NavigationBarItem(
                        selected = activeTab == ActiveTab.REDISCOVER,
                        onClick = { viewModel.setActiveTab(ActiveTab.REDISCOVER) },
                        icon = { Icon(Icons.Default.History, contentDescription = "Rediscover") },
                        label = { Text("Rediscover") }
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
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { shareProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Screen content based on active tab
                when (activeTab) {
                    ActiveTab.GALLERY -> {
                        GalleryTabContent(viewModel = viewModel)
                    }
                    ActiveTab.ALBUMS -> {
                        AlbumsTabContent(viewModel = viewModel)
                    }
                    ActiveTab.PERSONS -> {
                        PersonsTabContent(viewModel = viewModel)
                    }
                    ActiveTab.REDISCOVER -> {
                        RediscoverTabContent(viewModel = viewModel)
                    }
                    ActiveTab.SETTINGS -> {
                        SettingsTabContent(viewModel = viewModel)
                    }
                }
            }


        }
    }

    // Fullscreen view popup dialog
    selectedMedia?.let { media ->
        MediaViewerDialog(
            media = media,
            viewModel = viewModel,
            onDismiss = { viewModel.selectMedia(null) }
        )
    }
}
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryTabContent(viewModel: GalleryViewModel) {
    val galleryState by viewModel.galleryState.collectAsState()
    val pathHistory by viewModel.pathHistory.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val showSearchSuggestions by viewModel.showSearchSuggestions.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            BreadcrumbBar(
                pathHistory = pathHistory,
                onBreadcrumbClick = { index -> viewModel.navigateToBreadcrumb(index) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            val isRefreshing = galleryState is GalleryUiState.Loading
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.loadCurrentDirectory() },
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
                            viewModel.loadCurrentDirectory()
                        }
                    }
                    is GalleryUiState.Success -> {
                        val directory = state.directory
                        val rawSubfolders = directory.directories ?: emptyList()
                        val subfolders = remember(rawSubfolders) {
                            rawSubfolders.filter { (it.mediaCount ?: 0) > 0 }
                        }
                        val mediaList = directory.media ?: emptyList()

                        if (subfolders.isEmpty() && mediaList.isEmpty()) {
                            EmptyStateView("This folder is empty", "No subdirectories or images found inside this folder.")
                        } else {
                            GalleryContentGrid(
                                subfolders = subfolders,
                                mediaList = mediaList,
                                viewModel = viewModel,
                                onFolderClick = { folder -> viewModel.enterFolder(folder.path) },
                                onMediaClick = { media ->
                                    viewModel.selectMedia(media, mediaList)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isSearchActive && showSearchSuggestions) {
            SearchSuggestionsOverlay(
                searchQuery = searchQuery,
                searchSuggestions = searchSuggestions,
                onSuggestionClick = { clickedSuggestion ->
                    viewModel.setSearchQueryFromSuggestion(clickedSuggestion)
                },
                onPrefixClick = { prefix ->
                    viewModel.appendPrefixToSearch(prefix)
                },
                onRemoveToken = { token ->
                    viewModel.removeTokenFromSearch(token)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchSuggestionsOverlay(
    searchQuery: String,
    searchSuggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onPrefixClick: (String) -> Unit,
    onRemoveToken: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = com.example.data.search.SearchQueryParser.tokenize(searchQuery)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (tokens.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tokens.forEach { token ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveToken(token) },
                        label = { Text(token) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            enabled = true,
                            selected = false
                        )
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        }

        if (searchSuggestions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No suggestions",
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
                items(searchSuggestions) { suggestion ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionClick(suggestion) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsTabContent(viewModel: GalleryViewModel) {
    val albumsState by viewModel.albumsState.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val albumContentState by viewModel.albumContentState.collectAsState()

    val itemsPerRowPortrait by viewModel.itemsPerRowPortrait.collectAsState()
    val itemsPerRowLandscape by viewModel.itemsPerRowLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemsPerRow = if (isLandscape) itemsPerRowLandscape else itemsPerRowPortrait
    val spacing by viewModel.spacing.collectAsState()
    val cornerRadius by viewModel.cornerRadius.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()

    if (selectedAlbum != null) {
        // Render current album's content
        Column(modifier = Modifier.fillMaxSize()) {
            val isRefreshing = albumContentState is GalleryUiState.Loading
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { selectedAlbum?.let { viewModel.selectAlbum(it) } },
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                when (val state = albumContentState) {
                    is GalleryUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is GalleryUiState.Error -> {
                        ErrorStateView(message = state.message) {
                            selectedAlbum?.let { viewModel.selectAlbum(it) }
                        }
                    }
                    is GalleryUiState.Success -> {
                        val mediaList = state.directory.media ?: emptyList()
                        if (mediaList.isEmpty()) {
                            EmptyStateView("No items in album", "This query yielded no matching media results.")
                        } else {
                            GalleryContentGrid(
                                subfolders = emptyList(),
                                mediaList = mediaList,
                                viewModel = viewModel,
                                onFolderClick = {},
                                onMediaClick = { media ->
                                    viewModel.selectMedia(media, mediaList)
                                }
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    } else {
        // Render Album list
        val isRefreshing = albumsState is AlbumsUiState.Loading
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadAlbums() },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = albumsState) {
                is AlbumsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AlbumsUiState.Error -> {
                    ErrorStateView(message = state.message) {
                        viewModel.loadAlbums()
                    }
                }
                is AlbumsUiState.Success -> {
                    val albums = state.albums
                    if (albums.isEmpty()) {
                        EmptyStateView("No saved albums", "Create smart query albums in PiGallery2 web app to view them here.")
                    } else {

                        val spacingDp = spacing.dp
                        val cornerRadiusDp = cornerRadius.dp
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(itemsPerRow),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(spacingDp),
                            horizontalArrangement = Arrangement.spacedBy(spacingDp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(albums) { album ->
                                AlbumCard(
                                    album = album,
                                    viewModel = viewModel,
                                    cornerRadius = cornerRadiusDp,
                                    aspectRatio = aspectRatio
                                ) {
                                    viewModel.selectAlbum(album)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RediscoverTabContent(viewModel: GalleryViewModel) {
    val rediscoverState by viewModel.rediscoverState.collectAsState()
    val daysRange by viewModel.rediscoverDays.collectAsState()
    val itemsPerRowPortrait by viewModel.itemsPerRowPortrait.collectAsState()
    val itemsPerRowLandscape by viewModel.itemsPerRowLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemsPerRow = if (isLandscape) itemsPerRowLandscape else itemsPerRowPortrait
    val spacing by viewModel.spacing.collectAsState()
    val cornerRadius by viewModel.cornerRadius.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Days length settings slider header
        
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        when (val state = rediscoverState) {
            is RediscoverUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is RediscoverUiState.Error -> {
                ErrorStateView(message = state.message) {
                    viewModel.loadRediscover()
                }
            }
            is RediscoverUiState.Success -> {
                val grouped = state.groupedMedia
                if (grouped.isEmpty()) {
                    EmptyStateView("Nothing to rediscover today", "No photos were taken on this day range in previous years.")
                } else {
                    val expandedYears = remember { mutableStateMapOf<Int, Boolean>() }
                
                    val spacingDp = spacing.dp
                    val cornerRadiusDp = cornerRadius.dp
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(itemsPerRow.toInt()),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(spacing.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing.dp)
                    ) {
                        grouped.forEach { (year, mediaList) ->
                            val isExpanded = expandedYears[year] ?: false
                            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                            val diff = currentYear - year
                            
                            // Header
                            item(span = { GridItemSpan(itemsPerRow.toInt()) }) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    val hasMore = mediaList.size > itemsPerRow.toInt()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (hasMore) Modifier.clickable { expandedYears[year] = !isExpanded } else Modifier),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = if (diff == 1) "1 Year Ago ($year)" else "$diff Years Ago ($year)",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${mediaList.size} items",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        if (hasMore) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isExpanded) "Collapse" else "Expand"
                                            )
                                        }
                                    }
                                }
                            }

                            // Content
                            val displayList = if (isExpanded) mediaList else mediaList.take(itemsPerRow.toInt())
                            items(displayList) { media ->
                                RediscoverMediaItem(
                                    media = media, 
                                    viewModel = viewModel, 
                                    list = mediaList,
                                    cornerRadius = cornerRadius.dp,
                                    aspectRatio = aspectRatio
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTabContent(viewModel: GalleryViewModel) {
    val serverUrl by viewModel.savedServerUrl.collectAsState()
    val username by viewModel.savedUsername.collectAsState()

    // Suffix states
    var thumbnailSuffix by remember { mutableStateOf(viewModel.prefs.thumbnailPathSuffix) }
    var videoSuffix by remember { mutableStateOf(viewModel.prefs.videoPathSuffix) }

    // Visual states
    var showItemCount by remember { mutableStateOf(viewModel.prefs.showDirectoryItemCount) }
    var itemsPerRowPortrait by remember { mutableStateOf(viewModel.prefs.itemsPerRowPortrait.toFloat()) }
    var itemsPerRowLandscape by remember { mutableStateOf(viewModel.prefs.itemsPerRowLandscape.toFloat()) }
    var cornerRadius by remember { mutableStateOf(viewModel.prefs.cornerRadius.toFloat()) }
    var spacing by remember { mutableStateOf(viewModel.prefs.spacing.toFloat()) }
    var selectedRatioIndex by remember {
        mutableStateOf(
            when (viewModel.prefs.aspectRatio) {
                1.0f -> 0
                1.5f -> 1 // 3:2
                1.3333333f -> 2 // 4:3
                1.7777778f -> 3 // 16:9
                0.0f -> 4 // Original
                else -> 4
            }
        )
    }

    val ratioOptions = listOf("Square (1:1)", "Classic (3:2)", "Standard (4:3)", "Wide (16:9)", "Original")
    val ratioValues = listOf(1.0f, 1.5f, 1.3333333f, 1.7777778f, 0.0f)

    var selectedSettingsTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Appearance", "Features", "Server")
    val tabIcons = listOf(Icons.Default.Palette, Icons.Default.Star, Icons.Default.Cloud)

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedSettingsTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSettingsTab == index,
                    onClick = { selectedSettingsTab = index },
                    icon = { Icon(imageVector = tabIcons[index], contentDescription = title) },
                    text = { 
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selectedSettingsTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedSettingsTab == 0) {
                // --- 1. Theme Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Theme Color Picker
                        Column {
                            Text(text = "Theme Color", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            val colorsList = listOf(
                                "Purple" to Color(0xFFD0BCFF),
                                "Blue" to Color(0xFF82CFFF),
                                "Green" to Color(0xFF88F3A2),
                                "Orange" to Color(0xFFFFB77C),
                                "Teal" to Color(0xFF80F2DB),
                                "Pink" to Color(0xFFFFAEBA),
                                "Grey" to Color(0xFF8F9099)
                            )
                            
                            val selectedThemeColor by viewModel.themeColorOption.collectAsState()
                            
                            val scrollState = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(scrollState),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                colorsList.forEach { (name, color) ->
                                    val isSelected = selectedThemeColor == name
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable {
                                                viewModel.setThemeColorOption(name)
                                            }
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(
                                                        width = 3.dp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        shape = CircleShape
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = name,
                                                tint = Color.Black,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            if (scrollState.maxValue > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), shape = CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.2f)
                                            .fillMaxHeight()
                                            .align(Alignment.CenterStart)
                                            .graphicsLayer {
                                                val max = scrollState.maxValue
                                                val value = scrollState.value
                                                val fraction = if (max > 0) value.toFloat() / max.toFloat() else 0f
                                                translationX = fraction * (size.width * 4f)
                                            }
                                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Theme Mode Selector
                        Column {
                            Text(text = "Theme Mode", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            val selectedThemeMode by viewModel.themeMode.collectAsState()
                            val themeModes = listOf("Auto", "Light", "Dark")
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                themeModes.forEach { mode ->
                                    val isSelected = selectedThemeMode == mode
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setThemeMode(mode) },
                                        label = { Text(mode) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // --- 2. View Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "View",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Show Directory Item Count Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Show Folder Item Count", style = MaterialTheme.typography.bodyMedium)
                                Text(text = "Display media count under subfolders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = showItemCount,
                                onCheckedChange = {
                                    showItemCount = it
                                    viewModel.setShowDirectoryItemCount(it)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Items Per Row (Portrait) Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Items Per Row (Portrait)", style = MaterialTheme.typography.bodyMedium)
                                Text(text = "${itemsPerRowPortrait.toInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = itemsPerRowPortrait,
                                onValueChange = {
                                    itemsPerRowPortrait = it
                                    viewModel.setItemsPerRowPortrait(it.toInt())
                                },
                                valueRange = 1f..6f,
                                steps = 4
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Items Per Row (Landscape) Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Items Per Row (Landscape)", style = MaterialTheme.typography.bodyMedium)
                                Text(text = "${itemsPerRowLandscape.toInt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = itemsPerRowLandscape,
                                onValueChange = {
                                    itemsPerRowLandscape = it
                                    viewModel.setItemsPerRowLandscape(it.toInt())
                                },
                                valueRange = 2f..10f,
                                steps = 7
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Spacing Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Spacing", style = MaterialTheme.typography.bodyMedium)
                                Text(text = "${spacing.toInt()} dp", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = spacing,
                                onValueChange = {
                                    spacing = it
                                    viewModel.setSpacing(it.toInt())
                                },
                                valueRange = 0f..24f,
                                steps = 24
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Corner Radius Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Corner Radius", style = MaterialTheme.typography.bodyMedium)
                                Text(text = "${cornerRadius.toInt()} dp", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = cornerRadius,
                                onValueChange = {
                                    cornerRadius = it
                                    viewModel.setCornerRadius(it.toInt())
                                },
                                valueRange = 0f..32f,
                                steps = 32
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Aspect Ratio Dropdown/Chips Option
                        Column {
                            Text(text = "Aspect Ratio", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var expanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(ratioOptions[selectedRatioIndex])
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    ) {
                                        ratioOptions.forEachIndexed { index, option ->
                                            DropdownMenuItem(
                                                text = { Text(option) },
                                                onClick = {
                                                    selectedRatioIndex = index
                                                    viewModel.setAspectRatio(ratioValues[index])
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        
                    }
                }
            

            } else if (selectedSettingsTab == 1) {
                // --- 3. Features Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Features",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Rediscover Range
                        Column {
                             Text(text = "Rediscover Range", style = MaterialTheme.typography.bodyMedium)
                             Spacer(modifier = Modifier.height(8.dp))
                             val daysRange by viewModel.rediscoverDays.collectAsState()
                             Text(text = "$daysRange ${if (daysRange == 1) "day" else "days"}")
                             Slider(
                                 value = daysRange.toFloat(),
                                 onValueChange = { viewModel.setRediscoverDays(it.toInt()) },
                                 valueRange = 1f..14f,
                                 steps = 13
                             )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Slideshow Duration
                        Column {
                             Text(text = "Slideshow Duration", style = MaterialTheme.typography.bodyMedium)
                             Spacer(modifier = Modifier.height(8.dp))
                             val slideDuration by viewModel.slideshowDuration.collectAsState()
                             Text(text = "$slideDuration ${if (slideDuration == 1) "second" else "seconds"}")
                             Slider(
                                 value = slideDuration.toFloat(),
                                 onValueChange = { viewModel.setSlideshowDuration(it.toInt()) },
                                 valueRange = 1f..10f,
                                 steps = 8
                             )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // People Fallback to Keywords Switch
                        val peopleFallback by viewModel.peopleFallbackToKeywords.collectAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Keyword Fallback for Persons", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Also show photos where the person's name is found in the keywords or tags (e.g. when face recognition is unavailable)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = peopleFallback,
                                onCheckedChange = {
                                    viewModel.setPeopleFallbackToKeywords(it)
                                }
                            )
                        }
                    }
                }
            } else if (selectedSettingsTab == 2) {
                // --- 4. Server Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Connection Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Connected to: ${viewModel.prefs.serverUrl}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Logged in as: ${if (viewModel.prefs.username.isEmpty()) "Demo / Public User" else viewModel.prefs.username}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        var allowInsecureSsl by remember { mutableStateOf(viewModel.prefs.allowInsecureSsl) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { 
                                allowInsecureSsl = !allowInsecureSsl
                                viewModel.prefs.allowInsecureSsl = allowInsecureSsl
                            }
                        ) {
                            androidx.compose.material3.Switch(
                                checked = allowInsecureSsl,
                                onCheckedChange = { 
                                    allowInsecureSsl = it
                                    viewModel.prefs.allowInsecureSsl = it
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Allow insecure SSL (bad/no cert)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disconnect Server")
                        }
                    }
                }
                
                // --- Suffix Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "API Suffix Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = thumbnailSuffix,
                            onValueChange = {
                                thumbnailSuffix = it
                                viewModel.prefs.thumbnailPathSuffix = it
                            },
                            label = { Text("Thumbnail Path Suffix") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = videoSuffix,
                            onValueChange = {
                                videoSuffix = it
                                viewModel.prefs.videoPathSuffix = it
                            },
                            label = { Text("Video Path Suffix") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // --- Storage Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Speicher",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val cacheSize by viewModel.cacheSize.collectAsState()
                        LaunchedEffect(selectedSettingsTab) {
                            if (selectedSettingsTab == 2) {
                                viewModel.updateCacheSize()
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cache-Speicher",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Aktuell belegt: $cacheSize",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { viewModel.clearCaches() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Leeren")
                            }
                        }
                    }
                }


            }
        }
    }
}

@Composable
fun ThumbnailOverlay(
    name: String,
    count: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                    startY = 100f
                )
            )
    )
    Column(
        modifier = modifier
            .padding(8.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.Start
    ) {
        if (count.isNotBlank()) {
            Text(
                text = count,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
        }
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlbumCard(
    album: ApiAlbum,
    viewModel: GalleryViewModel,
    cornerRadius: Dp,
    aspectRatio: Float,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cookies = viewModel.getCookiesHeader()

    val cardRatio = if (aspectRatio > 0f) aspectRatio else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(cardRatio)
            .clickable { onClick() },
        shape = RoundedCornerShape(cornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val coverName = album.cache?.coverName
            val coverDirectory = album.cache?.coverDirectory

            if (coverName != null && coverDirectory != null) {
                val imageRequest = ImageRequest.Builder(context)
                    .data(viewModel.getAlbumCoverUrl(coverName, coverDirectory))
                    .apply {
                        if (cookies.isNotEmpty()) {
                            setHeader("Cookie", cookies)
                        }
                    }
                    .crossfade(true)
                    .build()

                AsyncImage(
                    model = imageRequest,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            ThumbnailOverlay(
                name = album.name,
                count = "${album.cache?.itemCount ?: 0}"
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RediscoverMediaItem(
    media: ApiMedia,
    viewModel: GalleryViewModel,
    list: List<ApiMedia> = emptyList(),
    cornerRadius: Dp,
    aspectRatio: Float
) {
    val context = LocalContext.current
    val cookies = viewModel.getCookiesHeader()
    val cardRatio = if (aspectRatio > 0f) aspectRatio else 1f
    
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedMediaForShare by viewModel.selectedMediaForShare.collectAsState()
    val isSelected = selectedMediaForShare.contains(media)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(cardRatio)
            .clip(RoundedCornerShape(cornerRadius))
            .combinedClickable(
                onClick = {
                    if (isSelectMode) {
                        viewModel.toggleSelectMedia(media)
                    } else {
                        viewModel.selectMedia(media, list)
                    }
                },
                onLongClick = {
                    viewModel.toggleSelectMedia(media)
                }
            ),
        shape = RoundedCornerShape(cornerRadius),
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val imageRequest = ImageRequest.Builder(context)
                .data(viewModel.getThumbnailUrl(media))
                .apply {
                    if (cookies.isNotEmpty()) {
                        setHeader("Cookie", cookies)
                    }
                }
                .crossfade(true)
                .build()

            AsyncImage(
                model = imageRequest,
                contentDescription = media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (media.isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                val durationStr = formatVideoDuration(media.metadata?.duration)
                if (durationStr.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = durationStr,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            if (isSelectMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSelected) Color.Black.copy(alpha = 0.2f) else Color.Transparent)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryContentGrid(
    subfolders: List<ApiSubFolder>,
    mediaList: List<ApiMedia>,
    viewModel: GalleryViewModel,
    onFolderClick: (ApiSubFolder) -> Unit,
    onMediaClick: (ApiMedia) -> Unit
) {
    val context = LocalContext.current
    val cookies = viewModel.getCookiesHeader()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedMediaForShare by viewModel.selectedMediaForShare.collectAsState()

    val spacing by viewModel.spacing.collectAsState()
    val itemsPerRowPortrait by viewModel.itemsPerRowPortrait.collectAsState()
    val itemsPerRowLandscape by viewModel.itemsPerRowLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemsPerRow = if (isLandscape) itemsPerRowLandscape else itemsPerRowPortrait
    val cornerRadius by viewModel.cornerRadius.collectAsState()
    val showCount by viewModel.showDirectoryItemCount.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()

    val spacingDp = spacing.dp
    val cornerRadiusDp = cornerRadius.dp


    val groupedMedia = remember(mediaList) {
        mediaList.groupBy { media ->
            val timestamp = media.metadata?.creationDate
            if (timestamp == null) "Unbekannt"
            else {
                val ms = if (timestamp < 10000000000L) timestamp * 1000L else timestamp
                try {
                    java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(ms))
                } catch (e: Exception) {
                    "Unbekannt"
                }
            }
        }
    }

                    LazyVerticalGrid(
        columns = GridCells.Fixed(itemsPerRow),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(spacingDp),
        horizontalArrangement = Arrangement.spacedBy(spacingDp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Render Subfolders
        items(subfolders) { folder ->
            val coverUrl = viewModel.getFolderCoverUrl(folder)
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .then(
                        if (!isSelectMode) {
                            Modifier.clickable { onFolderClick(folder) }
                        } else {
                            Modifier.graphicsLayer(alpha = 0.4f)
                        }
                    ),
                shape = RoundedCornerShape(cornerRadiusDp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                    if (coverUrl != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val context = LocalContext.current
                            val imageRequest = remember(coverUrl, cookies) {
                                val builder = ImageRequest.Builder(context)
                                    .data(coverUrl)
                                    .crossfade(true)
                                if (cookies.isNotEmpty()) {
                                    builder.addHeader("Cookie", cookies)
                                }
                                builder.build()
                            }
                            
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = folder.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            ThumbnailOverlay(
                                name = folder.name,
                                count = if (showCount && folder.mediaCount != null) "${folder.mediaCount}" else ""
                            )
                            
                            // ThumbnailOverlay handles name and count display
                            
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Folder",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (showCount && folder.mediaCount != null) {
                                Text(
                                    text = "${folder.mediaCount}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                            }
                            Text(
                                text = folder.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

        // Render Media grid
        groupedMedia.forEach { (monthYear, mediaItems) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = monthYear,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp, end = 4.dp).fillMaxWidth()
                )
            }
            items(mediaItems) { media ->
            val isSelected = selectedMediaForShare.contains(media)

            val cardModifier = Modifier
                .fillMaxWidth()
                .let { modifier ->
                    val ratio = aspectRatio
                    if (ratio > 0f) {
                        modifier.aspectRatio(ratio)
                    } else {
                        modifier
                    }
                }
                .clip(RoundedCornerShape(cornerRadiusDp))
                .combinedClickable(
                    onClick = {
                        if (isSelectMode) {
                            viewModel.toggleSelectMedia(media)
                        } else {
                            onMediaClick(media)
                        }
                    },
                    onLongClick = {
                        viewModel.toggleSelectMedia(media)
                    }
                )

            Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(cornerRadiusDp),
                border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Build dynamic Coil loader with Auth Cookies attached
                    val imageRequest = ImageRequest.Builder(context)
                        .data(viewModel.getThumbnailUrl(media))
                        .apply {
                            if (cookies.isNotEmpty()) {
                                setHeader("Cookie", cookies)
                            }
                        }
                        .crossfade(true)
                        .build()

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = media.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Render video overlay icon
                    if (media.isVideo) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Video",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        val durationStr = formatVideoDuration(media.metadata?.duration)
                        if (durationStr.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = durationStr,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Render Checkbox Indicator if in Select Mode or Item is Selected
                    if (isSelectMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (isSelected) Color.Black.copy(alpha = 0.2f) else Color.Transparent)
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color.White, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .border(2.dp, Color.White, CircleShape)
                                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
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

@Composable
fun BreadcrumbBar(
    pathHistory: List<String>,
    onBreadcrumbClick: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(pathHistory) { index, path ->
            val folderName = if (path.isEmpty()) "Root" else path.substringAfterLast('/')
            val isActive = index == pathHistory.lastIndex

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    Text(
                        text = "/",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Text(
                    text = folderName,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { onBreadcrumbClick(index) }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "An Error Occurred",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Empty",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

fun formatVideoDuration(durationMs: Double?): String {
    if (durationMs == null || durationMs <= 0.0) return ""
    var totalSecs = (durationMs / 1000.0).toLong()
    if (totalSecs == 0L && durationMs > 0.0) {
        totalSecs = durationMs.toLong()
    }
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortDialog(
    viewModel: GalleryViewModel,
    showFolderSort: Boolean = true,
    showMediaSort: Boolean = true,
    showFolderSortOptions: Boolean = true,
    folderSortTitle: String = "Sort folders by",
    onDismiss: () -> Unit
) {
    val currentPath = viewModel.getSortPath()
    
    var folderSortBy by remember { mutableStateOf(viewModel.prefs.getFolderSortBy(currentPath, viewModel.prefs.getFolderSortBy("global", "name"))) }
    var folderSortDir by remember { mutableStateOf(viewModel.prefs.getFolderSortDirection(currentPath, viewModel.prefs.getFolderSortDirection("global", "asc"))) }
    
    var mediaSortBy by remember { mutableStateOf(viewModel.prefs.getMediaSortBy(currentPath, viewModel.prefs.getMediaSortBy("global", "date"))) }
    var mediaSortDir by remember { mutableStateOf(viewModel.prefs.getMediaSortDirection(currentPath, viewModel.prefs.getMediaSortDirection("global", "asc"))) }
    
    var currentFolderOnly by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sort", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (showFolderSort) {
                    if (!showFolderSortOptions) {
                        folderSortBy = "name"
                    }
                    Text(folderSortTitle, style = MaterialTheme.typography.labelMedium)
                    if (showFolderSortOptions) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = folderSortBy == "name", onClick = { folderSortBy = "name" }, label = { Text("Name") })
                            FilterChip(selected = folderSortBy == "date", onClick = { folderSortBy = "date" }, label = { Text("Date") })
                            FilterChip(selected = folderSortBy == "random", onClick = { folderSortBy = "random" }, label = { Text("Random") })
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = folderSortDir == "asc", onClick = { folderSortDir = "asc" }, label = { if (showFolderSortOptions) Text("Ascending") else Text("A - Z") })
                        FilterChip(selected = folderSortDir == "desc", onClick = { folderSortDir = "desc" }, label = { if (showFolderSortOptions) Text("Descending") else Text("Z - A") })
                    }
                    if (showMediaSort) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                if (showMediaSort) {
                    Text("Sort media by", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mediaSortBy == "name", onClick = { mediaSortBy = "name" }, label = { Text("Name") })
                        FilterChip(selected = mediaSortBy == "date", onClick = { mediaSortBy = "date" }, label = { Text("Date") })
                        FilterChip(selected = mediaSortBy == "random", onClick = { mediaSortBy = "random" }, label = { Text("Random") })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mediaSortDir == "asc", onClick = { mediaSortDir = "asc" }, label = { Text("Ascending") })
                        FilterChip(selected = mediaSortDir == "desc", onClick = { mediaSortDir = "desc" }, label = { Text("Descending") })
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { currentFolderOnly = !currentFolderOnly }) {
                        Checkbox(checked = currentFolderOnly, onCheckedChange = { currentFolderOnly = it })
                        Text("Current folder only")
                    }
                } else {
                    currentFolderOnly = true
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        viewModel.updateSort(folderSortBy, folderSortDir, mediaSortBy, mediaSortDir, currentFolderOnly)
                        onDismiss()
                    }) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonsTabContent(viewModel: GalleryViewModel) {
    val personsState by viewModel.personsState.collectAsState()
    val selectedPerson by viewModel.selectedPerson.collectAsState()
    val personContentState by viewModel.personContentState.collectAsState()
    val itemsPerRowPortrait by viewModel.itemsPerRowPortrait.collectAsState()
    val itemsPerRowLandscape by viewModel.itemsPerRowLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemsPerRow = if (isLandscape) itemsPerRowLandscape else itemsPerRowPortrait
    val spacing by viewModel.spacing.collectAsState()

    if (selectedPerson != null) {
        // Render current person's content
        val isRefreshing = personContentState is GalleryUiState.Loading
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { selectedPerson?.let { viewModel.selectPerson(it) } },
            modifier = Modifier.fillMaxSize()
        ) {
                when (val state = personContentState) {
                    is GalleryUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is GalleryUiState.Error -> {
                        ErrorStateView(message = state.message) {
                            selectedPerson?.let { viewModel.selectPerson(it) }
                        }
                    }
                    is GalleryUiState.Success -> {
                        val mediaList = state.directory.media ?: emptyList()
                        if (mediaList.isEmpty()) {
                            EmptyStateView("No items for this person", "This query yielded no matching media results.")
                        } else {
                            GalleryContentGrid(
                                subfolders = emptyList(),
                                mediaList = mediaList,
                                viewModel = viewModel,
                                onFolderClick = {},
                                onMediaClick = { media ->
                                    viewModel.selectMedia(media, mediaList)
                                }
                            )
                        }
                    }
                    else -> {}
                }
            }
    } else {
        // Render persons list
        val isRefreshing = personsState is PersonsUiState.Loading
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadPersons() },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = personsState) {
                is PersonsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is PersonsUiState.Error -> {
                    ErrorStateView(message = state.message) {
                        viewModel.loadPersons()
                    }
                }
                is PersonsUiState.Success -> {
                    if (state.persons.isEmpty()) {
                        EmptyStateView("No persons found", "No faces detected or server hasn't scanned persons yet.")
                    } else {
                        val spacingDp = spacing.dp
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(itemsPerRow),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(spacingDp),
                            verticalArrangement = Arrangement.spacedBy(spacingDp)
                        ) {
                            items(state.persons, key = { it.name }) { person ->
                                PersonItem(
                                    person = person,
                                    viewModel = viewModel,
                                    onClick = { viewModel.selectPerson(person) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonItem(person: com.example.data.ApiPerson, viewModel: GalleryViewModel, onClick: () -> Unit) {
    val serverUrl = viewModel.prefs.serverUrl ?: ""
    val apiPrefix = viewModel.prefs.apiPrefix
    val thumbnailUrl = "${serverUrl}${apiPrefix}/person/${person.name}/thumbnail"
    val cookies = viewModel.prefs.cookies
    val cornerRadius by viewModel.cornerRadius.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()
    val cardRatio = if (aspectRatio > 0f) aspectRatio else 1f
    
    val imgRequest = ImageRequest.Builder(LocalContext.current)
        .data(thumbnailUrl)
        .apply {
            if (cookies.isNotEmpty()) {
                addHeader("Cookie", cookies)
            }
        }
        .crossfade(true)
        .build()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(cardRatio)
            .clickable { onClick() },
        shape = RoundedCornerShape(cornerRadius.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imgRequest,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            ThumbnailOverlay(
                name = person.name,
                count = "${person.cache?.count ?: 0}"
            )
            // Favorite toggle star icon button in the top right corner
            IconButton(
                onClick = { viewModel.togglePersonFavourite(person) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
            ) {
                val isFav = person.isFavourite == true
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Toggle favorite",
                    tint = if (isFav) Color(0xFFFFD700) else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("About PiGallery2") },
        text = {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Assuming we use an icon
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                val context = androidx.compose.ui.platform.LocalContext.current
                val version = try {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    packageInfo.versionName
                } catch (e: Exception) {
                    "1.0"
                }
                Text("Version $version", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "A beautiful and modern gallery app connecting to your PiGallery2 server.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // GitHub Link
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Button(
                    onClick = {
                        uriHandler.openUri("https://github.com/bpatrik/pigallery2") // Update this link to the actual repo later
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_github),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View on GitHub",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
                    append("Built with open source software:\n")
                    append("• Android Jetpack (Apache 2.0)\n")
                    append("• Kotlin (Apache 2.0)\n")
                    append("• Kotlin Coroutines (Apache 2.0)\n")
                    append("• Coil Image Loader (Apache 2.0)\n")
                    append("• OkHttp (Apache 2.0)\n")
                    append("• Retrofit (Apache 2.0)\n")
                    append("• Moshi (Apache 2.0)\n\n")
                    append("These libraries are licensed under the Apache License, Version 2.0. ")
                    append("You may obtain a copy of the License at ")
                    
                    val startIndex = length
                    append("http://www.apache.org/licenses/LICENSE-2.0")
                    val endIndex = length
                    
                    addStyle(
                        style = androidx.compose.ui.text.SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        ),
                        start = startIndex,
                        end = endIndex
                    )
                    addStringAnnotation(
                        tag = "URL",
                        annotation = "http://www.apache.org/licenses/LICENSE-2.0",
                        start = startIndex,
                        end = endIndex
                    )
                }

                androidx.compose.foundation.text.ClickableText(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
