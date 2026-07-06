import re

with open('/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

# 1. Add imports if missing
imports = [
    "import androidx.compose.foundation.layout.FlowRow",
    "import androidx.compose.foundation.layout.ExperimentalLayoutApi",
    "import androidx.compose.material3.InputChip",
    "import androidx.compose.material3.InputChipDefaults",
    "import androidx.compose.material.icons.filled.Clear",
    "import androidx.compose.foundation.BorderStroke"
]
for imp in imports:
    if imp not in content:
        content = content.replace("import androidx.compose.foundation.layout.*", f"{imp}\nimport androidx.compose.foundation.layout.*")

# 2. Update TextField for Search
old_textfield = """                            TextField(
                                value = searchQuery,
                                onValueChange = {
                                    viewModel.updateSearchQueryText(it)
                                    viewModel.fetchSearchSuggestions(it)
                                    viewModel.showSearchSuggestions.value = true
                                },
                                placeholder = { Text("Search... e.g. tag:sunset or John") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                                    viewModel.executeSearch()
                                    keyboardController?.hide()
                                }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )"""

new_textfield = """                            TextField(
                                value = searchQuery,
                                onValueChange = {
                                    viewModel.updateSearchQueryText(it)
                                    viewModel.fetchSearchSuggestions(it)
                                    viewModel.showSearchSuggestions.value = true
                                },
                                placeholder = { Text("Search") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                                    viewModel.executeSearch()
                                    keyboardController?.hide()
                                }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { 
                                            viewModel.updateSearchQueryText("")
                                            viewModel.fetchSearchSuggestions("")
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )"""
content = content.replace(old_textfield, new_textfield)

# 3. Update overlay call in GalleryTabContent
old_overlay_call = """            SearchSuggestionsOverlay(
                searchQuery = searchQuery,
                searchSuggestions = searchSuggestions,
                onSuggestionClick = { clickedSuggestion ->
                    viewModel.setSearchQueryFromSuggestion(clickedSuggestion)
                },
                modifier = Modifier.fillMaxSize()
            )"""
new_overlay_call = """            SearchSuggestionsOverlay(
                searchQuery = searchQuery,
                searchSuggestions = searchSuggestions,
                onSuggestionClick = { clickedSuggestion ->
                    viewModel.setSearchQueryFromSuggestion(clickedSuggestion)
                },
                onPrefixClick = { prefix ->
                    viewModel.appendPrefixToSearch(prefix)
                },
                onRemoveToken = { token ->
                    viewModel.removeTokenFromSearch(token)
                },
                modifier = Modifier.fillMaxSize()
            )"""
content = content.replace(old_overlay_call, new_overlay_call)


# 4. Replace SearchSuggestionsOverlay
overlay_pattern = re.compile(r"@Composable\nfun SearchSuggestionsOverlay\(.*?^}", re.MULTILINE | re.DOTALL)

new_overlay = """@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchSuggestionsOverlay(
    searchQuery: String,
    searchSuggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onPrefixClick: (String) -> Unit,
    onRemoveToken: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = searchQuery.trimEnd().split(Regex("\\\\s+")).filter { it.isNotBlank() }
    val prefixes = listOf("directory:", "file-name:", "caption:", "person:", "keyword:", "position:", "rating:", "resolution:", "orientation:", "date:", "last-%d-days:")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (tokens.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tokens.forEach { token ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveToken(token) },
                        label = { Text(token) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            enabled = true,
                            selected = false
                        )
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prefixes.forEach { prefix ->
                Surface(
                    onClick = { onPrefixClick(prefix) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = prefix,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        if (searchSuggestions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No suggestions",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchSuggestions) { suggestion ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionClick(suggestion) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}"""

content = overlay_pattern.sub(new_overlay, content)

with open('/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)

