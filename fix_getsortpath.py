import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

getsortpath_old = """    fun getSortPath(): String {
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

getsortpath_new = """    fun getSortPath(): String {
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
    }"""

if getsortpath_old in content:
    content = content.replace(getsortpath_old, getsortpath_new)
    print("Replaced getsortpath successfully")
else:
    print("Could not find getsortpath_old")

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
