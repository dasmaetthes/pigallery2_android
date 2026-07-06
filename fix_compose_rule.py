import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

target_bad = """                } else {
                    val spacing by viewModel.spacing.collectAsState()
                    val spacingDp = spacing.dp
                    LazyVerticalGrid("""

replacement = """                } else {
                    val spacingDp = spacing.dp
                    LazyVerticalGrid("""

if target_bad in content:
    content = content.replace(target_bad, replacement)
    
    # Also we need to add 'val spacing by viewModel.spacing.collectAsState()' to the top of PersonsTabContent
    top_target = """    val itemsPerRowLandscape by viewModel.itemsPerRowLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemsPerRow = if (isLandscape) itemsPerRowLandscape else itemsPerRowPortrait"""
    
    top_replacement = """    val itemsPerRowLandscape by viewModel.itemsPerRowLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemsPerRow = if (isLandscape) itemsPerRowLandscape else itemsPerRowPortrait
    val spacing by viewModel.spacing.collectAsState()"""
    
    content = content.replace(top_target, top_replacement)
    
    with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
        f.write(content)
        print("Fixed compose rule!")
else:
    print("Could not find target block.")
