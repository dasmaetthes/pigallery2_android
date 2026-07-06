import sys

file_path = "/app/applet/app/src/main/java/com/example/ui/MediaViewerDialog.kt"

with open(file_path, "r") as f:
    content = f.read()

target = """    // Video playback states
    var localVideoUri by remember { mutableStateOf<Uri?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }"""

if target in content:
    content = content.replace(target, "")
    with open(file_path, "w") as f:
        f.write(content)
    print("Cleaned successfully")
else:
    print("Target not found")
