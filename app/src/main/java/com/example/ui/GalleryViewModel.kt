package com.example.ui
import coil.imageLoader

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ApiAlbum
import com.example.data.ApiAlbumCache
import com.example.data.ApiDirectory
import com.example.data.ApiMedia
import com.example.data.ApiSubFolder
import com.example.data.ApiSubFolderCache
import com.example.data.PiGalleryApi
import com.example.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

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
    private val api = PiGalleryApi(application)

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
    private val _pathHistory = MutableStateFlow<List<String>>(listOf(""))
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

    // Search and directory flattening states
    val isSearchActive = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val isFlattened = MutableStateFlow(false)
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
                val path = currentPath
                if (path.isEmpty()) "global" else path
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
        val path = if (currentFolderOnly) getSortPath() else "global"
        
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
            "random" -> persons.shuffled()
            else -> persons
        }

        // Always show favorites first (stable sort)
        val localFavs = prefs.getFavoritePersons()
        return sorted.sortedByDescending { localFavs.contains(it.name) || it.isFavourite == true }
    }
    fun sortDirectory(directory: ApiDirectory): ApiDirectory {
        val currentContextPath = getSortPath()
        
        // Use current folder specific settings if available (not equal to global default unless actually global), 
        // actually prefs fallback is fine. Let's try current first, if it doesn't exist, we fall back to global.
        // Wait, SharedPreferences will just return the default ("name" or "date") if key not found.
        // So we can query the path. If it's missing, it gives default. But what if we want to fallback to "global" if path doesn't have it?
        // Let's modify logic: check if path exists in prefs. If not, use global.
        
        var fSort = prefs.getFolderSortBy(currentContextPath)
        var fDir = prefs.getFolderSortDirection(currentContextPath)
        var mSort = prefs.getMediaSortBy(currentContextPath)
        var mDir = prefs.getMediaSortDirection(currentContextPath)
        
        // Actually, our prefs.getXXX functions don't tell us if it's missing vs default. 
        // We will just read it. Wait, the user wants "Global" fallback if not set for current folder.
        // I will change the logic below to fetch properly by passing a check.
        // For simplicity, let's just use the current path if it's explicitly set. If not, use global.
        // Let's just use the prefs directly for the current path. Wait, if current Folder doesn't have it set, it returns "name", not the global setting.
        // I should read "global" first, then use it as fallback.
        
        val g_fSort = prefs.getFolderSortBy("global")
        val g_fDir = prefs.getFolderSortDirection("global")
        val g_mSort = prefs.getMediaSortBy("global")
        val g_mDir = prefs.getMediaSortDirection("global")

        // Wait, I didn't write get getString with fallback in prefs. I just wrote fallback to "name"/"date". 
        // Let me just read SharedPreferences directly here to see if the key exists, or I can just update prefs manager later.
        // For now, let's assume prefs.getFolderSortBy will be updated to take a fallback argument.
        
        fSort = prefs.getFolderSortBy(currentContextPath, g_fSort)
        fDir = prefs.getFolderSortDirection(currentContextPath, g_fDir)
        mSort = prefs.getMediaSortBy(currentContextPath, g_mSort)
        mDir = prefs.getMediaSortDirection(currentContextPath, g_mDir)
        
        val fAsc = fDir.lowercase() == "asc"
        val mAsc = mDir.lowercase() == "asc"

        val dirs = directory.directories.orEmpty()
        val sortedDirs = when (fSort.lowercase()) {
            "name" -> {
                if (fAsc) dirs.sortedBy { it.name.lowercase() }
                else dirs.sortedByDescending { it.name.lowercase() }
            }
            "date" -> {
                // Directories don't have direct date in ApiSubFolder, so we can sort them by name
                if (fAsc) dirs.sortedBy { it.name.lowercase() }
                else dirs.sortedByDescending { it.name.lowercase() }
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
                if (mAsc) media.sortedBy { it.metadata?.creationDate ?: 0L }
                else media.sortedByDescending { it.metadata?.creationDate ?: 0L }
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
                _pathHistory.value = listOf("") // Reset path history
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

    fun loadCurrentDirectory() {
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
            _galleryState.value = GalleryUiState.Loading
            try {
                var directory = if (searchQuery.value.isNotEmpty()) {
                    // Fetch search results using the advanced search query parser
                    val query = parseSearchStringToQuery(searchQuery.value)
                    val queryJson = api.serializeQuery(query)
                    api.search(server, queryJson, cookies, apiPrefix)
                } else if (isFlattened.value) {
                    // Fetch flattened directory content
                    val query = mapOf(
                        "type" to 102, // directory (not position 106!)
                        "value" to if (path.isEmpty()) "." else path,
                        "matchType" to 2 // contains/like (recursive)
                    )
                    val queryJson = api.serializeQuery(query)
                    val rawDir = api.search(server, queryJson, cookies, apiPrefix)
                    // Flatten it: remove sub-folders from display so only media items show!
                    rawDir.copy(directories = emptyList())
                } else {
                    // Normal folder fetch
                    api.getGalleryContent(server, path, cookies, apiPrefix)
                }

                // Apply sorting
                val sortedDirectory = sortDirectory(directory)
                _galleryState.value = GalleryUiState.Success(sortedDirectory)
            } catch (e: Exception) {
                _galleryState.value = GalleryUiState.Error(e.localizedMessage ?: "Failed to fetch gallery content")
            }
        }
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
                        val creationSecs = media.metadata?.creationDate ?: 0L
                        getYearFromTimestamp(creationSecs) < currentYear
                    }
                    .groupBy { media ->
                        val creationSecs = media.metadata?.creationDate ?: 0L
                        getYearFromTimestamp(creationSecs)
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

    private fun getYearFromTimestamp(timestamp: Long): Int {
        val ms = if (timestamp < 10000000000L) timestamp * 1000L else timestamp
        val cal = java.util.Calendar.getInstance()
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
    fun getThumbnailUrl(media: ApiMedia): String {
        val sanitizedBase = prefs.serverUrl.trimEnd('/')
        val apiPrefix = prefs.apiPrefix
        val relativePath = encodePath(getMediaFullPath(media))
        val suffix = prefs.thumbnailPathSuffix
        return "$sanitizedBase$apiPrefix/gallery/content/${relativePath}/$suffix"
    }

    fun getOriginalMediaUrl(media: ApiMedia): String {
        val sanitizedBase = prefs.serverUrl.trimEnd('/')
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
        prefs.clear()
        isLoggedIn.value = false
        savedServerUrl.value = ""
        savedUsername.value = ""
        savedPassword.value = ""
        _pathHistory.value = listOf("")
        _loginState.value = LoginUiState.Idle
        _activeTab.value = ActiveTab.GALLERY
        _selectedAlbum.value = null
        _albumContentState.value = null
        exitSelectMode()
    }

    private val _cacheSize = MutableStateFlow("0 B")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    fun updateCacheSize() {
        var size: Long = 0
        val context = getApplication<Application>()
        val imageCache = java.io.File(context.cacheDir, "image_cache")
        if (imageCache.exists()) {
            size += imageCache.walkBottomUp().sumOf { it.length() }
        }
        val httpCache = java.io.File(context.cacheDir, "http_cache")
        if (httpCache.exists()) {
            size += httpCache.walkBottomUp().sumOf { it.length() }
        }
        _cacheSize.value = formatSize(size)
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (kotlin.math.log10(size.toDouble()) / kotlin.math.log10(1024.0)).toInt()
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun clearCaches() {
        val context = getApplication<Application>()
        val imageCache = java.io.File(context.cacheDir, "image_cache")
        if (imageCache.exists()) {
            imageCache.deleteRecursively()
        }
        // Properly clear OkHttp cache without deleting the directory
        api.clearCache()
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
        updateCacheSize()
    }
}