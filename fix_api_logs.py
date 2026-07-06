import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

# I will add logging interceptor
old_client = """    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024L * 1024L)) // 50MB
        .addNetworkInterceptor { chain ->"""

new_client = """    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024L * 1024L)) // 50MB
        .addInterceptor { chain ->
            val request = chain.request()
            android.util.Log.e("PiGalleryApi", "REQUEST: ${request.url}")
            val response = chain.proceed(request)
            val body = response.peekBody(1024 * 1024).string()
            if (!response.isSuccessful) {
                 android.util.Log.e("PiGalleryApi", "ERROR ${response.code}: $body")
            }
            response
        }
        .addNetworkInterceptor { chain ->"""

content = content.replace(old_client, new_client)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
