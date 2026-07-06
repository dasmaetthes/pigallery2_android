import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

optin_str = "@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)"
content = content.replace(optin_str, "")

# Find fun MediaViewerDialog and add it right before
idx = content.find("fun MediaViewerDialog")
if idx != -1:
    # Look for @Composable
    composable_idx = content.rfind("@Composable", 0, idx)
    if composable_idx != -1:
        content = content[:composable_idx] + optin_str + "\n" + content[composable_idx:]
    else:
        content = content[:idx] + optin_str + "\n" + content[idx:]

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
