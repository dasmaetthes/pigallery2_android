import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

albums_sort_old = """            is AlbumsUiState.Success -> {
                val albums = state.albums.sortedBy { it.name }
                if (albums.isEmpty()) {"""

albums_sort_new = """            is AlbumsUiState.Success -> {
                val albums = state.albums
                if (albums.isEmpty()) {"""

if albums_sort_old in content:
    content = content.replace(albums_sort_old, albums_sort_new)
    print("Replaced albums UI sort correctly.")
else:
    print("Could not find albums_sort_old.")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
