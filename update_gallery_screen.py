import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

state_old = """    val isFlattened by viewModel.isFlattened.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()"""

state_new = """    val isFlattened by viewModel.isFlattened.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }"""

if state_old in content:
    content = content.replace(state_old, state_new)
    print("Replaced state")
else:
    print("Could not find state")

menu_old = """                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    viewModel.setActiveTab(ActiveTab.SETTINGS)
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null
                                    )
                                }
                            )
                        }"""

menu_new = """                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    viewModel.setActiveTab(ActiveTab.SETTINGS)
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    showAboutDialog = true
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null
                                    )
                                }
                            )
                        }"""

if menu_old in content:
    content = content.replace(menu_old, menu_new)
    print("Replaced menu")
else:
    print("Could not find menu")

scaffold_end_old = """        }
    )
}"""

scaffold_end_new = """        }
    )

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}"""

# We want to replace the LAST occurrence of scaffold_end_old.
# In a large file, this might be tricky, let's just insert before the final '}' of GalleryScreen.
# It's better to just do this explicitly.

idx = content.find("fun GalleryScreen(")
if idx != -1:
    # Find the matching closing brace for GalleryScreen
    count = 0
    in_function = False
    end_idx = -1
    for i in range(idx, len(content)):
        if content[i] == '{':
            count += 1
            in_function = True
        elif content[i] == '}':
            count -= 1
        
        if in_function and count == 0:
            end_idx = i
            break
            
    if end_idx != -1:
        content = content[:end_idx] + "    if (showAboutDialog) {\n        AboutDialog(onDismiss = { showAboutDialog = false })\n    }\n" + content[end_idx:]
        print("Inserted dialog call")
    else:
        print("Could not find end of GalleryScreen")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
