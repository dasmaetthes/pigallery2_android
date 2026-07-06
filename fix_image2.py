import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

# Remove the incorrectly placed imports from the very top
imports_top = """import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
"""
if content.startswith(imports_top):
    content = content[len(imports_top):]

# Find the package declaration
idx = content.find("package com.example.ui")
if idx != -1:
    end_of_package = content.find("\n", idx)
    content = content[:end_of_package+1] + "\n" + imports_top + content[end_of_package+1:]

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
