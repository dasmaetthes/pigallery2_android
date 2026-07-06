with open('/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

old_code = """    val searchQuery by viewModel.searchQuery.collectAsState()
    val isFlattened by viewModel.isFlattened.collectAsState()"""

new_code = """    val searchQuery by viewModel.searchQuery.collectAsState()
    var textFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(searchQuery)) }
    LaunchedEffect(searchQuery) {
        if (searchQuery != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = searchQuery,
                selection = androidx.compose.ui.text.TextRange(searchQuery.length)
            )
        }
    }
    val isFlattened by viewModel.isFlattened.collectAsState()"""

content = content.replace(old_code, new_code)

old_tf = """                            TextField(
                                value = searchQuery,
                                onValueChange = {
                                    viewModel.updateSearchQueryText(it)
                                    viewModel.fetchSearchSuggestions(it)
                                    viewModel.showSearchSuggestions.value = true
                                },"""

new_tf = """                            TextField(
                                value = textFieldValue,
                                onValueChange = { newValue ->
                                    textFieldValue = newValue
                                    if (searchQuery != newValue.text) {
                                        viewModel.updateSearchQueryText(newValue.text)
                                        viewModel.fetchSearchSuggestions(newValue.text)
                                        viewModel.showSearchSuggestions.value = true
                                    }
                                },"""

content = content.replace(old_tf, new_tf)

with open('/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
