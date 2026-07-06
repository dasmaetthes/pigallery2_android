import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

topappbar_cond_old = """                        if (activeTab == ActiveTab.GALLERY || (activeTab == ActiveTab.ALBUMS && selectedAlbum != null) || (activeTab == ActiveTab.PERSONS && selectedPerson != null)) {"""
topappbar_cond_new = """                        if (activeTab == ActiveTab.GALLERY || activeTab == ActiveTab.ALBUMS || activeTab == ActiveTab.PERSONS) {"""

if topappbar_cond_old in content:
    content = content.replace(topappbar_cond_old, topappbar_cond_new)
    print("Replaced topappbar condition successfully")
else:
    print("Could not find topappbar_cond_old")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
