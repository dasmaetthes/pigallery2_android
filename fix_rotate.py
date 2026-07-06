import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

content = content.replace("androidx.compose.material.icons.Icons.Default.RotateRight", "Icons.Default.RotateRight")

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
