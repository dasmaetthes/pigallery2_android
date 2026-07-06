import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

# I will find the exact blocks.
# View Settings Card starts with:
#                // --- 2. View Settings ---
#                Card(
# up to:
#                        Spacer(modifier = Modifier.height(16.dp))
#                        
#                    }
#                }
#
# Theme Settings Card starts with:
#                // --- 1. Theme Settings ---
#                Card(
# up to:
#                        }
#                    }
#                }
#            } else if (selectedSettingsTab == 1) {

pattern_view = r"(                // --- 2\. View Settings ---.*?)                // --- 1\. Theme Settings ---"
match_view = re.search(pattern_view, content, flags=re.DOTALL)

pattern_theme = r"(                // --- 1\. Theme Settings ---.*?)\n            \} else if \(selectedSettingsTab == 1\) \{"
match_theme = re.search(pattern_theme, content, flags=re.DOTALL)

if match_view and match_theme:
    view_block = match_view.group(1)
    theme_block = match_theme.group(1)
    
    # Let's replace the whole section
    full_pattern = r"(            if \(selectedSettingsTab == 0\) \{\n).*?(\n            \} else if \(selectedSettingsTab == 1\) \{)"
    full_match = re.search(full_pattern, content, flags=re.DOTALL)
    
    if full_match:
        new_content = content[:full_match.start()] + full_match.group(1) + theme_block + "\n" + view_block + full_match.group(2) + content[full_match.end():]
        
        with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
            f.write(new_content)
        print("Success")
    else:
        print("Full pattern not found")
else:
    print("Blocks not found")

