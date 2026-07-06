import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("androidx.compose.material.icons.Icons.Default.MoreVert", "Icons.Default.MoreVert")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
