import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

import_old = "import androidx.compose.material.icons.filled.Info"
if import_old not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Image", "import androidx.compose.material.icons.filled.Image\nimport androidx.compose.material.icons.filled.Info")

import_old2 = "import androidx.compose.material3.TextButton"
if import_old2 not in content:
    content = content.replace("import androidx.compose.material3.Text", "import androidx.compose.material3.Text\nimport androidx.compose.material3.TextButton")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
