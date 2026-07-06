import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

content = content.replace("media.metadata?.faces?.forEach { face ->", "media.metadata?.faces?.forEach { face: com.example.data.ApiFace ->")

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
