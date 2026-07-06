import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

back_album = """        // If an album detail view is open in Albums Tab, go back to albums list
        if (_activeTab.value == ActiveTab.ALBUMS && _selectedAlbum.value != null) {
            selectAlbum(null)
            return true
        }"""

back_person = """        // If a person detail view is open in Persons Tab, go back to persons list
        if (_activeTab.value == ActiveTab.PERSONS && _selectedPerson.value != null) {
            clearSelectedPerson()
            return true
        }"""

if "ActiveTab.PERSONS && _selectedPerson.value != null" not in content:
    content = content.replace(back_album, back_album + "\n" + back_person)

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
