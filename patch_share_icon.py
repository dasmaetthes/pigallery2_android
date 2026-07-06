import sys

file_path = "/app/applet/app/src/main/java/com/example/ui/MediaViewerDialog.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """                        IconButton(onClick = {
                            downloadFile(context, mediaUrl, currentMedia.name, cookies)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = Color.White
                            )
                        }"""

replacement = """                        IconButton(onClick = {
                            viewModel.shareSingleMedia(context, currentMedia)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = {
                            downloadFile(context, mediaUrl, currentMedia.name, cookies)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = Color.White
                            )
                        }"""

if target in content:
    content = content.replace(target, replacement)
    
    # Also add import for Icons.Default.Share if missing
    if "import androidx.compose.material.icons.filled.Share" not in content:
        content = content.replace("import androidx.compose.material.icons.filled.Download", "import androidx.compose.material.icons.filled.Download\nimport androidx.compose.material.icons.filled.Share")
        
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched share icon")
else:
    print("Target not found")
