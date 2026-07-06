import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

import_old = "import androidx.compose.foundation.layout.fillMaxWidth"
import_new = "import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.WindowInsets\nimport androidx.compose.foundation.layout.navigationBars\nimport androidx.compose.foundation.layout.asPaddingValues"

if import_old in content:
    content = content.replace(import_old, import_new)
    print("Replaced imports")
else:
    print("Could not find imports")

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
