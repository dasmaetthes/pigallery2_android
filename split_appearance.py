import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

# Replace Features & Content with Features
content = content.replace('text = "Features & Content"', 'text = "Features"')

# Split Appearance into two cards.
# Current code structure for Appearance:
#            if (selectedSettingsTab == 0) {
#                // --- 1. Appearance Settings ---
#                Card(
#                    modifier = Modifier.fillMaxWidth(),
#                    shape = RoundedCornerShape(16.dp)
#                ) {
#                    Column(modifier = Modifier.padding(16.dp)) {
#                        Text(
#                            text = "Appearance Settings",
#                            style = MaterialTheme.typography.titleMedium,
#                            fontWeight = FontWeight.Bold,
#                            color = MaterialTheme.colorScheme.primary
#                        )
#                        Spacer(modifier = Modifier.height(12.dp))
#                        // Show Directory Item Count Switch
# ... down to
#                        // Theme Mode Selector
#                        Column { ... }
#                    }
#                }
#            } else if (selectedSettingsTab == 1) {

# It's better to just regex replace the specific parts.

old_theme_mode = """                        // Theme Color Picker
                        Column {
                            Text(text = "Theme Color", style = MaterialTheme.typography.bodyMedium)"""

new_theme_mode = """                        
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Theme Color Picker
                        Column {
                            Text(text = "Theme Color", style = MaterialTheme.typography.bodyMedium)"""

content = content.replace(old_theme_mode, new_theme_mode)

old_appearance_title = """                        Text(
                            text = "Appearance Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )"""

new_appearance_title = """                        Text(
                            text = "View",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )"""

content = content.replace(old_appearance_title, new_appearance_title)

# Let's write the modified content back
with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)

