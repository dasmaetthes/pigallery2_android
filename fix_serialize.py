import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

old_serialize = """    fun serializeQuery(query: Map<String, Any?>): String {
        val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val adapter = moshi.adapter<Map<String, Any?>>(mapType)
        return adapter.toJson(query)
    }"""

new_serialize = """    fun serializeQuery(query: Map<String, Any?>): String {
        fun cleanMap(map: Map<String, Any?>): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()
            for ((k, v) in map) {
                if (v is Double && v % 1.0 == 0.0) {
                    result[k] = v.toLong()
                } else if (v is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    result[k] = cleanMap(v as Map<String, Any?>)
                } else {
                    result[k] = v
                }
            }
            return result
        }
        val cleanedQuery = cleanMap(query)
        val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val adapter = moshi.adapter<Map<String, Any?>>(mapType)
        return adapter.toJson(cleanedQuery)
    }"""

content = content.replace(old_serialize, new_serialize)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
