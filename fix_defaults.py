import re

with open('app/src/main/java/com/example/data/PreferencesManager.kt', 'r') as f:
    content = f.read()

content = content.replace('prefs.getInt(KEY_ITEMS_PER_ROW_LANDSCAPE, 5)', 'prefs.getInt(KEY_ITEMS_PER_ROW_LANDSCAPE, 6)')
content = content.replace('prefs.getInt(KEY_CORNER_RADIUS, 8)', 'prefs.getInt(KEY_CORNER_RADIUS, 5)')
content = content.replace('prefs.getInt(KEY_SPACING, 8)', 'prefs.getInt(KEY_SPACING, 5)')

with open('app/src/main/java/com/example/data/PreferencesManager.kt', 'w') as f:
    f.write(content)
