import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

sort_albums_old = """            "name" -> {
                if (fAsc) albums.sortedBy { it.name.lowercase() }
                else albums.sortedByDescending { it.name.lowercase() }
            }
            "random" -> albums.shuffled()
            else -> albums"""

sort_albums_new = """            "name", "date" -> {
                if (fAsc) albums.sortedBy { it.name.lowercase() }
                else albums.sortedByDescending { it.name.lowercase() }
            }
            "random" -> albums.shuffled()
            else -> albums"""

sort_persons_old = """            "name" -> {
                if (fAsc) persons.sortedBy { it.name.lowercase() }
                else persons.sortedByDescending { it.name.lowercase() }
            }
            "random" -> persons.shuffled()
            else -> persons"""

sort_persons_new = """            "name", "date" -> {
                if (fAsc) persons.sortedBy { it.name.lowercase() }
                else persons.sortedByDescending { it.name.lowercase() }
            }
            "random" -> persons.shuffled()
            else -> persons"""

if sort_albums_old in content:
    content = content.replace(sort_albums_old, sort_albums_new)
    print("Replaced albums sort successfully")

if sort_persons_old in content:
    content = content.replace(sort_persons_old, sort_persons_new)
    print("Replaced persons sort successfully")

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
