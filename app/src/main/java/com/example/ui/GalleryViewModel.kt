package com.example.ui
import coil.imageLoader

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ApiAlbum
import com.example.data.ApiAlbumCache
import com.example.data.ApiCoverPhoto
import com.example.data.ApiCoverPhotoDirectory
import com.example.data.ApiDirectory
import com.example.data.ApiMedia
import com.example.data.ApiSubFolder
import com.example.data.ApiSubFolderCache
import com.example.data.PiGalleryApi
import com.example.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    data class Success(val message: String) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

sealed interface GalleryUiState {
    object Loading : GalleryUiState
    data class Success(val directory: ApiDirectory) : GalleryUiState
    data class Error(val message: String) : GalleryUiState
}

enum class GalleryViewMode {
    FOLDER, DATE
}


sealed interface PersonsUiState {
    object Loading : PersonsUiState
    data class Success(val persons: List<com.example.data.ApiPerson>) : PersonsUiState
    data class Error(val message: String) : PersonsUiState
}

sealed interface AlbumsUiState {
    object Loading : AlbumsUiState
    data class Success(val albums: List<ApiAlbum>) : AlbumsUiState
    data class Error(val message: String) : AlbumsUiState
}

sealed interface RediscoverUiState {
    object Loading : RediscoverUiState
    data class Success(val groupedMedia: Map<Int, List<ApiMedia>>) : RediscoverUiState
    data class Error(val message: String) : RediscoverUiState
}

enum class ActiveTab {
    GALLERY,
    ALBUMS,
    PERSONS,
    REDISCOVER,
    SETTINGS
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = PreferencesManager(application)
    val api = PiGalleryApi(application)
    val database = com.example.data.database.DatabaseProvider.getDatabase(application)
    val repo = com.example.data.GalleryRepository(database)
    val searchHistoryManager = com.example.data.SearchHistoryManager(application)

    val localSearchHistory = searchHistoryManager.searchHistory


    // Active Navigation Tab
    private val _activeTab = MutableStateFlow(ActiveTab.GALLERY)
    val activeTab: StateFlow<ActiveTab> = _activeTab.asStateFlow()

    // Login/Connection State
    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    // Gallery State (Folders and Photos)
    private val _galleryState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val galleryState: StateFlow<GalleryUiState> = _galleryState.asStateFlow()

    // Navigation Stack (list of relative path strings)
    private val _pathHistory = MutableStateFlow<List<String>>(buildInitialPathStack(prefs.defaultRootPath))
    val pathHistory: StateFlow<List<String>> = _pathHistory.asStateFlow()

    val currentPath: String
        get() = _pathHistory.value.lastOrNull() ?: ""

    // Fullscreen View Mode selection
    private val _selectedMedia = MutableStateFlow<ApiMedia?>(null)
    val selectedMedia: StateFlow<ApiMedia?> = _selectedMedia.asStateFlow()

    private val _activeMediaList = MutableStateFlow<List<ApiMedia>>(emptyList())
    val activeMediaList: StateFlow<List<ApiMedia>> = _activeMediaList.asStateFlow()
    
    val videoFinished = MutableSharedFlow<Unit>(replay = 0)

    val pagingDataFlow: Flow<PagingData<ApiMedia>> = Pager(
        config = PagingConfig(pageSize = 50, enablePlaceholders = false, prefetchDistance = 10)
    ) {
        MediaPagingSource(_activeMediaList.value)
    }.flow.cachedIn(viewModelScope)

    fun emitVideoFinished() {
        viewModelScope.launch { videoFinished.emit(Unit) }
    }

    // Albums List State
    private val _albumsState = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
    val albumsState: StateFlow<AlbumsUiState> = _albumsState.asStateFlow()

    private val _personsState = MutableStateFlow<PersonsUiState>(PersonsUiState.Loading)
    val personsState: StateFlow<PersonsUiState> = _personsState.asStateFlow()

    private val _selectedPerson = MutableStateFlow<com.example.data.ApiPerson?>(null)
    val selectedPerson: StateFlow<com.example.data.ApiPerson?> = _selectedPerson.asStateFlow()

    private val _personContentState = MutableStateFlow<GalleryUiState?>(null)
    val personContentState: StateFlow<GalleryUiState?> = _personContentState.asStateFlow()


    // Selected Album for detail view (null means viewing albums list)
    private val _selectedAlbum = MutableStateFlow<ApiAlbum?>(null)
    val selectedAlbum: StateFlow<ApiAlbum?> = _selectedAlbum.asStateFlow()

    // Loaded Media content for the selected album
    private val _albumContentState = MutableStateFlow<GalleryUiState?>(null)
    val albumContentState: StateFlow<GalleryUiState?> = _albumContentState.asStateFlow()

    // Rediscover State (Grouped photos by Year)
    private val _rediscoverState = MutableStateFlow<RediscoverUiState>(RediscoverUiState.Loading)
    val rediscoverState: StateFlow<RediscoverUiState> = _rediscoverState.asStateFlow()

    val rediscoverDays = MutableStateFlow(prefs.rediscoverDays)
    val slideshowDuration = MutableStateFlow(prefs.slideshowDuration)
    val peopleFallbackToKeywords = MutableStateFlow(prefs.peopleFallbackToKeywords)
    val autoCheckUpdates = MutableStateFlow(prefs.autoCheckUpdates)

    // Multi-Selection State (for sharing several images)
    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()

    private val _selectedMediaForShare = MutableStateFlow<Set<ApiMedia>>(emptySet())
    val selectedMediaForShare: StateFlow<Set<ApiMedia>> = _selectedMediaForShare.asStateFlow()

    // Fields initialized with saved preferences
    val savedServerUrl = MutableStateFlow(prefs.serverUrl)
    val savedUsername = MutableStateFlow(prefs.username)
    val savedPassword = MutableStateFlow(prefs.password)
    val isLoggedIn = MutableStateFlow(prefs.isLoggedIn)

    // Reactive Visual Settings StateFlows
    val showDirectoryItemCount = MutableStateFlow(prefs.showDirectoryItemCount)
    val itemsPerRow = MutableStateFlow(prefs.itemsPerRow)
    val itemsPerRowPortrait = MutableStateFlow(prefs.itemsPerRowPortrait)
    val itemsPerRowLandscape = MutableStateFlow(prefs.itemsPerRowLandscape)
    val cornerRadius = MutableStateFlow(prefs.cornerRadius)
    val spacing = MutableStateFlow(prefs.spacing)
    val aspectRatio = MutableStateFlow(prefs.aspectRatio)
    val themeColorOption = MutableStateFlow(prefs.themeColor)
    val themeMode = MutableStateFlow(prefs.themeMode)
    val maxBrightnessEnabled = MutableStateFlow(prefs.maxBrightnessEnabled)
    val defaultRootPath = MutableStateFlow(prefs.defaultRootPath)
    val dismissGestureEnabled = MutableStateFlow(prefs.dismissGestureEnabled)
    val showMetadataGestureEnabled = MutableStateFlow(prefs.showMetadataGestureEnabled)

    // Search and directory flattening states
    val isSearchActive = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val isFlattened = MutableStateFlow(false)
    val galleryViewMode = MutableStateFlow(try { GalleryViewMode.valueOf(prefs.galleryViewMode) } catch (e: Exception) { GalleryViewMode.FOLDER })
    val networkConnectionInfo = MutableStateFlow(NetworkConnectionInfo())

    private val directoryCache = java.util.concurrent.ConcurrentHashMap<String, ApiDirectory>()
    private val cachedFlattenedMedia = java.util.concurrent.ConcurrentHashMap<String, List<ApiMedia>>()
    private val lastBackgroundSyncTime = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val fetchSemaphore = Semaphore(24)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun setGalleryViewMode(mode: GalleryViewMode) {
        galleryViewMode.value = mode
        prefs.galleryViewMode = mode.name
        _pathHistory.value = buildInitialPathStack(prefs.defaultRootPath)
        loadCurrentDirectory()
    }
    val searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val showSearchSuggestions = MutableStateFlow(false)

    fun toggleSearchActive() {
        val next = !isSearchActive.value
        isSearchActive.value = next
        showSearchSuggestions.value = next
        if (!next) {
            setSearchQuery("")
            searchSuggestions.value = emptyList()
        }
    }

    fun updateSearchQueryText(query: String) {
        searchQuery.value = query
    }

    fun executeSearch() {
        showSearchSuggestions.value = false
        val currentQuery = searchQuery.value.trim()
        if (currentQuery.isNotEmpty()) {
            viewModelScope.launch {
                searchHistoryManager.addSearchQuery(currentQuery)
            }
        }
        loadCurrentDirectory()
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
        loadCurrentDirectory()
    }

    fun setSearchQueryFromSuggestion(suggestion: String) {
        val current = searchQuery.value
        
        var formattedSuggestion = suggestion
        val colonIndex = suggestion.indexOf(':')
        if (colonIndex > 0 && suggestion.contains(" ")) {
            val prefix = suggestion.substring(0, colonIndex + 1)
            val value = suggestion.substring(colonIndex + 1)
            formattedSuggestion = if (!value.startsWith("\"")) {
                "$prefix\"$value\""
            } else {
                suggestion
            }
        } else if (suggestion.contains(" ") && !suggestion.startsWith("\"")) {
            formattedSuggestion = "\"$suggestion\""
        }

        // Find the boundary of the last token to replace it
        val tokens = com.example.data.search.SearchQueryParser.tokenize(current)
        val lastToken = tokens.lastOrNull() ?: ""
        
        val newQuery = if (lastToken.isNotEmpty()) {
            val lastTokenIndex = current.lastIndexOf(lastToken)
            if (lastTokenIndex != -1) {
                current.substring(0, lastTokenIndex) + formattedSuggestion
            } else {
                formattedSuggestion
            }
        } else {
            current + formattedSuggestion
        }
        
        searchQuery.value = newQuery + " "
        searchSuggestions.value = emptyList()
    }

    fun appendPrefixToSearch(prefix: String) {
        val current = searchQuery.value
        val lastSpaceIndex = current.lastIndexOf(' ')
        val newQuery = if (lastSpaceIndex != -1) {
            current.substring(0, lastSpaceIndex + 1) + prefix
        } else {
            prefix
        }
        searchQuery.value = newQuery
        fetchSearchSuggestions(newQuery)
    }

    fun removeTokenFromSearch(tokenToRemove: String) {
        val current = searchQuery.value
        val tokens = com.example.data.search.SearchQueryParser.tokenize(current).toMutableList()
        val index = tokens.lastIndexOf(tokenToRemove)
        if (index != -1) {
            tokens.removeAt(index)
        }
        val newQuery = tokens.joinToString(" ") + if (tokens.isNotEmpty()) " " else ""
        searchQuery.value = newQuery
        fetchSearchSuggestions(newQuery)
    }

    fun toggleFlattened() {
        isFlattened.value = !isFlattened.value
        loadCurrentDirectory()
    }

    fun getFolderCoverUrl(folder: ApiSubFolder): String? {
        val cover = folder.cache?.cover ?: return null
        val coverName = cover.name
        val coverDir = cover.directory
        val dirPath = if (coverDir.path.isEmpty() || coverDir.path == ".") {
            coverDir.name
        } else {
            "${coverDir.path}/${coverDir.name}"
        }
        val fullPath = if (dirPath.isEmpty()) coverName else "$dirPath/$coverName"
        val sanitizedBase = prefs.serverUrl.trimEnd('/')
        val apiPrefix = prefs.apiPrefix
        val relativePath = encodePath(fullPath)
        val suffix = prefs.thumbnailPathSuffix
        return "$sanitizedBase$apiPrefix/gallery/content/${relativePath}/$suffix"
    }

    private var suggestionsJob: kotlinx.coroutines.Job? = null

    fun fetchSearchSuggestions(text: String) {
        val lastToken = com.example.data.search.SearchQueryParser.tokenize(text).lastOrNull() ?: ""
        if (lastToken.isBlank() || lastToken.equals("and", ignoreCase = true) || lastToken.equals("or", ignoreCase = true)) {
            searchSuggestions.value = emptyList()
            return
        }
        
        var searchType = 100 // ANY_TEXT
        var searchValue = lastToken
        val colonIndex = lastToken.indexOf(':')

        if (colonIndex > 0) {
            val prefix = lastToken.substring(0, colonIndex).lowercase()
            searchValue = lastToken.substring(colonIndex + 1)
            
            searchType = when (prefix) {
                "tag", "keyword" -> 104
                "person" -> 105
                "position", "place" -> 106
                "caption" -> 101
                "filename", "file-name" -> 103
                "directory", "folder" -> 102
                else -> 100
            }
        }
        
        var cleanValue = searchValue
        if (cleanValue.startsWith("\"")) cleanValue = cleanValue.substring(1)
        if (cleanValue.endsWith("\"")) cleanValue = cleanValue.substring(0, cleanValue.length - 1)
        if (cleanValue.startsWith("(")) cleanValue = cleanValue.substring(1)
        if (cleanValue.endsWith(")")) cleanValue = cleanValue.substring(0, cleanValue.length - 1)

        if (cleanValue.isBlank()) {
            searchSuggestions.value = emptyList()
            return
        }

        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val suggestions = api.getAutocompleteSuggestions(prefs.serverUrl, cleanValue, prefs.cookies, prefs.apiPrefix, searchType)
                searchSuggestions.value = suggestions
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun getSortPath(): String {
        return when (_activeTab.value) {
            ActiveTab.ALBUMS -> {
                val album = _selectedAlbum.value
                if (album != null) "album_${album.name}" else "albums_root"
            }
            ActiveTab.PERSONS -> {
                val person = _selectedPerson.value
                if (person != null) "person_${person.name}" else "persons_root"
            }
            else -> {
                if (galleryViewMode.value == GalleryViewMode.DATE) {
                    val path = currentPath
                    if (path.isEmpty()) "date_mode_root" else "date_mode_$path"
                } else {
                    val path = currentPath
                    if (path.isEmpty()) "global" else path
                }
            }
        }
    }

    fun updateSort(
        folderSortBy: String, 
        folderSortDirection: String,
        mediaSortBy: String,
        mediaSortDirection: String,
        currentFolderOnly: Boolean
    ) {
        val path = if (currentFolderOnly) {
            getSortPath()
        } else if (galleryViewMode.value == GalleryViewMode.DATE) {
            "date_mode_global"
        } else {
            "global"
        }
        
        prefs.setFolderSortBy(path, folderSortBy)
        prefs.setFolderSortDirection(path, folderSortDirection)
        prefs.setMediaSortBy(path, mediaSortBy)
        prefs.setMediaSortDirection(path, mediaSortDirection)
        
        if (_activeTab.value == ActiveTab.ALBUMS) {
            val album = _selectedAlbum.value
            if (album != null) {
                val currentContent = _albumContentState.value
                if (currentContent is GalleryUiState.Success) {
                    _albumContentState.value = GalleryUiState.Success(sortDirectory(currentContent.directory))
                } else {
                    loadAlbumContent(album)
                }
            } else {
                val currentContent = _albumsState.value
                if (currentContent is AlbumsUiState.Success) {
                    _albumsState.value = AlbumsUiState.Success(sortAlbums(currentContent.albums))
                } else {
                    loadAlbums()
                }
            }
        } else if (_activeTab.value == ActiveTab.PERSONS) {
            val person = _selectedPerson.value
            if (person != null) {
                val currentContent = _personContentState.value
                if (currentContent is GalleryUiState.Success) {
                    _personContentState.value = GalleryUiState.Success(sortDirectory(currentContent.directory))
                } else {
                    loadPersonContent(person)
                }
            } else {
                val currentContent = _personsState.value
                if (currentContent is PersonsUiState.Success) {
                    _personsState.value = PersonsUiState.Success(sortPersons(currentContent.persons))
                } else {
                    loadPersons()
                }
            }
        } else {
            val currentContent = _galleryState.value
            if (currentContent is GalleryUiState.Success) {
                _galleryState.value = GalleryUiState.Success(sortDirectory(currentContent.directory))
            } else {
                loadCurrentDirectory()
            }
        }
    }


    fun sortAlbums(albums: List<com.example.data.ApiAlbum>): List<com.example.data.ApiAlbum> {
        val currentContextPath = getSortPath()
        val g_fSort = prefs.getFolderSortBy("global")
        val g_fDir = prefs.getFolderSortDirection("global")
        
        val fSort = prefs.getFolderSortBy(currentContextPath, g_fSort)
        val fDir = prefs.getFolderSortDirection(currentContextPath, g_fDir)
        
        val fAsc = fDir == "asc"
        
        return when (fSort.lowercase()) {
            "name", "date" -> {
                if (fAsc) albums.sortedBy { it.name.lowercase() }
                else albums.sortedByDescending { it.name.lowercase() }
            }
            "count" -> {
                if (fAsc) albums.sortedBy { it.cache?.itemCount ?: 0 }
                else albums.sortedByDescending { it.cache?.itemCount ?: 0 }
            }
            "random" -> albums.shuffled()
            else -> albums
        }
    }

    fun sortPersons(persons: List<com.example.data.ApiPerson>): List<com.example.data.ApiPerson> {
        val currentContextPath = getSortPath()
        val g_fSort = prefs.getFolderSortBy("global")
        val g_fDir = prefs.getFolderSortDirection("global")
        
        val fSort = prefs.getFolderSortBy(currentContextPath, g_fSort)
        val fDir = prefs.getFolderSortDirection(currentContextPath, g_fDir)
        
        val fAsc = fDir == "asc"
        
        val sorted = when (fSort.lowercase()) {
            "name", "date" -> {
                if (fAsc) persons.sortedBy { it.name.lowercase() }
                else persons.sortedByDescending { it.name.lowercase() }
            }
            "count" -> {
                if (fAsc) persons.sortedBy { it.cache?.count ?: 0 }
                else persons.sortedByDescending { it.cache?.count ?: 0 }
            }
            "random" -> persons.shuffled()
            else -> persons
        }

        // Always show favorites first (stable sort)
        val localFavs = prefs.getFavoritePersons()
        return sorted.sortedByDescending { localFavs.contains(it.name) || it.isFavourite == true }
    }
    private fun getMonthNumber(dir: ApiSubFolder): Int? {
        if (dir.path.contains("month:")) {
            return dir.path.substringAfter("month:").toIntOrNull()
        }
        val name = dir.name.lowercase().trim()
        return when {
            name.contains("jan") -> 1
            name.contains("feb") -> 2
            name.contains("mär") || name.contains("mar") -> 3
            name.contains("apr") -> 4
            name.contains("mai") || name.contains("may") -> 5
            name.contains("jun") -> 6
            name.contains("jul") -> 7
            name.contains("aug") -> 8
            name.contains("sep") -> 9
            name.contains("okt") || name.contains("oct") -> 10
            name.contains("nov") -> 11
            name.contains("dez") || name.contains("dec") -> 12
            else -> null
        }
    }

    fun sortDirectory(directory: ApiDirectory): ApiDirectory {
        val currentContextPath = getSortPath()
        val isDateMode = galleryViewMode.value == GalleryViewMode.DATE

        val defaultFSort = if (isDateMode) "date" else "name"
        val defaultFDir = if (isDateMode) "desc" else "asc"
        val defaultMSort = "date"
        val defaultMDir = if (isDateMode) "desc" else "asc"

        val globalPath = if (isDateMode) "date_mode_global" else "global"
        val g_fSort = prefs.getFolderSortBy(globalPath, defaultFSort)
        val g_fDir = prefs.getFolderSortDirection(globalPath, defaultFDir)
        val g_mSort = prefs.getMediaSortBy(globalPath, defaultMSort)
        val g_mDir = prefs.getMediaSortDirection(globalPath, defaultMDir)

        val fSort = prefs.getFolderSortBy(currentContextPath, g_fSort)
        val fDir = prefs.getFolderSortDirection(currentContextPath, g_fDir)
        val mSort = prefs.getMediaSortBy(currentContextPath, g_mSort)
        val mDir = prefs.getMediaSortDirection(currentContextPath, g_mDir)
        
        val fAsc = fDir.lowercase() == "asc"
        val mAsc = mDir.lowercase() == "asc"

        val dirs = directory.directories.orEmpty()
        val isMonthFolders = dirs.isNotEmpty() && dirs.all { getMonthNumber(it) != null }
        val isYearFolders = dirs.isNotEmpty() && dirs.all { it.name.toIntOrNull() != null }

        val sortedDirs = when (fSort.lowercase()) {
            "name", "date" -> {
                if (isMonthFolders) {
                    if (fAsc) dirs.sortedBy { getMonthNumber(it) ?: 0 }
                    else dirs.sortedByDescending { getMonthNumber(it) ?: 0 }
                } else if (isYearFolders) {
                    if (fAsc) dirs.sortedBy { it.name.toIntOrNull() ?: 0 }
                    else dirs.sortedByDescending { it.name.toIntOrNull() ?: 0 }
                } else {
                    if (fAsc) dirs.sortedBy { it.name.lowercase() }
                    else dirs.sortedByDescending { it.name.lowercase() }
                }
            }
            "count" -> {
                if (fAsc) dirs.sortedBy { it.mediaCount ?: 0 }
                else dirs.sortedByDescending { it.mediaCount ?: 0 }
            }
            "random" -> dirs.shuffled()
            else -> dirs
        }

        val media = directory.media.orEmpty()
        val sortedMedia = when (mSort.lowercase()) {
            "name" -> {
                if (mAsc) media.sortedBy { it.name.lowercase() }
                else media.sortedByDescending { it.name.lowercase() }
            }
            "date" -> {
                if (mAsc) media.sortedBy { DateUtils.getLocalTimeMs(it.metadata?.creationDate, it.metadata?.creationDateOffset) }
                else media.sortedByDescending { DateUtils.getLocalTimeMs(it.metadata?.creationDate, it.metadata?.creationDateOffset) }
            }
            "random" -> media.shuffled()
            else -> media
        }

        return directory.copy(directories = sortedDirs, media = sortedMedia)
    }

    fun parseSearchStringToQuery(searchText: String): Map<String, Any?> {
        val trimmed = searchText.trim()
        if (trimmed.isEmpty()) return emptyMap()
        val queryDto = com.example.data.search.SearchQueryParser.parse(trimmed)
        return queryDto.toJson()
    }

    init {
        checkForUpdates()
        if (prefs.isLoggedIn && prefs.serverUrl.isNotEmpty()) {
            loadCurrentDirectory()
            loadAlbums()
            loadRediscover()
        }
    }

    fun setActiveTab(tab: ActiveTab) {
        _activeTab.value = tab
        // Clear multi-select mode when switching tabs to avoid stray states
        exitSelectMode()
        
        // Refresh contents if needed when switching
        if (prefs.isLoggedIn && prefs.serverUrl.isNotEmpty()) {
            when (tab) {
                ActiveTab.GALLERY -> loadCurrentDirectory()
                ActiveTab.ALBUMS -> {
                    _selectedAlbum.value = null
                    _albumContentState.value = null
                    loadAlbums()
                }
                ActiveTab.PERSONS -> {
                    _selectedPerson.value = null
                    _personContentState.value = null
                    loadPersons()
                }
                ActiveTab.REDISCOVER -> loadRediscover()
                ActiveTab.SETTINGS -> { /* no-op */ }
            }
        }
    }

    fun connectAndLogin(url: String, user: String, pass: String, allowInsecureSsl: Boolean = prefs.allowInsecureSsl) {
        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = LoginUiState.Loading
            try {
                // Ensure URL has schema
                val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "http://$url"
                } else {
                    url
                }

                prefs.allowInsecureSsl = allowInsecureSsl
                api.updateClientSsl(allowInsecureSsl)

                val (cookies, apiPrefix) = api.login(formattedUrl, user, pass)
                
                // Save settings
                prefs.serverUrl = formattedUrl
                prefs.username = user
                prefs.password = pass
                prefs.cookies = cookies
                prefs.apiPrefix = apiPrefix
                prefs.isLoggedIn = true

                // Sync viewmodel fields
                savedServerUrl.value = formattedUrl
                savedUsername.value = user
                savedPassword.value = pass
                isLoggedIn.value = true

                _loginState.value = LoginUiState.Success("Connected successfully!")
                _pathHistory.value = buildInitialPathStack(prefs.defaultRootPath) // Reset path history
                _activeTab.value = ActiveTab.GALLERY

                loadCurrentDirectory()
                loadAlbums()
                loadRediscover()
            } catch (e: Exception) {
                _loginState.value = LoginUiState.Error(e.localizedMessage ?: "Connection failed")
            }
        }
    }

    private var directoryLoadJob: kotlinx.coroutines.Job? = null

    private fun deduplicateMedia(mediaList: List<ApiMedia>): List<ApiMedia> {
        return mediaList.distinctBy { media ->
            val id = media.id
            if (id != null && id != 0) {
                "id_$id"
            } else {
                "path_${media.parentPath ?: ""}/${media.name}"
            }
        }
    }

    private fun buildDateViewDirectory(currentPathStr: String, uniqueMedia: List<ApiMedia>): ApiDirectory {
        val rootPath = prefs.defaultRootPath
        val isRootDateView = currentPathStr.isEmpty() ||
            currentPathStr == rootPath ||
            currentPathStr.trim('/') == rootPath.trim('/') ||
            (!currentPathStr.startsWith("year:") && !currentPathStr.contains("month:"))

        return if (isRootDateView) {
            val groupedByYear = uniqueMedia
                .groupBy { media ->
                    val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                    if (localMs > 0L) getYearFromTimestamp(localMs) else 0
                }
                .toSortedMap(compareByDescending { it })

            val yearSubFolders = groupedByYear.map { (year, mediaList) ->
                val firstMedia = mediaList.firstOrNull()
                val cover = firstMedia?.let {
                    ApiCoverPhoto(
                        name = it.name,
                        directory = ApiCoverPhotoDirectory(name = "", path = it.parentPath ?: "")
                    )
                }
                ApiSubFolder(
                    id = year,
                    name = if (year > 0) "$year" else "Undated",
                    path = "year:$year",
                    mediaCount = mediaList.size,
                    cache = ApiSubFolderCache(cover = cover)
                )
            }
            ApiDirectory(
                id = -1,
                name = "Gallery",
                path = currentPathStr,
                directories = yearSubFolders,
                media = emptyList()
            )
        } else if (currentPathStr.startsWith("year:") && !currentPathStr.contains("month:")) {
            val yearStr = currentPathStr.substringAfter("year:")
            val year = yearStr.toIntOrNull() ?: 0
            val yearMedia = uniqueMedia.filter { media ->
                val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                val mYear = if (localMs > 0L) getYearFromTimestamp(localMs) else 0
                mYear == year
            }

            val groupedByMonth = yearMedia
                .groupBy { media ->
                    val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                    if (localMs > 0L) getMonthFromTimestamp(localMs) else 0
                }
                .toSortedMap(compareByDescending { it })

            val monthSubFolders = groupedByMonth.map { (month, mediaList) ->
                val firstMedia = mediaList.firstOrNull()
                val cover = firstMedia?.let {
                    ApiCoverPhoto(
                        name = it.name,
                        directory = ApiCoverPhotoDirectory(name = "", path = it.parentPath ?: "")
                    )
                }
                ApiSubFolder(
                    id = year * 100 + month,
                    name = if (month > 0) getMonthName(month) else "Undated",
                    path = "year:$year/month:$month",
                    mediaCount = mediaList.size,
                    cache = ApiSubFolderCache(cover = cover)
                )
            }
            ApiDirectory(
                id = -1,
                name = if (year > 0) "$year" else "Undated",
                path = currentPathStr,
                directories = monthSubFolders,
                media = emptyList()
            )
        } else if (currentPathStr.contains("month:")) {
            val year = currentPathStr.substringAfter("year:").substringBefore("/").toIntOrNull() ?: 0
            val month = currentPathStr.substringAfter("month:").toIntOrNull() ?: 0
            val monthMedia = uniqueMedia.filter { media ->
                val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                val mYear = if (localMs > 0L) getYearFromTimestamp(localMs) else 0
                val mMonth = if (localMs > 0L) getMonthFromTimestamp(localMs) else 0
                mYear == year && mMonth == month
            }
            ApiDirectory(
                id = -1,
                name = if (month > 0) "${getMonthName(month)} $year" else "Undated",
                path = currentPathStr,
                directories = emptyList(),
                media = monthMedia
            )
        } else {
            ApiDirectory(
                id = -1,
                name = "Gallery",
                path = currentPathStr,
                directories = emptyList(),
                media = uniqueMedia
            )
        }
    }

    private fun filterFlattenedMedia(currentPathStr: String, uniqueMedia: List<ApiMedia>): List<ApiMedia> {
        return if (currentPathStr.startsWith("year:") && !currentPathStr.contains("month:")) {
            val year = currentPathStr.substringAfter("year:").substringBefore("/").toIntOrNull() ?: 0
            uniqueMedia.filter { media ->
                val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                getYearFromTimestamp(localMs) == year
            }
        } else if (currentPathStr.contains("month:")) {
            val year = currentPathStr.substringAfter("year:").substringBefore("/").toIntOrNull() ?: 0
            val month = currentPathStr.substringAfter("month:").toIntOrNull() ?: 0
            uniqueMedia.filter { media ->
                val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                getYearFromTimestamp(localMs) == year && getMonthFromTimestamp(localMs) == month
            }
        } else {
            uniqueMedia
        }
    }

    fun refreshCurrentDirectory() {
        directoryCache.clear()
        loadCurrentDirectory(forceRefresh = true)
    }

    private fun hasMediaChanged(oldList: List<ApiMedia>?, newList: List<ApiMedia>): Boolean {
        if (oldList == null) return true
        if (oldList.size != newList.size) return true
        val oldKeys = oldList.map { it.id ?: ("${it.parentPath}/${it.name}".hashCode()) }.toHashSet()
        for (item in newList) {
            val key = item.id ?: ("${item.parentPath}/${item.name}".hashCode())
            if (!oldKeys.contains(key)) return true
        }
        return false
    }

    fun loadCurrentDirectory(forceRefresh: Boolean = false) {
        val server = prefs.serverUrl
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        val path = currentPath

        if (server.isEmpty()) {
            _galleryState.value = GalleryUiState.Error("No server configured.")
            return
        }

        directoryLoadJob?.cancel()
        directoryLoadJob = viewModelScope.launch(Dispatchers.IO) {
            var cachedEmitted = false
            val isDateOrFlattened = searchQuery.value.isEmpty() && (isFlattened.value || galleryViewMode.value == GalleryViewMode.DATE)

            // 1. Instant cache path for Date View and Flattened Mode (from in-memory or Room DB)
            if (isDateOrFlattened) {
                val rootPath = if (isFlattened.value && !currentPath.startsWith("year:") && !currentPath.startsWith("month:")) {
                    path
                } else {
                    prefs.defaultRootPath
                }
                val cleanRoot = rootPath.trim('/').replace("\\", "/")
                val cacheKey = cleanRoot.ifEmpty { "ROOT" }

                // Check in-memory cache first
                var mediaList = if (!forceRefresh) cachedFlattenedMedia[cacheKey] else null

                // If not in-memory (e.g. app restart or first load), immediately load from Room DB
                if (mediaList == null || mediaList.isEmpty()) {
                    val dbMedia = repo.getAllMedia()
                    if (dbMedia != null && dbMedia.isNotEmpty()) {
                        val filteredDb = if (cleanRoot.isEmpty()) {
                            dbMedia
                        } else {
                            dbMedia.filter {
                                val p = (it.parentPath ?: "").trim('/').replace("\\", "/")
                                p == cleanRoot || p.startsWith("$cleanRoot/")
                            }
                        }
                        if (filteredDb.isNotEmpty()) {
                            mediaList = deduplicateMedia(filteredDb)
                            cachedFlattenedMedia[cacheKey] = mediaList
                        }
                    }
                }

                // If cached media exists, emit it IMMEDIATELY so full counts and images appear with zero delay!
                if (mediaList != null && mediaList.isNotEmpty()) {
                    val cachedDirectory = if (isFlattened.value) {
                        val filteredMedia = filterFlattenedMedia(currentPath, mediaList)
                        ApiDirectory(
                            id = -1,
                            name = if (currentPath.isEmpty() || currentPath == prefs.defaultRootPath) "Flattened Gallery" else currentPath.substringAfterLast('/'),
                            path = currentPath,
                            directories = emptyList(),
                            media = filteredMedia
                        )
                    } else {
                        buildDateViewDirectory(currentPath, mediaList)
                    }
                    val sortedDirectory = sortDirectory(cachedDirectory)
                    _galleryState.value = GalleryUiState.Success(sortedDirectory)
                    cachedEmitted = true

                    // Check if we need to scan the server in the background:
                    // If not forced and checked recently (within 60s), navigation between folders is 0ms instant!
                    val lastSync = lastBackgroundSyncTime[cacheKey] ?: 0L
                    val shouldCheckBackground = forceRefresh || (System.currentTimeMillis() - lastSync > 60_000L)
                    if (!shouldCheckBackground) {
                        return@launch
                    }
                }
            }

            // 2. Room DB cache check for FOLDER view
            if (searchQuery.value.isEmpty() && !isFlattened.value && galleryViewMode.value == GalleryViewMode.FOLDER) {
                val cachedMedia = repo.getDirectory(path)
                if (cachedMedia != null && cachedMedia.isNotEmpty()) {
                    val directory = ApiDirectory(
                        id = -1,
                        name = path.substringAfterLast('/'),
                        path = path,
                        directories = emptyList(),
                        media = cachedMedia
                    )
                    _galleryState.value = GalleryUiState.Success(sortDirectory(directory))
                    cachedEmitted = true
                }
            }

            if (!cachedEmitted) {
                _galleryState.value = GalleryUiState.Loading
            } else if (forceRefresh) {
                _isRefreshing.value = true
            }

            try {
                if (searchQuery.value.isNotEmpty()) {
                    val query = parseSearchStringToQuery(searchQuery.value)
                    val queryJson = api.serializeQuery(query)
                    val searchResult = api.search(server, queryJson, cookies, apiPrefix)
                    val sortedDirectory = sortDirectory(searchResult)
                    _galleryState.value = GalleryUiState.Success(sortedDirectory)
                } else if (isDateOrFlattened) {
                    val rootPath = if (isFlattened.value && !currentPath.startsWith("year:") && !currentPath.startsWith("month:")) {
                        path
                    } else {
                        prefs.defaultRootPath
                    }
                    val cleanRoot = rootPath.trim('/').replace("\\", "/")
                    val cacheKey = cleanRoot.ifEmpty { "ROOT" }
                    val currentDisplayedMedia = cachedFlattenedMedia[cacheKey]

                    // If cached data was already emitted, do NOT emit partial 250ms chunks that would overwrite
                    // full counts with small numbers. Only emit progress if there was no cache!
                    val emitIntermediate = !cachedEmitted

                    val finalMediaList = getOrFetchAllMediaProgressive(
                        serverUrl = server,
                        rootPath = cleanRoot,
                        cookies = cookies,
                        apiPrefix = apiPrefix,
                        emitIntermediateBatches = emitIntermediate,
                        forceNetworkScan = forceRefresh || cachedEmitted
                    ) { partialList ->
                        if (emitIntermediate) {
                            val partialDir = if (isFlattened.value) {
                                val filtered = filterFlattenedMedia(currentPath, partialList)
                                ApiDirectory(
                                    id = -1,
                                    name = if (currentPath.isEmpty() || currentPath == prefs.defaultRootPath) "Flattened Gallery" else currentPath.substringAfterLast('/'),
                                    path = currentPath,
                                    directories = emptyList(),
                                    media = filtered
                                )
                            } else {
                                buildDateViewDirectory(currentPath, partialList)
                            }
                            _galleryState.value = GalleryUiState.Success(sortDirectory(partialDir))
                        }
                    }

                    lastBackgroundSyncTime[cacheKey] = System.currentTimeMillis()

                    if (cachedEmitted) {
                        // Only update state if new items arrived or counts changed!
                        if (hasMediaChanged(currentDisplayedMedia, finalMediaList)) {
                            val updatedDir = if (isFlattened.value) {
                                val filtered = filterFlattenedMedia(currentPath, finalMediaList)
                                ApiDirectory(
                                    id = -1,
                                    name = if (currentPath.isEmpty() || currentPath == prefs.defaultRootPath) "Flattened Gallery" else currentPath.substringAfterLast('/'),
                                    path = currentPath,
                                    directories = emptyList(),
                                    media = filtered
                                )
                            } else {
                                buildDateViewDirectory(currentPath, finalMediaList)
                            }
                            _galleryState.value = GalleryUiState.Success(sortDirectory(updatedDir))
                        }
                    } else {
                        // First load without any cache: emit the final complete directory
                        val finalDir = if (isFlattened.value) {
                            val filtered = filterFlattenedMedia(currentPath, finalMediaList)
                            ApiDirectory(
                                id = -1,
                                name = if (currentPath.isEmpty() || currentPath == prefs.defaultRootPath) "Flattened Gallery" else currentPath.substringAfterLast('/'),
                                path = currentPath,
                                directories = emptyList(),
                                media = filtered
                            )
                        } else {
                            buildDateViewDirectory(currentPath, finalMediaList)
                        }
                        _galleryState.value = GalleryUiState.Success(sortDirectory(finalDir))
                    }
                } else {
                    // Normal folder view
                    val freshDirectory = api.getGalleryContent(server, path, cookies, apiPrefix)
                    if (freshDirectory.media != null && searchQuery.value.isEmpty() && !isFlattened.value && galleryViewMode.value == GalleryViewMode.FOLDER) {
                        repo.saveDirectory(path, freshDirectory.media)
                    }
                    val sortedDirectory = sortDirectory(freshDirectory)
                    _galleryState.value = GalleryUiState.Success(sortedDirectory)
                }
            } catch (e: Exception) {
                if (!cachedEmitted) {
                    _galleryState.value = GalleryUiState.Error(e.localizedMessage ?: "Failed to fetch gallery content")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun getOrFetchAllMediaProgressive(
        serverUrl: String,
        rootPath: String,
        cookies: String,
        apiPrefix: String,
        emitIntermediateBatches: Boolean = true,
        forceNetworkScan: Boolean = false,
        onBatchEmit: (List<ApiMedia>) -> Unit
    ): List<ApiMedia> {
        val cleanRoot = rootPath.trim('/').replace("\\", "/")
        val cacheKey = cleanRoot.ifEmpty { "ROOT" }
        if (!forceNetworkScan) {
            cachedFlattenedMedia[cacheKey]?.let {
                onBatchEmit(it)
                return it
            }
        }

        val accumulatedMedia = java.util.concurrent.ConcurrentLinkedQueue<ApiMedia>()
        var lastEmitTime = System.currentTimeMillis()

        suspend fun scanDirectory(path: String) {
            val directory = if (!forceNetworkScan) directoryCache[path] else null
            val resolvedDirectory = directory ?: fetchSemaphore.withPermit {
                (if (!forceNetworkScan) directoryCache[path] else null) ?: run {
                    val fetched = api.getGalleryContent(serverUrl, path, cookies, apiPrefix)
                    directoryCache[path] = fetched
                    fetched
                }
            }

            val currentMedia = (resolvedDirectory.media ?: emptyList()).map { media ->
                if (media.parentPath.isNullOrEmpty()) {
                    media.copy(parentPath = path)
                } else media
            }
            if (currentMedia.isNotEmpty()) {
                accumulatedMedia.addAll(currentMedia)
                // Persist current directory to Room DB immediately so cache is saved progressively!
                try {
                    repo.saveDirectory(path, currentMedia)
                } catch (e: Exception) {
                    // Ignore transient DB write errors
                }
                if (emitIntermediateBatches) {
                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime > 250 || accumulatedMedia.size <= currentMedia.size) {
                        lastEmitTime = now
                        val snapshot = deduplicateMedia(accumulatedMedia.toList())
                        onBatchEmit(snapshot)
                    }
                }
            }

            val subDirs = resolvedDirectory.directories ?: emptyList()
            if (subDirs.isNotEmpty()) {
                kotlinx.coroutines.coroutineScope {
                    subDirs.map { subFolder ->
                        async(Dispatchers.IO) {
                            try {
                                scanDirectory(subFolder.path)
                            } catch (e: Exception) {
                                // Ignore single subfolder failure without cancelling overall scan
                            }
                        }
                    }.awaitAll()
                }
            }
        }

        try {
            scanDirectory(cleanRoot)
        } catch (e: Exception) {
            // Ignore top-level scan failure
        }

        val finalMediaList = deduplicateMedia(accumulatedMedia.toList())
        if (finalMediaList.isNotEmpty()) {
            cachedFlattenedMedia[cacheKey] = finalMediaList
            try {
                repo.saveAllMedia(finalMediaList)
            } catch (e: Exception) {
                // Ignore
            }
            if (emitIntermediateBatches) {
                onBatchEmit(finalMediaList)
            }
        } else if (accumulatedMedia.isEmpty()) {
            if (emitIntermediateBatches) {
                onBatchEmit(emptyList())
            }
        }
        return finalMediaList
    }

    fun enterFolder(folderPath: String) {
        val currentStack = _pathHistory.value.toMutableList()
        currentStack.add(folderPath)
        _pathHistory.value = currentStack
        loadCurrentDirectory()
    }

    fun navigateToBreadcrumb(index: Int) {
        val currentStack = _pathHistory.value
        if (index in currentStack.indices) {
            _pathHistory.value = currentStack.take(index + 1)
            loadCurrentDirectory()
        }
    }

    fun goBackFolder(): Boolean {
        // If in Select Mode, exit select mode first
        if (_isSelectMode.value) {
            exitSelectMode()
            return true
        }

        // If an album detail view is open in Albums Tab, go back to albums list
        if (_activeTab.value == ActiveTab.ALBUMS && _selectedAlbum.value != null) {
            selectAlbum(null)
            return true
        }
        // If a person detail view is open in Persons Tab, go back to persons list
        if (_activeTab.value == ActiveTab.PERSONS && _selectedPerson.value != null) {
            clearSelectedPerson()
            return true
        }

        val currentStack = _pathHistory.value.toMutableList()
        if (currentStack.size > 1) {
            currentStack.removeAt(currentStack.size - 1)
            _pathHistory.value = currentStack
            loadCurrentDirectory()
            return true // handled back navigation
        }
        return false // let OS handle back
    }

    fun selectMedia(media: ApiMedia?, list: List<ApiMedia> = emptyList()) {
        _selectedMedia.value = media
        if (media != null) {
            if (list.isNotEmpty()) {
                _activeMediaList.value = list
            } else {
                _activeMediaList.value = listOf(media)
            }
        } else {
            _activeMediaList.value = emptyList()
        }
    }

    // --- Albums Functionality ---
    
    // --- Persons Functionality ---
    fun loadPersons() {
        val server = prefs.serverUrl ?: run {
            _personsState.value = PersonsUiState.Error("No server configured.")
            return
        }
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        _personsState.value = PersonsUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backendPersons = api.getPersons(server, cookies, apiPrefix)
                val localFavs = prefs.getFavoritePersons()
                
                // Synchronize and construct the final person list
                val persons = backendPersons.map { person ->
                    val isLocalFav = localFavs.contains(person.name)
                    val isBackendFav = person.isFavourite == true
                    
                    // If backend has it as favorite but local doesn't, sync to local.
                    if (isBackendFav && !isLocalFav) {
                        prefs.setFavoritePerson(person.name, true)
                    }
                    
                    person.copy(isFavourite = isLocalFav || isBackendFav)
                }

                _personsState.value = PersonsUiState.Success(sortPersons(persons))
            } catch (e: Exception) {
                _personsState.value = PersonsUiState.Error(e.localizedMessage ?: "Failed to fetch persons")
            }
        }
    }

    fun togglePersonFavourite(person: com.example.data.ApiPerson) {
        val currentFavState = prefs.getFavoritePersons().contains(person.name) || person.isFavourite == true
        val newFavState = !currentFavState
        
        // 1. Instantly update local preferences for optimistic UI
        prefs.setFavoritePerson(person.name, newFavState)
        
        // 2. Instantly update UI states so there is zero delay for the user
        val currentState = _personsState.value
        if (currentState is PersonsUiState.Success) {
            val updatedList = currentState.persons.map {
                if (it.name == person.name) {
                    it.copy(isFavourite = newFavState)
                } else {
                    it
                }
            }
            _personsState.value = PersonsUiState.Success(sortPersons(updatedList))
        }
        
        // Update selectedPerson if it is currently active
        val currentSelected = _selectedPerson.value
        if (currentSelected != null && currentSelected.name == person.name) {
            _selectedPerson.value = currentSelected.copy(isFavourite = newFavState)
        }

        // 3. Sync with backend in the background
        viewModelScope.launch(Dispatchers.IO) {
            val server = prefs.serverUrl
            val cookies = prefs.cookies
            val apiPrefix = prefs.apiPrefix
            if (server.isNotEmpty()) {
                val success = api.updatePersonFavourite(server, person.name, newFavState, cookies, apiPrefix)
                if (success) {
                    android.util.Log.d("GalleryViewModel", "Successfully synced favorite state for ${person.name} with backend.")
                } else {
                    android.util.Log.e("GalleryViewModel", "Failed to sync favorite state with backend for ${person.name}, but kept local state.")
                }
            }
        }
    }
    
    fun selectPerson(person: com.example.data.ApiPerson) {
        _selectedPerson.value = person
        loadPersonContent(person)
    }

    fun clearSelectedPerson() {
        _selectedPerson.value = null
        _personContentState.value = null
    }

    private fun loadPersonContent(person: com.example.data.ApiPerson) {
        val server = prefs.serverUrl ?: return
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        _personContentState.value = GalleryUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val searchQuery = if (prefs.peopleFallbackToKeywords) {
                    mapOf(
                        "type" to 2, // SearchQueryTypes.OR
                        "list" to listOf(
                            mapOf("type" to 105, "value" to person.name, "matchType" to 1),
                            mapOf("type" to 104, "value" to person.name, "matchType" to 2) // LIKE match for keyword
                        )
                    )
                } else {
                    mapOf("type" to 105, "value" to person.name, "matchType" to 1)
                }
                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = moshi.adapter<Map<String, Any>>(type)
                val searchQueryJson = adapter.toJson(searchQuery)
                val dummyDir = api.search(server, searchQueryJson, cookies, apiPrefix)
                val sortedDirectory = sortDirectory(dummyDir)
                _personContentState.value = GalleryUiState.Success(sortedDirectory)
            } catch (e: Exception) {
                _personContentState.value = GalleryUiState.Error(e.localizedMessage ?: "Failed to fetch person content")
            }
        }
    }

fun loadAlbums() {
        val server = prefs.serverUrl
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix

        if (server.isEmpty()) {
            _albumsState.value = AlbumsUiState.Error("No server configured.")
            return
        }

        _albumsState.value = AlbumsUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val albums = api.getAlbums(server, cookies, apiPrefix)
                _albumsState.value = AlbumsUiState.Success(sortAlbums(albums))
            } catch (e: Exception) {
                _albumsState.value = AlbumsUiState.Error(e.localizedMessage ?: "Failed to fetch albums")
            }
        }
    }

    fun selectAlbum(album: ApiAlbum?) {
        _selectedAlbum.value = album
        if (album != null) {
            loadAlbumContent(album)
        } else {
            _albumContentState.value = null
        }
    }

    private fun loadAlbumContent(album: ApiAlbum) {
        val server = prefs.serverUrl
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        val query = album.searchQuery ?: return

        _albumContentState.value = GalleryUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val queryJson = api.serializeQuery(query)
                val directory = api.search(server, queryJson, cookies, apiPrefix)
                val sortedDirectory = sortDirectory(directory)
                _albumContentState.value = GalleryUiState.Success(sortedDirectory)
            } catch (e: Exception) {
                _albumContentState.value = GalleryUiState.Error(e.localizedMessage ?: "Failed to load album content")
            }
        }
    }

    // --- Rediscover (Top Picks) Functionality ---
    fun loadRediscover() {
        val server = prefs.serverUrl
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        val days = rediscoverDays.value

        if (server.isEmpty()) {
            _rediscoverState.value = RediscoverUiState.Error("No server configured.")
            return
        }

        _rediscoverState.value = RediscoverUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Query DatePatternSearch: every_year (frequency = 3)
                val queryEveryYear = mapOf(
                    "type" to 60,
                    "daysLength" to days,
                    "frequency" to 3
                )
                val everyYearDir = try {
                    api.search(server, api.serializeQuery(queryEveryYear), cookies, apiPrefix)
                } catch (e: Exception) {
                    null
                }

                // Combine results
                val allMedia = mutableListOf<ApiMedia>()
                everyYearDir?.media?.let { allMedia.addAll(it) }

                // Deduplicate by name and parent path
                val uniqueMedia = allMedia.distinctBy { getMediaFullPath(it) }

                // Group by year, excluding the current year and media with invalid creationDate
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val grouped = uniqueMedia
                    .filter { it.metadata?.creationDate != null }
                    .filter { media ->
                        val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                        getYearFromTimestamp(localMs) < currentYear
                    }
                    .groupBy { media ->
                        val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                        getYearFromTimestamp(localMs)
                    }.toSortedMap(compareByDescending { it })

                _rediscoverState.value = RediscoverUiState.Success(grouped)
            } catch (e: Exception) {
                _rediscoverState.value = RediscoverUiState.Error(e.localizedMessage ?: "Failed to load rediscover content")
            }
        }
    }

    fun setRediscoverDays(days: Int) {
        prefs.rediscoverDays = days
        rediscoverDays.value = days
        loadRediscover()
    }

    fun setSlideshowDuration(duration: Int) {
        prefs.slideshowDuration = duration
        slideshowDuration.value = duration
    }

    fun setPeopleFallbackToKeywords(value: Boolean) {
        prefs.peopleFallbackToKeywords = value
        peopleFallbackToKeywords.value = value
        val currentPerson = _selectedPerson.value
        if (currentPerson != null) {
            loadPersonContent(currentPerson)
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        prefs.autoCheckUpdates = enabled
        autoCheckUpdates.value = enabled
        if (enabled) {
            checkForUpdates(force = true)
        }
    }

    fun setShowDirectoryItemCount(value: Boolean) {
        prefs.showDirectoryItemCount = value
        showDirectoryItemCount.value = value
    }

    fun setItemsPerRow(value: Int) {
        prefs.itemsPerRow = value
        itemsPerRow.value = value
    }

    fun setItemsPerRowPortrait(value: Int) {
        prefs.itemsPerRowPortrait = value
        itemsPerRowPortrait.value = value
    }

    fun setItemsPerRowLandscape(value: Int) {
        prefs.itemsPerRowLandscape = value
        itemsPerRowLandscape.value = value
    }

    fun setCornerRadius(value: Int) {
        prefs.cornerRadius = value
        cornerRadius.value = value
    }

    fun setSpacing(value: Int) {
        prefs.spacing = value
        spacing.value = value
    }

    fun setAspectRatio(value: Float) {
        prefs.aspectRatio = value
        aspectRatio.value = value
    }

    fun setThemeColorOption(value: String) {
        prefs.themeColor = value
        themeColorOption.value = value
    }

    fun setThemeMode(value: String) {
        prefs.themeMode = value
        themeMode.value = value
    }

    fun setMaxBrightnessEnabled(enabled: Boolean) {
        prefs.maxBrightnessEnabled = enabled
        maxBrightnessEnabled.value = enabled
    }

    fun setDismissGestureEnabled(enabled: Boolean) {
        prefs.dismissGestureEnabled = enabled
        dismissGestureEnabled.value = enabled
    }

    fun setShowMetadataGestureEnabled(enabled: Boolean) {
        prefs.showMetadataGestureEnabled = enabled
        showMetadataGestureEnabled.value = enabled
    }

    private fun buildInitialPathStack(defaultRoot: String): List<String> {
        if (defaultRoot.isBlank()) return listOf("")
        
        val parts = defaultRoot.split("/")
        val stack = mutableListOf("")
        var current = ""
        for (part in parts) {
            if (part.isNotBlank()) {
                current = if (current.isEmpty()) part else "$current/$part"
                stack.add(current)
            }
        }
        return stack
    }

    fun setDefaultRootPath(value: String) {
        prefs.defaultRootPath = value
        defaultRootPath.value = value
        _pathHistory.value = buildInitialPathStack(value)
        if (_activeTab.value == ActiveTab.GALLERY) {
            loadCurrentDirectory()
        }
    }

    private fun getYearFromTimestamp(timestamp: Long): Int {
        val ms = if (timestamp < 10000000000L) timestamp * 1000L else timestamp
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        return cal.get(java.util.Calendar.YEAR)
    }

    // --- Multi-Selection (Share several images) ---
    fun enterSelectMode() {
        _isSelectMode.value = true
        _selectedMediaForShare.value = emptySet()
    }

    fun exitSelectMode() {
        _isSelectMode.value = false
        _selectedMediaForShare.value = emptySet()
    }

    fun toggleSelectMedia(media: ApiMedia) {
        if (!_isSelectMode.value) {
            enterSelectMode()
        }
        val currentSet = _selectedMediaForShare.value.toMutableSet()
        if (currentSet.contains(media)) {
            currentSet.remove(media)
            if (currentSet.isEmpty()) {
                exitSelectMode()
            } else {
                _selectedMediaForShare.value = currentSet
            }
        } else {
            currentSet.add(media)
            _selectedMediaForShare.value = currentSet
        }
    }

    private val _shareProgress = MutableStateFlow(-1f)
    val shareProgress: StateFlow<Float> = _shareProgress.asStateFlow()

    private var shareJob: kotlinx.coroutines.Job? = null

    fun shareSingleMedia(context: Context, media: ApiMedia) {
        shareJob?.cancel()
        shareJob = viewModelScope.launch(Dispatchers.IO) {
            _shareProgress.value = 0f
            try {
                val file = downloadMediaToCache(context, media) { progress ->
                    _shareProgress.value = progress
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                
                _shareProgress.value = -1f // Reset
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (media.isVideo) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Media via"))
            } catch (e: Exception) {
                // Handle download error
                _shareProgress.value = -1f
            }
        }
    }

    fun downloadSelectedMedia(context: Context) {
        val selected = _selectedMediaForShare.value
        if (selected.isEmpty()) return

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val cookies = getCookiesHeader()

        selected.forEach { media ->
            try {
                val url = getOriginalMediaUrl(media)
                val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                    .setTitle(media.name)
                    .setDescription("Downloading file from PiGallery2")
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, media.name)
                    .apply {
                        if (cookies.isNotEmpty()) {
                            addRequestHeader("Cookie", cookies)
                        }
                    }
                downloadManager.enqueue(request)
            } catch (e: Exception) {
                // Handle error
            }
        }
        android.widget.Toast.makeText(context, "Download started for ${selected.size} items", android.widget.Toast.LENGTH_SHORT).show()
        exitSelectMode()
    }

    fun shareSelectedMedia(context: Context) {
        val selected = _selectedMediaForShare.value
        if (selected.isEmpty()) return

        shareJob?.cancel()
        shareJob = viewModelScope.launch(Dispatchers.IO) {
            _shareProgress.value = 0f
            val uris = mutableListOf<android.net.Uri>()
            
            selected.forEachIndexed { index, media ->
                try {
                    val file = downloadMediaToCache(context, media) { fileProgress ->
                        // Combine single file download progress with overall selection index
                        val overallProgress = (index.toFloat() + fileProgress) / selected.size
                        _shareProgress.value = overallProgress
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    uris.add(uri)
                } catch (e: Exception) {
                    // Handle download error
                }
            }
            
            _shareProgress.value = -1f // Reset

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, java.util.ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Photos via"))
            
            exitSelectMode()
        }
    }

    private suspend fun downloadMediaToCache(
        context: Context,
        media: ApiMedia,
        onProgress: ((Float) -> Unit)? = null
    ): java.io.File {
        val url = getOriginalMediaUrl(media)
        val file = java.io.File(context.cacheDir, media.name)
        
        val response = api.download(url, prefs.cookies)
        
        response.body?.let { body ->
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = java.io.FileOutputStream(file)
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (totalBytes > 0) {
                    val progress = totalBytesRead.toFloat() / totalBytes
                    onProgress?.invoke(progress)
                }
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
        }
        
        return file
    }

    // --- URL Construction and Media Resolution ---
    fun getActiveServerUrl(): String {
        val primary = prefs.serverUrl.let { if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it }.trimEnd('/')
        val local = prefs.localServerUrl.let { if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it }.trimEnd('/')
        if (local.isEmpty()) return primary

        return try {
            val uri = java.net.URI(local)
            val host = uri.host ?: return primary
            val port = if (uri.port != -1) uri.port else 80
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(host, port), 150)
            socket.close()
            local
        } catch (e: Exception) {
            primary
        }
    }

    fun getThumbnailUrl(media: ApiMedia): String {
        val sanitizedBase = getActiveServerUrl()
        val apiPrefix = prefs.apiPrefix
        val relativePath = encodePath(getMediaFullPath(media))
        val suffix = prefs.thumbnailPathSuffix
        return "$sanitizedBase$apiPrefix/gallery/content/${relativePath}/$suffix"
    }

    fun getPreloadMediaUrl(media: ApiMedia): String? {
        if (media.isVideo) return null
        val suffix = prefs.preloadPathSuffix
        if (suffix.isBlank()) return null
        val sanitizedBase = getActiveServerUrl()
        val apiPrefix = prefs.apiPrefix
        val relativePath = encodePath(getMediaFullPath(media))
        return "$sanitizedBase$apiPrefix/gallery/content/${relativePath}/$suffix"
    }

    fun getOriginalMediaUrl(media: ApiMedia): String {
        val sanitizedBase = getActiveServerUrl()
        val apiPrefix = prefs.apiPrefix
        val relativePath = encodePath(getMediaFullPath(media))
        return if (media.isVideo) {
            val suffix = prefs.videoPathSuffix
            if (suffix.isNotEmpty()) {
                "$sanitizedBase$apiPrefix/gallery/content/${relativePath}/$suffix"
            } else {
                "$sanitizedBase$apiPrefix/gallery/content/${relativePath}"
            }
        } else {
            "$sanitizedBase$apiPrefix/gallery/content/${relativePath}"
        }
    }

    fun getAlbumCoverUrl(coverName: String, coverDirectory: String): String {
        val sanitizedBase = prefs.serverUrl.trimEnd('/')
        val apiPrefix = prefs.apiPrefix
        val fullPath = if (coverDirectory.isEmpty()) coverName else "$coverDirectory/$coverName"
        val relativePath = encodePath(fullPath)
        val suffix = prefs.thumbnailPathSuffix
        return "$sanitizedBase$apiPrefix/gallery/content/${relativePath}/$suffix"
    }

    fun getMediaFullPath(media: ApiMedia): String {
        val parent = media.parentPath ?: currentPath
        return if (parent.isEmpty()) {
            media.name
        } else {
            "$parent/${media.name}"
        }
    }

    private fun encodePath(path: String): String {
        return try {
            path.split('/')
                .joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        } catch (e: Exception) {
            path
        }
    }

    fun getCookiesHeader(): String {
        return prefs.cookies
    }

    fun logout() {
        directoryCache.clear()
        cachedFlattenedMedia.clear()
        prefs.clear()
        isLoggedIn.value = false
        savedServerUrl.value = ""
        savedUsername.value = ""
        savedPassword.value = ""
        _pathHistory.value = buildInitialPathStack(prefs.defaultRootPath)
        _loginState.value = LoginUiState.Idle
        _activeTab.value = ActiveTab.GALLERY
        _selectedAlbum.value = null
        _albumContentState.value = null
        exitSelectMode()
    }

    private val _cacheSize = MutableStateFlow("0 B")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    fun updateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var size: Long = 0
            val cacheDir = context.cacheDir
            if (cacheDir != null && cacheDir.exists()) {
                size += cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            }
            val externalCache = context.externalCacheDir
            if (externalCache != null && externalCache.exists()) {
                size += externalCache.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            }
            _cacheSize.value = formatSize(size)
        }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (kotlin.math.log10(size.toDouble()) / kotlin.math.log10(1024.0)).toInt()
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCaches() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            
            try {
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                api.clearCache()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                context.cacheDir?.listFiles()?.forEach { child ->
                    child.deleteRecursively()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                context.externalCacheDir?.listFiles()?.forEach { child ->
                    child.deleteRecursively()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                repo.clearAllCache()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            directoryCache.clear()
            cachedFlattenedMedia.clear()
            com.example.data.ThumbnailLruCache.clear()

            updateCacheSize()
        }
    }

    private fun getMonthFromTimestamp(timestampMs: Long): Int {
        val ms = if (timestampMs < 10000000000L) timestampMs * 1000L else timestampMs
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        return cal.get(java.util.Calendar.MONTH) + 1
    }

    sealed interface UpdateState {
        object Idle : UpdateState
        object Checking : UpdateState
        data class UpToDate(val currentVersion: String) : UpdateState
        data class NewRelease(val tagName: String, val releaseUrl: String, val releaseNotes: String) : UpdateState
        data class Error(val message: String) : UpdateState
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun checkForUpdates(force: Boolean = false) {
        val lastCheck = prefs.lastUpdateCheck
        val now = System.currentTimeMillis()
        if (!force && !prefs.autoCheckUpdates) return
        if (!force && (now - lastCheck) < 24 * 60 * 60 * 1000) return

        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateState.Checking
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://api.github.com/repos/dasmaetthes/pigallery2_android/releases/latest")
                    .header("User-Agent", "PiGallery2-Android")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateState.value = UpdateState.Error("Failed to check for updates (${response.code})")
                        return@launch
                    }
                    val bodyString = response.body?.string() ?: ""
                    val json = org.json.JSONObject(bodyString)
                    val tagName = json.optString("tag_name", json.optString("tagName", "v1.0.0"))
                    val htmlUrl = json.optString("html_url", "https://github.com/dasmaetthes/pigallery2_android/releases")
                    val releaseNotes = json.optString("body", "No release notes provided.")

                    val currentVersion = com.example.BuildConfig.VERSION_NAME
                    val cleanRemote = tagName.removePrefix("v").trim()
                    val cleanCurrent = currentVersion.removePrefix("v").trim()

                    prefs.lastUpdateCheck = now

                    if (cleanRemote != cleanCurrent && cleanRemote.isNotEmpty()) {
                        _updateState.value = UpdateState.NewRelease(tagName, htmlUrl, releaseNotes)
                    } else {
                        _updateState.value = UpdateState.UpToDate(currentVersion)
                    }
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Network error checking for updates")
            }
        }
    }

    fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "Januar"
            2 -> "Februar"
            3 -> "März"
            4 -> "April"
            5 -> "Mai"
            6 -> "Juni"
            7 -> "Juli"
            8 -> "August"
            9 -> "September"
            10 -> "Oktober"
            11 -> "November"
            12 -> "Dezember"
            else -> "$month"
        }
    }

    fun testNetworkConnections() {
        viewModelScope.launch(Dispatchers.IO) {
            networkConnectionInfo.value = networkConnectionInfo.value.copy(isTesting = true)

            val primaryUrl = prefs.serverUrl.let { if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it }.trimEnd('/')
            val localUrl = prefs.localServerUrl.let { if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it }.trimEnd('/')

            var localStatus = ServerRouteState.NOT_CONFIGURED
            var localLatency: Long? = null

            if (localUrl.isNotEmpty()) {
                val start = System.currentTimeMillis()
                try {
                    val uri = java.net.URI(localUrl)
                    val host = uri.host ?: ""
                    val port = if (uri.port != -1) uri.port else 80
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(host, port), 250)
                    socket.close()
                    localLatency = (System.currentTimeMillis() - start).coerceAtLeast(1)
                    localStatus = ServerRouteState.CONNECTED
                } catch (e: Exception) {
                    localStatus = ServerRouteState.UNREACHABLE
                }
            }

            var remoteStatus = ServerRouteState.NOT_CONFIGURED
            var remoteLatency: Long? = null

            if (primaryUrl.isNotEmpty()) {
                val start = System.currentTimeMillis()
                try {
                    val uri = java.net.URI(primaryUrl)
                    val host = uri.host ?: ""
                    val port = if (uri.port != -1) uri.port else if (primaryUrl.startsWith("https")) 443 else 80
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(host, port), 800)
                    socket.close()
                    remoteLatency = (System.currentTimeMillis() - start).coerceAtLeast(1)
                    remoteStatus = ServerRouteState.CONNECTED
                } catch (e: Exception) {
                    remoteStatus = ServerRouteState.UNREACHABLE
                }
            }

            val activeRoute = when {
                localStatus == ServerRouteState.CONNECTED -> ActiveRouteType.LOCAL_WIFI
                remoteStatus == ServerRouteState.CONNECTED -> ActiveRouteType.REMOTE_PRIMARY
                else -> ActiveRouteType.NONE
            }

            networkConnectionInfo.value = NetworkConnectionInfo(
                activeRoute = activeRoute,
                localStatus = localStatus,
                localLatencyMs = localLatency,
                remoteStatus = remoteStatus,
                remoteLatencyMs = remoteLatency,
                isTesting = false
            )
        }
    }
}

enum class ServerRouteState {
    CONNECTED,
    UNREACHABLE,
    NOT_CONFIGURED
}

enum class ActiveRouteType {
    LOCAL_WIFI,
    REMOTE_PRIMARY,
    NONE
}

data class NetworkConnectionInfo(
    val activeRoute: ActiveRouteType = ActiveRouteType.NONE,
    val localStatus: ServerRouteState = ServerRouteState.NOT_CONFIGURED,
    val localLatencyMs: Long? = null,
    val remoteStatus: ServerRouteState = ServerRouteState.NOT_CONFIGURED,
    val remoteLatencyMs: Long? = null,
    val isTesting: Boolean = false
)
