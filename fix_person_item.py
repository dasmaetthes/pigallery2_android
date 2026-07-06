import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

bad_box = """        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(cornerRadius.dp))
                .clip(RoundedCornerShape(cornerRadius.dp)),
            contentAlignment = Alignment.Center
        ) {"""

good_box = """        Box(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (aspectRatio > 0f) it.aspectRatio(aspectRatio) else it.aspectRatio(1f) }
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(cornerRadius.dp))
                .clip(RoundedCornerShape(cornerRadius.dp)),
            contentAlignment = Alignment.Center
        ) {"""

content = content.replace(bad_box, good_box)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
