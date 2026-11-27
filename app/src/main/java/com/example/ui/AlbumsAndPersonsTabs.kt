package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ApiAlbum
import com.example.data.ApiPerson
import com.example.data.BackedUpAlbum
import com.example.util.rememberTvCapabilities
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsTabContent(
    viewModel: GalleryViewModel,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val albumsState by viewModel.albumsState.collectAsState()
    val searchQuery by viewModel.albumsFilterQuery.collectAsState()
    val isAlbumsSearchActive by viewModel.isAlbumsSearchActive.collectAsState()
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
        Column(modifier = Modifier.fillMaxSize()) {
            val isRefreshing by viewModel.isAlbumContentRefreshing.collectAsState()
            AdaptivePullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { selectedAlbum?.let { viewModel.refreshAlbumContent(it) } },
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                when (val state = albumContentState) {
                    is GalleryUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is GalleryUiState.Error -> {
                        ErrorStateView(
                            message = state.message,
                            actionFocusRequester = firstItemFocusRequester
                        ) {
                            selectedAlbum?.let { viewModel.selectAlbum(it) }
                        }
                    }
                    is GalleryUiState.Success -> {
                        val mediaList = state.directory.media ?: emptyList()
                        if (mediaList.isEmpty()) {
                            EmptyStateView(
                                title = stringResource(R.string.no_items_in_album),
                                subtitle = stringResource(R.string.no_items_in_album_subtitle),
                                icon = Icons.Outlined.PhotoLibrary,
                                illustration = EmptyStateIllustration.ALBUMS,
                                guidanceText = stringResource(R.string.empty_guidance_album_detail),
                                actionLabel = stringResource(R.string.refresh),
                                onAction = { selectedAlbum?.let { viewModel.refreshAlbumContent(it) } },
                                actionFocusRequester = firstItemFocusRequester
                            )
                        } else {
                            val activeAlbum = selectedAlbum
                            val detailScrollKey = "album_detail:${activeAlbum?.id}_${activeAlbum?.name}"
                            key(detailScrollKey) {
                                val detailGridState = rememberPersistedLazyGridState(viewModel, detailScrollKey)
                                GalleryContentGrid(
                                    subfolders = emptyList(),
                                    mediaList = mediaList,
                                    viewModel = viewModel,
                                    onFolderClick = {},
                                    onMediaClick = { media ->
                                        viewModel.selectMedia(media, mediaList)
                                    },
                                    state = detailGridState,
                                    firstItemFocusRequester = firstItemFocusRequester
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    } else {
        val isRefreshing by viewModel.isAlbumsRefreshing.collectAsState()
        val recoverableAlbums by viewModel.recoverableAlbums.collectAsState()
        val albumRestoreState by viewModel.albumRestoreState.collectAsState()
        val canManageAlbums by viewModel.canManageAlbums.collectAsState()
        var showRestoreDialog by remember { mutableStateOf(false) }
        var showDismissBackupDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current

        LaunchedEffect(albumRestoreState) {
            if (albumRestoreState is AlbumRestoreUiState.Success) {
                val restoredCount = (albumRestoreState as AlbumRestoreUiState.Success).restoredCount
                Toast.makeText(
                    context,
                    context.getString(R.string.album_restore_success_toast, restoredCount),
                    Toast.LENGTH_SHORT
                ).show()
                showRestoreDialog = false
                viewModel.resetAlbumRestoreState()
            }
        }

        val tvCapabilities = rememberTvCapabilities()
        val isTv = tvCapabilities.isTvMode
        val effectiveCanManageAlbums = tvCapabilities.canManageAlbums(canManageAlbums)
        val showRecoveryBanner = recoverableAlbums.isNotEmpty() && effectiveCanManageAlbums && searchQuery.isBlank()

        AdaptivePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshAlbums() },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = albumsState) {
                is AlbumsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AlbumsUiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        actionFocusRequester = firstItemFocusRequester
                    ) {
                        viewModel.loadAlbums()
                    }
                }
                is AlbumsUiState.Success -> {
                    val allAlbums = state.albums
                    val albums = remember(allAlbums, searchQuery) {
                        if (searchQuery.isBlank()) allAlbums else allAlbums.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    var hasInitialTvFocus by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!isAlbumsSearchActive && searchQuery.isNotBlank()) {
                            ActiveFilterBar(
                                query = searchQuery,
                                onClear = { viewModel.setAlbumsFilterQuery("") },
                                onEdit = { viewModel.setAlbumsSearchActive(true) }
                            )
                        }

                        if (albums.isEmpty()) {
                            if (allAlbums.isEmpty()) {
                                if (showRecoveryBanner) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 24.dp, vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            modifier = Modifier.widthIn(max = 480.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            AlbumRestoreBannerCard(
                                                recoverableAlbums = recoverableAlbums,
                                                onRestoreClick = {
                                                    viewModel.resetAlbumRestoreState()
                                                    showRestoreDialog = true
                                                },
                                                onDismissClick = { showDismissBackupDialog = true }
                                            )
                                            OutlinedButton(
                                                onClick = { viewModel.openGlobalSearch() },
                                                modifier = Modifier
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .focusRequester(firstItemFocusRequester)
                                                    .tvFocus(shape = RoundedCornerShape(20.dp))
                                            ) {
                                                Text(stringResource(R.string.search))
                                            }
                                        }
                                    }
                                } else {
                                    EmptyStateView(
                                        title = stringResource(R.string.no_saved_albums),
                                        subtitle = stringResource(R.string.no_saved_albums_subtitle),
                                        icon = Icons.Outlined.PhotoLibrary,
                                        illustration = EmptyStateIllustration.ALBUMS,
                                        guidanceText = stringResource(R.string.empty_guidance_albums),
                                        actionLabel = stringResource(R.string.search),
                                        onAction = { viewModel.openGlobalSearch() },
                                        actionFocusRequester = firstItemFocusRequester
                                    )
                                }
                            } else {
                                EmptyStateView(
                                    title = stringResource(R.string.no_matching_albums),
                                    subtitle = stringResource(R.string.try_different_search),
                                    icon = Icons.Outlined.SearchOff,
                                    illustration = EmptyStateIllustration.SEARCH,
                                    guidanceText = stringResource(R.string.empty_guidance_search),
                                    actionLabel = stringResource(R.string.reset_filter),
                                    onAction = { viewModel.setAlbumsSearchActive(active = false, clearQueryOnClose = true) },
                                    actionFocusRequester = firstItemFocusRequester
                                )
                            }
                        } else {
                            if (showRecoveryBanner) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AlbumRestoreBannerCard(
                                        recoverableAlbums = recoverableAlbums,
                                        onRestoreClick = {
                                            viewModel.resetAlbumRestoreState()
                                            showRestoreDialog = true
                                        },
                                        onDismissClick = { showDismissBackupDialog = true },
                                        modifier = Modifier.widthIn(max = 600.dp)
                                    )
                                }
                            }

                            val spacingDp = if (isTv) spacing.dp.coerceAtLeast(10.dp) else spacing.dp
                            val cornerRadiusDp = cornerRadius.dp
                            val listScrollKey = "albums_list:$searchQuery"

                            key(listScrollKey) {
                                val listGridState = rememberPersistedLazyGridState(viewModel, listScrollKey)

                                LaunchedEffect(albums.isNotEmpty()) {
                                    if (isTv && albums.isNotEmpty() && !hasInitialTvFocus && listGridState.firstVisibleItemIndex == 0) {
                                        hasInitialTvFocus = true
                                        try {
                                            delay(100)
                                            firstItemFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                }

                                LazyVerticalGrid(
                                    state = listGridState,
                                    columns = GridCells.Fixed(itemsPerRow),
                                    contentPadding = PaddingValues(if (isTv) 20.dp else 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(spacingDp),
                                    horizontalArrangement = Arrangement.spacedBy(spacingDp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .focusGroup()
                                ) {
                                    items(count = albums.size, key = { index -> "album_${albums[index].name}_${albums[index].id}" }) { index ->
                                        val album = albums[index]
                                        val focusModifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                                        AlbumCard(
                                            modifier = focusModifier,
                                            album = album,
                                            viewModel = viewModel,
                                            cornerRadius = cornerRadiusDp,
                                            aspectRatio = aspectRatio
                                        ) {
                                            if (listGridState.layoutInfo.totalItemsCount > 0) {
                                                viewModel.saveScrollPosition(
                                                    listScrollKey,
                                                    listGridState.firstVisibleItemIndex,
                                                    listGridState.firstVisibleItemScrollOffset
                                                )
                                            }
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

        if (effectiveCanManageAlbums && showRestoreDialog && recoverableAlbums.isNotEmpty()) {
            RestoreAlbumsDialog(
                recoverableAlbums = recoverableAlbums,
                restoreState = albumRestoreState,
                onConfirmRestore = { selected -> viewModel.restoreBackedUpAlbums(selected) },
                onDismiss = {
                    if (albumRestoreState !is AlbumRestoreUiState.Restoring) {
                        showRestoreDialog = false
                        viewModel.resetAlbumRestoreState()
                    }
                }
            )
        }

        if (effectiveCanManageAlbums && showDismissBackupDialog && recoverableAlbums.isNotEmpty()) {
            DismissAlbumBackupDialog(
                albumCount = recoverableAlbums.size,
                onConfirmDismiss = {
                    showDismissBackupDialog = false
                    viewModel.dismissAlbumBackup()
                },
                onCancel = { showDismissBackupDialog = false }
            )
        }
    }
}

@Composable
fun AlbumCard(
    modifier: Modifier = Modifier,
    album: ApiAlbum,
    viewModel: GalleryViewModel,
    cornerRadius: Dp,
    aspectRatio: Float,
    onClick: () -> Unit
) {
    val cookies = remember { viewModel.getCookiesHeader() }
    val tvCapabilities = rememberTvCapabilities()
    val protectedFolders by viewModel.protectedFolders.collectAsState()
    val coverName = album.cache?.coverName
    val coverDirectory = album.cache?.coverDirectory
    val isCoverProtected = remember(coverDirectory, protectedFolders, tvCapabilities.allowItemLocking) {
        tvCapabilities.allowItemLocking && coverDirectory != null && viewModel.isFolderProtected(coverDirectory)
    }
    val coverUrl = remember(coverName, coverDirectory, isCoverProtected) {
        if (coverName != null && coverDirectory != null && !isCoverProtected) {
            viewModel.getAlbumCoverUrl(coverName, coverDirectory)
        } else {
            null
        }
    }
    val cacheKey = remember(album.id, coverName, coverDirectory) {
        "album_${album.id}_${coverDirectory}_$coverName"
    }

    CollectionCard(
        name = album.name,
        count = "${album.cache?.itemCount ?: 0}",
        coverUrl = coverUrl,
        cacheKey = cacheKey,
        cookies = cookies,
        placeholderIcon = Icons.Outlined.PhotoLibrary,
        cornerRadius = cornerRadius,
        aspectRatio = aspectRatio,
        onClick = onClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonsTabContent(
    viewModel: GalleryViewModel,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val personsState by viewModel.personsState.collectAsState()
    val searchQuery by viewModel.personsFilterQuery.collectAsState()
    val isPersonsSearchActive by viewModel.isPersonsSearchActive.collectAsState()
    val selectedPerson by viewModel.selectedPerson.collectAsState()
    val personContentState by viewModel.personContentState.collectAsState()
    val itemsPerRowPortrait by viewModel.itemsPerRowPortrait.collectAsState()
    val itemsPerRowLandscape by viewModel.itemsPerRowLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemsPerRow = if (isLandscape) itemsPerRowLandscape else itemsPerRowPortrait
    val spacing by viewModel.spacing.collectAsState()

    if (selectedPerson != null) {
        val isRefreshing by viewModel.isPersonContentRefreshing.collectAsState()
        AdaptivePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { selectedPerson?.let { viewModel.refreshPersonContent(it) } },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = personContentState) {
                is GalleryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is GalleryUiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        actionFocusRequester = firstItemFocusRequester
                    ) {
                        selectedPerson?.let { viewModel.selectPerson(it) }
                    }
                }
                is GalleryUiState.Success -> {
                    val mediaList = state.directory.media ?: emptyList()
                    if (mediaList.isEmpty()) {
                        EmptyStateView(
                            title = stringResource(R.string.no_items_for_person),
                            subtitle = stringResource(R.string.no_items_for_person_subtitle),
                            icon = Icons.Outlined.Person,
                            illustration = EmptyStateIllustration.PEOPLE,
                            guidanceText = stringResource(R.string.empty_guidance_person_detail),
                            actionLabel = stringResource(R.string.refresh),
                            onAction = { selectedPerson?.let { viewModel.refreshPersonContent(it) } },
                            actionFocusRequester = firstItemFocusRequester
                        )
                    } else {
                        val activePerson = selectedPerson
                        val detailScrollKey = "person_detail:${activePerson?.name}"
                        key(detailScrollKey) {
                            val detailGridState = rememberPersistedLazyGridState(viewModel, detailScrollKey)
                            GalleryContentGrid(
                                subfolders = emptyList(),
                                mediaList = mediaList,
                                viewModel = viewModel,
                                onFolderClick = {},
                                onMediaClick = { media ->
                                    viewModel.selectMedia(media, mediaList)
                                },
                                state = detailGridState,
                                firstItemFocusRequester = firstItemFocusRequester
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    } else {
        val isRefreshing by viewModel.isPersonsRefreshing.collectAsState()
        AdaptivePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshPersons() },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = personsState) {
                is PersonsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is PersonsUiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        actionFocusRequester = firstItemFocusRequester
                    ) {
                        viewModel.loadPersons()
                    }
                }
                is PersonsUiState.Success -> {
                    val allPersons = state.persons
                    val persons = remember(allPersons, searchQuery) {
                        if (searchQuery.isBlank()) allPersons else allPersons.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    val tvCapabilities = rememberTvCapabilities()
                    val isTv = tvCapabilities.isTvMode
                    var hasInitialTvFocus by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!isPersonsSearchActive && searchQuery.isNotBlank()) {
                            ActiveFilterBar(
                                query = searchQuery,
                                onClear = { viewModel.setPersonsFilterQuery("") },
                                onEdit = { viewModel.setPersonsSearchActive(true) }
                            )
                        }

                        if (persons.isEmpty()) {
                            if (allPersons.isEmpty()) {
                                EmptyStateView(
                                    title = stringResource(R.string.no_persons_found),
                                    subtitle = stringResource(R.string.no_persons_found_subtitle),
                                    icon = Icons.Outlined.Person,
                                    illustration = EmptyStateIllustration.PEOPLE,
                                    guidanceText = stringResource(R.string.empty_guidance_people),
                                    actionLabel = stringResource(R.string.empty_action_open_settings),
                                    onAction = {
                                        viewModel.setSelectedSettingsTab(1)
                                        viewModel.setActiveTab(ActiveTab.SETTINGS)
                                    },
                                    actionFocusRequester = firstItemFocusRequester
                                )
                            } else {
                                EmptyStateView(
                                    title = stringResource(R.string.no_matching_persons),
                                    subtitle = stringResource(R.string.try_different_search),
                                    icon = Icons.Outlined.SearchOff,
                                    illustration = EmptyStateIllustration.SEARCH,
                                    guidanceText = stringResource(R.string.empty_guidance_search),
                                    actionLabel = stringResource(R.string.reset_filter),
                                    onAction = { viewModel.setPersonsSearchActive(active = false, clearQueryOnClose = true) },
                                    actionFocusRequester = firstItemFocusRequester
                                )
                            }
                        } else {
                            val spacingDp = if (isTv) spacing.dp.coerceAtLeast(10.dp) else spacing.dp
                            val listScrollKey = "persons_list:$searchQuery"

                            key(listScrollKey) {
                                val listGridState = rememberPersistedLazyGridState(viewModel, listScrollKey)

                                LaunchedEffect(persons.isNotEmpty()) {
                                    if (isTv && persons.isNotEmpty() && !hasInitialTvFocus && listGridState.firstVisibleItemIndex == 0) {
                                        hasInitialTvFocus = true
                                        try {
                                            delay(100)
                                            firstItemFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                }

                                LazyVerticalGrid(
                                    state = listGridState,
                                    columns = GridCells.Fixed(itemsPerRow),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .focusGroup(),
                                    contentPadding = PaddingValues(if (isTv) 20.dp else 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(spacingDp),
                                    verticalArrangement = Arrangement.spacedBy(spacingDp)
                                ) {
                                    items(
                                        count = persons.size,
                                        key = { index -> "person_${persons[index].id ?: index}_${persons[index].name}" }
                                    ) { index ->
                                        val person = persons[index]
                                        val focusModifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                                        PersonItem(
                                            modifier = focusModifier,
                                            person = person,
                                            viewModel = viewModel,
                                            onClick = {
                                                if (listGridState.layoutInfo.totalItemsCount > 0) {
                                                    viewModel.saveScrollPosition(
                                                        listScrollKey,
                                                        listGridState.firstVisibleItemIndex,
                                                        listGridState.firstVisibleItemScrollOffset
                                                    )
                                                }
                                                viewModel.selectPerson(person)
                                            }
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
}

@Composable
fun PersonItem(
    modifier: Modifier = Modifier,
    person: ApiPerson,
    viewModel: GalleryViewModel,
    onClick: () -> Unit
) {
    val tvCapabilities = rememberTvCapabilities()
    val thumbnailUrl = remember(person.name) { viewModel.getPersonThumbnailUrl(person) }
    val cookies = remember { viewModel.getCookiesHeader() }
    val cornerRadius by viewModel.cornerRadius.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()
    val cacheKey = remember(person.name) { "person_${person.name}" }
    val isFav = person.isFavourite == true

    CollectionCard(
        name = person.name,
        count = "${person.cache?.count ?: 0}",
        coverUrl = thumbnailUrl,
        cacheKey = cacheKey,
        cookies = cookies,
        placeholderIcon = Icons.Outlined.Person,
        cornerRadius = cornerRadius.dp,
        aspectRatio = aspectRatio,
        onClick = onClick,
        modifier = modifier,
        topEndOverlay = if (!tvCapabilities.allowFavoriteMarking) {
            null
        } else {
            {
                IconButton(
                    onClick = { viewModel.togglePersonFavourite(person) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .testTag("person_favorite_button_${person.name}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(R.string.toggle_favorite),
                            tint = if (isFav) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun SaveSearchAsAlbumDialog(
    searchQuery: String,
    saveAlbumState: SaveAlbumUiState,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var albumName by remember(searchQuery) { mutableStateOf("") }
    val nameFieldFocusRequester = remember { FocusRequester() }
    val isSaving = saveAlbumState is SaveAlbumUiState.Saving

    LaunchedEffect(Unit) {
        try {
            delay(100)
            nameFieldFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.BookmarkAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.save_search_as_album),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.save_album_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = searchQuery,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                OutlinedTextField(
                    value = albumName,
                    onValueChange = { albumName = it },
                    label = { Text(stringResource(R.string.album_name_label)) },
                    placeholder = { Text(stringResource(R.string.album_name_placeholder)) },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = saveAlbumState is SaveAlbumUiState.Error,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (albumName.isNotBlank() && !isSaving) {
                                onSave(albumName)
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFieldFocusRequester)
                        .testTag("save_album_name_input")
                )

                if (saveAlbumState is SaveAlbumUiState.Error) {
                    Text(
                        text = saveAlbumState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("save_album_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(albumName) },
                enabled = albumName.isNotBlank() && !isSaving,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("save_album_confirm_button")
                    .tvFocus(shape = RoundedCornerShape(20.dp))
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("save_album_cancel_button")
                    .tvFocus(shape = RoundedCornerShape(20.dp))
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun DeleteAlbumDialog(
    album: ApiAlbum,
    deleteAlbumState: DeleteAlbumUiState,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDeleting = deleteAlbumState is DeleteAlbumUiState.Deleting

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.delete_album),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.delete_album_confirm_message, album.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (deleteAlbumState is DeleteAlbumUiState.Error) {
                    Text(
                        text = deleteAlbumState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("delete_album_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("delete_album_confirm_button")
                    .tvFocus(shape = RoundedCornerShape(20.dp))
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("delete_album_cancel_button")
                    .tvFocus(shape = RoundedCornerShape(20.dp))
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun AlbumRestoreBannerCard(
    recoverableAlbums: List<BackedUpAlbum>,
    onRestoreClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previewNames = remember(recoverableAlbums) {
        val visible = recoverableAlbums.take(3).joinToString(", ") { it.name }
        visible to (recoverableAlbums.size - 3).coerceAtLeast(0)
    }

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("album_restore_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.album_restore_banner_title, recoverableAlbums.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = stringResource(R.string.album_restore_banner_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                val (visibleNames, extraCount) = previewNames
                Text(
                    text = if (extraCount > 0) {
                        stringResource(R.string.album_restore_preview_more, visibleNames, extraCount)
                    } else {
                        visibleNames
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismissClick,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("album_restore_dismiss_button")
                        .tvFocus(shape = RoundedCornerShape(20.dp))
                ) {
                    Text(stringResource(R.string.album_restore_dismiss_action))
                }
                Button(
                    onClick = onRestoreClick,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("album_restore_button")
                        .tvFocus(shape = RoundedCornerShape(20.dp))
                ) {
                    Text(stringResource(R.string.album_restore_action))
                }
            }
        }
    }
}

@Composable
fun RestoreAlbumsDialog(
    recoverableAlbums: List<BackedUpAlbum>,
    restoreState: AlbumRestoreUiState,
    onConfirmRestore: (List<BackedUpAlbum>) -> Unit,
    onDismiss: () -> Unit
) {
    val isRestoring = restoreState is AlbumRestoreUiState.Restoring
    var selectedIndices by remember(recoverableAlbums) {
        mutableStateOf(recoverableAlbums.indices.toSet())
    }
    val dateFormatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val allSelected = selectedIndices.size == recoverableAlbums.size && recoverableAlbums.isNotEmpty()

    AlertDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        modifier = Modifier.testTag("album_restore_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.album_restore_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (recoverableAlbums.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                selectedIndices = if (allSelected) emptySet() else recoverableAlbums.indices.toSet()
                            },
                            enabled = !isRestoring,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("album_restore_toggle_all_button")
                        ) {
                            Text(
                                text = stringResource(
                                    if (allSelected) R.string.album_restore_deselect_all else R.string.album_restore_select_all
                                )
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    recoverableAlbums.forEachIndexed { index, backup ->
                        val checked = selectedIndices.contains(index)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isRestoring) {
                                    selectedIndices = if (checked) {
                                        selectedIndices - index
                                    } else {
                                        selectedIndices + index
                                    }
                                }
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .testTag("album_restore_item_$index"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedIndices = if (isChecked) {
                                        selectedIndices + index
                                    } else {
                                        selectedIndices - index
                                    }
                                },
                                enabled = !isRestoring
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = backup.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (backup.savedAtMs > 0L) {
                                    Text(
                                        text = stringResource(
                                            R.string.album_restore_saved_date,
                                            dateFormatter.format(Date(backup.savedAtMs))
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (restoreState is AlbumRestoreUiState.Restoring) {
                    val progress = if (restoreState.total > 0) {
                        restoreState.completed.toFloat() / restoreState.total.toFloat()
                    } else 0f
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(
                                R.string.album_restore_progress,
                                restoreState.completed,
                                restoreState.total
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (restoreState is AlbumRestoreUiState.Error) {
                    Text(
                        text = restoreState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("album_restore_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = recoverableAlbums.filterIndexed { index, _ -> selectedIndices.contains(index) }
                    onConfirmRestore(chosen)
                },
                enabled = selectedIndices.isNotEmpty() && !isRestoring,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("album_restore_confirm_button")
                    .tvFocus(shape = RoundedCornerShape(20.dp))
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.album_restore_confirm_count, selectedIndices.size))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isRestoring,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("album_restore_cancel_button")
                    .tvFocus(shape = RoundedCornerShape(20.dp))
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun DismissAlbumBackupDialog(
    albumCount: Int,
    onConfirmDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("album_backup_dismiss_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.album_backup_dismiss_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.album_backup_dismiss_dialog_message, albumCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("album_backup_dismiss_confirm_button")
                    .tvFocus(shape = RoundedCornerShape(20.dp))
            ) {
                Text(stringResource(R.string.album_backup_dismiss_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("album_backup_dismiss_cancel_button")
                    .tvFocus(shape = RoundedCornerShape(20.dp))
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
