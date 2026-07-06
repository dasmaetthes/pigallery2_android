import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

imports = """import androidx.compose.ui.window.DialogProperties
import coil.imageLoader
import coil.request.ImageRequest"""

if 'import coil.imageLoader' not in content:
    content = content.replace('import androidx.compose.ui.window.DialogProperties', imports)

# Find LaunchedEffect(isSlideshowPlaying)
old_code = """    LaunchedEffect(isSlideshowPlaying) {"""

new_code = """    val imageLoader = context.imageLoader
    LaunchedEffect(pagerState.currentPage, mediaList) {
        val start = (pagerState.currentPage - 3).coerceAtLeast(0)
        val end = (pagerState.currentPage + 3).coerceAtMost(mediaList.size - 1)
        for (i in start..end) {
            if (i != pagerState.currentPage) {
                val prefetchMedia = mediaList[i]
                if (!prefetchMedia.isVideo) {
                    val prefetchUrl = viewModel.getOriginalMediaUrl(prefetchMedia)
                    val request = ImageRequest.Builder(context)
                        .data(prefetchUrl)
                        .addHeader("Cookie", cookies)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
        }
    }

    LaunchedEffect(isSlideshowPlaying) {"""

content = content.replace(old_code, new_code)

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)
