import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

bad = """    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {"""

good = """    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {"""

content = content.replace(bad, good)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
