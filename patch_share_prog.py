import sys

file_path = "/app/applet/app/src/main/java/com/example/ui/MediaViewerDialog.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                HorizontalPager("""

replacement = """            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val shareProgress by viewModel.shareProgress.collectAsState()
                
                HorizontalPager("""

if target in content:
    content = content.replace(target, replacement)
    
    # Also add the progress indicator
    target2 = """                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->"""
                
    replacement2 = """                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->"""
                
    content = content.replace(target2, replacement2)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched share progress state")
else:
    print("Target not found")
