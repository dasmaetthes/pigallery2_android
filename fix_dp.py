import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

if content.startswith("import androidx.compose.ui.unit.dp\n"):
    content = content[len("import androidx.compose.ui.unit.dp\n"):]

idx = content.find("package com.example.ui")
if idx != -1:
    end_of_package = content.find("\n", idx)
    content = content[:end_of_package+1] + "\nimport androidx.compose.ui.unit.dp\n" + content[end_of_package+1:]

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
