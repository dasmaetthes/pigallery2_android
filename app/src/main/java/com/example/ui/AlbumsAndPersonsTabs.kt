package com.example.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.ApiAlbum
import com.example.data.ApiPerson
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsTabContent(viewModel: GalleryViewModel) {
    val albumsState by viewModel.albumsState.collectAsState()
    val searchQuery by viewModel.albumsFilterQuery.collectAsState()
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
                        ErrorStateView(message = state.message) {
                            selectedAlbum?.let { viewModel.selectAlbum(it) }
                        }
                    }
                    is GalleryUiState.Success -> {
                        val mediaList = state.directory.media ?: emptyList()
                        if (mediaList.isEmpty()) {
                            EmptyStateView(stringResource(R.string.no_items_in_album), stringResource(R.string.no_items_in_album_subtitle))
                        } else {
                            val activeAlbum = selectedAlbum
                            val detailScrollKey = "album_detail:${activeAlbum?.id}_${activeAlbum?.name}"
                            key(detailScrollKey) {
                                val detailGridState = remember(detailScrollKey) {
                                    val saved = viewModel.getScrollPosition(detailScrollKey)
                                    LazyGridState(saved.index, saved.offset)
                                }
                                LaunchedEffect(detailGridState, detailScrollKey) {
                                    snapshotFlow { detailGridState.firstVisibleItemIndex to detailGridState.firstVisibleItemScrollOffset }
                                        .collect { (index, offset) ->
                                            viewModel.saveScrollPosition(detailScrollKey, index, offset)
                                        }
                                }
                                GalleryContentGrid(
                                    subfolders = emptyList(),
                                    mediaList = mediaList,
                                    viewModel = viewModel,
                                    onFolderClick = {},
                                    onMediaClick = { media ->
                                        viewModel.selectMedia(media, mediaList)
                                    },
                                    state = detailGridState
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
                    ErrorStateView(message = state.message) {
                        viewModel.loadAlbums()
                    }
                }
                is AlbumsUiState.Success -> {
                    val allAlbums = state.albums
                    val albums = remember(allAlbums, searchQuery) {
                        if (searchQuery.isBlank()) allAlbums else allAlbums.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    val context = LocalContext.current
                    val isTv = remember(context) {
                        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                    }
                    val firstItemFocusRequester = remember { FocusRequester() }
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current
                    var hasInitialTvFocus by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setAlbumsFilterQuery(it) },
                            placeholder = { Text(stringResource(R.string.search_albums_hint)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setAlbumsFilterQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    try { firstItemFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            ),
                            shape = RoundedCornerShape(percent = 50),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .focusProperties { down = firstItemFocusRequester }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown &&
                                        (event.key == Key.DirectionDown || event.key == Key.Enter || event.key == Key.NumPadEnter)
                                    ) {
                                        keyboardController?.hide()
                                        try {
                                            firstItemFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                }
                        )

                        if (albums.isEmpty()) {
                            if (allAlbums.isEmpty()) {
                                EmptyStateView(stringResource(R.string.no_saved_albums), stringResource(R.string.no_saved_albums_subtitle))
                            } else {
                                EmptyStateView(stringResource(R.string.no_matching_albums), stringResource(R.string.try_different_search))
                            }
                        } else {
                            val spacingDp = spacing.dp
                            val cornerRadiusDp = cornerRadius.dp
                            val listScrollKey = "albums_list:$searchQuery"

                            key(listScrollKey) {
                                val listGridState = remember(listScrollKey) {
                                    val saved = viewModel.getScrollPosition(listScrollKey)
                                    LazyGridState(saved.index, saved.offset)
                                }
                                LaunchedEffect(listGridState, listScrollKey) {
                                    snapshotFlow { listGridState.firstVisibleItemIndex to listGridState.firstVisibleItemScrollOffset }
                                        .collect { (index, offset) ->
                                            viewModel.saveScrollPosition(listScrollKey, index, offset)
                                        }
                                }

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
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(spacingDp),
                                    horizontalArrangement = Arrangement.spacedBy(spacingDp),
                                    modifier = Modifier.weight(1f).fillMaxWidth()
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
    val context = LocalContext.current
    val cookies = remember { viewModel.getCookiesHeader() }
    val protectedFolders by viewModel.protectedFolders.collectAsState()
    val cardRatio = if (aspectRatio > 0f) aspectRatio else 1f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(cardRatio)
            .clip(RoundedCornerShape(cornerRadius))
            .tvFocus(shape = RoundedCornerShape(cornerRadius))
            .clickable { onClick() },
        shape = RoundedCornerShape(cornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val coverName = album.cache?.coverName
            val coverDirectory = album.cache?.coverDirectory
            val isCoverProtected = remember(coverDirectory, protectedFolders) {
                coverDirectory != null && viewModel.isFolderProtected(coverDirectory)
            }

            if (coverName != null && coverDirectory != null && !isCoverProtected) {
                val coverUrl = remember(coverName, coverDirectory) {
                    viewModel.getAlbumCoverUrl(coverName, coverDirectory)
                }
                val cacheKey = remember(album.id, coverName, coverDirectory) {
                    "album_${album.id}_${coverDirectory}_$coverName"
                }
                val imageRequest = remember(coverUrl, cookies, cacheKey) {
                    ImageRequest.Builder(context)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .data(coverUrl)
                        .apply {
                            if (cookies.isNotEmpty()) {
                                setHeader("Cookie", cookies)
                            }
                        }
                        .crossfade(true)
                        .build()
                }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonsTabContent(viewModel: GalleryViewModel) {
    val personsState by viewModel.personsState.collectAsState()
    val searchQuery by viewModel.personsFilterQuery.collectAsState()
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
                    ErrorStateView(message = state.message) {
                        selectedPerson?.let { viewModel.selectPerson(it) }
                    }
                }
                is GalleryUiState.Success -> {
                    val mediaList = state.directory.media ?: emptyList()
                    if (mediaList.isEmpty()) {
                        EmptyStateView(stringResource(R.string.no_items_for_person), stringResource(R.string.no_items_for_person_subtitle))
                    } else {
                        val activePerson = selectedPerson
                        val detailScrollKey = "person_detail:${activePerson?.name}"
                        key(detailScrollKey) {
                            val detailGridState = remember(detailScrollKey) {
                                val saved = viewModel.getScrollPosition(detailScrollKey)
                                LazyGridState(saved.index, saved.offset)
                            }
                            LaunchedEffect(detailGridState, detailScrollKey) {
                                snapshotFlow { detailGridState.firstVisibleItemIndex to detailGridState.firstVisibleItemScrollOffset }
                                    .collect { (index, offset) ->
                                        viewModel.saveScrollPosition(detailScrollKey, index, offset)
                                    }
                            }
                            GalleryContentGrid(
                                subfolders = emptyList(),
                                mediaList = mediaList,
                                viewModel = viewModel,
                                onFolderClick = {},
                                onMediaClick = { media ->
                                    viewModel.selectMedia(media, mediaList)
                                },
                                state = detailGridState
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
                    ErrorStateView(message = state.message) {
                        viewModel.loadPersons()
                    }
                }
                is PersonsUiState.Success -> {
                    val allPersons = state.persons
                    val persons = remember(allPersons, searchQuery) {
                        if (searchQuery.isBlank()) allPersons else allPersons.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    val context = LocalContext.current
                    val isTv = remember(context) {
                        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                    }
                    val firstItemFocusRequester = remember { FocusRequester() }
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current
                    var hasInitialTvFocus by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setPersonsFilterQuery(it) },
                            placeholder = { Text(stringResource(R.string.search_persons_hint)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setPersonsFilterQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    try { firstItemFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            ),
                            shape = RoundedCornerShape(percent = 50),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .focusProperties { down = firstItemFocusRequester }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown &&
                                        (event.key == Key.DirectionDown || event.key == Key.Enter || event.key == Key.NumPadEnter)
                                    ) {
                                        keyboardController?.hide()
                                        try {
                                            firstItemFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                }
                        )

                        if (persons.isEmpty()) {
                            if (allPersons.isEmpty()) {
                                EmptyStateView(stringResource(R.string.no_persons_found), stringResource(R.string.no_persons_found_subtitle))
                            } else {
                                EmptyStateView(stringResource(R.string.no_matching_persons), stringResource(R.string.try_different_search))
                            }
                        } else {
                            val spacingDp = spacing.dp
                            val listScrollKey = "persons_list:$searchQuery"

                            key(listScrollKey) {
                                val listGridState = remember(listScrollKey) {
                                    val saved = viewModel.getScrollPosition(listScrollKey)
                                    LazyGridState(saved.index, saved.offset)
                                }
                                LaunchedEffect(listGridState, listScrollKey) {
                                    snapshotFlow { listGridState.firstVisibleItemIndex to listGridState.firstVisibleItemScrollOffset }
                                        .collect { (index, offset) ->
                                            viewModel.saveScrollPosition(listScrollKey, index, offset)
                                        }
                                }

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
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(spacingDp),
                                    verticalArrangement = Arrangement.spacedBy(spacingDp)
                                ) {
                                    items(count = persons.size, key = { index -> persons[index].name }) { index ->
                                        val person = persons[index]
                                        val focusModifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                                        PersonItem(
                                            modifier = focusModifier,
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
    }
}

@Composable
fun PersonItem(
    modifier: Modifier = Modifier,
    person: ApiPerson,
    viewModel: GalleryViewModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnailUrl = remember(person.name) { viewModel.getPersonThumbnailUrl(person) }
    val cookies = remember { viewModel.getCookiesHeader() }
    val cornerRadius by viewModel.cornerRadius.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()
    val cardRatio = if (aspectRatio > 0f) aspectRatio else 1f
    val cacheKey = remember(person.name) { "person_${person.name}" }

    val imgRequest = remember(thumbnailUrl, cookies, cacheKey) {
        ImageRequest.Builder(context)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .networkCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .data(thumbnailUrl)
            .apply {
                if (cookies.isNotEmpty()) {
                    addHeader("Cookie", cookies)
                }
            }
            .crossfade(true)
            .build()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(cardRatio)
            .clip(RoundedCornerShape(cornerRadius.dp))
            .tvFocus(shape = RoundedCornerShape(cornerRadius.dp))
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

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { viewModel.togglePersonFavourite(person) },
                    modifier = Modifier.size(48.dp)
                ) {
                    val isFav = person.isFavourite == true
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(R.string.toggle_favorite),
                        tint = if (isFav) Color(0xFFFFD700) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
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
