import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

bad_ratio = "val aspectRatio by viewModel.aspectRatio.collectAsState()"
good_ratio = "val ratio by viewModel.aspectRatio.collectAsState()"
content = content.replace(bad_ratio, good_ratio)

bad_ratio2 = ".let { if (aspectRatio > 0f) it.aspectRatio(aspectRatio) else it.aspectRatio(1f) }"
good_ratio2 = ".let { if (ratio > 0f) it.aspectRatio(ratio) else it.aspectRatio(1f) }"
content = content.replace(bad_ratio2, good_ratio2)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
