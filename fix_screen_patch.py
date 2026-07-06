import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

# Fix the title text one
bad_title = """                            ActiveTab.ALBUMS -> {
                                selectedAlbum?.name ?: "Albums"
                            }
                    ActiveTab.PERSONS -> {
                        PersonsTabContent(viewModel = viewModel)
                    }"""

good_title = """                            ActiveTab.ALBUMS -> {
                                selectedAlbum?.name ?: "Albums"
                            }
                            ActiveTab.PERSONS -> {
                                val selectedPerson = viewModel.selectedPerson.value
                                selectedPerson?.name ?: "Persons"
                            }"""

content = content.replace(bad_title, good_title)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
