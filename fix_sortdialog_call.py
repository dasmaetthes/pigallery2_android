import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

sortdialog_call_old = """                            if (showSortDialog) {
                                SortDialog(
                                    viewModel = viewModel,
                                    onDismiss = { showSortDialog = false }
                                )
                            }"""

sortdialog_call_new = """                            if (showSortDialog) {
                                val showFolderSort = when(activeTab) {
                                    ActiveTab.GALLERY -> true
                                    ActiveTab.ALBUMS -> selectedAlbum == null
                                    ActiveTab.PERSONS -> selectedPerson == null
                                    else -> false
                                }
                                val showMediaSort = when(activeTab) {
                                    ActiveTab.GALLERY -> true
                                    ActiveTab.ALBUMS -> selectedAlbum != null
                                    ActiveTab.PERSONS -> selectedPerson != null
                                    else -> false
                                }
                                val folderSortTitle = when(activeTab) {
                                    ActiveTab.ALBUMS -> "Alben sortieren nach"
                                    ActiveTab.PERSONS -> "Personen sortieren nach"
                                    else -> "Ordner sortieren nach"
                                }
                                SortDialog(
                                    viewModel = viewModel,
                                    showFolderSort = showFolderSort,
                                    showMediaSort = showMediaSort,
                                    folderSortTitle = folderSortTitle,
                                    onDismiss = { showSortDialog = false }
                                )
                            }"""

if sortdialog_call_old in content:
    content = content.replace(sortdialog_call_old, sortdialog_call_new)
    print("Replaced sortdialog call successfully")
else:
    print("Could not find sortdialog_call_old")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
