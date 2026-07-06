with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

# Remove the broken imports at the very start
content = content.replace("import android.content.Context\nimport okhttp3.Cache\nimport java.io.File\npackage com.example.data\n", "package com.example.data\nimport android.content.Context\nimport okhttp3.Cache\nimport java.io.File\n")

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
