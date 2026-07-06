import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

# Remove the flatten button
flatten_btn = """                            // Flatten Directory Button
                            IconButton(onClick = { viewModel.toggleFlattened() }) {
                                Icon(
                                    imageVector = if (isFlattened) Icons.Default.GridView else Icons.Default.Layers,
                                    contentDescription = if (isFlattened) "Normal View" else "Flatten Directory",
                                    tint = if (isFlattened) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }"""

content = content.replace(flatten_btn, "")

# Add More menu to the end of actions
actions_end = """                        if (activeTab == ActiveTab.SETTINGS) {
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors("""

new_actions_end = """                        if (activeTab == ActiveTab.SETTINGS) {
                        }
                        
                        var showMoreMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(androidx.compose.material.icons.Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            if (activeTab == ActiveTab.GALLERY) {
                                DropdownMenuItem(
                                    text = { Text(if (isFlattened) "Unflatten Directory" else "Flatten Directory") },
                                    onClick = {
                                        viewModel.toggleFlattened()
                                        showMoreMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isFlattened) Icons.Default.GridView else Icons.Default.Layers,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                            DropdownMenuItem(
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
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors("""

content = content.replace(actions_end, new_actions_end)

nav_bar_item = """                    NavigationBarItem(
                        selected = activeTab == ActiveTab.SETTINGS,
                        onClick = { viewModel.setActiveTab(ActiveTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )"""

content = content.replace(nav_bar_item, "")

# Add missing import for MoreVert
import_str = "import androidx.compose.material.icons.filled.MoreVert\\n"
if "MoreVert" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Settings\\n", "import androidx.compose.material.icons.filled.Settings\\nimport androidx.compose.material.icons.filled.MoreVert\\n")


with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
