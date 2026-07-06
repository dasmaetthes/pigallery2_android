import sys

file_path = "/app/applet/app/src/main/java/com/example/data/PiGalleryApi.kt"
with open(file_path, "r") as f:
    content = f.read()

# 1. Update parseDirectory signature and calls
content = content.replace(
    "private fun parseDirectory(map: Map<String, Any?>): ApiDirectory {",
    "private fun parseDirectory(map: Map<String, Any?>, cwMap: Map<String, Any?>?): ApiDirectory {"
)

content = content.replace(
    "return parseDirectory(directoryMap)",
    "val cwMap = result?.get(\"map\") as? Map<String, Any?>\n                            return parseDirectory(directoryMap, cwMap)"
)

# 2. Add parseMetadata helper
helper = """    private fun parseMetadata(metaMap: Map<*, *>, cwMap: Map<String, Any?>?): ApiMediaMetadata {
        val sizeMap = metaMap["size"] as? Map<*, *>
        val sizeList = metaMap["d"] as? List<*>
        val size = if (sizeMap != null) {
            val w = (sizeMap["width"] as? Number)?.toInt()
            val h = (sizeMap["height"] as? Number)?.toInt()
            ApiSize(w, h)
        } else if (sizeList != null && sizeList.size >= 2) {
            val w = (sizeList[0] as? Number)?.toInt()
            val h = (sizeList[1] as? Number)?.toInt()
            ApiSize(w, h)
        } else {
            null
        }

        val creationDate = (metaMap["creationDate"] as? Number)?.toLong()
            ?: (metaMap["t"] as? Number)?.toLong()

        val cwFaces = cwMap?.get("faces") as? List<*>
        val cwKeywords = cwMap?.get("keywords") as? List<*>
        val cwLens = cwMap?.get("lens") as? List<*>
        val cwCamera = cwMap?.get("camera") as? List<*>

        val cameraDataMap = metaMap["cameraData"] as? Map<*, *> ?: metaMap["c"] as? Map<*, *>
        val cameraData = if (cameraDataMap != null) {
            val makeIndex = cameraDataMap["m"] as? Number
            val make = makeIndex?.let { cwCamera?.getOrNull(it.toInt()) as? String } ?: cameraDataMap["make"] as? String
            val modelIndex = cameraDataMap["o"] as? Number
            val model = modelIndex?.let { cwCamera?.getOrNull(it.toInt()) as? String } ?: cameraDataMap["model"] as? String
            val lensIndex = cameraDataMap["l"] as? Number
            val lens = lensIndex?.let { cwLens?.getOrNull(it.toInt()) as? String } ?: cameraDataMap["lens"] as? String

            ApiCameraData(
                ISO = (cameraDataMap["ISO"] as? Number ?: cameraDataMap["i"] as? Number)?.toInt(),
                make = make,
                model = model,
                fStop = (cameraDataMap["fStop"] as? Number ?: cameraDataMap["s"] as? Number)?.toDouble(),
                exposure = (cameraDataMap["exposure"] as? Number ?: cameraDataMap["e"] as? Number)?.toDouble(),
                focalLength = (cameraDataMap["focalLength"] as? Number ?: cameraDataMap["a"] as? Number)?.toDouble(),
                lens = lens
            )
        } else null

        val keywordsRaw = metaMap["keywords"] as? List<*> ?: metaMap["k"] as? List<*>
        val keywords = keywordsRaw?.mapNotNull { item ->
            if (item is Number) {
                cwKeywords?.getOrNull(item.toInt()) as? String
            } else {
                item as? String
            }
        }

        val facesRaw = metaMap["faces"] as? List<*> ?: metaMap["f"] as? List<*>
        val faces = facesRaw?.mapNotNull { item ->
            if (item is Map<*, *>) {
                ApiFace(name = item["name"] as? String)
            } else if (item is List<*>) {
                val faceIndex = item.getOrNull(4) as? Number
                if (faceIndex != null) {
                    ApiFace(name = cwFaces?.getOrNull(faceIndex.toInt()) as? String)
                } else null
            } else null
        }

        return ApiMediaMetadata(size, creationDate, cameraData, keywords, faces)
    }
"""

content = content.replace("class PiGalleryApi {", "class PiGalleryApi {\n" + helper)

with open(file_path, "w") as f:
    f.write(content)
print("Added helper and updated parseDirectory signature")
