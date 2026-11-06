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

    private val directoryCache = java.util.concurrent.ConcurrentHashMap<String, ApiDirectory>()
    private val cachedFlattenedMedia = java.util.concurrent.ConcurrentHashMap<String, List<ApiMedia>>()
    private val cachedYearMedia = java.util.concurrent.ConcurrentHashMap<Int, List<ApiMedia>>()
    private val lastBackgroundSyncTime = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val fetchSemaphore = Semaphore(4)
    private var backgroundSyncJob: kotlinx.coroutines.Job? = null
    private var directoryLoadJob: kotlinx.coroutines.Job? = null

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

    fun normalizeSortPath(path: String): String {
        val clean = path.trim().replace('\\', '/').trim('/')
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
                    val path = currentPath.trim().replace('\\', '/').trim('/')
                    if (path.isEmpty()) "date_mode_root" else "date_mode_$path"
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
                    prefs.getMediaSortBy(albumPath, g_mSort)
                } else "name"
            }
            ActiveTab.PERSONS -> {
                val person = _selectedPerson.value
                if (person != null) {
                    val personPath = "person_${person.name}"
                    val g_mSort = prefs.getMediaSortBy("global", "date")
                    prefs.getMediaSortBy(personPath, g_mSort)
                } else "name"
            }
            else -> {
                val isDateMode = galleryViewMode.value == GalleryViewMode.DATE
                val globalPath = if (isDateMode) "date_mode_global" else "global"
                val g_mSort = prefs.getMediaSortBy(globalPath, "date")
                val currentContextPath = getSortPath()
                prefs.getMediaSortBy(currentContextPath, g_mSort)
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
                        val path = if (directory.path.isNotBlank()) directory.path else currentPath
                        normalizeSortPath(path)
                    }
                }
            }

            // Look up specific preference for this folder; if not set, fall back to global default!
            // This guarantees that "Only this folder" does NOT leak into subfolders or other folders!
            val fSort = prefs.getFolderSortBy(contextPath, g_fSort)
            val fDir = prefs.getFolderSortDirection(contextPath, g_fDir)
            val mSort = prefs.getMediaSortBy(contextPath, g_mSort)
            val mDir = prefs.getMediaSortDirection(contextPath, g_mDir)
            
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

            val media = directory.media.orEmpty()
            val sortedMedia = when (mSort.lowercase()) {
                "name" -> {
                    if (mAsc) media.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    else media.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
                }
                "date" -> {
                    if (mAsc) media.sortedBy { 
                        val time = DateUtils.getLocalTimeMs(it.metadata?.creationDate, it.metadata?.creationDateOffset)
                        if (time <= 0L) Long.MAX_VALUE else time
                    }
                    else media.sortedByDescending { 
                        DateUtils.getLocalTimeMs(it.metadata?.creationDate, it.metadata?.creationDateOffset)
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
        try {
            checkForUpdates()
        } catch (e: Exception) {
            // Safe fallback
        }
        if (prefs.isLoggedIn && prefs.serverUrl.isNotEmpty()) {
            try {
                loadCurrentDirectory()
                loadAlbums()
                loadRediscover()
            } catch (e: Exception) {
                // Safe fallback
            }
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

    private fun deduplicateMedia(mediaList: List<ApiMedia>): List<ApiMedia> {
        return mediaList.distinctBy { media ->
            "${media.parentPath?.trim('/') ?: ""}/${media.name}"
        }
    }

    suspend fun fetchMediaForYear(year: Int): List<ApiMedia> {
        val cached = cachedYearMedia[year]
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
            val result = api.search(server, queryJson, cookies, apiPrefix)
            val mediaList = deduplicateMedia(result.media.orEmpty())
            if (mediaList.isNotEmpty()) {
                cachedYearMedia[year] = mediaList
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
        } catch (e: Exception) {
            ApiDirectory(
                id = -1,
                name = "Gallery",
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
        directoryCache.clear()
        cachedYearMedia.clear()
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
        rootPath: String,
        cookies: String,
        apiPrefix: String
    ): List<ApiMedia> {
        val accumulatedMedia = java.util.concurrent.ConcurrentLinkedQueue<ApiMedia>()
        val visitedDirs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        suspend fun scanDirectory(path: String) {
            if (!visitedDirs.add(path)) return
            val directory = try {
                fetchSemaphore.withPermit {
                    api.getGalleryContent(serverUrl, path, cookies, apiPrefix)
                }
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
        val cookies = prefs.cookies
        val apiPrefix = prefs.apiPrefix
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
                val freshMediaList = fetchAllMediaSilently(server, cleanRoot, cookies, apiPrefix)
                if (freshMediaList.isNotEmpty()) {
                    cachedFlattenedMedia[cacheKey] = freshMediaList
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
                                name = if (activePath.isEmpty() || activePath == prefs.defaultRootPath) "Flattened Gallery" else activePath.substringAfterLast('/'),
                                path = activePath,
                                directories = emptyList(),
                                media = filtered
                            )
                        } else {
                            buildDateViewDirectory(activePath, freshMediaList)
                        }
                        _galleryState.value = GalleryUiState.Success(sortDirectory(updatedDir))
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
                            var yearMedia = cachedYearMedia[year]
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
                                cachedYearMedia[year] = yearMedia
                                val dir = buildDateViewDirectory(currentPath, yearMedia)
                                _galleryState.value = GalleryUiState.Success(sortDirectory(dir))
                            } else {
                                _galleryState.value = GalleryUiState.Loading
                            }

                            // Fetch from server specifically for this year in background/refresh
                            val freshYearMedia = fetchMediaForYear(year)
                            if (freshYearMedia.isNotEmpty()) {
                                val dir = buildDateViewDirectory(currentPath, freshYearMedia)
                                _galleryState.value = GalleryUiState.Success(sortDirectory(dir))
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
                    var mediaList = cachedFlattenedMedia[cacheKey]
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

                    // Sofort anzeigen wenn gecached
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
                        _galleryState.value = GalleryUiState.Success(sortDirectory(cachedDirectory))
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
                        _galleryState.value = GalleryUiState.Success(sortDirectory(directory))
                    } else {
                        _galleryState.value = GalleryUiState.Loading
                    }
                } else if (_galleryState.value !is GalleryUiState.Success) {
                    _galleryState.value = GalleryUiState.Loading
                }

                if (searchQuery.value.isNotEmpty()) {
                    val query = parseSearchStringToQuery(searchQuery.value)
                    val queryJson = api.serializeQuery(query)
                    val searchResult = api.search(server, queryJson, cookies, apiPrefix)
                    val filteredMedia = searchResult.media.orEmpty().filter { !isMediaProtected(it) }
                    val sortedDirectory = sortDirectory(searchResult.copy(media = filteredMedia))
                    _galleryState.value = GalleryUiState.Success(sortedDirectory)
                } else {
                    // Normal folder view
                    val freshDirectory = api.getGalleryContent(server, path, cookies, apiPrefix)
                    if (freshDirectory.media != null && searchQuery.value.isEmpty() && !isFlattened.value && galleryViewMode.value == GalleryViewMode.FOLDER) {
                        repo.saveDirectory(path, freshDirectory.media)
                    }
                    val sortedDirectory = sortDirectory(freshDirectory)
                    _galleryState.value = GalleryUiState.Success(sortedDirectory)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_galleryState.value !is GalleryUiState.Success) {
                    _galleryState.value = GalleryUiState.Error(e.localizedMessage ?: "Failed to fetch gallery content")
                }
            } finally {
                if (!isDateOrFlattened) {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun enterFolder(folderPath: String) {
        val clean = folderPath.trim('/').replace('\\', '/')
        val fullPath = if (clean.startsWith(currentPath, ignoreCase = true) || currentPath.isEmpty()) {
            clean
        } else {
            "$currentPath/$clean"
        }
        val currentStack = _pathHistory.value.toMutableList()
        currentStack.add(fullPath)
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
                val filteredMedia = dummyDir.media.orEmpty().filter { !isMediaFromProtectedFolder(it) }
                val sortedDirectory = sortDirectory(dummyDir.copy(media = filteredMedia))
                _personContentState.value = GalleryUiState.Success(sortedDirectory)
            } catch (e: Exception) {
                _personContentState.value = GalleryUiState.Error(e.localizedMessage ?: "Failed to fetch person content")
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
                val cookies = prefs.cookies
                val apiPrefix = prefs.apiPrefix
                val backendPersons = api.getPersons(server, cookies, apiPrefix)
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
                // Keep showing existing data
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
                val cookies = prefs.cookies
                val apiPrefix = prefs.apiPrefix
                val searchQuery = if (prefs.peopleFallbackToKeywords) {
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
                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = moshi.adapter<Map<String, Any>>(type)
                val searchQueryJson = adapter.toJson(searchQuery)
                val dummyDir = api.search(server, searchQueryJson, cookies, apiPrefix)
                val filteredMedia = dummyDir.media.orEmpty().filter { !isMediaFromProtectedFolder(it) }
                val sortedDirectory = sortDirectory(dummyDir.copy(media = filteredMedia))
                _personContentState.value = GalleryUiState.Success(sortedDirectory)
            } catch (e: Exception) {
                // Keep showing existing data
            } finally {
                _isPersonContentRefreshing.value = false
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
                val filteredMedia = directory.media.orEmpty().filter { !isMediaFromProtectedFolder(it) }
                val sortedDirectory = sortDirectory(directory.copy(media = filteredMedia))
                _albumContentState.value = GalleryUiState.Success(sortedDirectory)
            } catch (e: Exception) {
                _albumContentState.value = GalleryUiState.Error(e.localizedMessage ?: "Failed to load album content")
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
                val cookies = prefs.cookies
                val apiPrefix = prefs.apiPrefix
                if (server.isNotEmpty()) {
                    val albums = api.getAlbums(server, cookies, apiPrefix)
                    _albumsState.value = AlbumsUiState.Success(sortAlbums(albums))
                }
            } catch (e: Exception) {
                // Keep showing existing albums
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
                val cookies = prefs.cookies
                val apiPrefix = prefs.apiPrefix
                if (server.isNotEmpty()) {
                    val queryJson = api.serializeQuery(query)
                    val directory = api.search(server, queryJson, cookies, apiPrefix)
                    val filteredMedia = directory.media.orEmpty().filter { !isMediaFromProtectedFolder(it) }
                    val sortedDirectory = sortDirectory(directory.copy(media = filteredMedia))
                    _albumContentState.value = GalleryUiState.Success(sortedDirectory)
                }
            } catch (e: Exception) {
                // Keep showing existing content
            } finally {
                _isAlbumContentRefreshing.value = false
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

                // Deduplicate by name and parent path and filter out protected media
                val uniqueMedia = allMedia.distinctBy { getMediaFullPath(it) }.filter { !isMediaProtected(it) }

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
        if (prefs.isFolderProtected(path)) return true

        // If path is a subfolder relative to currentPath
        val clean = path.trim('/').replace('\\', '/')
        if (clean.isNotEmpty() && currentPath.isNotEmpty()) {
            if (prefs.isFolderProtected(currentPath)) return true
            val combined = "$currentPath/$clean"
            if (prefs.isFolderProtected(combined)) return true
        }

        // If any ancestor folder in current path history is protected
        if (_pathHistory.value.any { it.isNotBlank() && prefs.isFolderProtected(it) }) {
            return true
        }

        return false
    }

    fun isMediaFromProtectedFolder(media: ApiMedia): Boolean {
        val parent = media.parentPath.orEmpty()
        if (parent.isNotEmpty() && isFolderProtected(parent)) return true

        val fullPath = getMediaFullPath(media)
        if (fullPath.isNotEmpty() && isFolderProtected(fullPath)) return true

        return false
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
        response.use { resp ->
            resp.body?.let { body ->
                val totalBytes = body.contentLength()
                body.byteStream().use { inputStream ->
                    java.io.FileOutputStream(file).use { outputStream ->
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
                    }
                }
            }
        }
        
        return file
    }

    @Volatile
    private var cachedActiveUrl: String? = null
    @Volatile
    private var lastUrlCheckTime: Long = 0L

    // --- URL Construction and Media Resolution ---
    fun getActiveServerUrl(): String {
        val primary = prefs.serverUrl.let { if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it }.trimEnd('/')
        val local = prefs.localServerUrl.let { if (it.isNotBlank() && !it.startsWith("http")) "http://$it" else it }.trimEnd('/')
        if (local.isEmpty()) return primary

        val now = System.currentTimeMillis()
        val cached = cachedActiveUrl
        if (cached != null && (now - lastUrlCheckTime < 20_000L)) {
            return cached
        }

        // Trigger asynchronous background check so the UI thread is never blocked during scrolling
        if (now - lastUrlCheckTime >= 20_000L) {
            lastUrlCheckTime = now
            viewModelScope.launch(Dispatchers.IO) {
                val resolved = try {
                    val uri = java.net.URI(local)
                    val host = uri.host ?: ""
                    val port = if (uri.port != -1) uri.port else 80
                    val socket = java.net.Socket()
                    socket.use {
                        it.connect(java.net.InetSocketAddress(host, port), 250)
                    }
                    local
                } catch (e: Exception) {
                    primary
                }
                cachedActiveUrl = resolved
            }
        }

        return cached ?: primary
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
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
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

                    val currentVersion = try { com.example.BuildConfig.VERSION_NAME } catch (e: Exception) { "2.6" } ?: "2.6"
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
