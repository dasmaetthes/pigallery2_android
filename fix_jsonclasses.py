import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

# Find all "data class X(" not preceded by @JsonClass
lines = content.split('\n')
for i, line in enumerate(lines):
    if line.strip().startswith('data class '):
        if i > 0 and '@JsonClass' not in lines[i-1]:
            lines[i] = '@JsonClass(generateAdapter = true)\n' + line

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write('\n'.join(lines))
