import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()
    if 'Unbekannt' in content:
        print("Found Unbekannt")
