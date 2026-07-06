import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

sortdialog_decl_old = """@Composable
fun SortDialog(viewModel: GalleryViewModel, onDismiss: () -> Unit) {"""

sortdialog_decl_new = """@Composable
fun SortDialog(
    viewModel: GalleryViewModel,
    showFolderSort: Boolean = true,
    showMediaSort: Boolean = true,
    folderSortTitle: String = "Ordner sortieren nach",
    onDismiss: () -> Unit
) {"""

if sortdialog_decl_old in content:
    content = content.replace(sortdialog_decl_old, sortdialog_decl_new)
    print("Replaced sortdialog decl successfully")
else:
    print("Could not find sortdialog_decl_old")

sortdialog_body_old = """                Text("Ordner sortieren nach", style = MaterialTheme.typography.labelMedium)
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
                }"""

sortdialog_body_new = """                if (showFolderSort) {
                    Text(folderSortTitle, style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = folderSortBy == "name", onClick = { folderSortBy = "name" }, label = { Text("Name") })
                        FilterChip(selected = folderSortBy == "date", onClick = { folderSortBy = "date" }, label = { Text("Datum") })
                        FilterChip(selected = folderSortBy == "random", onClick = { folderSortBy = "random" }, label = { Text("Zufall") })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = folderSortDir == "asc", onClick = { folderSortDir = "asc" }, label = { Text("Aufsteigend") })
                        FilterChip(selected = folderSortDir == "desc", onClick = { folderSortDir = "desc" }, label = { Text("Absteigend") })
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
                }"""

if sortdialog_body_old in content:
    content = content.replace(sortdialog_body_old, sortdialog_body_new)
    print("Replaced sortdialog body successfully")
else:
    print("Could not find sortdialog_body_old")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
