import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

# Replace client builder to remove forced caching
old_client_start = "    private val client = OkHttpClient.Builder()"
old_client_end = ".build()"

# We need to replace the client definition carefully
new_client = """    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024L * 1024L)) // 50MB
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
        
    fun clearCache() {
        try {
            client.cache?.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }"""

# Find the block
start_idx = content.find(old_client_start)
end_idx = content.find(old_client_end, start_idx) + len(old_client_end)

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + new_client + content[end_idx:]

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
