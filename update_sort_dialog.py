import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

caller_old = """                                val showFolderSort = when(activeTab) {
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
                                )"""

caller_new = """                                val showFolderSort = when(activeTab) {
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
                                val showFolderSortOptions = activeTab == ActiveTab.GALLERY
                                SortDialog(
                                    viewModel = viewModel,
                                    showFolderSort = showFolderSort,
                                    showMediaSort = showMediaSort,
                                    showFolderSortOptions = showFolderSortOptions,
                                    folderSortTitle = folderSortTitle,
                                    onDismiss = { showSortDialog = false }
                                )"""

if caller_old in content:
    content = content.replace(caller_old, caller_new)
    print("Replaced caller successfully")
else:
    print("Could not find caller_old")


dialog_old = """fun SortDialog(
    viewModel: GalleryViewModel,
    showFolderSort: Boolean = true,
    showMediaSort: Boolean = true,
    folderSortTitle: String = "Ordner sortieren nach",
    onDismiss: () -> Unit
) {
    val currentPath = viewModel.getSortPath()
    
    var folderSortBy by remember { mutableStateOf(viewModel.prefs.getFolderSortBy(currentPath, viewModel.prefs.getFolderSortBy("global", "name"))) }
    var folderSortDir by remember { mutableStateOf(viewModel.prefs.getFolderSortDirection(currentPath, viewModel.prefs.getFolderSortDirection("global", "asc"))) }
    
    var mediaSortBy by remember { mutableStateOf(viewModel.prefs.getMediaSortBy(currentPath, viewModel.prefs.getMediaSortBy("global", "date"))) }
    var mediaSortDir by remember { mutableStateOf(viewModel.prefs.getMediaSortDirection(currentPath, viewModel.prefs.getMediaSortDirection("global", "asc"))) }
    
    var currentFolderOnly by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sortierung", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                folderSortBy = "name"
                mediaSortBy = "name"
                if (showFolderSort) {
                    Text(folderSortTitle, style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = folderSortDir == "asc", onClick = { folderSortDir = "asc" }, label = { Text("A - Z") })
                        FilterChip(selected = folderSortDir == "desc", onClick = { folderSortDir = "desc" }, label = { Text("Z - A") })
                    }
                    if (showMediaSort) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                if (showMediaSort) {
                    Text("Bilder sortieren", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mediaSortDir == "asc", onClick = { mediaSortDir = "asc" }, label = { Text("A - Z") })
                        FilterChip(selected = mediaSortDir == "desc", onClick = { mediaSortDir = "desc" }, label = { Text("Z - A") })
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { currentFolderOnly = !currentFolderOnly }) {
                        Checkbox(checked = currentFolderOnly, onCheckedChange = { currentFolderOnly = it })
                        Text("Nur für aktuellen Ordner")
                    }
                } else {
                    currentFolderOnly = true
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                    Button(onClick = {
                        viewModel.updateSort(folderSortBy, folderSortDir, mediaSortBy, mediaSortDir, currentFolderOnly)
                        onDismiss()
                    }) {
                        Text("Übernehmen")
                    }
                }
            }
        }
    }
}"""

dialog_new = """fun SortDialog(
    viewModel: GalleryViewModel,
    showFolderSort: Boolean = true,
    showMediaSort: Boolean = true,
    showFolderSortOptions: Boolean = true,
    folderSortTitle: String = "Ordner sortieren nach",
    onDismiss: () -> Unit
) {
    val currentPath = viewModel.getSortPath()
    
    var folderSortBy by remember { mutableStateOf(viewModel.prefs.getFolderSortBy(currentPath, viewModel.prefs.getFolderSortBy("global", "name"))) }
    var folderSortDir by remember { mutableStateOf(viewModel.prefs.getFolderSortDirection(currentPath, viewModel.prefs.getFolderSortDirection("global", "asc"))) }
    
    var mediaSortBy by remember { mutableStateOf(viewModel.prefs.getMediaSortBy(currentPath, viewModel.prefs.getMediaSortBy("global", "date"))) }
    var mediaSortDir by remember { mutableStateOf(viewModel.prefs.getMediaSortDirection(currentPath, viewModel.prefs.getMediaSortDirection("global", "asc"))) }
    
    var currentFolderOnly by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sortierung", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (showFolderSort) {
                    if (!showFolderSortOptions) {
                        folderSortBy = "name"
                    }
                    Text(folderSortTitle, style = MaterialTheme.typography.labelMedium)
                    if (showFolderSortOptions) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = folderSortBy == "name", onClick = { folderSortBy = "name" }, label = { Text("Name") })
                            FilterChip(selected = folderSortBy == "date", onClick = { folderSortBy = "date" }, label = { Text("Datum") })
                            FilterChip(selected = folderSortBy == "random", onClick = { folderSortBy = "random" }, label = { Text("Zufall") })
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = folderSortDir == "asc", onClick = { folderSortDir = "asc" }, label = { if (showFolderSortOptions) Text("Aufsteigend") else Text("A - Z") })
                        FilterChip(selected = folderSortDir == "desc", onClick = { folderSortDir = "desc" }, label = { if (showFolderSortOptions) Text("Absteigend") else Text("Z - A") })
                    }
                    if (showMediaSort) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                if (showMediaSort) {
                    Text("Bilder sortieren nach", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mediaSortBy == "name", onClick = { mediaSortBy = "name" }, label = { Text("Name") })
                        FilterChip(selected = mediaSortBy == "date", onClick = { mediaSortBy = "date" }, label = { Text("Datum") })
                        FilterChip(selected = mediaSortBy == "random", onClick = { mediaSortBy = "random" }, label = { Text("Zufall") })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mediaSortDir == "asc", onClick = { mediaSortDir = "asc" }, label = { Text("Aufsteigend") })
                        FilterChip(selected = mediaSortDir == "desc", onClick = { mediaSortDir = "desc" }, label = { Text("Absteigend") })
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { currentFolderOnly = !currentFolderOnly }) {
                        Checkbox(checked = currentFolderOnly, onCheckedChange = { currentFolderOnly = it })
                        Text("Nur für aktuellen Ordner")
                    }
                } else {
                    currentFolderOnly = true
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                    Button(onClick = {
                        viewModel.updateSort(folderSortBy, folderSortDir, mediaSortBy, mediaSortDir, currentFolderOnly)
                        onDismiss()
                    }) {
                        Text("Übernehmen")
                    }
                }
            }
        }
    }
}"""

if dialog_old in content:
    content = content.replace(dialog_old, dialog_new)
    print("Replaced dialog successfully")
else:
    print("Could not find dialog_old")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
