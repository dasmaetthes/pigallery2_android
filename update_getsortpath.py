import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

get_sort_path_old = """    fun getSortPath(): String {
        return if (_activeTab.value == ActiveTab.ALBUMS) {
            val album = _selectedAlbum.value
            if (album != null) "album_${album.name}" else "global"
        } else {
            val path = currentPath
            if (path.isEmpty()) "global" else path
        }
    }"""

get_sort_path_new = """    fun getSortPath(): String {
        return when (_activeTab.value) {
            ActiveTab.ALBUMS -> {
                val album = _selectedAlbum.value
                if (album != null) "album_${album.name}" else "global"
            }
            ActiveTab.PERSONS -> {
                val person = _selectedPerson.value
                if (person != null) "person_${person.name}" else "global"
            }
            else -> {
                val path = currentPath
                if (path.isEmpty()) "global" else path
            }
        }
    }"""

if get_sort_path_old in content:
    content = content.replace(get_sort_path_old, get_sort_path_new)
    print("Replaced getSortPath successfully")
else:
    print("Could not find getSortPath_old")

loadPersonContent_old = """                val dummyDir = api.search(server, searchQueryJson, cookies, apiPrefix)
                _personContentState.value = GalleryUiState.Success(dummyDir)"""

loadPersonContent_new = """                val dummyDir = api.search(server, searchQueryJson, cookies, apiPrefix)
                val sortedDirectory = sortDirectory(dummyDir)
                _personContentState.value = GalleryUiState.Success(sortedDirectory)"""

if loadPersonContent_old in content:
    content = content.replace(loadPersonContent_old, loadPersonContent_new)
    print("Replaced loadPersonContent successfully")
else:
    print("Could not find loadPersonContent_old")

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
