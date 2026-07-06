import sys

file_path = "/app/applet/app/src/main/java/com/example/ui/MediaViewerDialog.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->"""

replacement = """                if (shareProgress >= 0f) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (shareProgress > 0f) shareProgress else 0f },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(bottom = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->"""

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched share progress ui")
else:
    print("Target not found")
