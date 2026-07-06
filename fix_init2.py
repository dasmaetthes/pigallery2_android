import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('var itemsPerRowPortrait by remember { mutableStateOf(viewModel.prefs.itemsPerRowPortrait.toFloat().takeIf { it > 0 } ?: 3f) }', 'var itemsPerRowPortrait by remember { mutableStateOf(viewModel.prefs.itemsPerRowPortrait.toFloat()) }')
content = content.replace('var itemsPerRowLandscape by remember { mutableStateOf(viewModel.prefs.itemsPerRowLandscape.toFloat().takeIf { it > 0 } ?: 6f) }', 'var itemsPerRowLandscape by remember { mutableStateOf(viewModel.prefs.itemsPerRowLandscape.toFloat()) }')
content = content.replace('var cornerRadius by remember { mutableStateOf(viewModel.prefs.cornerRadius.toFloat().takeIf { it > 0 } ?: 5f) }', 'var cornerRadius by remember { mutableStateOf(viewModel.prefs.cornerRadius.toFloat()) }')
content = content.replace('var spacing by remember { mutableStateOf(viewModel.prefs.spacing.toFloat().takeIf { it > 0 } ?: 5f) }', 'var spacing by remember { mutableStateOf(viewModel.prefs.spacing.toFloat()) }')

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
