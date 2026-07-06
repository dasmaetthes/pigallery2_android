import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

bad = "                        .padding(horizontal = 16.dp, top = 12.dp, bottom = 32.dp)"
good = "                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)"

if bad in content:
    content = content.replace(bad, good)
    print("Replaced padding successfully")
else:
    print("Could not find bad padding")

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
