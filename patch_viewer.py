import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

# 1. Add showFaceRegions to MediaViewerItem
old_def = """fun MediaViewerItem(
    media: ApiMedia,
    viewModel: GalleryViewModel,
    cookies: String,
    context: Context,
    rotation: Float,
    onToggleBars: () -> Unit
)"""
new_def = """fun MediaViewerItem(
    media: ApiMedia,
    viewModel: GalleryViewModel,
    cookies: String,
    context: Context,
    rotation: Float,
    showFaceRegions: Boolean,
    onToggleBars: () -> Unit
)"""
content = content.replace(old_def, new_def)

# 2. Pass showFaceRegions
old_call = """                        MediaViewerItem(
                            media = pageMedia,
                            viewModel = viewModel,
                            cookies = cookies,
                            context = context,
                            rotation = pageRotation,
                            onToggleBars = {
                                showBars = !showBars
                                if (showBars) isSlideshowPlaying = false
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
                                if (showBars) isSlideshowPlaying = false
                            }
                        )"""
content = content.replace(old_call, new_call)

# 3. Fix currentMedia and other errors in .drawWithContent
# currentMedia -> media
old_face = "currentMedia.metadata?.faces?.forEach { face ->"
new_face = "media.metadata?.faces?.forEach { face ->"
content = content.replace(old_face, new_face)

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
