import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

# I will just write a specific replacement that changes the tab routing logic without breaking the internal code.

# Find the start of the `if (selectedSettingsTab == 1)` up to the end of Server settings.
# It's easier to just do string replacements on the `} else if ...`

c1 = content.replace('if (selectedSettingsTab == 1) {', 'if (selectedSettingsTab == 0) {')
c2 = c1.replace('} else if (selectedSettingsTab == 0) {', '')
c3 = c2.replace('} else if (selectedSettingsTab == 2) {', '} else if (selectedSettingsTab == 1) {')
c4 = c3.replace('} else if (selectedSettingsTab == 3) {', '} else if (selectedSettingsTab == 2) {')

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(c4)
