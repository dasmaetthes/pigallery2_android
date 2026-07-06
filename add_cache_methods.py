import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

imports = """import android.app.Application
import androidx.lifecycle.AndroidViewModel"""

if 'import coil.imageLoader' not in content:
    content = content.replace(imports, imports + "\nimport coil.imageLoader\nimport kotlinx.coroutines.flow.asStateFlow")

cache_methods = """
    private val _cacheSize = MutableStateFlow("0 B")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    fun updateCacheSize() {
        var size: Long = 0
        val context = getApplication<Application>()
        val imageCache = java.io.File(context.cacheDir, "image_cache")
        if (imageCache.exists()) {
            size += imageCache.walkBottomUp().sumOf { it.length() }
        }
        val httpCache = java.io.File(context.cacheDir, "http_cache")
        if (httpCache.exists()) {
            size += httpCache.walkBottomUp().sumOf { it.length() }
        }
        _cacheSize.value = formatSize(size)
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (kotlin.math.log10(size.toDouble()) / kotlin.math.log10(1024.0)).toInt()
        return String.format("%.2f %s", size / kotlin.math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun clearCaches() {
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
    }
}"""

content = content.replace("    }\n}", cache_methods)

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
