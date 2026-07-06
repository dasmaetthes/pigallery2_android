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
            val shortBody = if (bodyString.length > 300) bodyString.take(300) + "..." else bodyString
            throw IOException("Parse err: ${e.message} | Body: $shortBody")
        }"""

manual_parse = """        val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)
        val responseMap = try {
            mapAdapter.fromJson(bodyString)
        } catch (e: Exception) {
            val shortBody = if (bodyString.length > 300) bodyString.take(300) + "..." else bodyString
            throw IOException("Failed to parse server response as JSON: ${e.message} | Body: $shortBody")
        }
           
        // Try getting error as string, if it's an object, it'll just be ignored or we can check
        val errorObj = responseMap?.get("error")
        if (errorObj != null) {
            throw IOException("Server returned error: $errorObj")
        }

        val resultList = responseMap?.get("result") as? List<*> ?: return emptyList()
        return resultList.mapNotNull { item ->
            val personMap = item as? Map<*, *> ?: return@mapNotNull null
            val id = (personMap["id"] as? Number)?.toInt()
            val name = (personMap["name"] as? String) ?: ""
            val missingThumbnail = personMap["missingThumbnail"] as? Boolean
            val isFavourite = personMap["isFavourite"] as? Boolean
            
            val cacheMap = personMap["cache"] as? Map<*, *>
            val count = (cacheMap?.get("count") as? Number)?.toInt()
            
            val cache = if (count != null) ApiPersonCache(count) else null
            
            ApiPerson(
                id = id,
                name = name,
                missingThumbnail = missingThumbnail,
                isFavourite = isFavourite,
                cache = cache
            )
        }"""

content = content.replace(target, manual_parse)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
