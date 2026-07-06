import sys

file_path = "/app/applet/app/src/main/java/com/example/ui/GalleryViewModel.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """    fun shareSelectedMedia(context: Context) {"""

replacement = """    fun shareSingleMedia(context: Context, media: ApiMedia) {
        shareJob?.cancel()
        shareJob = viewModelScope.launch(Dispatchers.IO) {
            _shareProgress.value = 0f
            try {
                val file = downloadMediaToCache(context, media)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                
                _shareProgress.value = -1f // Reset
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (media.isVideo) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Media via"))
            } catch (e: Exception) {
                // Handle download error
                _shareProgress.value = -1f
            }
        }
    }

    fun shareSelectedMedia(context: Context) {"""

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched shareSingleMedia")
else:
    print("Target not found")
