import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

# Replace Features & Content with Features
content = content.replace('text = "Features & Content"', 'text = "Features"')

# Split Appearance tab into Theme and View tabs
# Let's see the tab titles
content = content.replace(
    'val tabTitles = listOf("Appearance", "Features", "Server")',
    'val tabTitles = listOf("Theme", "View", "Features", "Server")'
)

content = content.replace(
    'val tabIcons = listOf(Icons.Default.Palette, Icons.Default.Star, Icons.Default.Cloud)',
    'val tabIcons = listOf(Icons.Default.Palette, Icons.Default.ViewModule, Icons.Default.Star, Icons.Default.Cloud)'
)

# And we also need to add import for ViewModule if it's not there, but let's just use what's already imported. Wait, I should make sure ViewModule is imported.
