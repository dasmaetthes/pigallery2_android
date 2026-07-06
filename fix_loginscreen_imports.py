import re

with open('app/src/main/java/com/example/ui/LoginScreen.kt', 'r') as f:
    content = f.read()

import_old = "import androidx.compose.ui.Alignment"
import_new = "import androidx.compose.ui.Alignment\nimport androidx.compose.foundation.clickable"

if import_old in content:
    content = content.replace(import_old, import_new)
    print("Replaced imports")
else:
    print("Could not find imports")

with open('app/src/main/java/com/example/ui/LoginScreen.kt', 'w') as f:
    f.write(content)
