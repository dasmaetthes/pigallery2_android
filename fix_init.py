import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('var itemsPerRowPortrait by remember { mutableStateOf(viewModel.prefs.itemsPerRowPortrait.toFloat()) }', 'var itemsPerRowPortrait by remember { mutableStateOf(viewModel.prefs.itemsPerRowPortrait.toFloat().takeIf { it > 0 } ?: 3f) }')
content = content.replace('var itemsPerRowLandscape by remember { mutableStateOf(viewModel.prefs.itemsPerRowLandscape.toFloat()) }', 'var itemsPerRowLandscape by remember { mutableStateOf(viewModel.prefs.itemsPerRowLandscape.toFloat().takeIf { it > 0 } ?: 6f) }')
content = content.replace('var cornerRadius by remember { mutableStateOf(viewModel.prefs.cornerRadius.toFloat()) }', 'var cornerRadius by remember { mutableStateOf(viewModel.prefs.cornerRadius.toFloat().takeIf { it > 0 } ?: 5f) }')
content = content.replace('var spacing by remember { mutableStateOf(viewModel.prefs.spacing.toFloat()) }', 'var spacing by remember { mutableStateOf(viewModel.prefs.spacing.toFloat().takeIf { it > 0 } ?: 5f) }')

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
