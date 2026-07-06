import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

nav_item = """                    NavigationBarItem(
                        selected = activeTab == ActiveTab.PERSONS,
                        onClick = { viewModel.setActiveTab(ActiveTab.PERSONS) },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Persons") },
                        label = { Text("Persons") }
                    )"""

target = """                    NavigationBarItem(
                        selected = activeTab == ActiveTab.REDISCOVER,"""

content = content.replace(target, nav_item + "\n" + target)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
