import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

bad_when = """        // Refresh contents if needed when switching
        if (prefs.isLoggedIn && prefs.serverUrl.isNotEmpty()) {
            when (tab) {
                ActiveTab.GALLERY -> loadCurrentDirectory()
                ActiveTab.ALBUMS -> {
                    _selectedAlbum.value = null
                    _albumContentState.value = null
                    loadAlbums()
                }
                ActiveTab.REDISCOVER -> loadRediscover()
                ActiveTab.SETTINGS -> { /* no-op */ }
            }
        }"""

good_when = """        // Refresh contents if needed when switching
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
        }"""

if "ActiveTab.PERSONS -> {" not in content:
    content = content.replace(bad_when, good_when)

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
