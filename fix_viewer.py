import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

# Fix MediaViewerItem call
old_call = """                        MediaViewerItem(
                            media = pageMedia,
                            viewModel = viewModel,
                            cookies = cookies,
                            context = context,
                            rotation = pageRotation,
                            onToggleBars = {
                                showBars = !showBars
                            }
                        )"""
new_call = """                        MediaViewerItem(
                            media = pageMedia,
                            viewModel = viewModel,
                            cookies = cookies,
                            context = context,
                            rotation = pageRotation,
                            showFaceRegions = showFaceRegions,
                            onToggleBars = {
                                showBars = !showBars
                            }
                        )"""
content = content.replace(old_call, new_call)

# Fix RotateRight icon
content = content.replace("androidx.compose.material.icons.automirrored.filled.RotateRight", "androidx.compose.material.icons.Icons.Default.RotateRight")
content = content.replace("Icons.Default.RotateRight", "androidx.compose.material.icons.Icons.Default.RotateRight")
content = content.replace("Icons.AutoMirrored.Filled.RotateRight", "androidx.compose.material.icons.Icons.Default.RotateRight")
content = content.replace("androidx.compose.material.icons.Icons.Default.RotateRight", "Icons.Default.RotateRight")

# Add import if missing? We'll just use Icons.Default.RotateRight and add import
if "import androidx.compose.material.icons.filled.RotateRight" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.PlayArrow", "import androidx.compose.material.icons.filled.PlayArrow\nimport androidx.compose.material.icons.filled.RotateRight")

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
