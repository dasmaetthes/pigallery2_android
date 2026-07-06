import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

updatesort_old = """        if (_activeTab.value == ActiveTab.ALBUMS) {
            val album = _selectedAlbum.value
            if (album != null) {
                val currentContent = _albumContentState.value
                if (currentContent is GalleryUiState.Success) {
                    _albumContentState.value = GalleryUiState.Success(sortDirectory(currentContent.directory))
                } else {
                    loadAlbumContent(album)
                }
            }
        } else {
            val currentContent = _galleryState.value
            if (currentContent is GalleryUiState.Success) {
                _galleryState.value = GalleryUiState.Success(sortDirectory(currentContent.directory))
            } else {
                loadCurrentDirectory()
            }
        }"""

updatesort_new = """        if (_activeTab.value == ActiveTab.ALBUMS) {
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
        } else {
            val currentContent = _galleryState.value
            if (currentContent is GalleryUiState.Success) {
                _galleryState.value = GalleryUiState.Success(sortDirectory(currentContent.directory))
            } else {
                loadCurrentDirectory()
            }
        }"""

if updatesort_old in content:
    content = content.replace(updatesort_old, updatesort_new)
    print("Replaced updatesort successfully")
else:
    print("Could not find updatesort_old")
    
with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
