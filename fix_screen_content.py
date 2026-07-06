import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

tab_content_case = """                    ActiveTab.PERSONS -> {
                        PersonsTabContent(viewModel = viewModel)
                    }"""

if "PersonsTabContent(viewModel = viewModel)" not in content:
    idx = content.find("ActiveTab.ALBUMS -> {\n                        AlbumsTabContent(viewModel = viewModel)\n                    }")
    if idx != -1:
        end_idx = content.find("}", idx) + 1
        content = content[:end_idx] + "\n" + tab_content_case + content[end_idx:]

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
