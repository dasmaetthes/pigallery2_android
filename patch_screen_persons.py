import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

nav_item = """                    NavigationBarItem(
                        selected = activeTab == ActiveTab.PERSONS,
                        onClick = { viewModel.setActiveTab(ActiveTab.PERSONS) },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Persons") },
                        label = { Text("Persons") }
                    )"""

if "ActiveTab.PERSONS" not in content and "Icon(Icons.Default.Person" not in content:
    idx = content.find("ActiveTab.REDISCOVER")
    if idx != -1:
        # We need to find the previous NavigationBarItem which is ALBUMS
        nav_albums_idx = content.rfind("NavigationBarItem(", 0, idx)
        if nav_albums_idx != -1:
            rediscover_idx = content.find("NavigationBarItem(", nav_albums_idx + 10)
            if rediscover_idx != -1:
                content = content[:rediscover_idx] + nav_item + "\n" + content[rediscover_idx:]


tab_content_case = """                    ActiveTab.PERSONS -> {
                        PersonsTabContent(viewModel = viewModel)
                    }"""

if "ActiveTab.PERSONS ->" not in content:
    idx = content.find("ActiveTab.ALBUMS -> {")
    if idx != -1:
        end_idx = content.find("}", idx) + 1
        content = content[:end_idx] + "\n" + tab_content_case + content[end_idx:]

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
