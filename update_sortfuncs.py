import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

load_albums_old = """                val albums = api.getAlbums(server, cookies, apiPrefix)
                _albumsState.value = AlbumsUiState.Success(albums)"""

load_albums_new = """                val albums = api.getAlbums(server, cookies, apiPrefix)
                _albumsState.value = AlbumsUiState.Success(sortAlbums(albums))"""

load_persons_old = """                val persons = api.getPersons(server, cookies, apiPrefix)
                _personsState.value = PersonsUiState.Success(persons)"""

load_persons_new = """                val persons = api.getPersons(server, cookies, apiPrefix)
                _personsState.value = PersonsUiState.Success(sortPersons(persons))"""

update_sort_old = """        if (_activeTab.value == ActiveTab.ALBUMS) {
            val album = _selectedAlbum.value
            if (album != null) {
                val currentContent = _albumContentState.value
                if (currentContent is GalleryUiState.Success) {
                    _albumContentState.value = GalleryUiState.Success(sortDirectory(currentContent.directory))
                } else {
                    loadAlbumContent(album)
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
            }
        }"""

update_sort_new = """        if (_activeTab.value == ActiveTab.ALBUMS) {
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
        }"""

sort_funcs = """
    fun sortAlbums(albums: List<com.example.data.ApiAlbum>): List<com.example.data.ApiAlbum> {
        val currentContextPath = getSortPath()
        val g_fSort = prefs.getFolderSortBy("global")
        val g_fDir = prefs.getFolderSortDirection("global")
        
        val fSort = prefs.getFolderSortBy(currentContextPath, g_fSort)
        val fDir = prefs.getFolderSortDirection(currentContextPath, g_fDir)
        
        val fAsc = fDir == "asc"
        
        return when (fSort.lowercase()) {
            "name" -> {
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
        
        return when (fSort.lowercase()) {
            "name" -> {
                if (fAsc) persons.sortedBy { it.name.lowercase() }
                else persons.sortedByDescending { it.name.lowercase() }
            }
            "random" -> persons.shuffled()
            else -> persons
        }
    }
"""

if load_albums_old in content: content = content.replace(load_albums_old, load_albums_new)
if load_persons_old in content: content = content.replace(load_persons_old, load_persons_new)
if update_sort_old in content: content = content.replace(update_sort_old, update_sort_new)

# Insert sort_funcs before sortDirectory
content = content.replace("    fun sortDirectory", sort_funcs + "    fun sortDirectory")

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
