import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

# Replace the OkHttpClient instantiation
old_client = """    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024L * 1024L)) // 50MB
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            var response = chain.proceed(request)
            if (request.method == "GET") {
                response = response.newBuilder()
                    .header("Cache-Control", "public, max-age=3600") // 1 hour cache
                    .build()
            }
            response
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()"""

new_client = """    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024L * 1024L)) // 50MB
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            // Only cache successful JSON responses for a shorter time, or don't force cache
            if (request.method == "GET" && response.isSuccessful) {
                // If it's the search or albums endpoint, maybe we shouldn't cache it aggressively
                // For now, let's just cache for 5 minutes instead of 1 hour
                return@addNetworkInterceptor response.newBuilder()
                    .header("Cache-Control", "public, max-age=300") 
                    .build()
            }
            response
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()"""

content = content.replace(old_client, new_client)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
