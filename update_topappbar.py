import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

topappbar_old = """                        if (activeTab == ActiveTab.GALLERY || (activeTab == ActiveTab.ALBUMS && selectedAlbum != null)) {"""

topappbar_new = """                        if (activeTab == ActiveTab.GALLERY || (activeTab == ActiveTab.ALBUMS && selectedAlbum != null) || (activeTab == ActiveTab.PERSONS && selectedPerson != null)) {"""

if topappbar_old in content:
    content = content.replace(topappbar_old, topappbar_new)
    print("Replaced topappbar successfully")
else:
    print("Could not find topappbar_old")
    
with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
