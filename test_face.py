import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

# Replace ApiFace
old_face = """@JsonClass(generateAdapter = true)
data class ApiFace(
    val name: String?
)"""

new_face = """@JsonClass(generateAdapter = true)
data class ApiFaceBox(
    val width: Int,
    val height: Int,
    val left: Int,
    val top: Int
)

@JsonClass(generateAdapter = true)
data class ApiFace(
    val name: String?,
    val box: ApiFaceBox? = null
)"""

content = content.replace(old_face, new_face)

# Replace face parsing
old_parse = """        val faces = facesRaw?.mapNotNull { item ->
            if (item is Map<*, *>) {
                ApiFace(name = item["name"] as? String)
            } else if (item is List<*>) {
                val faceIndex = item.getOrNull(4) as? Number
                if (faceIndex != null) {
                    ApiFace(name = cwFaces?.getOrNull(faceIndex.toInt()) as? String)
                } else null
            } else null
        }"""

new_parse = """        val faces = facesRaw?.mapNotNull { item ->
            if (item is Map<*, *>) {
                val name = item["name"] as? String
                val boxMap = item["box"] as? Map<*, *>
                val box = if (boxMap != null) {
                    val w = (boxMap["width"] as? Number)?.toInt() ?: 0
                    val h = (boxMap["height"] as? Number)?.toInt() ?: 0
                    val l = (boxMap["left"] as? Number)?.toInt() ?: 0
                    val t = (boxMap["top"] as? Number)?.toInt() ?: 0
                    ApiFaceBox(w, h, l, t)
                } else null
                ApiFace(name, box)
            } else if (item is List<*>) {
                val w = (item.getOrNull(0) as? Number)?.toInt() ?: 0
                val h = (item.getOrNull(1) as? Number)?.toInt() ?: 0
                val l = (item.getOrNull(2) as? Number)?.toInt() ?: 0
                val t = (item.getOrNull(3) as? Number)?.toInt() ?: 0
                val box = if (w > 0 && h > 0) ApiFaceBox(w, h, l, t) else null
                
                val faceIndex = item.getOrNull(4) as? Number
                if (faceIndex != null) {
                    ApiFace(name = cwFaces?.getOrNull(faceIndex.toInt()) as? String, box = box)
                } else {
                    ApiFace(name = null, box = box)
                }
            } else null
        }"""

content = content.replace(old_parse, new_parse)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
