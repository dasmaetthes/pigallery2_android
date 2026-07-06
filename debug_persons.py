import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

target = """        try {
            val adapter = moshi.adapter(PersonsResponse::class.java)
            val result = adapter.fromJson(bodyString)
            if (result?.error != null) {
                throw IOException(result.error)
            }
            
            return result?.result ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            throw IOException("Failed to parse persons response: ${e.message}")
        }"""

replacement = """        try {
            val adapter = moshi.adapter(PersonsResponse::class.java)
            val result = adapter.fromJson(bodyString)
            if (result?.error != null) {
                throw IOException(result.error)
            }
            
            return result?.result ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            val shortBody = if (bodyString.length > 300) bodyString.take(300) + "..." else bodyString
            throw IOException("Parse err: ${e.message} | Body: $shortBody")
        }"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
