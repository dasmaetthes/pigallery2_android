import sys

# GalleryScreen.kt
file_path_1 = "/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt"
with open(file_path_1, "r") as f:
    content_1 = f.read()

content_1 = content_1.replace("if (shareProgress > 0f) {", "if (shareProgress >= 0f) {")

with open(file_path_1, "w") as f:
    f.write(content_1)


# GalleryViewModel.kt
file_path_2 = "/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt"
with open(file_path_2, "r") as f:
    content_2 = f.read()

content_2 = content_2.replace("private val _shareProgress = MutableStateFlow(0f)", "private val _shareProgress = MutableStateFlow(-1f)")
content_2 = content_2.replace("_shareProgress.value = 0f // Reset", "_shareProgress.value = -1f // Reset")

with open(file_path_2, "w") as f:
    f.write(content_2)

print("Patched share progress")
