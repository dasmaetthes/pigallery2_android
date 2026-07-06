import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

old_server = """            } else {
                // --- 3. Server Settings ---"""
                        
new_server = """            } else if (selectedSettingsTab == 3) {
                // --- 4. Server Settings ---"""

content = content.replace(old_server, new_server)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
