import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

pattern = r"                    val groupedMedia = remember\(mediaList\) \{.*?\}\s+LazyVerticalGrid\("

content = re.sub(pattern, "                    LazyVerticalGrid(", content, flags=re.DOTALL, count=2)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
