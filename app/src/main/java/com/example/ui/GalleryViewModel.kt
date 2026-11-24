package com.example.ui
import coil.imageLoader

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.IOException

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
    data class Success(val directory: ApiDirectory, val contextKey: String = "") : GalleryUiState
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

sealed interface SaveAlbumUiState {
    object Idle : SaveAlbumUiState
    object Saving : SaveAlbumUiState
    data class Success(val albumName: String) : SaveAlbumUiState
    data class Error(val message: String) : SaveAlbumUiState
}

sealed interface DeleteAlbumUiState {
    object Idle : DeleteAlbumUiState
    object Deleting : DeleteAlbumUiState
    data class Success(val albumName: String) : DeleteAlbumUiState
    data class Error(val message: String) : DeleteAlbumUiState
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

    private fun str(@StringRes resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

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

    // Preserved per-tab UI filter & expansion states across tab switches and detail navigation
    data class SavedScrollPosition(val index: Int = 0, val offset: Int = 0)
    private val scrollPositions = android.util.LruCache<String, SavedScrollPosition>(64)

    fun getScrollPosition(key: String): SavedScrollPosition =
        scrollPositions.get(key) ?: SavedScrollPosition()

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        scrollPositions.put(key, SavedScrollPosition(index, offset))
    }

    private val _albumsFilterQuery = MutableStateFlow("")
    val albumsFilterQuery: StateFlow<String> = _albumsFilterQuery.asStateFlow()

    fun setAlbumsFilterQuery(query: String) {
        _albumsFilterQuery.value = query
    }

    private val _personsFilterQuery = MutableStateFlow("")
    val personsFilterQuery: StateFlow<String> = _personsFilterQuery.asStateFlow()

    fun setPersonsFilterQuery(query: String) {
        _personsFilterQuery.value = query
    }

    private val _rediscoverExpandedYears = MutableStateFlow<Set<Int>>(emptySet())
    val rediscoverExpandedYears: StateFlow<Set<Int>> = _rediscoverExpandedYears.asStateFlow()

    fun toggleRediscoverYear(year: Int) {
        val current = _rediscoverExpandedYears.value
        _rediscoverExpandedYears.value = if (year in current) current - year else current + year
    }

    private val _selectedSettingsTab = MutableStateFlow(0)
    val selectedSettingsTab: StateFlow<Int> = _selectedSettingsTab.asStateFlow()

    fun setSelectedSettingsTab(tabIndex: Int) {
        _selectedSettingsTab.value = tabIndex
    }

    val rediscoverDays = MutableStateFlow(prefs.rediscoverDays)
    val slideshowDuration = MutableStateFlow(prefs.slideshowDuration)
    val peopleFallbackToKeywords = MutableStateFlow(prefs.peopleFallbackToKeywords)
    val autoCheckUpdates = MutableStateFlow(prefs.autoCheckUpdates)

    sealed interface UpdateState {
        object Idle : UpdateState
        object Checking : UpdateState
        data class UpToDate(val currentVersion: String) : UpdateState
        data class NewRelease(val tagName: String, val releaseUrl: String, val releaseNotes: String) : UpdateState
        data class Error(val message: String) : UpdateState
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

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

    // User role & Saved Search Album permissions
    private var lastPermissionsSyncMs: Long = 0L
    private val _userRole = MutableStateFlow(prefs.userRole)
    val userRole: StateFlow<Int> = _userRole.asStateFlow()

    private val _canManageAlbums = MutableStateFlow(computeCanManageAlbums())
    val canManageAlbums: StateFlow<Boolean> = _canManageAlbums.asStateFlow()

    private val _saveAlbumState = MutableStateFlow<SaveAlbumUiState>(SaveAlbumUiState.Idle)
    val saveAlbumState: StateFlow<SaveAlbumUiState> = _saveAlbumState.asStateFlow()

    private val _deleteAlbumState = MutableStateFlow<DeleteAlbumUiState>(DeleteAlbumUiState.Idle)
    val deleteAlbumState: StateFlow<DeleteAlbumUiState> = _deleteAlbumState.asStateFlow()

    private fun computeCanManageAlbums(): Boolean {
        return com.example.data.UserRoles.canManageAlbums(
            role = prefs.userRole,
            albumWriteMinRole = prefs.albumWriteMinRole,
            albumFeatureEnabled = prefs.albumFeatureEnabled,
            userCanCreateAlbumsOverride = prefs.userCanCreateAlbumsOverride
        )
    }

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
    val isBiometricLockEnabled = MutableStateFlow(prefs.biometricLockEnabled)
    val isAppLocked = MutableStateFlow(prefs.biometricLockEnabled)
    val protectedFolders = MutableStateFlow(prefs.getProtectedFolders())
    val hideProtectedItems = MutableStateFlow(prefs.hideProtectedItems)

    // Search and directory flattening states
    val isSearchActive = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val isFlattened = MutableStateFlow(false)
    val galleryViewMode = MutableStateFlow(try { GalleryViewMode.valueOf(prefs.galleryViewMode) } catch (e: Exception) { GalleryViewMode.FOLDER })
    val networkConnectionInfo = MutableStateFlow(NetworkConnectionInfo())

    private val cachedFlattenedMedia = android.util.LruCache<String, List<ApiMedia>>(4)
    private val cachedYearMedia = android.util.LruCache<Int, List<ApiMedia>>(8)
    private val lastBackgroundSyncTime = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val fetchSemaphore = Semaphore(4)
    private val reauthMutex = Mutex()
    private var backgroundSyncJob: kotlinx.coroutines.Job? = null
    private var directoryLoadJob: kotlinx.coroutines.Job? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var urlProbeJob: kotlinx.coroutines.Job? = null

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isAlbumsRefreshing = MutableStateFlow(false)
    val isAlbumsRefreshing: StateFlow<Boolean> = _isAlbumsRefreshing.asStateFlow()

    private val _isAlbumContentRefreshing = MutableStateFlow(false)
    val isAlbumContentRefreshing: StateFlow<Boolean> = _isAlbumContentRefreshing.asStateFlow()

    private val _isPersonsRefreshing = MutableStateFlow(false)
    val isPersonsRefreshing: StateFlow<Boolean> = _isPersonsRefreshing.asStateFlow()

    private val _isPersonContentRefreshing = MutableStateFlow(false)
    val isPersonContentRefreshing: StateFlow<Boolean> = _isPersonContentRefreshing.asStateFlow()

    fun setGalleryViewMode(mode: GalleryViewMode) {
        galleryViewMode.value = mode
        prefs.galleryViewMode = mode.name
        _pathHistory.value = if (mode == GalleryViewMode.DATE) listOf("") else buildInitialPathStack(prefs.defaultRootPath)
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

    fun removeSearchHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistoryManager.removeSearchQuery(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistoryManager.clearHistory()
        }
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
        val cleanDirPath = coverDir.path.replace('\\', '/').trim().trim('/').let {
            if (it == "." || it == "./") "" else it.removePrefix("./").trim('/')
        }
        val cleanDirName = coverDir.name.replace('\\', '/').trim().trim('/').let {
            if (it == "." || it == "./") "" else it.removePrefix("./").trim('/')
        }
        val dirPath = when {
            cleanDirPath.isEmpty() -> cleanDirName
            cleanDirName.isEmpty() -> cleanDirPath
            else -> "$cleanDirPath/$cleanDirName"
        }
        val fullPath = if (dirPath.isEmpty()) coverName else "$dirPath/$coverName"
        val sanitizedBase = getActiveServerUrl()
        val apiPrefix = prefs.apiPrefix
        val relativePath = encodePath(fullPath)
        val suffix = prefs.thumbnailPathSuffix.trim('/')
        return if (suffix.isNotEmpty()) {
            "$sanitizedBase$apiPrefix/gallery/content/$relativePath/$suffix"
        } else {
            "$sanitizedBase$apiPrefix/gallery/content/$relativePath"
        }
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

    fun normalizeSortPath(path: String): String {
        val clean = path.replace('\\', '/').trim().trim('/').let {
            if (it == "." || it == "./") "" else it.removePrefix("./").trim('/')
        }
        return clean.ifEmpty { "root" }
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
                    val path = normalizeSortPath(currentPath)
                    if (path == "root") "date_mode_root" else "date_mode_$path"
                } else {
                    normalizeSortPath(currentPath)
                }
            }
        }
    }

    fun hasSpecificSortForCurrentPath(): Boolean {
        val pathKey = getSortPath()
        return prefs.hasFolderSort(pathKey) || prefs.hasMediaSort(pathKey)
    }

    fun getCurrentMediaSortBy(): String {
        return when (_activeTab.value) {
            ActiveTab.ALBUMS -> {
                val album = _selectedAlbum.value
                if (album != null) {
                    val albumPath = "album_${album.name}"
                    val g_mSort = prefs.getMediaSortBy("global", "date")
                    if (prefs.hasMediaSort(albumPath)) prefs.getMediaSortBy(albumPath, g_mSort) else g_mSort
                } else "name"
            }
            ActiveTab.PERSONS -> {
                val person = _selectedPerson.value
                if (person != null) {
                    val personPath = "person_${person.name}"
                    val g_mSort = prefs.getMediaSortBy("global", "date")
                    if (prefs.hasMediaSort(personPath)) prefs.getMediaSortBy(personPath, g_mSort) else g_mSort
                } else "name"
            }
            else -> {
                val isDateMode = galleryViewMode.value == GalleryViewMode.DATE
                val globalPath = if (isDateMode) "date_mode_global" else "global"
                val g_mSort = prefs.getMediaSortBy(globalPath, "date")
                val currentContextPath = getSortPath()
                if (prefs.hasMediaSort(currentContextPath)) {
                    prefs.getMediaSortBy(currentContextPath, g_mSort)
                } else {
                    g_mSort
                }
            }
        }
    }

    fun updateAlbumSort(sortBy: String, sortDir: String) {
        prefs.setAlbumSortBy(sortBy)
        prefs.setAlbumSortDirection(sortDir)
        val currentContent = _albumsState.value
        if (currentContent is AlbumsUiState.Success) {
            _albumsState.value = AlbumsUiState.Success(sortAlbums(currentContent.albums))
        } else {
            loadAlbums()
        }
    }

    fun updatePersonSort(sortBy: String, sortDir: String) {
        prefs.setPersonSortBy(sortBy)
        prefs.setPersonSortDirection(sortDir)
        val currentContent = _personsState.value
        if (currentContent is PersonsUiState.Success) {
            _personsState.value = PersonsUiState.Success(sortPersons(currentContent.persons))
        } else {
            loadPersons()
        }
    }

    fun updateSort(
        folderSortBy: String, 
        folderSortDirection: String,
        mediaSortBy: String,
        mediaSortDirection: String,
        currentFolderOnly: Boolean
    ) {
        val targetPath = if (currentFolderOnly) {
            getSortPath()
        } else if (galleryViewMode.value == GalleryViewMode.DATE) {
            "date_mode_global"
        } else {
            "global"
        }
        
        prefs.setFolderSortBy(targetPath, folderSortBy)
        prefs.setFolderSortDirection(targetPath, folderSortDirection)
        prefs.setMediaSortBy(targetPath, mediaSortBy)
        prefs.setMediaSortDirection(targetPath, mediaSortDirection)

        if (!currentFolderOnly) {
            // Clear folder-specific overrides so this folder follows global
            val currentKey = getSortPath()
            if (currentKey != targetPath) {
                prefs.removeFolderSort(currentKey)
                prefs.removeMediaSort(currentKey)
            }
        }
        
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
                _galleryState.value = GalleryUiState.Success(
                    sortDirectory(currentContent.directory),
                    currentContent.contextKey
                )
            } else {
                loadCurrentDirectory()
            }
        }
    }


    fun sortAlbums(albums: List<com.example.data.ApiAlbum>): List<com.example.data.ApiAlbum> {
        return try {
            val aSort = prefs.getAlbumSortBy("name")
            val aDir = prefs.getAlbumSortDirection("asc")
            val aAsc = aDir.lowercase() == "asc"
            
            when (aSort.lowercase()) {
                "name" -> {
                    if (aAsc) albums.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    else albums.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
                }
                "date" -> {
                    if (aAsc) albums.sortedBy { 
                        val t = it.cache?.youngestMedia ?: it.cache?.oldestMedia ?: 0L
                        if (t <= 0L) Long.MAX_VALUE else t
                    }
                    else albums.sortedByDescending { 
                        it.cache?.youngestMedia ?: it.cache?.oldestMedia ?: 0L 
                    }
                }
                "count" -> {
                    if (aAsc) albums.sortedBy { it.cache?.itemCount ?: 0 }
                    else albums.sortedByDescending { it.cache?.itemCount ?: 0 }
                }
                "random" -> albums.shuffled()
                else -> albums
            }
        } catch (e: Exception) {
            albums
        }
    }

    fun sortPersons(persons: List<com.example.data.ApiPerson>): List<com.example.data.ApiPerson> {
        return try {
            val pSort = prefs.getPersonSortBy("name")
            val pDir = prefs.getPersonSortDirection("asc")
            val pAsc = pDir.lowercase() == "asc"
            
            val sorted = when (pSort.lowercase()) {
                "name" -> {
                    if (pAsc) persons.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    else persons.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
                }
                "count" -> {
                    if (pAsc) persons.sortedBy { it.cache?.count ?: 0 }
                    else persons.sortedByDescending { it.cache?.count ?: 0 }
                }
                "random" -> persons.shuffled()
                else -> persons
            }

            // Always show favorites first (stable sort)
            val localFavs = prefs.getFavoritePersons()
            sorted.sortedByDescending { localFavs.contains(it.name) || it.isFavourite == true }
        } catch (e: Exception) {
            persons
        }
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
        return try {
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

            // Resolve context path specifically for this directory:
            // When in gallery, directory.path is used (normalized), never defaulting to "global"
            val contextPath = when (_activeTab.value) {
                ActiveTab.ALBUMS -> {
                    val album = _selectedAlbum.value
                    if (album != null) "album_${album.name}" else "albums_root"
                }
                ActiveTab.PERSONS -> {
                    val person = _selectedPerson.value
                    if (person != null) "person_${person.name}" else "persons_root"
                }
                else -> {
                    if (isDateMode) {
                        getSortPath()
                    } else {
                        val path = if (currentPath.isNotBlank()) currentPath else directory.path
                        normalizeSortPath(path)
                    }
                }
            }

            // Look up specific preference for this folder; if not set, fall back to global default!
            // This guarantees that "Only this folder" does NOT leak into subfolders or other folders!
            val fSort = if (prefs.hasFolderSort(contextPath)) prefs.getFolderSortBy(contextPath, g_fSort) else g_fSort
            val fDir = if (prefs.hasFolderSort(contextPath)) prefs.getFolderSortDirection(contextPath, g_fDir) else g_fDir
            val mSort = if (prefs.hasMediaSort(contextPath)) prefs.getMediaSortBy(contextPath, g_mSort) else g_mSort
            val mDir = if (prefs.hasMediaSort(contextPath)) prefs.getMediaSortDirection(contextPath, g_mDir) else g_mDir
            
            val fAsc = fDir.lowercase() == "asc"
            val mAsc = mDir.lowercase() == "asc"

            val rawDirs = directory.directories.orEmpty()
            val dirs = if (hideProtectedItems.value) rawDirs.filter { !isFolderProtected(it.path) } else rawDirs
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
                        if (fAsc) dirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        else dirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
                    }
                }
                "count" -> {
                    if (fAsc) dirs.sortedBy { it.mediaCount ?: 0 }
                    else dirs.sortedByDescending { it.mediaCount ?: 0 }
                }
                "random" -> dirs.shuffled()
                else -> dirs
            }

            val media = com.example.data.LivePhotoHelper.pairLivePhotos(directory.media.orEmpty())
            val sortedMedia = when (mSort.lowercase()) {
                "name" -> {
                    if (mAsc) media.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    else media.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
                }
                "date" -> {
                    if (mAsc) {
                        media.sortedWith(
                            compareBy<ApiMedia> { 
                                val time = DateUtils.getLocalTimeMs(it.metadata?.creationDate, it.metadata?.creationDateOffset)
                                if (time <= 0L) Long.MAX_VALUE else time
                            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                        )
                    } else {
                        media.sortedWith(
                            compareByDescending<ApiMedia> { 
                                DateUtils.getLocalTimeMs(it.metadata?.creationDate, it.metadata?.creationDateOffset)
                            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                        )
                    }
                }
                "random" -> media.shuffled()
                else -> media
            }

            directory.copy(directories = sortedDirs, media = sortedMedia)
        } catch (e: Exception) {
            directory
        }
    }

    fun parseSearchStringToQuery(searchText: String): Map<String, Any?> {
        val trimmed = searchText.trim()
        if (trimmed.isEmpty()) return emptyMap()
        val queryDto = com.example.data.search.SearchQueryParser.parse(trimmed)
        return queryDto.toJson()
    }

    init {
        registerNetworkCallback()
        try {
            checkForUpdates()
        } catch (e: Exception) {
            // Safe fallback
        }
        if (prefs.isLoggedIn && prefs.serverUrl.isNotEmpty()) {
            try {
                refreshUserPermissions(force = true)
                loadCurrentDirectory()
                loadAlbums()
                loadRediscover()
            } catch (e: Exception) {
                // Safe fallback
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                ?: return
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    invalidateActiveServerUrl(triggerProbe = true)
                }

                override fun onLost(network: android.net.Network) {
                    invalidateActiveServerUrl(triggerProbe = true)
                }

                override fun onCapabilitiesChanged(
                    network: android.net.Network,
                    networkCapabilities: android.net.NetworkCapabilities
                ) {
                    invalidateActiveServerUrl(triggerProbe = true)
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (_: Exception) {
            // Safely ignore on restricted/test environments
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkCallback?.let { cb ->
            try {
                val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                cm?.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
            networkCallback = null
        }
    }

    fun refreshUserPermissions(force: Boolean = false) {
        _userRole.value = prefs.userRole
        _canManageAlbums.value = computeCanManageAlbums()

        val now = System.currentTimeMillis()
        if (!force && (now - lastPermissionsSyncMs) < 30_000L) return

        val server = prefs.serverUrl
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        if (server.isEmpty()) return

        lastPermissionsSyncMs = now
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val requiresAuth = prefs.username.isNotBlank()
                executeAuthenticated { activeCookies, activePrefix ->
                    api.fetchCurrentUserSession(
                        serverUrl = server,
                        cookies = activeCookies,
                        apiPrefix = activePrefix,
                        throwOnUnauthorized = requiresAuth
                    )
                }
                _userRole.value = prefs.userRole
                _canManageAlbums.value = computeCanManageAlbums()
            } catch (_: Exception) {
                // Keep previously known permissions if session/config check fails transiently
            }
        }
    }

    fun setActiveTab(tab: ActiveTab) {
        val previousTab = _activeTab.value
        _activeTab.value = tab
        // Clear multi-select mode when switching tabs to avoid stray states
        exitSelectMode()

        // Tapping an already-active tab returns from detail view to its overview list
        if (previousTab == tab) {
            when (tab) {
                ActiveTab.ALBUMS -> if (_selectedAlbum.value != null) selectAlbum(null)
                ActiveTab.PERSONS -> if (_selectedPerson.value != null) clearSelectedPerson()
                else -> {}
            }
            return
        }

        // Preserve tab context (open album/person, scroll state, loaded data); only load if needed
        if (prefs.isLoggedIn && prefs.serverUrl.isNotEmpty()) {
            when (tab) {
                ActiveTab.GALLERY -> {
                    refreshUserPermissions(force = false)
                    if (_galleryState.value !is GalleryUiState.Success) {
                        loadCurrentDirectory()
                    }
                }
                ActiveTab.ALBUMS -> {
                    refreshUserPermissions(force = false)
                    if (_albumsState.value !is AlbumsUiState.Success) {
                        loadAlbums()
                    }
                }
                ActiveTab.PERSONS -> {
                    if (_personsState.value !is PersonsUiState.Success) {
                        loadPersons()
                    }
                }
                ActiveTab.REDISCOVER -> {
                    if (_rediscoverState.value !is RediscoverUiState.Success) {
                        loadRediscover()
                    }
                }
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
                com.example.util.AppImageLoaderFactory.install(getApplication(), allowInsecureSsl)

                val (cookies, apiPrefix) = api.login(formattedUrl, user, pass)
                
                // Save settings
                prefs.serverUrl = formattedUrl
                prefs.username = user
                prefs.password = pass
                prefs.cookies = cookies
                prefs.apiPrefix = apiPrefix
                prefs.isLoggedIn = true

                // Sync viewmodel fields and fetch server ClientConfig (e.g. PR #1400 user album rights)
                savedServerUrl.value = formattedUrl
                savedUsername.value = user
                savedPassword.value = pass
                isLoggedIn.value = true
                invalidateActiveServerUrl(triggerProbe = true)
                refreshUserPermissions(force = true)

                _loginState.value = LoginUiState.Success(str(R.string.connected_successfully))
                _pathHistory.value = buildInitialPathStack(prefs.defaultRootPath) // Reset path history
                _activeTab.value = ActiveTab.GALLERY

                loadCurrentDirectory()
                loadAlbums()
                loadRediscover()
            } catch (e: Exception) {
                _loginState.value = LoginUiState.Error(e.localizedMessage ?: str(R.string.connection_failed))
            }
        }
    }

    private fun deduplicateMedia(mediaList: List<ApiMedia>): List<ApiMedia> {
        return mediaList.distinctBy { media ->
            "${media.parentPath?.trim('/') ?: ""}/${media.name}"
        }
    }

    suspend fun fetchMediaForYear(year: Int): List<ApiMedia> {
        val cached = cachedYearMedia.get(year)
        if (cached != null) return cached

        val server = prefs.serverUrl
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        if (server.isEmpty()) return emptyList()

        return try {
            val minMs = com.example.data.search.SearchQueryParser.parse("date:$year-01-01").let { 
                (it as? com.example.data.search.RangeSearch)?.min ?: 0L 
            }
            val maxMs = com.example.data.search.SearchQueryParser.parse("date:$year-12-31").let { 
                (it as? com.example.data.search.RangeSearch)?.max ?: Long.MAX_VALUE 
            }
            val queryYear: Map<String, Any?> = mapOf(
                "type" to 10, // DATE RangeSearch
                "min" to minMs,
                "max" to maxMs
            )
            val queryJson = api.serializeQuery(queryYear)
            val result = executeAuthenticated { activeCookies, activePrefix ->
                api.search(server, queryJson, activeCookies, activePrefix)
            }
            val mediaList = deduplicateMedia(result.media.orEmpty())
            if (mediaList.isNotEmpty()) {
                cachedYearMedia.put(year, mediaList)
            }
            mediaList
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildDateViewDirectory(currentPathStr: String, rawMedia: List<ApiMedia>): ApiDirectory {
        val uniqueMedia = rawMedia.filter { !isMediaProtected(it) }
        return try {
            val rootPath = prefs.defaultRootPath
            val isRootDateView = currentPathStr.isEmpty() ||
                currentPathStr == rootPath ||
                currentPathStr.trim('/') == rootPath.trim('/') ||
                (!currentPathStr.startsWith("year:") && !currentPathStr.contains("month:"))

            if (isRootDateView) {
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
                        name = if (year > 0) "$year" else str(R.string.undated),
                        path = "year:$year",
                        mediaCount = mediaList.size,
                        cache = ApiSubFolderCache(cover = cover)
                    )
                }
                ApiDirectory(
                    id = -1,
                    name = str(R.string.gallery),
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
                        name = if (month > 0) getMonthName(month) else str(R.string.undated),
                        path = "year:$year/month:$month",
                        mediaCount = mediaList.size,
                        cache = ApiSubFolderCache(cover = cover)
                    )
                }
                ApiDirectory(
                    id = -1,
                    name = if (year > 0) "$year" else str(R.string.undated),
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
                    name = if (month > 0) "${getMonthName(month)} $year" else str(R.string.undated),
                    path = currentPathStr,
                    directories = emptyList(),
                    media = monthMedia
                )
            } else {
                ApiDirectory(
                    id = -1,
                    name = str(R.string.gallery),
                    path = currentPathStr,
                    directories = emptyList(),
                    media = uniqueMedia
                )
            }
        } catch (e: Exception) {
            ApiDirectory(
                id = -1,
                name = str(R.string.gallery),
                path = currentPathStr,
                directories = emptyList(),
                media = uniqueMedia
            )
        }
    }

    private fun filterFlattenedMedia(currentPathStr: String, rawMedia: List<ApiMedia>): List<ApiMedia> {
        val uniqueMedia = if (isFolderProtected(currentPathStr)) rawMedia else rawMedia.filter { !isMediaProtected(it) }
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
        cachedYearMedia.evictAll()
        _isRefreshing.value = true
        // Safety timeout so refresh indicator is guaranteed to dismiss within 12 seconds max
        viewModelScope.launch {
            kotlinx.coroutines.delay(12_000L)
            if (_isRefreshing.value) {
                _isRefreshing.value = false
            }
        }
        if (galleryViewMode.value == GalleryViewMode.DATE || isFlattened.value) {
            triggerSilentBackgroundSync(force = true)
        } else {
            loadCurrentDirectory(forceRefresh = true)
        }
    }

    private suspend fun fetchAllMediaSilently(
        serverUrl: String,
        rootPath: String
    ): List<ApiMedia> {
        val accumulatedMedia = java.util.concurrent.ConcurrentLinkedQueue<ApiMedia>()
        val visitedDirs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        suspend fun scanDirectory(path: String) {
            if (!visitedDirs.add(path)) return
            val directory = try {
                fetchSemaphore.withPermit {
                    executeAuthenticated { activeCookies, activePrefix ->
                        api.getGalleryContent(serverUrl, path, activeCookies, activePrefix)
                    }
                }
            } catch (e: com.example.data.UnauthorizedSessionException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: return

            val currentMedia = (directory.media ?: emptyList()).map { media ->
                if (media.parentPath.isNullOrEmpty()) {
                    media.copy(parentPath = path)
                } else media
            }
            if (currentMedia.isNotEmpty()) {
                accumulatedMedia.addAll(currentMedia)
            }

            val subDirs = directory.directories ?: emptyList()
            if (subDirs.isNotEmpty()) {
                kotlinx.coroutines.coroutineScope {
                    subDirs.map { subFolder ->
                        async(Dispatchers.IO) {
                            try {
                                scanDirectory(subFolder.path)
                            } catch (e: Exception) {
                                // Ignore failure on single folder
                            }
                        }
                    }.awaitAll()
                }
            }
        }

        try {
            scanDirectory(rootPath)
        } catch (e: Exception) {
            // Ignore top level scan error
        }

        return deduplicateMedia(accumulatedMedia.toList())
    }

    fun triggerSilentBackgroundSync(force: Boolean = false) {
        val server = prefs.serverUrl
        if (server.isEmpty()) return

        val rootPath = prefs.defaultRootPath
        val cleanRoot = rootPath.trim('/').replace("\\", "/")
        val cacheKey = cleanRoot.ifEmpty { "ROOT" }

        val lastSync = lastBackgroundSyncTime[cacheKey] ?: 0L
        val now = System.currentTimeMillis()
        if (!force && backgroundSyncJob?.isActive == true) return
        if (!force && (now - lastSync) < 120_000L) return

        backgroundSyncJob?.cancel()
        backgroundSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (force) {
                    _isRefreshing.value = true
                }
                val freshMediaList = fetchAllMediaSilently(server, cleanRoot)
                if (freshMediaList.isNotEmpty()) {
                    cachedFlattenedMedia.put(cacheKey, freshMediaList)
                    try {
                        repo.saveAllMedia(freshMediaList)
                    } catch (e: Exception) {
                        // Safe ignore
                    }
                    lastBackgroundSyncTime[cacheKey] = System.currentTimeMillis()

                    // Update frontend when finished ("wenn fertig")
                    if (searchQuery.value.isEmpty() && (isFlattened.value || galleryViewMode.value == GalleryViewMode.DATE)) {
                        val activePath = currentPath
                        val updatedDir = if (isFlattened.value) {
                            val filtered = filterFlattenedMedia(activePath, freshMediaList)
                            ApiDirectory(
                                id = -1,
                                name = if (activePath.isEmpty() || activePath == prefs.defaultRootPath) str(R.string.flattened_gallery) else activePath.substringAfterLast('/'),
                                path = activePath,
                                directories = emptyList(),
                                media = filtered
                            )
                        } else {
                            buildDateViewDirectory(activePath, freshMediaList)
                        }
                        _galleryState.value = GalleryUiState.Success(
                            sortDirectory(updatedDir),
                            buildGalleryContextKey(path = activePath)
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled
            } catch (e: Exception) {
                // Keep showing cached data
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun buildGalleryContextKey(
        path: String = currentPath,
        viewMode: GalleryViewMode = galleryViewMode.value,
        flattened: Boolean = isFlattened.value,
        query: String = searchQuery.value
    ): String {
        return if (query.isNotEmpty()) {
            "gallery:search:$query"
        } else {
            "gallery:${viewMode.name}:$flattened:$path"
        }
    }

    fun loadCurrentDirectory(forceRefresh: Boolean = false) {
        val server = prefs.serverUrl
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        val path = currentPath
        val requestContextKey = buildGalleryContextKey(path = path)

        if (server.isEmpty()) {
            _galleryState.value = GalleryUiState.Error(str(R.string.no_server_configured))
            return
        }

        directoryLoadJob?.cancel()
        directoryLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val isDateOrFlattened = searchQuery.value.isEmpty() && (isFlattened.value || galleryViewMode.value == GalleryViewMode.DATE)
            try {
                if (forceRefresh) {
                    _isRefreshing.value = true
                }

                if (isDateOrFlattened) {
                    // Check if we are inside a specific year or month in DATE mode
                    if (galleryViewMode.value == GalleryViewMode.DATE && (currentPath.startsWith("year:") || currentPath.contains("month:"))) {
                        val year = currentPath.substringAfter("year:").substringBefore("/").toIntOrNull() ?: 0
                        if (year > 0) {
                            var yearMedia = cachedYearMedia.get(year)
                            if (yearMedia == null || yearMedia.isEmpty()) {
                                // Try getting from Room DB or fast server query
                                val dbMedia = repo.getAllMedia()
                                if (dbMedia != null && dbMedia.isNotEmpty()) {
                                    yearMedia = dbMedia.filter { media ->
                                        val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                                        getYearFromTimestamp(localMs) == year
                                    }
                                }
                            }

                            if (yearMedia != null && yearMedia.isNotEmpty()) {
                                cachedYearMedia.put(year, yearMedia)
                                val dir = buildDateViewDirectory(currentPath, yearMedia)
                                _galleryState.value = GalleryUiState.Success(sortDirectory(dir), requestContextKey)
                            } else {
                                _galleryState.value = GalleryUiState.Loading
                            }

                            // Fetch from server specifically for this year in background/refresh
                            val freshYearMedia = fetchMediaForYear(year)
                            if (freshYearMedia.isNotEmpty()) {
                                val dir = buildDateViewDirectory(currentPath, freshYearMedia)
                                _galleryState.value = GalleryUiState.Success(sortDirectory(dir), requestContextKey)
                            }
                            _isRefreshing.value = false
                            return@launch
                        }
                    }

                    val rootPath = if (isFlattened.value && !currentPath.startsWith("year:") && !currentPath.startsWith("month:")) {
                        path
                    } else {
                        prefs.defaultRootPath
                    }
                    val cleanRoot = rootPath.trim('/').replace("\\", "/")
                    val cacheKey = cleanRoot.ifEmpty { "ROOT" }

                    // 1. Lade sofort den letzten Stand (aus Speicher oder Room DB)
                    var mediaList = cachedFlattenedMedia.get(cacheKey)
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
                                cachedFlattenedMedia.put(cacheKey, mediaList)
                            }
                        }
                    }

                    // Sofort anzeigen wenn gecached
                    if (mediaList != null && mediaList.isNotEmpty()) {
                        val cachedDirectory = if (isFlattened.value) {
                            val filteredMedia = filterFlattenedMedia(currentPath, mediaList)
                            ApiDirectory(
                                id = -1,
                                name = if (currentPath.isEmpty() || currentPath == prefs.defaultRootPath) str(R.string.flattened_gallery) else currentPath.substringAfterLast('/'),
                                path = currentPath,
                                directories = emptyList(),
                                media = filteredMedia
                            )
                        } else {
                            buildDateViewDirectory(currentPath, mediaList)
                        }
                        _galleryState.value = GalleryUiState.Success(sortDirectory(cachedDirectory), requestContextKey)
                    } else {
                        _galleryState.value = GalleryUiState.Loading
                    }

                    // 2. Silent background sync starten (ohne UI zu unterbrechen oder Teillisten zu pushen)
                    triggerSilentBackgroundSync(force = forceRefresh)
                    return@launch
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
                        _galleryState.value = GalleryUiState.Success(sortDirectory(directory), requestContextKey)
                    } else {
                        _galleryState.value = GalleryUiState.Loading
                    }
                } else if (searchQuery.value.isNotEmpty() || _galleryState.value !is GalleryUiState.Success) {
                    _galleryState.value = GalleryUiState.Loading
                }

                if (searchQuery.value.isNotEmpty()) {
                    val query = parseSearchStringToQuery(searchQuery.value)
                    val queryJson = api.serializeQuery(query)
                    val searchResult = executeAuthenticated { activeCookies, activePrefix ->
                        api.search(server, queryJson, activeCookies, activePrefix)
                    }
                    val filteredMedia = searchResult.media.orEmpty().filter { !isMediaProtected(it) }
                    val sortedDirectory = sortDirectory(searchResult.copy(media = filteredMedia))
                    _galleryState.value = GalleryUiState.Success(sortedDirectory, requestContextKey)
                } else {
                    // Normal folder view
                    val freshDirectory = executeAuthenticated { activeCookies, activePrefix ->
                        api.getGalleryContent(server, path, activeCookies, activePrefix)
                    }
                    if (freshDirectory.media != null && searchQuery.value.isEmpty() && !isFlattened.value && galleryViewMode.value == GalleryViewMode.FOLDER) {
                        repo.saveDirectory(path, freshDirectory.media)
                    }
                    val sortedDirectory = sortDirectory(freshDirectory)
                    _galleryState.value = GalleryUiState.Success(sortedDirectory, requestContextKey)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                handleApiError(e) { msg ->
                    if (_galleryState.value !is GalleryUiState.Success) {
                        _galleryState.value = GalleryUiState.Error(msg.ifEmpty { str(R.string.failed_fetch_gallery) })
                    }
                }
            } finally {
                if (!isDateOrFlattened) {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun enterFolder(folderPath: String) {
        val clean = folderPath.replace('\\', '/').trim().trim('/').let {
            if (it == "." || it == "./") "" else it.removePrefix("./").trim('/')
        }
        val currentStack = _pathHistory.value.toMutableList()
        currentStack.add(clean)
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
            _personsState.value = PersonsUiState.Error(str(R.string.no_server_configured))
            return
        }
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        _personsState.value = PersonsUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backendPersons = executeAuthenticated { activeCookies, activePrefix ->
                    api.getPersons(server, activeCookies, activePrefix)
                }
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
                handleApiError(e) { msg ->
                    _personsState.value = PersonsUiState.Error(msg.ifEmpty { str(R.string.failed_fetch_persons) })
                }
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
                val searchQuery: Map<String, Any?> = if (prefs.peopleFallbackToKeywords) {
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
                val searchQueryJson = api.serializeQuery(searchQuery)
                val dummyDir = executeAuthenticated { activeCookies, activePrefix ->
                    api.search(server, searchQueryJson, activeCookies, activePrefix)
                }
                val filteredMedia = dummyDir.media.orEmpty().filter { !isMediaFromProtectedFolder(it) }
                val sortedDirectory = sortDirectory(dummyDir.copy(media = filteredMedia))
                _personContentState.value = GalleryUiState.Success(sortedDirectory)
            } catch (e: Exception) {
                handleApiError(e) { msg ->
                    _personContentState.value = GalleryUiState.Error(msg.ifEmpty { str(R.string.failed_fetch_person_content) })
                }
            }
        }
    }

    fun refreshPersons() {
        _isPersonsRefreshing.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(12_000L)
            if (_isPersonsRefreshing.value) {
                _isPersonsRefreshing.value = false
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val server = prefs.serverUrl ?: return@launch
                val backendPersons = executeAuthenticated { activeCookies, activePrefix ->
                    api.getPersons(server, activeCookies, activePrefix)
                }
                val localFavs = prefs.getFavoritePersons()
                val persons = backendPersons.map { person ->
                    val isLocalFav = localFavs.contains(person.name)
                    val isBackendFav = person.isFavourite == true
                    if (isBackendFav && !isLocalFav) {
                        prefs.setFavoritePerson(person.name, true)
                    }
                    person.copy(isFavourite = isLocalFav || isBackendFav)
                }
                _personsState.value = PersonsUiState.Success(sortPersons(persons))
            } catch (e: Exception) {
                handleApiError(e)
            } finally {
                _isPersonsRefreshing.value = false
            }
        }
    }

    fun refreshPersonContent(person: com.example.data.ApiPerson) {
        _isPersonContentRefreshing.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(12_000L)
            if (_isPersonContentRefreshing.value) {
                _isPersonContentRefreshing.value = false
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val server = prefs.serverUrl ?: return@launch
                val searchQuery: Map<String, Any?> = if (prefs.peopleFallbackToKeywords) {
                    mapOf(
                        "type" to 2,
                        "list" to listOf(
                            mapOf("type" to 105, "value" to person.name, "matchType" to 1),
                            mapOf("type" to 104, "value" to person.name, "matchType" to 2)
                        )
                    )
                } else {
                    mapOf("type" to 105, "value" to person.name, "matchType" to 1)
                }
                val searchQueryJson = api.serializeQuery(searchQuery)
                val dummyDir = executeAuthenticated { activeCookies, activePrefix ->
                    api.search(server, searchQueryJson, activeCookies, activePrefix)
                }
                val filteredMedia = dummyDir.media.orEmpty().filter { !isMediaFromProtectedFolder(it) }
                val sortedDirectory = sortDirectory(dummyDir.copy(media = filteredMedia))
                _personContentState.value = GalleryUiState.Success(sortedDirectory)
            } catch (e: Exception) {
                handleApiError(e)
            } finally {
                _isPersonContentRefreshing.value = false
            }
        }
    }

    fun loadAlbums() {
        val server = prefs.serverUrl

        if (server.isEmpty()) {
            _albumsState.value = AlbumsUiState.Error(str(R.string.no_server_configured))
            return
        }

        _albumsState.value = AlbumsUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val albums = executeAuthenticated { activeCookies, activePrefix ->
                    api.getAlbums(server, activeCookies, activePrefix)
                }
                _albumsState.value = AlbumsUiState.Success(sortAlbums(albums))
            } catch (e: Exception) {
                handleApiError(e) { msg ->
                    _albumsState.value = AlbumsUiState.Error(msg.ifEmpty { str(R.string.failed_fetch_albums) })
                }
            }
        }
    }

    fun resetSaveAlbumState() {
        _saveAlbumState.value = SaveAlbumUiState.Idle
    }

    fun saveSearchAsAlbum(name: String, rawQuery: String = searchQuery.value) {
        if (!_canManageAlbums.value) {
            _saveAlbumState.value = SaveAlbumUiState.Error(str(R.string.not_authorized_save_album))
            return
        }
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            _saveAlbumState.value = SaveAlbumUiState.Error(str(R.string.album_name_empty))
            return
        }
        val parsedQuery = parseSearchStringToQuery(rawQuery)
        if (parsedQuery.isEmpty()) {
            _saveAlbumState.value = SaveAlbumUiState.Error(str(R.string.search_query_empty))
            return
        }
        val server = prefs.serverUrl
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
        if (server.isEmpty()) {
            _saveAlbumState.value = SaveAlbumUiState.Error(str(R.string.no_server_configured))
            return
        }

        _saveAlbumState.value = SaveAlbumUiState.Saving
        viewModelScope.launch(Dispatchers.IO) {
            try {
                executeAuthenticated { activeCookies, activePrefix ->
                    api.createSavedSearchAlbum(
                        serverUrl = server,
                        name = trimmedName,
                        searchQuery = parsedQuery,
                        cookies = activeCookies,
                        apiPrefix = activePrefix
                    )
                }
                _saveAlbumState.value = SaveAlbumUiState.Success(trimmedName)
                loadAlbums()
            } catch (e: Exception) {
                handleApiError(e) { msg ->
                    _saveAlbumState.value = SaveAlbumUiState.Error(
                        msg.ifEmpty { str(R.string.failed_save_album) }
                    )
                    refreshUserPermissions(force = true)
                }
            }
        }
    }

    fun resetDeleteAlbumState() {
        _deleteAlbumState.value = DeleteAlbumUiState.Idle
    }

    fun deleteAlbum(album: ApiAlbum) {
        if (!_canManageAlbums.value) {
            _deleteAlbumState.value = DeleteAlbumUiState.Error(str(R.string.not_authorized_delete_album))
            return
        }
        if (album.locked) {
            _deleteAlbumState.value = DeleteAlbumUiState.Error(str(R.string.locked_album_cannot_delete))
            return
        }
        val server = prefs.serverUrl
        if (server.isEmpty()) {
            _deleteAlbumState.value = DeleteAlbumUiState.Error(str(R.string.no_server_configured))
            return
        }

        _deleteAlbumState.value = DeleteAlbumUiState.Deleting
        viewModelScope.launch(Dispatchers.IO) {
            try {
                executeAuthenticated { activeCookies, activePrefix ->
                    api.deleteAlbum(
                        serverUrl = server,
                        albumId = album.id,
                        cookies = activeCookies,
                        apiPrefix = activePrefix
                    )
                }
                if (_selectedAlbum.value?.id == album.id) {
                    _selectedAlbum.value = null
                    _albumContentState.value = null
                }
                val currentAlbumsState = _albumsState.value
                if (currentAlbumsState is AlbumsUiState.Success) {
                    _albumsState.value = AlbumsUiState.Success(
                        currentAlbumsState.albums.filterNot { it.id == album.id }
                    )
                }
                _deleteAlbumState.value = DeleteAlbumUiState.Success(album.name)
                loadAlbums()
            } catch (e: Exception) {
                handleApiError(e) { msg ->
                    _deleteAlbumState.value = DeleteAlbumUiState.Error(
                        msg.ifEmpty { str(R.string.failed_delete_album) }
                    )
                    refreshUserPermissions(force = true)
                }
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
        val query = album.searchQuery ?: return

        _albumContentState.value = GalleryUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val queryJson = api.serializeQuery(query)
                val directory = executeAuthenticated { activeCookies, activePrefix ->
                    api.search(server, queryJson, activeCookies, activePrefix)
                }
                val filteredMedia = directory.media.orEmpty().filter { !isMediaFromProtectedFolder(it) }
                val sortedDirectory = sortDirectory(directory.copy(media = filteredMedia))
                _albumContentState.value = GalleryUiState.Success(sortedDirectory)
            } catch (e: Exception) {
                handleApiError(e) { msg ->
                    _albumContentState.value = GalleryUiState.Error(msg.ifEmpty { str(R.string.failed_load_album_content) })
                }
            }
        }
    }

    fun refreshAlbums() {
        _isAlbumsRefreshing.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(12_000L)
            if (_isAlbumsRefreshing.value) {
                _isAlbumsRefreshing.value = false
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val server = prefs.serverUrl
                if (server.isNotEmpty()) {
                    val albums = executeAuthenticated { activeCookies, activePrefix ->
                        api.getAlbums(server, activeCookies, activePrefix)
                    }
                    _albumsState.value = AlbumsUiState.Success(sortAlbums(albums))
                }
            } catch (e: Exception) {
                handleApiError(e)
            } finally {
                _isAlbumsRefreshing.value = false
            }
        }
    }

    fun refreshAlbumContent(album: ApiAlbum) {
        val query = album.searchQuery ?: return
        _isAlbumContentRefreshing.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(12_000L)
            if (_isAlbumContentRefreshing.value) {
                _isAlbumContentRefreshing.value = false
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val server = prefs.serverUrl
                if (server.isNotEmpty()) {
                    val queryJson = api.serializeQuery(query)
                    val directory = executeAuthenticated { activeCookies, activePrefix ->
                        api.search(server, queryJson, activeCookies, activePrefix)
                    }
                    val filteredMedia = directory.media.orEmpty().filter { !isMediaFromProtectedFolder(it) }
                    val sortedDirectory = sortDirectory(directory.copy(media = filteredMedia))
                    _albumContentState.value = GalleryUiState.Success(sortedDirectory)
                }
            } catch (e: Exception) {
                handleApiError(e)
            } finally {
                _isAlbumContentRefreshing.value = false
            }
        }
    }

    // --- Rediscover (Top Picks) Functionality ---
    fun loadRediscover() {
        val server = prefs.serverUrl
        val days = rediscoverDays.value

        if (server.isEmpty()) {
            _rediscoverState.value = RediscoverUiState.Error(str(R.string.no_server_configured))
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
                val everyYearDir = executeAuthenticated { activeCookies, activePrefix ->
                    api.search(server, api.serializeQuery(queryEveryYear), activeCookies, activePrefix)
                }

                // Combine results
                val allMedia = mutableListOf<ApiMedia>()
                everyYearDir.media?.let { allMedia.addAll(it) }

                // Deduplicate by name and parent path and filter out protected media
                val uniqueMedia = com.example.data.LivePhotoHelper.pairLivePhotos(
                    allMedia.distinctBy { getMediaFullPath(it) }
                ).filter { !isMediaProtected(it) }

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
                handleApiError(e) { msg ->
                    _rediscoverState.value = RediscoverUiState.Error(msg.ifEmpty { str(R.string.failed_load_rediscover) })
                }
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

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.biometricLockEnabled = enabled
        isBiometricLockEnabled.value = enabled
        if (!enabled) {
            isAppLocked.value = false
        }
    }

    fun lockApp() {
        if (isBiometricLockEnabled.value) {
            isAppLocked.value = true
        }
    }

    fun unlockApp() {
        isAppLocked.value = false
    }

    fun toggleProtectedFolder(path: String) {
        prefs.toggleProtectedFolder(path)
        protectedFolders.value = prefs.getProtectedFolders()
        loadRediscover()
        _selectedAlbum.value?.let { loadAlbumContent(it) }
        _selectedPerson.value?.let { loadPersonContent(it) }
    }

    fun setHideProtectedItems(hide: Boolean) {
        prefs.hideProtectedItems = hide
        hideProtectedItems.value = hide
    }

    fun isFolderDirectlyProtected(path: String): Boolean {
        return prefs.isFolderDirectlyProtected(path)
    }

    fun isFolderProtected(path: String): Boolean {
        val clean = path.replace('\\', '/').trim().trim('/').let {
            if (it == "." || it == "./") "" else it.removePrefix("./").trim('/')
        }
        if (clean.isEmpty()) return false

        // Check against protected folders configured in prefs
        if (prefs.isFolderProtected(clean)) return true

        // If currently browsing inside a protected folder, subfolders inherit protection
        if (currentPath.isNotEmpty() && prefs.isFolderProtected(currentPath)) {
            if (clean == currentPath || clean.startsWith("$currentPath/")) {
                return true
            }
        }

        return false
    }

    fun isMediaFromProtectedFolder(media: ApiMedia): Boolean {
        val parent = media.parentPath.orEmpty().replace('\\', '/').trim().trim('/').let {
            if (it == "." || it == "./") "" else it.removePrefix("./").trim('/')
        }
        return parent.isNotEmpty() && isFolderProtected(parent)
    }

    fun isMediaProtected(media: ApiMedia): Boolean {
        return isMediaFromProtectedFolder(media)
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
        if (timestamp <= 0L) return 0
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

                _shareProgress.value = -1f
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (media.isVideo) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, str(R.string.share_media_via)))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _shareProgress.value = -1f
                throw e
            } catch (e: Exception) {
                _shareProgress.value = -1f
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.download_failed_single, e.localizedMessage ?: ""),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
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
                val safeFileName = media.name.substringAfterLast('/').ifBlank { "media_${media.id ?: 0}" }
                val request = android.app.DownloadManager.Request(url.toUri())
                    .setTitle(safeFileName)
                    .setDescription(context.getString(R.string.download_description))
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, safeFileName)
                    .apply {
                        if (cookies.isNotEmpty()) {
                            addRequestHeader("Cookie", cookies)
                        }
                    }
                downloadManager.enqueue(request)
            } catch (_: Exception) {
                // Continue with remaining items
            }
        }
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.download_started_multiple, selected.size),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        exitSelectMode()
    }

    fun shareSelectedMedia(context: Context) {
        val selected = _selectedMediaForShare.value
        if (selected.isEmpty()) return

        shareJob?.cancel()
        shareJob = viewModelScope.launch(Dispatchers.IO) {
            _shareProgress.value = 0f
            val uris = mutableListOf<android.net.Uri>()
            var lastError: Exception? = null

            selected.forEachIndexed { index, media ->
                try {
                    val file = downloadMediaToCache(context, media) { fileProgress ->
                        val overallProgress = (index.toFloat() + fileProgress) / selected.size
                        _shareProgress.value = overallProgress
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    uris.add(uri)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    _shareProgress.value = -1f
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }

            _shareProgress.value = -1f

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (uris.isEmpty()) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.download_failed_single, lastError?.localizedMessage ?: ""),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = if (selected.all { it.isVideo }) "video/*" else if (selected.none { it.isVideo }) "image/*" else "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, java.util.ArrayList(uris))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, str(R.string.share_photos_via)))
                    exitSelectMode()
                }
            }
        }
    }

    private suspend fun downloadMediaToCache(
        context: Context,
        media: ApiMedia,
        onProgress: ((Float) -> Unit)? = null
    ): java.io.File {
        val url = getOriginalMediaUrl(media)
        val safeName = media.name.substringAfterLast('/').ifBlank { "media_${media.id ?: 0}" }
        val file = java.io.File(context.cacheDir, safeName)

        val response = executeAuthenticated { activeCookies, _ ->
            api.download(url, activeCookies).also { resp ->
                if (resp.code == 401) {
                    resp.close()
                    throw com.example.data.UnauthorizedSessionException("Unauthorized. Session might have expired.")
                }
            }
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Server returned error code: ${resp.code}")
            }
            val body = resp.body ?: throw IOException("Empty download response")
            val totalBytes = body.contentLength()
            try {
                body.byteStream().use { inputStream ->
                    java.io.FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (totalBytes > 0) {
                                onProgress?.invoke(totalBytesRead.toFloat() / totalBytes)
                            }
                        }
                        outputStream.flush()
                    }
                }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }

        return file
    }

    @Volatile
    private var cachedActiveUrl: String? = null
    @Volatile
    private var lastUrlCheckTime: Long = 0L

    fun invalidateActiveServerUrl(triggerProbe: Boolean = false) {
        cachedActiveUrl = null
        lastUrlCheckTime = 0L
        if (triggerProbe) {
            val primary = PiGalleryApi.ensureHttpUrl(prefs.serverUrl)
            val local = PiGalleryApi.ensureHttpUrl(prefs.localServerUrl)
            if (local.isNotEmpty()) {
                triggerUrlProbeAsync(local, primary)
            }
        }
    }

    private fun triggerUrlProbeAsync(local: String, primary: String) {
        if (urlProbeJob?.isActive == true) return
        lastUrlCheckTime = System.currentTimeMillis()
        urlProbeJob = viewModelScope.launch(Dispatchers.IO) {
            val resolved = try {
                val uri = java.net.URI(local)
                val host = uri.host ?: ""
                val port = if (uri.port != -1) uri.port else 80
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(host, port), 250)
                }
                local
            } catch (_: Exception) {
                primary
            }
            cachedActiveUrl = resolved
        }
    }

    // --- URL Construction and Media Resolution ---
    fun getActiveServerUrl(): String {
        val primary = PiGalleryApi.ensureHttpUrl(prefs.serverUrl)
        val local = PiGalleryApi.ensureHttpUrl(prefs.localServerUrl)
        if (local.isEmpty()) return primary

        val now = System.currentTimeMillis()
        val cached = cachedActiveUrl
        if (cached != null && (now - lastUrlCheckTime < 20_000L)) {
            return cached
        }

        // Trigger asynchronous background check so the UI thread is never blocked during scrolling
        if (now - lastUrlCheckTime >= 20_000L) {
            triggerUrlProbeAsync(local, primary)
        }

        return cached ?: primary
    }

    fun getThumbnailUrl(media: ApiMedia): String {
        val sanitizedBase = getActiveServerUrl()
        val apiPrefix = prefs.apiPrefix
        val relativePath = encodePath(getMediaFullPath(media))
        val suffix = prefs.thumbnailPathSuffix.trim('/')
        return if (suffix.isNotEmpty()) {
            "$sanitizedBase$apiPrefix/gallery/content/$relativePath/$suffix"
        } else {
            "$sanitizedBase$apiPrefix/gallery/content/$relativePath"
        }
    }

    fun getPreloadMediaUrl(media: ApiMedia): String? {
        if (media.isVideo) return null
        val suffix = prefs.preloadPathSuffix.trim('/')
        if (suffix.isBlank()) return null
        val sanitizedBase = getActiveServerUrl()
        val apiPrefix = prefs.apiPrefix
        val relativePath = encodePath(getMediaFullPath(media))
        return "$sanitizedBase$apiPrefix/gallery/content/$relativePath/$suffix"
    }

    fun getOriginalMediaUrl(media: ApiMedia): String {
        val sanitizedBase = getActiveServerUrl()
        val apiPrefix = prefs.apiPrefix
        val relativePath = encodePath(getMediaFullPath(media))
        return if (media.isVideo) {
            val suffix = prefs.videoPathSuffix.trim('/')
            if (suffix.isNotEmpty()) {
                "$sanitizedBase$apiPrefix/gallery/content/$relativePath/$suffix"
            } else {
                "$sanitizedBase$apiPrefix/gallery/content/$relativePath"
            }
        } else {
            "$sanitizedBase$apiPrefix/gallery/content/$relativePath"
        }
    }

    fun getLiveVideoUrl(media: ApiMedia): String? {
        val companionPath = com.example.data.LivePhotoHelper.resolveCompanionVideoPath(media, currentPath)
            ?: return null
        val sanitizedBase = getActiveServerUrl()
        val apiPrefix = prefs.apiPrefix
        val relativePath = encodePath(companionPath)
        return "$sanitizedBase$apiPrefix/gallery/content/$relativePath"
    }

    private fun isSessionUnauthorizedError(e: Exception): Boolean {
        if (e is com.example.data.UnauthorizedSessionException) return true
        val msg = e.localizedMessage ?: e.message ?: ""
        return msg.contains("Unauthorized", ignoreCase = true) ||
            msg.contains("Session might have expired", ignoreCase = true) ||
            msg.contains("error code: 401", ignoreCase = true)
    }

    /**
     * Executes an authenticated PiGallery2 API call.
     * On HTTP 401 (UnauthorizedSessionException), attempts one serialized silent re-login
     * using stored credentials before falling back to a clean logout (preserving settings).
     */
    internal suspend fun <T> executeAuthenticated(
        block: suspend (cookies: String, apiPrefix: String) -> T
    ): T {
        val initialCookies = prefs.cookies
        val initialPrefix = prefs.apiPrefix
        return try {
            block(initialCookies, initialPrefix)
        } catch (e: Exception) {
            if (!isSessionUnauthorizedError(e)) throw e

            val refreshed = trySilentReauth(failedCookies = initialCookies)
            if (refreshed) {
                try {
                    block(prefs.cookies, prefs.apiPrefix)
                } catch (retryErr: Exception) {
                    if (isSessionUnauthorizedError(retryErr)) {
                        handleSessionExpired()
                    }
                    throw retryErr
                }
            } else {
                handleSessionExpired()
                throw e
            }
        }
    }

    private suspend fun trySilentReauth(failedCookies: String): Boolean {
        return reauthMutex.withLock {
            if (!prefs.isLoggedIn) return@withLock false
            // Another coroutine may have already refreshed the session while we waited
            val currentCookies = prefs.cookies
            if (currentCookies.isNotBlank() && currentCookies != failedCookies) {
                return@withLock true
            }
            val server = prefs.serverUrl
            val user = prefs.username
            val pass = prefs.password
            if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                return@withLock false
            }
            try {
                val (newCookies, newPrefix) = api.login(server, user, pass)
                if (newCookies.isNotBlank()) {
                    prefs.cookies = newCookies
                    prefs.apiPrefix = newPrefix
                    prefs.isLoggedIn = true
                    _userRole.value = prefs.userRole
                    _canManageAlbums.value = computeCanManageAlbums()
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun handleSessionExpired() {
        if (isLoggedIn.value || prefs.isLoggedIn) {
            logout()
            _loginState.value = LoginUiState.Error(str(R.string.session_expired))
        }
    }

    private fun handleApiError(e: Exception, onNonAuthError: ((String) -> Unit)? = null) {
        val msg = e.localizedMessage ?: e.message ?: ""
        if (isSessionUnauthorizedError(e)) {
            handleSessionExpired()
        } else if (onNonAuthError != null) {
            onNonAuthError(msg)
        } else if (_galleryState.value !is GalleryUiState.Success) {
            _galleryState.value = GalleryUiState.Error(msg.ifEmpty { str(R.string.failed_fetch_content) })
        }
    }

    fun getAlbumCoverUrl(coverName: String, coverDirectory: String): String {
        val sanitizedBase = getActiveServerUrl()
        val apiPrefix = prefs.apiPrefix
        val fullPath = if (coverDirectory.isEmpty()) coverName else "$coverDirectory/$coverName"
        val relativePath = encodePath(fullPath)
        val suffix = prefs.thumbnailPathSuffix.trim('/')
        return if (suffix.isNotEmpty()) {
            "$sanitizedBase$apiPrefix/gallery/content/$relativePath/$suffix"
        } else {
            "$sanitizedBase$apiPrefix/gallery/content/$relativePath"
        }
    }

    fun getPersonThumbnailUrl(person: com.example.data.ApiPerson): String {
        val sanitizedBase = getActiveServerUrl()
        val apiPrefix = prefs.apiPrefix
        val encodedName = encodePath(person.name)
        return "$sanitizedBase$apiPrefix/person/$encodedName/thumbnail"
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
        cachedFlattenedMedia.evictAll()
        cachedYearMedia.evictAll()
        scrollPositions.evictAll()
        prefs.clearSession()
        isLoggedIn.value = false
        savedServerUrl.value = prefs.serverUrl
        savedUsername.value = prefs.username
        savedPassword.value = ""
        lastPermissionsSyncMs = 0L
        _userRole.value = -1
        _canManageAlbums.value = false
        _saveAlbumState.value = SaveAlbumUiState.Idle
        _deleteAlbumState.value = DeleteAlbumUiState.Idle
        _pathHistory.value = buildInitialPathStack(prefs.defaultRootPath)
        _loginState.value = LoginUiState.Idle
        _activeTab.value = ActiveTab.GALLERY
        _selectedAlbum.value = null
        _albumContentState.value = null
        _selectedPerson.value = null
        _personContentState.value = null
        _selectedMedia.value = null
        _albumsFilterQuery.value = ""
        _personsFilterQuery.value = ""
        _rediscoverExpandedYears.value = emptySet()
        _selectedSettingsTab.value = 0
        isSearchActive.value = false
        searchQuery.value = ""
        showSearchSuggestions.value = false
        isFlattened.value = false
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
        val digitGroups = (kotlin.math.log10(size.toDouble()) / kotlin.math.log10(1024.0))
            .toInt()
            .coerceIn(0, units.lastIndex)
        return String.format(
            java.util.Locale.getDefault(),
            "%.2f %s",
            size / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
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

            cachedFlattenedMedia.evictAll()
            cachedYearMedia.evictAll()
            lastBackgroundSyncTime.clear()

            updateCacheSize()
        }
    }

    private fun getMonthFromTimestamp(timestampMs: Long): Int {
        if (timestampMs <= 0L) return 0
        val ms = if (timestampMs < 10000000000L) timestampMs * 1000L else timestampMs
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        return cal.get(java.util.Calendar.MONTH) + 1
    }

    fun checkForUpdates(force: Boolean = false) {
        val lastCheck = prefs.lastUpdateCheck
        val now = System.currentTimeMillis()
        if (!force && !prefs.autoCheckUpdates) return
        if (!force && (now - lastCheck) < 24 * 60 * 60 * 1000) return

        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateState.Checking
            try {
                val client = api.client.newBuilder()
                    .cache(null)
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("https://api.github.com/repos/dasmaetthes/pigallery2_android/releases/latest")
                    .header("User-Agent", "PiGallery2-Android")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateState.value = UpdateState.Error(str(R.string.failed_check_updates, response.code))
                        return@launch
                    }
                    val bodyString = response.body?.string() ?: ""
                    val json = org.json.JSONObject(bodyString)
                    val tagName = json.optString("tag_name", json.optString("tagName", "v1.0.0"))
                    val htmlUrl = json.optString("html_url", "https://github.com/dasmaetthes/pigallery2_android/releases")
                    val releaseNotes = json.optString("body").ifBlank { str(R.string.no_release_notes) }

                    val currentVersion = com.example.BuildConfig.VERSION_NAME.ifBlank { "2.7" }
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
                _updateState.value = UpdateState.Error(e.localizedMessage ?: str(R.string.network_error_updates))
            }
        }
    }

    fun getMonthName(month: Int): String {
        return when (month) {
            1 -> str(R.string.month_1)
            2 -> str(R.string.month_2)
            3 -> str(R.string.month_3)
            4 -> str(R.string.month_4)
            5 -> str(R.string.month_5)
            6 -> str(R.string.month_6)
            7 -> str(R.string.month_7)
            8 -> str(R.string.month_8)
            9 -> str(R.string.month_9)
            10 -> str(R.string.month_10)
            11 -> str(R.string.month_11)
            12 -> str(R.string.month_12)
            else -> "$month"
        }
    }

    fun testNetworkConnections() {
        viewModelScope.launch(Dispatchers.IO) {
            networkConnectionInfo.value = networkConnectionInfo.value.copy(isTesting = true)

            val primaryUrl = PiGalleryApi.ensureHttpUrl(prefs.serverUrl)
            val localUrl = PiGalleryApi.ensureHttpUrl(prefs.localServerUrl)

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
