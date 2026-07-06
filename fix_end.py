import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

content = re.sub(
    r"(\s+exitSelectMode\(\)\n)(\s+private val _cacheSize = MutableStateFlow\(\"0 B\"\))",
    r"\1    }\n\2",
    content
)

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
