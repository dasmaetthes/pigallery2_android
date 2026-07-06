import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

top_old = """    // Album Tab sub-navigation states
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()"""

top_new = """    // Album Tab sub-navigation states
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedPerson by viewModel.selectedPerson.collectAsState()"""

if top_old in content:
    content = content.replace(top_old, top_new)
    print("Replaced selectedAlbum state successfully")
else:
    print("Could not find top_old")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
