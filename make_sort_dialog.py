import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

old_sort_menu = """                            var showSortMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort Options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    val currentSortBy = viewModel.prefs.sortBy
                                    val currentDir = viewModel.prefs.sortDirection
                                    
                                    Text(
                                        text = "Sort By",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    DropdownMenuItem(
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Name")
                                                if (currentSortBy == "name") {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateSort("name", currentDir)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Date")
                                                if (currentSortBy == "date") {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateSort("date", currentDir)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Random")
                                                if (currentSortBy == "random") {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateSort("random", currentDir)
                                            showSortMenu = false
                                        }
                                    )
                                    
                                    HorizontalDivider()
                                    
                                    Text(
                                        text = "Direction",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    DropdownMenuItem(
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Ascending")
                                                if (currentDir == "asc") {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateSort(currentSortBy, "asc")
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Descending")
                                                if (currentDir == "desc") {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateSort(currentSortBy, "desc")
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }"""

new_sort_menu = """                            var showSortDialog by remember { mutableStateOf(false) }
                            IconButton(onClick = { showSortDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort Options"
                                )
                            }
                            if (showSortDialog) {
                                SortDialog(
                                    viewModel = viewModel,
                                    onDismiss = { showSortDialog = false }
                                )
                            }"""

content = content.replace(old_sort_menu, new_sort_menu)

sort_dialog_code = """
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortDialog(viewModel: GalleryViewModel, onDismiss: () -> Unit) {
    val currentPath = viewModel.getSortPath()
    
    var folderSortBy by remember { mutableStateOf(viewModel.prefs.getFolderSortBy(currentPath, viewModel.prefs.getFolderSortBy("global", "name"))) }
    var folderSortDir by remember { mutableStateOf(viewModel.prefs.getFolderSortDirection(currentPath, viewModel.prefs.getFolderSortDirection("global", "asc"))) }
    
    var mediaSortBy by remember { mutableStateOf(viewModel.prefs.getMediaSortBy(currentPath, viewModel.prefs.getMediaSortBy("global", "date"))) }
    var mediaSortDir by remember { mutableStateOf(viewModel.prefs.getMediaSortDirection(currentPath, viewModel.prefs.getMediaSortDirection("global", "asc"))) }
    
    var currentFolderOnly by remember { mutableStateOf(false) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sortierung", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Ordner sortieren nach", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = folderSortBy == "name", onClick = { folderSortBy = "name" }, label = { Text("Name") })
                    FilterChip(selected = folderSortBy == "date", onClick = { folderSortBy = "date" }, label = { Text("Datum") })
                    FilterChip(selected = folderSortBy == "random", onClick = { folderSortBy = "random" }, label = { Text("Zufall") })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = folderSortDir == "asc", onClick = { folderSortDir = "asc" }, label = { Text("Aufsteigend") })
                    FilterChip(selected = folderSortDir == "desc", onClick = { folderSortDir = "desc" }, label = { Text("Absteigend") })
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
}
"""

content += sort_dialog_code

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
