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
import com.example.util.TvFeatureCapabilities
import com.example.util.TvModeHelper
import com.example.util.VersionComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

sealed interface AlbumRestoreUiState {
    object Idle : AlbumRestoreUiState
    data class Restoring(val completed: Int, val total: Int) : AlbumRestoreUiState
    data class Success(val restoredCount: Int) : AlbumRestoreUiState
    data class Error(val message: String, val restoredCount: Int = 0) : AlbumRestoreUiState
}

data class SharingConfigState(
    val isConfigLoaded: Boolean = false,
    val isSupportedByServer: Boolean = false,
    val isEnabled: Boolean = false,
    val isAuthenticationRequired: Boolean = true,
    val passwordProtected: Boolean = true,
    val minRole: Int = com.example.data.UserRoles.USER
) {
    fun isAvailableForRole(userRole: Int): Boolean {
        if (!isConfigLoaded || !isSupportedByServer || !isEnabled || !isAuthenticationRequired) {
            return false
        }
        return com.example.data.UserRoles.canCreateShareLinks(
            role = userRole,
            sharingMinRole = minRole,
            sharingFeatureEnabled = true
        )
    }
}

sealed interface ShareLinkUiState {
    object Idle : ShareLinkUiState
    object Creating : ShareLinkUiState
    data class Success(val shareUrl: String, val share: com.example.data.ApiShareLink) : ShareLinkUiState
    data class Error(val message: String) : ShareLinkUiState
}

sealed interface ShareLinkToastEvent {
    data class Created(val shareUrl: String) : ShareLinkToastEvent
    data class Failed(val message: String) : ShareLinkToastEvent
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
    val albumBackupManager = com.example.data.AlbumBackupManager(application)

    private fun str(@StringRes resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    val localSearchHistory = searchHistoryManager.searchHistory


    // Active Navigation Tab
    private val _activeTab = MutableStateFlow(ActiveTab.GALLERY)
    val activeTab: StateFlow<ActiveTab> = _activeTab.asStateFlow()

    private val _previousNonSettingsTab = MutableStateFlow(ActiveTab.GALLERY)
    val previousNonSettingsTab: StateFlow<ActiveTab> = _previousNonSettingsTab.asStateFlow()

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
    private val scrollPositions = android.util.LruCache<String, SavedScrollPosition>(128)
    private val scrollKeyOwnerSeq = java.util.concurrent.atomic.AtomicLong(0L)
    private val activeScrollKeyOwners = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun claimScrollKeyOwner(key: String): Long {
        val token = scrollKeyOwnerSeq.incrementAndGet()
        activeScrollKeyOwners[key] = token
        return token
    }

    fun getScrollPosition(key: String): SavedScrollPosition =
        scrollPositions.get(key) ?: SavedScrollPosition()

    fun saveScrollPosition(key: String, index: Int, offset: Int, ownerToken: Long? = null) {
        if (ownerToken != null && activeScrollKeyOwners[key] != ownerToken) {
            return
        }
        scrollPositions.put(key, SavedScrollPosition(index.coerceAtLeast(0), offset.coerceAtLeast(0)))
    }

    private val _albumsFilterQuery = MutableStateFlow("")
    val albumsFilterQuery: StateFlow<String> = _albumsFilterQuery.asStateFlow()

    private val _isAlbumsSearchActive = MutableStateFlow(false)
    val isAlbumsSearchActive: StateFlow<Boolean> = _isAlbumsSearchActive.asStateFlow()

    fun setAlbumsFilterQuery(query: String) {
        _albumsFilterQuery.value = query
    }

    fun setAlbumsSearchActive(active: Boolean, clearQueryOnClose: Boolean = false) {
        _isAlbumsSearchActive.value = active
        if (!active && clearQueryOnClose) {
            _albumsFilterQuery.value = ""
        }
    }

    fun toggleAlbumsSearchActive() {
        val next = !_isAlbumsSearchActive.value
        _isAlbumsSearchActive.value = next
        if (!next) {
            _albumsFilterQuery.value = ""
        }
    }

    private val _personsFilterQuery = MutableStateFlow("")
    val personsFilterQuery: StateFlow<String> = _personsFilterQuery.asStateFlow()

    private val _isPersonsSearchActive = MutableStateFlow(false)
    val isPersonsSearchActive: StateFlow<Boolean> = _isPersonsSearchActive.asStateFlow()

    fun setPersonsFilterQuery(query: String) {
        _personsFilterQuery.value = query
    }

    fun setPersonsSearchActive(active: Boolean, clearQueryOnClose: Boolean = false) {
        _isPersonsSearchActive.value = active
        if (!active && clearQueryOnClose) {
            _personsFilterQuery.value = ""
        }
    }

    fun togglePersonsSearchActive() {
        val next = !_isPersonsSearchActive.value
        _isPersonsSearchActive.value = next
        if (!next) {
            _personsFilterQuery.value = ""
        }
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

    private val _recoverableAlbums = MutableStateFlow<List<com.example.data.BackedUpAlbum>>(emptyList())
    val recoverableAlbums: StateFlow<List<com.example.data.BackedUpAlbum>> = _recoverableAlbums.asStateFlow()

    private val _albumRestoreState = MutableStateFlow<AlbumRestoreUiState>(AlbumRestoreUiState.Idle)
    val albumRestoreState: StateFlow<AlbumRestoreUiState> = _albumRestoreState.asStateFlow()

    private val _sharingConfig = MutableStateFlow(buildSharingConfigState())
    val sharingConfig: StateFlow<SharingConfigState> = _sharingConfig.asStateFlow()

    private val _canCreateShareLink = MutableStateFlow(computeCanCreateShareLink())
    val canCreateShareLink: StateFlow<Boolean> = _canCreateShareLink.asStateFlow()

    private val _sharingPasswordProtected = MutableStateFlow(prefs.sharingPasswordProtected)
    val sharingPasswordProtected: StateFlow<Boolean> = _sharingPasswordProtected.asStateFlow()

    private val _shareLinkState = MutableStateFlow<ShareLinkUiState>(ShareLinkUiState.Idle)
    val shareLinkState: StateFlow<ShareLinkUiState> = _shareLinkState.asStateFlow()

    private val _shareLinkToastEvents = MutableSharedFlow<ShareLinkToastEvent>(extraBufferCapacity = 4)
    val shareLinkToastEvents: SharedFlow<ShareLinkToastEvent> = _shareLinkToastEvents.asSharedFlow()

    private fun buildSharingConfigState(): SharingConfigState {
        return SharingConfigState(
            isConfigLoaded = prefs.sharingConfigLoaded,
            isSupportedByServer = prefs.sharingSupportedByServer,
            isEnabled = prefs.sharingFeatureEnabled,
            isAuthenticationRequired = prefs.sharingAuthenticationRequired,
            passwordProtected = prefs.sharingPasswordProtected,
            minRole = prefs.sharingMinRole
        )
    }

    private fun computeCanManageAlbums(): Boolean {
        return com.example.data.UserRoles.canManageAlbums(
            role = prefs.userRole,
            albumWriteMinRole = prefs.albumWriteMinRole,
            albumFeatureEnabled = prefs.albumFeatureEnabled,
            userCanCreateAlbumsOverride = prefs.userCanCreateAlbumsOverride
        )
    }

    private fun computeCanCreateShareLink(): Boolean {
        return buildSharingConfigState().isAvailableForRole(prefs.userRole)
    }

    internal fun syncPermissionStateFlows() {
        val currentSharingConfig = buildSharingConfigState()
        val canShare = currentSharingConfig.isAvailableForRole(prefs.userRole)
        _userRole.value = prefs.userRole
        _sharingConfig.value = currentSharingConfig
        _canManageAlbums.value = computeCanManageAlbums()
        _canCreateShareLink.value = canShare
        _sharingPasswordProtected.value = currentSharingConfig.passwordProtected
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
    val appLanguage = MutableStateFlow(com.example.util.LocaleHelper.normalizeLanguageTag(prefs.appLanguage))
    val maxBrightnessEnabled = MutableStateFlow(prefs.maxBrightnessEnabled)
    val defaultRootPath = MutableStateFlow(prefs.defaultRootPath)
    val dismissGestureEnabled = MutableStateFlow(prefs.dismissGestureEnabled)
    val showMetadataGestureEnabled = MutableStateFlow(prefs.showMetadataGestureEnabled)
    private fun tvCapabilities(): TvFeatureCapabilities =
        TvModeHelper.capabilitiesFor(getApplication())

    val isBiometricLockEnabled = MutableStateFlow(prefs.biometricLockEnabled)
    val isAppLocked = MutableStateFlow(if (tvCapabilities().allowItemLocking) prefs.biometricLockEnabled else false)
    val protectedFolders = MutableStateFlow(prefs.getProtectedFolders())
    val hideProtectedItems = MutableStateFlow(prefs.hideProtectedItems)

    // Search and directory flattening states
    val isSearchActive = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val isFlattened = MutableStateFlow(false)
    val galleryViewMode = MutableStateFlow(try { GalleryViewMode.valueOf(prefs.galleryViewMode) } catch (e: Exception) { GalleryViewMode.FOLDER })
    val networkConnectionInfo = MutableStateFlow(NetworkConnectionInfo())

    private val cachedFolderDirectories = android.util.LruCache<String, ApiDirectory>(32)
    private val cachedAlbumDirectories = android.util.LruCache<String, ApiDirectory>(24)
    private val cachedPersonDirectories = android.util.LruCache<String, ApiDirectory>(24)
    private val cachedFlattenedMedia = android.util.LruCache<String, List<ApiMedia>>(4)
    private val cachedYearMedia = android.util.LruCache<String, List<ApiMedia>>(12)
    private val cachedMonthMedia = android.util.LruCache<String, List<ApiMedia>>(36)
    private val cachedDateRootFolders = android.util.LruCache<String, List<ApiSubFolder>>(4)
    private val lastBackgroundSyncTime = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val lastDateRootSyncTime = java.util.concurrent.ConcurrentHashMap<String, Long>()

    internal fun folderCacheKey(path: String): String =
        path.trim('/').replace("\\", "/").ifEmpty { "ROOT" }

    internal fun cacheFolderDirectory(path: String, directory: ApiDirectory) {
        cachedFolderDirectories.put(folderCacheKey(path), directory)
    }

    private fun cacheCurrentDisplayedFolderIfApplicable() {
        if (galleryViewMode.value == GalleryViewMode.FOLDER && !isFlattened.value && searchQuery.value.isEmpty()) {
            val currentSuccess = _galleryState.value as? GalleryUiState.Success ?: return
            val key = folderCacheKey(currentPath)
            if (cachedFolderDirectories.get(key) == null &&
                currentSuccess.contextKey == buildGalleryContextKey(path = currentPath)
            ) {
                cachedFolderDirectories.put(key, currentSuccess.directory)
            }
        }
    }

    private fun emitGallerySuccessIfChanged(sortedDirectory: ApiDirectory, requestContextKey: String) {
        val current = _galleryState.value as? GalleryUiState.Success
        if (current != null && current.contextKey == requestContextKey && current.directory == sortedDirectory) {
            return
        }
        _galleryState.value = GalleryUiState.Success(sortedDirectory, requestContextKey)
    }
    private val fetchSemaphore = Semaphore(4)
    private val reauthMutex = Mutex()
    private var backgroundSyncJob: kotlinx.coroutines.Job? = null
    private var directoryLoadJob: kotlinx.coroutines.Job? = null
    private var dateIndexJob: kotlinx.coroutines.Job? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var urlProbeJob: kotlinx.coroutines.Job? = null

    private val _availableDateYears = MutableStateFlow<List<Int>>(emptyList())
    val availableDateYears: StateFlow<List<Int>> = _availableDateYears.asStateFlow()

    private val _isDateIndexLoading = MutableStateFlow(false)
    val isDateIndexLoading: StateFlow<Boolean> = _isDateIndexLoading.asStateFlow()

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

    private val _isRediscoverRefreshing = MutableStateFlow(false)
    val isRediscoverRefreshing: StateFlow<Boolean> = _isRediscoverRefreshing.asStateFlow()

    fun setGalleryViewMode(mode: GalleryViewMode) {
        if (mode != GalleryViewMode.DATE) {
            dateIndexJob?.cancel()
            _isDateIndexLoading.value = false
        }
        galleryViewMode.value = mode
        prefs.galleryViewMode = mode.name
        _pathHistory.value = if (mode == GalleryViewMode.DATE) listOf("") else buildInitialPathStack(prefs.defaultRootPath)
        loadCurrentDirectory()
    }

    /**
     * Jumps directly to Date Root (year == null), a specific Year, or a specific Year + Month
     * in "View by Date", immediately issuing a targeted Search API DATE query.
     */
    fun jumpToDate(year: Int?, month: Int? = null) {
        if (galleryViewMode.value != GalleryViewMode.DATE) {
            galleryViewMode.value = GalleryViewMode.DATE
            prefs.galleryViewMode = GalleryViewMode.DATE.name
        }
        if (isSearchActive.value || searchQuery.value.isNotEmpty()) {
            isSearchActive.value = false
            showSearchSuggestions.value = false
            searchQuery.value = ""
        }
        _pathHistory.value = when {
            year == null || year <= 0 -> listOf("")
            month == null || month !in 1..12 -> listOf("", com.example.data.search.DateSearchQueryHelper.buildDatePath(year))
            else -> listOf(
                "",
                com.example.data.search.DateSearchQueryHelper.buildDatePath(year),
                com.example.data.search.DateSearchQueryHelper.buildDatePath(year, month)
            )
        }
        loadCurrentDirectory()
    }

    /**
     * Opens the unified inline Search bar pre-filled with PiGallery2's `date:YYYY` or `date:YYYY-MM`
     * parameter matching the current View by Date scope so users can combine date + keywords/people/places.
     */
    fun searchInCurrentDateScope() {
        val path = currentPath
        val year = com.example.data.search.DateSearchQueryHelper.parseYearFromPath(path)
        val month = com.example.data.search.DateSearchQueryHelper.parseMonthFromPath(path)
        val prefix = if (year != null) {
            "${com.example.data.search.DateSearchQueryHelper.buildSearchTextForDate(year, month)} "
        } else {
            "date:"
        }
        if (_activeTab.value != ActiveTab.GALLERY) {
            setActiveTab(ActiveTab.GALLERY)
        }
        searchQuery.value = prefix
        isSearchActive.value = true
        showSearchSuggestions.value = true
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

    fun openGlobalSearch() {
        if (_activeTab.value != ActiveTab.GALLERY) {
            setActiveTab(ActiveTab.GALLERY)
        }
        if (!isSearchActive.value) {
            isSearchActive.value = true
        }
        showSearchSuggestions.value = true
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
        syncPermissionStateFlows()

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
                syncPermissionStateFlows()
            } catch (_: Exception) {
                // Keep previously known permissions if session/config check fails transiently
            }
        }
    }

    fun setActiveTab(tab: ActiveTab) {
        val previousTab = _activeTab.value
        if (previousTab != ActiveTab.SETTINGS) {
            _previousNonSettingsTab.value = previousTab
        }
        if (tab != ActiveTab.SETTINGS) {
            _previousNonSettingsTab.value = tab
        }
        _activeTab.value = tab
        // Clear multi-select mode when switching tabs to avoid stray states
        exitSelectMode()

        // Collapse inline TopAppBar search inputs when leaving Albums or Persons
        if (previousTab != tab) {
            if (previousTab == ActiveTab.ALBUMS) {
                _isAlbumsSearchActive.value = false
            }
            if (previousTab == ActiveTab.PERSONS) {
                _isPersonsSearchActive.value = false
            }
        }

        // Tapping an already-active tab returns from detail view (or active search) to its overview list
        if (previousTab == tab) {
            when (tab) {
                ActiveTab.ALBUMS -> {
                    if (_selectedAlbum.value != null) {
                        selectAlbum(null)
                    } else if (_isAlbumsSearchActive.value || _albumsFilterQuery.value.isNotEmpty()) {
                        setAlbumsSearchActive(active = false, clearQueryOnClose = true)
                    }
                }
                ActiveTab.PERSONS -> {
                    if (_selectedPerson.value != null) {
                        clearSelectedPerson()
                    } else if (_isPersonsSearchActive.value || _personsFilterQuery.value.isNotEmpty()) {
                        setPersonsSearchActive(active = false, clearQueryOnClose = true)
                    }
                }
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
                    refreshUserPermissions(force = false)
                    if (_personsState.value !is PersonsUiState.Success) {
                        loadPersons()
                    }
                }
                ActiveTab.REDISCOVER -> {
                    if (_rediscoverState.value !is RediscoverUiState.Success) {
                        loadRediscover()
                    }
                }
                ActiveTab.SETTINGS -> {
                    refreshUserPermissions(force = false)
                }
            }
        }
    }

    fun navigateBackFromSettings() {
        val target = _previousNonSettingsTab.value.takeIf { it != ActiveTab.SETTINGS } ?: ActiveTab.GALLERY
        setActiveTab(target)
    }

    fun connectAndLogin(url: String, user: String, pass: String, allowInsecureSsl: Boolean = prefs.allowInsecureSsl) {
        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = LoginUiState.Loading
            try {
                // Ensure URL has schema and clean whitespace/trailing slash
                val formattedUrl = PiGalleryApi.ensureHttpUrl(url)
                val previousServerUrl = prefs.serverUrl
                val previousLocalServerUrl = prefs.localServerUrl

                prefs.allowInsecureSsl = allowInsecureSsl
                api.updateClientSsl(allowInsecureSsl)
                com.example.util.AppImageLoaderFactory.install(getApplication(), allowInsecureSsl)

                // Clear any previous server's permission/sharing config before authenticating to the new server
                lastPermissionsSyncMs = 0L
                prefs.userRole = -1
                prefs.albumWriteMinRole = com.example.data.UserRoles.USER
                prefs.albumFeatureEnabled = true
                prefs.userCanCreateAlbumsOverride = -1
                prefs.sharingConfigLoaded = false
                prefs.sharingSupportedByServer = false
                prefs.sharingFeatureEnabled = false
                prefs.sharingAuthenticationRequired = true
                syncPermissionStateFlows()

                val (cookies, apiPrefix) = api.login(formattedUrl, user, pass)
                
                // Save settings
                prefs.serverUrl = formattedUrl
                prefs.username = user
                prefs.password = pass
                prefs.cookies = cookies
                prefs.apiPrefix = apiPrefix
                prefs.isLoggedIn = true

                // If user reconnected via the previously configured local URL (or swapped primary/local),
                // carry over the local album backup snapshot seamlessly.
                val normalizedFormatted = com.example.data.AlbumBackupManager.normalizeServerKey(formattedUrl)
                val normalizedPrevPrimary = com.example.data.AlbumBackupManager.normalizeServerKey(previousServerUrl)
                val normalizedPrevLocal = com.example.data.AlbumBackupManager.normalizeServerKey(previousLocalServerUrl)
                val normalizedCurrLocal = com.example.data.AlbumBackupManager.normalizeServerKey(prefs.localServerUrl)
                val switchedBetweenLinkedUrls = normalizedPrevPrimary.isNotEmpty() &&
                    normalizedFormatted != normalizedPrevPrimary &&
                    (
                        (normalizedPrevLocal.isNotEmpty() &&
                            com.example.data.AlbumBackupManager.hostKey(formattedUrl) == com.example.data.AlbumBackupManager.hostKey(previousLocalServerUrl)) ||
                            (normalizedCurrLocal.isNotEmpty() &&
                                com.example.data.AlbumBackupManager.hostKey(prefs.localServerUrl) == com.example.data.AlbumBackupManager.hostKey(previousServerUrl))
                        )
                if (switchedBetweenLinkedUrls) {
                    albumBackupManager.migrateBackupsIfMissing(previousServerUrl, formattedUrl)
                }

                // Sync viewmodel fields and fetch server ClientConfig (e.g. PR #1400 user album rights & Sharing config)
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

    private fun dateScopeRoot(): String = PiGalleryApi.cleanServerPath(prefs.defaultRootPath)

    private fun yearCacheKey(year: Int, cleanRoot: String = dateScopeRoot()): String =
        "${cleanRoot.ifEmpty { "ROOT" }}:$year"

    private fun monthCacheKey(year: Int, month: Int, cleanRoot: String = dateScopeRoot()): String =
        "${cleanRoot.ifEmpty { "ROOT" }}:$year-$month"

    private fun filterExactYear(media: List<ApiMedia>, year: Int, cleanRoot: String): List<ApiMedia> {
        return deduplicateMedia(media).filter { item ->
            com.example.data.search.DateSearchQueryHelper.matchesRootPath(item, cleanRoot) &&
                getYearFromTimestamp(
                    DateUtils.getLocalTimeMs(item.metadata?.creationDate, item.metadata?.creationDateOffset)
                ) == year
        }
    }

    private fun filterExactMonth(media: List<ApiMedia>, year: Int, month: Int, cleanRoot: String): List<ApiMedia> {
        return deduplicateMedia(media).filter { item ->
            if (!com.example.data.search.DateSearchQueryHelper.matchesRootPath(item, cleanRoot)) return@filter false
            val localMs = DateUtils.getLocalTimeMs(item.metadata?.creationDate, item.metadata?.creationDateOffset)
            getYearFromTimestamp(localMs) == year && getMonthFromTimestamp(localMs) == month
        }
    }

    private suspend fun executeDateSearchQuery(
        server: String,
        query: Map<String, Any?>
    ): ApiDirectory {
        val queryJson = api.serializeQuery(query)
        return fetchSemaphore.withPermit {
            executeAuthenticated { activeCookies, activePrefix ->
                api.search(server, queryJson, activeCookies, activePrefix)
            }
        }
    }

    private fun prewarmMonthCachesForYear(year: Int, yearMedia: List<ApiMedia>, cleanRoot: String) {
        val byMonth = yearMedia.groupBy { media ->
            val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
            if (localMs > 0L) getMonthFromTimestamp(localMs) else 0
        }
        for (m in 1..12) {
            cachedMonthMedia.put(monthCacheKey(year, m, cleanRoot), byMonth[m].orEmpty())
        }
    }

    private fun registerDiscoveredYears(years: Collection<Int>) {
        val positive = years.filter { it > 0 }
        if (positive.isEmpty()) return
        val merged = (_availableDateYears.value + positive).distinct().sortedDescending()
        _availableDateYears.value = merged
    }

    /**
     * Fetches media for a single (year, month) directly via PiGallery2 Search API DATE range.
     * Avoids downloading the entire year or crawling folders when browsing a specific month.
     */
    suspend fun fetchMediaForMonth(
        year: Int,
        month: Int,
        forceRefresh: Boolean = false,
        rethrowErrors: Boolean = false
    ): List<ApiMedia> {
        val cleanRoot = dateScopeRoot()
        val key = monthCacheKey(year, month, cleanRoot)
        if (!forceRefresh) {
            cachedMonthMedia.get(key)?.let { return it }
        }

        val server = prefs.serverUrl
        if (server.isEmpty() || year <= 0 || month !in 1..12) return emptyList()

        return try {
            val query = com.example.data.search.DateSearchQueryHelper.buildMonthQuery(year, month, cleanRoot)
            val result = executeDateSearchQuery(server, query)
            val monthMedia = filterExactMonth(result.media.orEmpty(), year, month, cleanRoot)
            cachedMonthMedia.put(key, monthMedia)
            if (monthMedia.isNotEmpty()) {
                registerDiscoveredYears(listOf(year))
                try {
                    repo.saveAllMedia(monthMedia)
                } catch (_: Exception) {
                    // Safe ignore on local cache write
                }
            }
            monthMedia
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: com.example.data.UnauthorizedSessionException) {
            throw e
        } catch (e: Exception) {
            if (rethrowErrors) throw e
            emptyList()
        }
    }

    /**
     * Fetches media for an entire year via PiGallery2 Search API DATE range.
     * If the server signals resultOverflow (e.g. > maxMediaResult photos in one year),
     * automatically splits the request into 12 parallel month queries so no photos are lost.
     */
    suspend fun fetchMediaForYear(
        year: Int,
        forceRefresh: Boolean = false,
        rethrowErrors: Boolean = false
    ): List<ApiMedia> {
        val cleanRoot = dateScopeRoot()
        val key = yearCacheKey(year, cleanRoot)
        if (!forceRefresh) {
            cachedYearMedia.get(key)?.let { return it }
        }

        val server = prefs.serverUrl
        if (server.isEmpty() || year <= 0) return emptyList()

        return try {
            val query = com.example.data.search.DateSearchQueryHelper.buildYearQuery(year, cleanRoot)
            val result = executeDateSearchQuery(server, query)
            val yearMedia = if (result.resultOverflow) {
                kotlinx.coroutines.coroutineScope {
                    (1..12).map { month ->
                        async(Dispatchers.IO) {
                            fetchMediaForMonth(year, month, forceRefresh = true)
                        }
                    }.awaitAll().flatten()
                }.let { deduplicateMedia(it) }
            } else {
                val filtered = filterExactYear(result.media.orEmpty(), year, cleanRoot)
                prewarmMonthCachesForYear(year, filtered, cleanRoot)
                filtered
            }
            cachedYearMedia.put(key, yearMedia)
            if (yearMedia.isNotEmpty()) {
                registerDiscoveredYears(listOf(year))
                try {
                    repo.saveAllMedia(yearMedia)
                } catch (_: Exception) {
                    // Safe ignore on local cache write
                }
            }
            yearMedia
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: com.example.data.UnauthorizedSessionException) {
            throw e
        } catch (e: Exception) {
            if (rethrowErrors) throw e
            emptyList()
        }
    }

    private fun buildYearSubFolder(year: Int, mediaList: List<ApiMedia>): ApiSubFolder {
        val firstMedia = mediaList.firstOrNull()
        val cover = firstMedia?.let {
            ApiCoverPhoto(
                name = it.name,
                directory = ApiCoverPhotoDirectory(name = "", path = it.parentPath ?: "")
            )
        }
        return ApiSubFolder(
            id = year,
            name = if (year > 0) "$year" else str(R.string.undated),
            path = "year:$year",
            mediaCount = mediaList.size,
            cache = ApiSubFolderCache(cover = cover)
        )
    }

    private fun publishDateRootSubFolders(
        subFolders: List<ApiSubFolder>,
        cacheKey: String,
        requestContextKey: String
    ) {
        val sortedSubFolders = subFolders
            .filter { (it.mediaCount ?: 0) > 0 }
            .sortedByDescending { it.id ?: 0 }
        cachedDateRootFolders.put(cacheKey, sortedSubFolders)
        registerDiscoveredYears(sortedSubFolders.mapNotNull { it.id })

        if (searchQuery.value.isEmpty() &&
            galleryViewMode.value == GalleryViewMode.DATE &&
            !isFlattened.value &&
            currentPath.isEmpty()
        ) {
            val dir = ApiDirectory(
                id = -1,
                name = str(R.string.gallery),
                path = currentPath,
                directories = sortedSubFolders,
                media = emptyList()
            )
            emitGallerySuccessIfChanged(sortDirectory(dir), requestContextKey)
        }
    }

    /**
     * Search-first Year discovery for "View by Date" Root:
     * 1. Issues a single DATE >= 1 Search API query. If resultOverflow == false, the entire
     *    year/month index is built in 1 HTTP call (pre-warming year & month caches!).
     * 2. If resultOverflow == true (very large gallery), streams years newest-first in parallel
     *    batches of 4 with an early-exit `DATE < oldestCheckedYear` boundary probe so it stops
     *    immediately once no older photos exist—never needing a full recursive folder crawl.
     */
    private fun startDateRootSearchDiscovery(
        server: String,
        cleanRoot: String,
        requestContextKey: String,
        forceRefresh: Boolean
    ) {
        val cacheKey = cleanRoot.ifEmpty { "ROOT" }
        val lastSync = lastDateRootSyncTime[cacheKey] ?: 0L
        val now = System.currentTimeMillis()
        if (!forceRefresh && dateIndexJob?.isActive == true) return
        if (!forceRefresh && cachedDateRootFolders.get(cacheKey) != null && (now - lastSync) < 120_000L) {
            _isRefreshing.value = false
            return
        }

        dateIndexJob?.cancel()
        dateIndexJob = viewModelScope.launch(Dispatchers.IO) {
            _isDateIndexLoading.value = true
            try {
                val allQuery = com.example.data.search.DateSearchQueryHelper.buildAllDatedQuery(cleanRoot)
                val probeResult = executeDateSearchQuery(server, allQuery)
                val probeMedia = deduplicateMedia(probeResult.media.orEmpty())
                    .filter { com.example.data.search.DateSearchQueryHelper.matchesRootPath(it, cleanRoot) }
                    .filter { !isMediaProtected(it) }

                if (!probeResult.resultOverflow) {
                    val grouped = probeMedia.groupBy { media ->
                        val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                        if (localMs > 0L) getYearFromTimestamp(localMs) else 0
                    }
                    val folders = grouped.map { (year, items) ->
                        if (year > 0) {
                            cachedYearMedia.put(yearCacheKey(year, cleanRoot), items)
                            prewarmMonthCachesForYear(year, items, cleanRoot)
                        }
                        buildYearSubFolder(year, items)
                    }
                    if (probeMedia.isNotEmpty()) {
                        cachedFlattenedMedia.put(cacheKey, probeMedia)
                        try {
                            repo.saveAllMedia(probeMedia)
                        } catch (_: Exception) {
                            // Safe ignore
                        }
                    }
                    lastDateRootSyncTime[cacheKey] = System.currentTimeMillis()
                    publishDateRootSubFolders(folders, cacheKey, requestContextKey)
                    return@launch
                }

                // Overflow path: preserve any previously cached years while merging initial probe
                // so already-known older years never vanish temporarily during refresh.
                val yearFoldersMap = java.util.concurrent.ConcurrentHashMap<Int, ApiSubFolder>()
                cachedDateRootFolders.get(cacheKey)?.forEach { existing ->
                    val y = existing.id
                    if (y != null && y > 0) {
                        yearFoldersMap[y] = existing
                    }
                }
                probeMedia.groupBy { media ->
                    val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                    if (localMs > 0L) getYearFromTimestamp(localMs) else 0
                }.forEach { (year, items) ->
                    if (year > 0 && items.isNotEmpty()) {
                        yearFoldersMap[year] = buildYearSubFolder(year, items)
                    }
                }
                if (yearFoldersMap.isNotEmpty()) {
                    publishDateRootSubFolders(yearFoldersMap.values.toList(), cacheKey, requestContextKey)
                }

                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val minYear = com.example.data.search.DateSearchQueryHelper.MIN_DISCOVERY_YEAR
                var cursorYear = currentYear
                val batchSize = 4

                while (cursorYear >= minYear) {
                    val batchEnd = (cursorYear - batchSize + 1).coerceAtLeast(minYear)
                    val batchYears = (cursorYear downTo batchEnd).toList()
                    val batchResults = kotlinx.coroutines.coroutineScope {
                        batchYears.map { year ->
                            async(Dispatchers.IO) {
                                year to fetchMediaForYear(year, forceRefresh = forceRefresh)
                            }
                        }.awaitAll()
                    }

                    var updated = false
                    for ((year, items) in batchResults) {
                        val visible = items.filter { !isMediaProtected(it) }
                        if (visible.isNotEmpty()) {
                            yearFoldersMap[year] = buildYearSubFolder(year, visible)
                            updated = true
                        } else if (yearFoldersMap.remove(year) != null) {
                            updated = true
                        }
                    }
                    if (updated) {
                        publishDateRootSubFolders(yearFoldersMap.values.toList(), cacheKey, requestContextKey)
                    }

                    if (batchEnd <= minYear) break

                    // Boundary early-exit check: ask PiGallery2 if ANY photo exists before batchEnd
                    val beforeQuery = com.example.data.search.DateSearchQueryHelper.buildBeforeYearQuery(batchEnd, cleanRoot)
                    val beforeResult = executeDateSearchQuery(server, beforeQuery)
                    val strictlyOlder = deduplicateMedia(beforeResult.media.orEmpty())
                        .filter { com.example.data.search.DateSearchQueryHelper.matchesRootPath(it, cleanRoot) }
                        .filter { !isMediaProtected(it) }
                        .filter { media ->
                            val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                            val y = if (localMs > 0L) getYearFromTimestamp(localMs) else 0
                            y in 1 until batchEnd
                        }

                    if (!beforeResult.resultOverflow) {
                        // All remaining older media arrived in 1 shot (or none exist) -> we are done!
                        strictlyOlder.groupBy { media ->
                            val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                            getYearFromTimestamp(localMs)
                        }.forEach { (year, items) ->
                            if (year > 0 && items.isNotEmpty()) {
                                cachedYearMedia.put(yearCacheKey(year, cleanRoot), items)
                                prewarmMonthCachesForYear(year, items, cleanRoot)
                                yearFoldersMap[year] = buildYearSubFolder(year, items)
                            }
                        }
                        if (strictlyOlder.isNotEmpty()) {
                            try {
                                repo.saveAllMedia(strictlyOlder)
                            } catch (_: Exception) {
                                // Safe ignore
                            }
                        }
                        publishDateRootSubFolders(yearFoldersMap.values.toList(), cacheKey, requestContextKey)
                        break
                    } else {
                        // More older media exists beyond batchEnd; seed any newly seen years & continue
                        strictlyOlder.groupBy { media ->
                            val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                            getYearFromTimestamp(localMs)
                        }.forEach { (year, items) ->
                            if (year > 0 && items.isNotEmpty() && !yearFoldersMap.containsKey(year)) {
                                yearFoldersMap[year] = buildYearSubFolder(year, items)
                            }
                        }
                        cursorYear = batchEnd - 1
                    }
                }

                lastDateRootSyncTime[cacheKey] = System.currentTimeMillis()
                publishDateRootSubFolders(yearFoldersMap.values.toList(), cacheKey, requestContextKey)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.example.data.UnauthorizedSessionException) {
                handleApiError(e)
            } catch (_: Exception) {
                // Fallback to directory crawl only if Search API is unavailable on server
                if (cachedDateRootFolders.get(cacheKey).isNullOrEmpty()) {
                    triggerSilentBackgroundSync(force = forceRefresh)
                }
            } finally {
                _isDateIndexLoading.value = false
                _isRefreshing.value = false
            }
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
        _isRefreshing.value = true
        // Safety timeout so refresh indicator is guaranteed to dismiss within 12 seconds max
        viewModelScope.launch {
            kotlinx.coroutines.delay(12_000L)
            if (_isRefreshing.value) {
                _isRefreshing.value = false
            }
        }
        if (galleryViewMode.value == GalleryViewMode.DATE && searchQuery.value.isEmpty()) {
            val cleanRoot = dateScopeRoot()
            val path = currentPath
            val year = com.example.data.search.DateSearchQueryHelper.parseYearFromPath(path)
            val month = com.example.data.search.DateSearchQueryHelper.parseMonthFromPath(path)
            when {
                year != null && month != null -> {
                    cachedMonthMedia.remove(monthCacheKey(year, month, cleanRoot))
                }
                year != null -> {
                    cachedYearMedia.remove(yearCacheKey(year, cleanRoot))
                    for (m in 1..12) cachedMonthMedia.remove(monthCacheKey(year, m, cleanRoot))
                }
                else -> {
                    cachedDateRootFolders.evictAll()
                    cachedYearMedia.evictAll()
                    cachedMonthMedia.evictAll()
                    lastDateRootSyncTime.clear()
                }
            }
            loadCurrentDirectory(forceRefresh = true)
        } else if (isFlattened.value) {
            cachedYearMedia.evictAll()
            cachedMonthMedia.evictAll()
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
                        emitGallerySuccessIfChanged(
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

        val isDateOrFlattened = searchQuery.value.isEmpty() && (isFlattened.value || galleryViewMode.value == GalleryViewMode.DATE)
        val initialAlreadyShowingSameContext = (_galleryState.value as? GalleryUiState.Success)?.contextKey == requestContextKey

        // Synchronous RAM-cache restore BEFORE launching IO so returning to a parent folder
        // (via Back or Breadcrumb) restores the complete directory (including all subfolders)
        // on the very same frame — preventing folders from vanishing or scroll positions shifting.
        var restoredSynchronously = false
        if (!forceRefresh && !initialAlreadyShowingSameContext && searchQuery.value.isEmpty()) {
            if (!isFlattened.value && galleryViewMode.value == GalleryViewMode.FOLDER) {
                val cachedDir = cachedFolderDirectories.get(folderCacheKey(path))
                if (cachedDir != null) {
                    emitGallerySuccessIfChanged(sortDirectory(cachedDir), requestContextKey)
                    restoredSynchronously = true
                }
            } else if (galleryViewMode.value == GalleryViewMode.DATE) {
                val cleanRoot = dateScopeRoot()
                val parsedYear = com.example.data.search.DateSearchQueryHelper.parseYearFromPath(path)
                val parsedMonth = com.example.data.search.DateSearchQueryHelper.parseMonthFromPath(path)
                when {
                    parsedYear != null && parsedMonth != null -> {
                        val cachedMonth = cachedMonthMedia.get(monthCacheKey(parsedYear, parsedMonth, cleanRoot))
                        if (cachedMonth != null) {
                            val dir = if (isFlattened.value) {
                                ApiDirectory(
                                    id = -1,
                                    name = "${getMonthName(parsedMonth)} $parsedYear",
                                    path = path,
                                    directories = emptyList(),
                                    media = filterFlattenedMedia(path, cachedMonth)
                                )
                            } else {
                                buildDateViewDirectory(path, cachedMonth)
                            }
                            emitGallerySuccessIfChanged(sortDirectory(dir), requestContextKey)
                            restoredSynchronously = true
                        }
                    }
                    parsedYear != null -> {
                        val cachedYear = cachedYearMedia.get(yearCacheKey(parsedYear, cleanRoot))
                        if (!cachedYear.isNullOrEmpty()) {
                            val dir = if (isFlattened.value) {
                                ApiDirectory(
                                    id = -1,
                                    name = "$parsedYear",
                                    path = path,
                                    directories = emptyList(),
                                    media = filterFlattenedMedia(path, cachedYear)
                                )
                            } else {
                                buildDateViewDirectory(path, cachedYear)
                            }
                            emitGallerySuccessIfChanged(sortDirectory(dir), requestContextKey)
                            restoredSynchronously = true
                        }
                    }
                    !isFlattened.value -> {
                        val dateCacheKey = cleanRoot.ifEmpty { "ROOT" }
                        val cachedFolders = cachedDateRootFolders.get(dateCacheKey)
                        if (!cachedFolders.isNullOrEmpty()) {
                            publishDateRootSubFolders(cachedFolders, dateCacheKey, requestContextKey)
                            restoredSynchronously = true
                        }
                    }
                }
            } else if (isFlattened.value) {
                val rootPath = if (!path.startsWith("year:") && !path.startsWith("month:")) {
                    path
                } else {
                    prefs.defaultRootPath
                }
                val cleanFlatRoot = rootPath.trim('/').replace("\\", "/")
                val flatCacheKey = cleanFlatRoot.ifEmpty { "ROOT" }
                val cachedFlat = cachedFlattenedMedia.get(flatCacheKey)
                if (!cachedFlat.isNullOrEmpty()) {
                    val filteredMedia = filterFlattenedMedia(path, cachedFlat)
                    val cachedDir = ApiDirectory(
                        id = -1,
                        name = if (path.isEmpty() || path == prefs.defaultRootPath) str(R.string.flattened_gallery) else path.substringAfterLast('/'),
                        path = path,
                        directories = emptyList(),
                        media = filteredMedia
                    )
                    emitGallerySuccessIfChanged(sortDirectory(cachedDir), requestContextKey)
                    restoredSynchronously = true
                }
            }
        }

        // Transition synchronously to Loading when entering an uncached context so the previous
        // folder's LazyVerticalGrid disposes cleanly on the same frame that pathHistory changes.
        if (!restoredSynchronously && !initialAlreadyShowingSameContext && !forceRefresh) {
            _galleryState.value = GalleryUiState.Loading
        }

        directoryLoadJob?.cancel()
        directoryLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val alreadyShowingSameContext = initialAlreadyShowingSameContext || restoredSynchronously
            try {
                if (forceRefresh) {
                    _isRefreshing.value = true
                }

                if (isDateOrFlattened) {
                    val cleanRoot = dateScopeRoot()
                    val parsedYear = com.example.data.search.DateSearchQueryHelper.parseYearFromPath(path)
                    val parsedMonth = com.example.data.search.DateSearchQueryHelper.parseMonthFromPath(path)

                    // 1. Targeted MONTH search query (year:YYYY/month:M) — fastest path, only fetches 1 month!
                    if (galleryViewMode.value == GalleryViewMode.DATE && parsedYear != null && parsedMonth != null) {
                        val mKey = monthCacheKey(parsedYear, parsedMonth, cleanRoot)
                        var initialMonthMedia = if (!forceRefresh) cachedMonthMedia.get(mKey) else null
                        if (initialMonthMedia == null && !alreadyShowingSameContext) {
                            cachedYearMedia.get(yearCacheKey(parsedYear, cleanRoot))?.let { cachedYear ->
                                initialMonthMedia = filterExactMonth(cachedYear, parsedYear, parsedMonth, cleanRoot)
                            }
                        }
                        if (initialMonthMedia == null && !alreadyShowingSameContext) {
                            val (minMs, maxMs) = com.example.data.search.DateSearchQueryHelper.paddedBounds(
                                com.example.data.search.DateSearchQueryHelper.monthUtcBounds(parsedYear, parsedMonth)
                            )
                            val dbRange = repo.getMediaInDateRange(minMs, maxMs)
                            if (!dbRange.isNullOrEmpty()) {
                                initialMonthMedia = filterExactMonth(dbRange, parsedYear, parsedMonth, cleanRoot)
                            }
                        }

                        if (initialMonthMedia != null) {
                            val dir = if (isFlattened.value) {
                                ApiDirectory(
                                    id = -1,
                                    name = "${getMonthName(parsedMonth)} $parsedYear",
                                    path = path,
                                    directories = emptyList(),
                                    media = filterFlattenedMedia(path, initialMonthMedia!!)
                                )
                            } else {
                                buildDateViewDirectory(path, initialMonthMedia!!)
                            }
                            emitGallerySuccessIfChanged(sortDirectory(dir), requestContextKey)
                        } else if (!alreadyShowingSameContext) {
                            _galleryState.value = GalleryUiState.Loading
                        }

                        val needsNetwork = forceRefresh || cachedMonthMedia.get(mKey) == null
                        if (needsNetwork) {
                            val freshMonthMedia = fetchMediaForMonth(
                                year = parsedYear,
                                month = parsedMonth,
                                forceRefresh = forceRefresh,
                                rethrowErrors = initialMonthMedia == null && !alreadyShowingSameContext
                            )
                            if (buildGalleryContextKey() == requestContextKey) {
                                val dir = if (isFlattened.value) {
                                    ApiDirectory(
                                        id = -1,
                                        name = "${getMonthName(parsedMonth)} $parsedYear",
                                        path = path,
                                        directories = emptyList(),
                                        media = filterFlattenedMedia(path, freshMonthMedia)
                                    )
                                } else {
                                    buildDateViewDirectory(path, freshMonthMedia)
                                }
                                emitGallerySuccessIfChanged(sortDirectory(dir), requestContextKey)
                            }
                        }
                        _isRefreshing.value = false
                        return@launch
                    }

                    // 2. Targeted YEAR search query (year:YYYY) — 1 query (auto-splits by month on overflow)
                    if (galleryViewMode.value == GalleryViewMode.DATE && parsedYear != null) {
                        val yKey = yearCacheKey(parsedYear, cleanRoot)
                        var initialYearMedia = if (!forceRefresh) cachedYearMedia.get(yKey) else null
                        if (initialYearMedia == null && !alreadyShowingSameContext) {
                            val (minMs, maxMs) = com.example.data.search.DateSearchQueryHelper.paddedBounds(
                                com.example.data.search.DateSearchQueryHelper.yearUtcBounds(parsedYear)
                            )
                            val dbRange = repo.getMediaInDateRange(minMs, maxMs)
                            if (!dbRange.isNullOrEmpty()) {
                                initialYearMedia = filterExactYear(dbRange, parsedYear, cleanRoot)
                            }
                        }

                        if (initialYearMedia != null && initialYearMedia!!.isNotEmpty()) {
                            val dir = if (isFlattened.value) {
                                ApiDirectory(
                                    id = -1,
                                    name = "$parsedYear",
                                    path = path,
                                    directories = emptyList(),
                                    media = filterFlattenedMedia(path, initialYearMedia!!)
                                )
                            } else {
                                buildDateViewDirectory(path, initialYearMedia!!)
                            }
                            emitGallerySuccessIfChanged(sortDirectory(dir), requestContextKey)
                        } else if (!alreadyShowingSameContext) {
                            _galleryState.value = GalleryUiState.Loading
                        }

                        val needsNetwork = forceRefresh || cachedYearMedia.get(yKey) == null
                        if (needsNetwork) {
                            val freshYearMedia = fetchMediaForYear(
                                year = parsedYear,
                                forceRefresh = forceRefresh,
                                rethrowErrors = initialYearMedia.isNullOrEmpty() && !alreadyShowingSameContext
                            )
                            if (buildGalleryContextKey() == requestContextKey) {
                                val dir = if (isFlattened.value) {
                                    ApiDirectory(
                                        id = -1,
                                        name = "$parsedYear",
                                        path = path,
                                        directories = emptyList(),
                                        media = filterFlattenedMedia(path, freshYearMedia)
                                    )
                                } else {
                                    buildDateViewDirectory(path, freshYearMedia)
                                }
                                emitGallerySuccessIfChanged(sortDirectory(dir), requestContextKey)
                            }
                        }
                        _isRefreshing.value = false
                        return@launch
                    }

                    // 3. Date Root (Years overview) via fast Search API discovery
                    if (galleryViewMode.value == GalleryViewMode.DATE && !isFlattened.value) {
                        val cacheKey = cleanRoot.ifEmpty { "ROOT" }
                        val cachedFolders = if (!forceRefresh) cachedDateRootFolders.get(cacheKey) else null
                        if (!cachedFolders.isNullOrEmpty()) {
                            publishDateRootSubFolders(cachedFolders, cacheKey, requestContextKey)
                        } else if (!alreadyShowingSameContext) {
                            // Instant warm start from RAM/Room if previously populated
                            var warmMedia = cachedFlattenedMedia.get(cacheKey)
                            if (warmMedia.isNullOrEmpty()) {
                                val dbMedia = repo.getAllMedia()
                                if (!dbMedia.isNullOrEmpty()) {
                                    val filteredDb = dbMedia.filter {
                                        com.example.data.search.DateSearchQueryHelper.matchesRootPath(it, cleanRoot)
                                    }
                                    if (filteredDb.isNotEmpty()) {
                                        warmMedia = deduplicateMedia(filteredDb)
                                        cachedFlattenedMedia.put(cacheKey, warmMedia)
                                    }
                                }
                            }
                            if (!warmMedia.isNullOrEmpty()) {
                                val warmDir = buildDateViewDirectory(path, warmMedia)
                                warmDir.directories?.let { dirs ->
                                    cachedDateRootFolders.put(cacheKey, dirs)
                                    registerDiscoveredYears(dirs.mapNotNull { it.id })
                                }
                                emitGallerySuccessIfChanged(sortDirectory(warmDir), requestContextKey)
                            } else {
                                _galleryState.value = GalleryUiState.Loading
                            }
                        }

                        startDateRootSearchDiscovery(
                            server = server,
                            cleanRoot = cleanRoot,
                            requestContextKey = requestContextKey,
                            forceRefresh = forceRefresh
                        )
                        return@launch
                    }

                    // 4. Flattened folder view (non-Date-Root)
                    val rootPath = if (isFlattened.value && !currentPath.startsWith("year:") && !currentPath.startsWith("month:")) {
                        path
                    } else {
                        prefs.defaultRootPath
                    }
                    val cleanFlatRoot = rootPath.trim('/').replace("\\", "/")
                    val cacheKey = cleanFlatRoot.ifEmpty { "ROOT" }

                    var mediaList = cachedFlattenedMedia.get(cacheKey)
                    if (mediaList == null || mediaList.isEmpty()) {
                        val dbMedia = repo.getAllMedia()
                        if (dbMedia != null && dbMedia.isNotEmpty()) {
                            val filteredDb = if (cleanFlatRoot.isEmpty()) {
                                dbMedia
                            } else {
                                dbMedia.filter {
                                    val p = (it.parentPath ?: "").trim('/').replace("\\", "/")
                                    p == cleanFlatRoot || p.startsWith("$cleanFlatRoot/")
                                }
                            }
                            if (filteredDb.isNotEmpty()) {
                                mediaList = deduplicateMedia(filteredDb)
                                cachedFlattenedMedia.put(cacheKey, mediaList)
                            }
                        }
                    }

                    if (mediaList != null && mediaList.isNotEmpty()) {
                        val filteredMedia = filterFlattenedMedia(currentPath, mediaList)
                        val cachedDirectory = ApiDirectory(
                            id = -1,
                            name = if (currentPath.isEmpty() || currentPath == prefs.defaultRootPath) str(R.string.flattened_gallery) else currentPath.substringAfterLast('/'),
                            path = currentPath,
                            directories = emptyList(),
                            media = filteredMedia
                        )
                        emitGallerySuccessIfChanged(sortDirectory(cachedDirectory), requestContextKey)
                    } else if (!alreadyShowingSameContext) {
                        _galleryState.value = GalleryUiState.Loading
                    }

                    triggerSilentBackgroundSync(force = forceRefresh)
                    return@launch
                }

                // 2. Folder view & search:
                // Never emit subfolder-less Room DB media ahead of the network call — doing so hides
                // top-row subfolders temporarily and causes LazyVerticalGrid to anchor on media items
                // and jump to the wrong scroll position once subfolders arrive.
                // Visited folders are restored synchronously from cachedFolderDirectories above;
                // uncached folders wait cleanly for the complete ApiDirectory (with Room as offline fallback).
                if (!alreadyShowingSameContext) {
                    _galleryState.value = GalleryUiState.Loading
                }

                if (searchQuery.value.isNotEmpty()) {
                    val query = parseSearchStringToQuery(searchQuery.value)
                    val queryJson = api.serializeQuery(query)
                    val searchResult = executeAuthenticated { activeCookies, activePrefix ->
                        api.search(server, queryJson, activeCookies, activePrefix)
                    }
                    if (buildGalleryContextKey() == requestContextKey) {
                        val filteredMedia = searchResult.media.orEmpty().filter { !isMediaProtected(it) }
                        val sortedDirectory = sortDirectory(searchResult.copy(media = filteredMedia))
                        emitGallerySuccessIfChanged(sortedDirectory, requestContextKey)
                    }
                } else {
                    // Normal folder view
                    val freshDirectory = executeAuthenticated { activeCookies, activePrefix ->
                        api.getGalleryContent(server, path, activeCookies, activePrefix)
                    }
                    cachedFolderDirectories.put(folderCacheKey(path), freshDirectory)
                    if (freshDirectory.media != null && searchQuery.value.isEmpty() && !isFlattened.value && galleryViewMode.value == GalleryViewMode.FOLDER) {
                        repo.saveDirectory(path, freshDirectory.media)
                    }
                    if (buildGalleryContextKey() == requestContextKey) {
                        val sortedDirectory = sortDirectory(freshDirectory)
                        emitGallerySuccessIfChanged(sortedDirectory, requestContextKey)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline fallback for uncached folder views: show Room-cached media if available
                val currentMatches = (_galleryState.value as? GalleryUiState.Success)?.contextKey == requestContextKey
                if (!currentMatches && searchQuery.value.isEmpty() && !isFlattened.value && galleryViewMode.value == GalleryViewMode.FOLDER) {
                    val offlineMedia = repo.getDirectory(path)
                    if (!offlineMedia.isNullOrEmpty() && buildGalleryContextKey() == requestContextKey) {
                        val offlineDir = ApiDirectory(
                            id = -1,
                            name = path.substringAfterLast('/'),
                            path = path,
                            directories = emptyList(),
                            media = offlineMedia
                        )
                        emitGallerySuccessIfChanged(sortDirectory(offlineDir), requestContextKey)
                        return@launch
                    }
                }
                handleApiError(e) { msg ->
                    if (!currentMatches && _galleryState.value !is GalleryUiState.Success) {
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
        cacheCurrentDisplayedFolderIfApplicable()
        val clean = folderPath.replace('\\', '/').trim().trim('/').let {
            if (it == "." || it == "./") "" else it.removePrefix("./").trim('/')
        }
        val currentStack = _pathHistory.value.toMutableList()
        currentStack.add(clean)
        _pathHistory.value = currentStack
        loadCurrentDirectory()
    }

    fun navigateToBreadcrumb(index: Int) {
        cacheCurrentDisplayedFolderIfApplicable()
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

        // If on Settings screen, return to previous tab
        if (_activeTab.value == ActiveTab.SETTINGS) {
            navigateBackFromSettings()
            return true
        }

        // If an album detail view is open in Albums Tab, go back to albums list
        if (_activeTab.value == ActiveTab.ALBUMS && _selectedAlbum.value != null) {
            selectAlbum(null)
            return true
        }
        // If Albums filter/search is active, clear and close it first
        if (_activeTab.value == ActiveTab.ALBUMS && (_isAlbumsSearchActive.value || _albumsFilterQuery.value.isNotEmpty())) {
            setAlbumsSearchActive(active = false, clearQueryOnClose = true)
            return true
        }
        // If a person detail view is open in Persons Tab, go back to persons list
        if (_activeTab.value == ActiveTab.PERSONS && _selectedPerson.value != null) {
            clearSelectedPerson()
            return true
        }
        // If Persons filter/search is active, clear and close it first
        if (_activeTab.value == ActiveTab.PERSONS && (_isPersonsSearchActive.value || _personsFilterQuery.value.isNotEmpty())) {
            setPersonsSearchActive(active = false, clearQueryOnClose = true)
            return true
        }

        val currentStack = _pathHistory.value.toMutableList()
        if (currentStack.size > 1) {
            cacheCurrentDisplayedFolderIfApplicable()
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
        if (!tvCapabilities().allowFavoriteMarking) return
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
        _isPersonsSearchActive.value = false
        _selectedPerson.value = person
        loadPersonContent(person)
    }

    fun clearSelectedPerson() {
        _selectedPerson.value = null
        _personContentState.value = null
    }

    fun buildPersonSearchQuery(person: com.example.data.ApiPerson): Map<String, Any?> {
        return if (prefs.peopleFallbackToKeywords) {
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
    }

    private fun loadPersonContent(person: com.example.data.ApiPerson) {
        val server = prefs.serverUrl ?: return
        val cacheKey = person.name
        val cached = cachedPersonDirectories.get(cacheKey)
        if (cached != null) {
            _personContentState.value = GalleryUiState.Success(sortDirectory(cached))
        } else {
            _personContentState.value = GalleryUiState.Loading
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val searchQuery = buildPersonSearchQuery(person)
                val searchQueryJson = api.serializeQuery(searchQuery)
                val dummyDir = executeAuthenticated { activeCookies, activePrefix ->
                    api.search(server, searchQueryJson, activeCookies, activePrefix)
                }
                val filteredMedia = dummyDir.media.orEmpty().filter { !isMediaFromProtectedFolder(it) }
                val cleanDir = dummyDir.copy(media = filteredMedia)
                cachedPersonDirectories.put(cacheKey, cleanDir)
                if (_selectedPerson.value?.name == person.name) {
                    val sortedDirectory = sortDirectory(cleanDir)
                    val current = _personContentState.value as? GalleryUiState.Success
                    if (current == null || current.directory != sortedDirectory) {
                        _personContentState.value = GalleryUiState.Success(sortedDirectory)
                    }
                }
            } catch (e: Exception) {
                if (cached == null) {
                    handleApiError(e) { msg ->
                        _personContentState.value = GalleryUiState.Error(msg.ifEmpty { str(R.string.failed_fetch_person_content) })
                    }
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
                val searchQuery = buildPersonSearchQuery(person)
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

    internal fun reconcileAlbumsWithBackup(server: String, fetchedAlbums: List<ApiAlbum>) {
        if (server.isBlank()) return
        val unlockedAlbums = fetchedAlbums.filter {
            !it.locked && !it.searchQuery.isNullOrEmpty() && it.name.isNotBlank()
        }
        if (unlockedAlbums.isNotEmpty()) {
            albumBackupManager.replaceUnlockedSnapshot(server, unlockedAlbums)
            _recoverableAlbums.value = emptyList()
        } else {
            _recoverableAlbums.value = albumBackupManager.getBackups(server)
        }
    }

    private fun isForbiddenPermissionMessage(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("403") ||
            lower.contains("not authorized") ||
            lower.contains("not enough rights") ||
            lower.contains("forbidden") ||
            lower.contains("admin role")
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
                reconcileAlbumsWithBackup(server, albums)
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
        if (!tvCapabilities().allowAlbumManagement) return
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
        if (server.isEmpty()) {
            _saveAlbumState.value = SaveAlbumUiState.Error(str(R.string.no_server_configured))
            return
        }

        _saveAlbumState.value = SaveAlbumUiState.Saving
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val createdAlbum = executeAuthenticated { activeCookies, activePrefix ->
                    api.createSavedSearchAlbum(
                        serverUrl = server,
                        name = trimmedName,
                        searchQuery = parsedQuery,
                        cookies = activeCookies,
                        apiPrefix = activePrefix
                    )
                }
                albumBackupManager.upsertAlbum(server, trimmedName, parsedQuery, createdAlbum?.id)
                _saveAlbumState.value = SaveAlbumUiState.Success(trimmedName)
                loadAlbums()
            } catch (e: Exception) {
                handleApiError(e) { msg ->
                    if (isForbiddenPermissionMessage(msg) && prefs.userRole < com.example.data.UserRoles.ADMIN) {
                        prefs.albumWriteMinRole = com.example.data.UserRoles.ADMIN
                        syncPermissionStateFlows()
                    }
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
        if (!tvCapabilities().allowAlbumManagement) return
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
                // Remove from local backup BEFORE reloading so deleting even the last album
                // inside the app never triggers a false restore prompt.
                albumBackupManager.removeAlbum(server, album)
                _recoverableAlbums.value = albumBackupManager.getBackups(server)

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
                    if (isForbiddenPermissionMessage(msg) && prefs.userRole < com.example.data.UserRoles.ADMIN) {
                        prefs.albumWriteMinRole = com.example.data.UserRoles.ADMIN
                        syncPermissionStateFlows()
                    }
                    _deleteAlbumState.value = DeleteAlbumUiState.Error(
                        msg.ifEmpty { str(R.string.failed_delete_album) }
                    )
                    refreshUserPermissions(force = true)
                }
            }
        }
    }

    fun resetAlbumRestoreState() {
        _albumRestoreState.value = AlbumRestoreUiState.Idle
    }

    fun dismissAlbumBackup() {
        val server = prefs.serverUrl
        if (server.isNotEmpty()) {
            albumBackupManager.clearBackups(server)
        }
        _recoverableAlbums.value = emptyList()
        _albumRestoreState.value = AlbumRestoreUiState.Idle
    }

    fun restoreBackedUpAlbums(selectedAlbums: List<com.example.data.BackedUpAlbum>) {
        if (!tvCapabilities().allowAlbumManagement) return
        if (selectedAlbums.isEmpty()) return
        if (_albumRestoreState.value is AlbumRestoreUiState.Restoring) return
        if (!_canManageAlbums.value) {
            _albumRestoreState.value = AlbumRestoreUiState.Error(str(R.string.not_authorized_save_album))
            return
        }
        val server = prefs.serverUrl
        if (server.isEmpty()) {
            _albumRestoreState.value = AlbumRestoreUiState.Error(str(R.string.no_server_configured))
            return
        }

        _albumRestoreState.value = AlbumRestoreUiState.Restoring(0, selectedAlbums.size)
        viewModelScope.launch(Dispatchers.IO) {
            var restoredCount = 0
            try {
                val existingNames = try {
                    executeAuthenticated { activeCookies, activePrefix ->
                        api.getAlbums(server, activeCookies, activePrefix)
                    }.filter { !it.locked }.map { it.name.trim().lowercase() }.toMutableSet()
                } catch (_: Exception) {
                    mutableSetOf()
                }

                for (backup in selectedAlbums) {
                    val normalizedName = backup.name.trim().lowercase()
                    if (!existingNames.contains(normalizedName)) {
                        executeAuthenticated { activeCookies, activePrefix ->
                            api.createSavedSearchAlbum(
                                serverUrl = server,
                                name = backup.name,
                                searchQuery = backup.searchQuery,
                                cookies = activeCookies,
                                apiPrefix = activePrefix
                            )
                        }
                        existingNames.add(normalizedName)
                    }
                    restoredCount++
                    _albumRestoreState.value = AlbumRestoreUiState.Restoring(restoredCount, selectedAlbums.size)
                }

                val freshAlbums = executeAuthenticated { activeCookies, activePrefix ->
                    api.getAlbums(server, activeCookies, activePrefix)
                }
                _albumsState.value = AlbumsUiState.Success(sortAlbums(freshAlbums))
                reconcileAlbumsWithBackup(server, freshAlbums)
                _albumRestoreState.value = AlbumRestoreUiState.Success(restoredCount)
            } catch (e: Exception) {
                handleApiError(e) { msg ->
                    if (isForbiddenPermissionMessage(msg) && prefs.userRole < com.example.data.UserRoles.ADMIN) {
                        prefs.albumWriteMinRole = com.example.data.UserRoles.ADMIN
                        syncPermissionStateFlows()
                    }
                    _albumRestoreState.value = AlbumRestoreUiState.Error(
                        message = msg.ifEmpty { str(R.string.album_restore_failed) },
                        restoredCount = restoredCount
                    )
                    refreshUserPermissions(force = true)
                }
            }
        }
    }

    fun buildPublicShareUrl(sharingKey: String): String {
        val rawBase = prefs.serverUrl.ifBlank { getActiveServerUrl() }
        val base = PiGalleryApi.ensureHttpUrl(rawBase).trimEnd('/')
        val encodedKey = java.net.URLEncoder.encode(sharingKey, "UTF-8").replace("+", "%20")
        return "$base/share/$encodedKey"
    }

    fun resolveShareUrl(share: com.example.data.ApiShareLink): String {
        val serverProvided = share.shareUrl?.trim().orEmpty()
        if (serverProvided.startsWith("http://", ignoreCase = true) ||
            serverProvided.startsWith("https://", ignoreCase = true)
        ) {
            return serverProvided
        }
        if (serverProvided.startsWith("/")) {
            val rawBase = prefs.serverUrl.ifBlank { getActiveServerUrl() }
            val base = PiGalleryApi.ensureHttpUrl(rawBase).trimEnd('/')
            return "$base$serverProvided"
        }
        return buildPublicShareUrl(share.sharingKey)
    }

    fun resetShareLinkState() {
        _shareLinkState.value = ShareLinkUiState.Idle
    }

    internal fun setShareLinkStateForTesting(state: ShareLinkUiState) {
        _shareLinkState.value = state
    }

    fun openShareDialog(@Suppress("UNUSED_PARAMETER") target: com.example.data.ShareTarget) {
        if (!tvCapabilities().allowShareLinks) return
        if (_shareLinkState.value !is ShareLinkUiState.Creating) {
            _shareLinkState.value = ShareLinkUiState.Idle
        }
        refreshUserPermissions(force = false)
    }

    fun createShareLink(
        target: com.example.data.ShareTarget,
        includeSubfolders: Boolean,
        validity: com.example.data.ShareValidity,
        password: String?,
        preventDownload: Boolean = false
    ) {
        if (!tvCapabilities().allowShareLinks) return
        if (_shareLinkState.value is ShareLinkUiState.Creating) {
            return
        }
        if (!_canCreateShareLink.value) {
            val message = str(R.string.not_authorized_share)
            _shareLinkState.value = ShareLinkUiState.Error(message)
            _shareLinkToastEvents.tryEmit(ShareLinkToastEvent.Failed(message))
            return
        }
        val server = getActiveServerUrl().ifBlank { PiGalleryApi.ensureHttpUrl(prefs.serverUrl) }
        if (server.isEmpty()) {
            val message = str(R.string.no_server_configured)
            _shareLinkState.value = ShareLinkUiState.Error(message)
            _shareLinkToastEvents.tryEmit(ShareLinkToastEvent.Failed(message))
            return
        }
        if (target is com.example.data.ShareTarget.Search && target.searchQuery.isEmpty()) {
            val message = str(R.string.search_query_empty)
            _shareLinkState.value = ShareLinkUiState.Error(message)
            _shareLinkToastEvents.tryEmit(ShareLinkToastEvent.Failed(message))
            return
        }

        val effectivePassword = if (_sharingPasswordProtected.value && !password.isNullOrBlank()) {
            password.trim()
        } else {
            null
        }

        _shareLinkState.value = ShareLinkUiState.Creating
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val created = executeAuthenticated { activeCookies, activePrefix ->
                    when (target) {
                        is com.example.data.ShareTarget.Directory -> {
                            api.createShareLink(
                                serverUrl = server,
                                path = target.path,
                                includeSubfolders = includeSubfolders,
                                validity = validity,
                                password = effectivePassword,
                                cookies = activeCookies,
                                apiPrefix = activePrefix,
                                preventDownload = preventDownload
                            )
                        }
                        is com.example.data.ShareTarget.Search -> {
                            api.createSearchShareLink(
                                serverUrl = server,
                                searchQuery = target.searchQuery,
                                validity = validity,
                                password = effectivePassword,
                                cookies = activeCookies,
                                apiPrefix = activePrefix,
                                preventDownload = preventDownload
                            )
                        }
                    }
                }
                val shareUrl = resolveShareUrl(created)
                _shareLinkState.value = ShareLinkUiState.Success(shareUrl, created)
                viewModelScope.launch(Dispatchers.Main) {
                    _shareLinkToastEvents.emit(ShareLinkToastEvent.Created(shareUrl))
                }
            } catch (e: Exception) {
                handleApiError(e) { msg ->
                    val errorMsg = msg.ifEmpty { str(R.string.failed_create_share) }
                    _shareLinkState.value = ShareLinkUiState.Error(errorMsg)
                    viewModelScope.launch(Dispatchers.Main) {
                        _shareLinkToastEvents.emit(ShareLinkToastEvent.Failed(errorMsg))
                    }
                    refreshUserPermissions(force = true)
                }
            }
        }
    }

    fun selectAlbum(album: ApiAlbum?) {
        if (album != null) {
            _isAlbumsSearchActive.value = false
        }
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
        val cacheKey = "${album.id}_${album.name}"
        val cached = cachedAlbumDirectories.get(cacheKey)
        if (cached != null) {
            _albumContentState.value = GalleryUiState.Success(sortDirectory(cached))
        } else {
            _albumContentState.value = GalleryUiState.Loading
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val queryJson = api.serializeQuery(query)
                val directory = executeAuthenticated { activeCookies, activePrefix ->
                    api.search(server, queryJson, activeCookies, activePrefix)
                }
                val filteredMedia = directory.media.orEmpty().filter { !isMediaFromProtectedFolder(it) }
                val cleanDir = directory.copy(media = filteredMedia)
                cachedAlbumDirectories.put(cacheKey, cleanDir)
                if (_selectedAlbum.value?.id == album.id && _selectedAlbum.value?.name == album.name) {
                    val sortedDirectory = sortDirectory(cleanDir)
                    val current = _albumContentState.value as? GalleryUiState.Success
                    if (current == null || current.directory != sortedDirectory) {
                        _albumContentState.value = GalleryUiState.Success(sortedDirectory)
                    }
                }
            } catch (e: Exception) {
                if (cached == null) {
                    handleApiError(e) { msg ->
                        _albumContentState.value = GalleryUiState.Error(msg.ifEmpty { str(R.string.failed_load_album_content) })
                    }
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
                    reconcileAlbumsWithBackup(server, albums)
                }
            } catch (e: Exception) {
                handleApiError(e) {}
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
    private suspend fun fetchRediscoverGrouped(server: String, days: Int): Map<Int, List<ApiMedia>> {
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
        return uniqueMedia
            .filter { it.metadata?.creationDate != null }
            .filter { media ->
                val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                getYearFromTimestamp(localMs) < currentYear
            }
            .groupBy { media ->
                val localMs = DateUtils.getLocalTimeMs(media.metadata?.creationDate, media.metadata?.creationDateOffset)
                getYearFromTimestamp(localMs)
            }.toSortedMap(compareByDescending { it })
    }

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
                val grouped = fetchRediscoverGrouped(server, days)
                _rediscoverState.value = RediscoverUiState.Success(grouped)
            } catch (e: Exception) {
                handleApiError(e) { msg ->
                    _rediscoverState.value = RediscoverUiState.Error(msg.ifEmpty { str(R.string.failed_load_rediscover) })
                }
            }
        }
    }

    fun refreshRediscover() {
        val server = prefs.serverUrl
        val days = rediscoverDays.value
        if (server.isEmpty()) {
            _isRediscoverRefreshing.value = false
            return
        }
        _isRediscoverRefreshing.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(12_000L)
            if (_isRediscoverRefreshing.value) {
                _isRediscoverRefreshing.value = false
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val grouped = fetchRediscoverGrouped(server, days)
                _rediscoverState.value = RediscoverUiState.Success(grouped)
            } catch (e: Exception) {
                handleApiError(e)
            } finally {
                _isRediscoverRefreshing.value = false
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

    fun setAppLanguage(value: String) {
        val normalized = com.example.util.LocaleHelper.normalizeLanguageTag(value)
        prefs.appLanguage = normalized
        appLanguage.value = normalized
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
        if (!tvCapabilities().allowItemLocking) {
            isAppLocked.value = false
            return
        }
        prefs.biometricLockEnabled = enabled
        isBiometricLockEnabled.value = enabled
        if (!enabled) {
            isAppLocked.value = false
        }
    }

    fun lockApp() {
        if (!tvCapabilities().allowItemLocking) {
            isAppLocked.value = false
            return
        }
        if (isBiometricLockEnabled.value) {
            isAppLocked.value = true
        }
    }

    fun unlockApp() {
        isAppLocked.value = false
    }

    fun toggleProtectedFolder(path: String) {
        if (!tvCapabilities().allowItemLocking) return
        prefs.toggleProtectedFolder(path)
        protectedFolders.value = prefs.getProtectedFolders()
        loadRediscover()
        _selectedAlbum.value?.let { loadAlbumContent(it) }
        _selectedPerson.value?.let { loadPersonContent(it) }
    }

    fun setHideProtectedItems(hide: Boolean) {
        if (!tvCapabilities().allowItemLocking) return
        prefs.hideProtectedItems = hide
        hideProtectedItems.value = hide
    }

    fun isFolderDirectlyProtected(path: String): Boolean {
        if (!tvCapabilities().allowItemLocking) return false
        return prefs.isFolderDirectlyProtected(path)
    }

    fun isFolderProtected(path: String): Boolean {
        if (!tvCapabilities().allowItemLocking) return false
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
        if (!tvCapabilities().allowItemLocking) return false
        val parent = media.parentPath.orEmpty().replace('\\', '/').trim().trim('/').let {
            if (it == "." || it == "./") "" else it.removePrefix("./").trim('/')
        }
        return parent.isNotEmpty() && isFolderProtected(parent)
    }

    fun isMediaProtected(media: ApiMedia): Boolean {
        if (!tvCapabilities().allowItemLocking) return false
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
        cachedDateRootFolders.evictAll()
        cachedYearMedia.evictAll()
        cachedMonthMedia.evictAll()
        lastDateRootSyncTime.clear()
        _availableDateYears.value = emptyList()
        _pathHistory.value = if (galleryViewMode.value == GalleryViewMode.DATE) listOf("") else buildInitialPathStack(value)
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
        if (!tvCapabilities().allowMultiSelect) return
        _isSelectMode.value = true
        _selectedMediaForShare.value = emptySet()
    }

    fun exitSelectMode() {
        _isSelectMode.value = false
        _selectedMediaForShare.value = emptySet()
    }

    fun toggleSelectMedia(media: ApiMedia) {
        if (!tvCapabilities().allowMultiSelect) return
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

    fun toggleSelectAllMedia(mediaList: List<ApiMedia>) {
        if (!tvCapabilities().allowMultiSelect) return
        if (mediaList.isEmpty()) return
        if (!_isSelectMode.value) {
            enterSelectMode()
        }
        val allSet = mediaList.toSet()
        if (_selectedMediaForShare.value.size == allSet.size && _selectedMediaForShare.value.containsAll(allSet)) {
            _selectedMediaForShare.value = emptySet()
        } else {
            _selectedMediaForShare.value = allSet
        }
    }

    private val _shareProgress = MutableStateFlow(-1f)
    val shareProgress: StateFlow<Float> = _shareProgress.asStateFlow()

    private var shareJob: kotlinx.coroutines.Job? = null

    fun shareSingleMedia(context: Context, media: ApiMedia) {
        if (!tvCapabilities().allowSharing) return
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
        if (!tvCapabilities().allowDownloads) return
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
        if (!tvCapabilities().allowSharing) return
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
                    syncPermissionStateFlows()
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
        dateIndexJob?.cancel()
        _isDateIndexLoading.value = false
        cachedFolderDirectories.evictAll()
        cachedAlbumDirectories.evictAll()
        cachedPersonDirectories.evictAll()
        cachedFlattenedMedia.evictAll()
        cachedYearMedia.evictAll()
        cachedMonthMedia.evictAll()
        cachedDateRootFolders.evictAll()
        lastDateRootSyncTime.clear()
        _availableDateYears.value = emptyList()
        scrollPositions.evictAll()
        activeScrollKeyOwners.clear()
        prefs.clearSession()
        isLoggedIn.value = false
        savedServerUrl.value = prefs.serverUrl
        savedUsername.value = prefs.username
        savedPassword.value = ""
        lastPermissionsSyncMs = 0L
        _userRole.value = -1
        _canManageAlbums.value = false
        _sharingConfig.value = SharingConfigState()
        _canCreateShareLink.value = false
        _sharingPasswordProtected.value = true
        _saveAlbumState.value = SaveAlbumUiState.Idle
        _deleteAlbumState.value = DeleteAlbumUiState.Idle
        _recoverableAlbums.value = emptyList()
        _albumRestoreState.value = AlbumRestoreUiState.Idle
        _galleryState.value = GalleryUiState.Loading
        _albumsState.value = AlbumsUiState.Loading
        _personsState.value = PersonsUiState.Loading
        _rediscoverState.value = RediscoverUiState.Loading
        _shareLinkState.value = ShareLinkUiState.Idle
        _pathHistory.value = buildInitialPathStack(prefs.defaultRootPath)
        _loginState.value = LoginUiState.Idle
        _activeTab.value = ActiveTab.GALLERY
        _previousNonSettingsTab.value = ActiveTab.GALLERY
        _selectedAlbum.value = null
        _albumContentState.value = null
        _selectedPerson.value = null
        _personContentState.value = null
        _selectedMedia.value = null
        _albumsFilterQuery.value = ""
        _isAlbumsSearchActive.value = false
        _personsFilterQuery.value = ""
        _isPersonsSearchActive.value = false
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

            dateIndexJob?.cancel()
            _isDateIndexLoading.value = false
            cachedFolderDirectories.evictAll()
            cachedFlattenedMedia.evictAll()
            cachedYearMedia.evictAll()
            cachedMonthMedia.evictAll()
            cachedDateRootFolders.evictAll()
            lastBackgroundSyncTime.clear()
            lastDateRootSyncTime.clear()
            _availableDateYears.value = emptyList()

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
                    val currentVersion = com.example.BuildConfig.VERSION_NAME.ifBlank { "2.7" }

                    prefs.lastUpdateCheck = now
                    _updateState.value = evaluateReleaseJson(bodyString, currentVersion)
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.localizedMessage ?: str(R.string.network_error_updates))
            }
        }
    }

    internal fun evaluateReleaseJson(bodyString: String, currentVersion: String): UpdateState {
        val json = org.json.JSONObject(bodyString)
        val tagName = json.optString("tag_name", json.optString("tagName", "")).trim()
        val isDraft = json.optBoolean("draft", false)
        val isPrerelease = json.optBoolean("prerelease", false)
        val htmlUrl = json.optString("html_url", "https://github.com/dasmaetthes/pigallery2_android/releases")
        val releaseNotes = json.optString("body").ifBlank { str(R.string.no_release_notes) }

        return if (!isDraft && !isPrerelease && VersionComparator.isRemoteNewer(tagName, currentVersion)) {
            UpdateState.NewRelease(tagName, htmlUrl, releaseNotes)
        } else {
            UpdateState.UpToDate(currentVersion)
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
