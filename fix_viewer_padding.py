import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

bottombar_old = """                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),"""

bottombar_new = """                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, top = 12.dp, bottom = 32.dp),"""

if bottombar_old in content:
    content = content.replace(bottombar_old, bottombar_new)
    print("Replaced bottombar successfully")
else:
    print("Could not find bottombar_old")

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
