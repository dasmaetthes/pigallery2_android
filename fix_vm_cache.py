import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

old_clear = """    fun clearCaches() {
        val context = getApplication<Application>()
        val imageCache = java.io.File(context.cacheDir, "image_cache")
        if (imageCache.exists()) {
            imageCache.deleteRecursively()
        }
        val httpCache = java.io.File(context.cacheDir, "http_cache")
        if (httpCache.exists()) {
            httpCache.deleteRecursively()
        }
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
        updateCacheSize()
    }"""

new_clear = """    fun clearCaches() {
        val context = getApplication<Application>()
        val imageCache = java.io.File(context.cacheDir, "image_cache")
        if (imageCache.exists()) {
            imageCache.deleteRecursively()
        }
        // Properly clear OkHttp cache without deleting the directory
        api.clearCache()
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
        updateCacheSize()
    }"""

content = content.replace(old_clear, new_clear)

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
