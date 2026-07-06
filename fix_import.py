import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

if "import androidx.compose.material.icons.filled.MoreVert" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Settings", "import androidx.compose.material.icons.filled.Settings\\nimport androidx.compose.material.icons.filled.MoreVert")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
