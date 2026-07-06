import sys

file_path = "/app/applet/app/src/main/java/com/example/data/PiGalleryApi.kt"
with open(file_path, "r") as f:
    content = f.read()

# Replace inline metadata parsing in parseDirectory
target1 = """            val metadata = if (metaMap != null) {
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
                    
                val cameraDataMap = metaMap["cameraData"] as? Map<*, *>
                val cameraData = if (cameraDataMap != null) {
                    ApiCameraData(
                        ISO = (cameraDataMap["ISO"] as? Number)?.toInt(),
                        make = cameraDataMap["make"] as? String,
                        model = cameraDataMap["model"] as? String,
                        fStop = (cameraDataMap["fStop"] as? Number)?.toDouble(),
                        exposure = (cameraDataMap["exposure"] as? Number)?.toDouble(),
                        focalLength = (cameraDataMap["focalLength"] as? Number)?.toDouble(),
                        lens = cameraDataMap["lens"] as? String
                    )
                } else null
                
                val keywordsList = metaMap["keywords"] as? List<*>
                val keywords = keywordsList?.mapNotNull { it as? String }
                
                val facesList = metaMap["faces"] as? List<*>
                val faces = facesList?.mapNotNull { 
                    val faceMap = it as? Map<*, *>
                    faceMap?.let { fm -> ApiFace(name = fm["name"] as? String) }
                }
                    
                ApiMediaMetadata(size, creationDate, cameraData, keywords, faces)
            } else {
                null
            }"""

replacement1 = """            val metadata = if (metaMap != null) {
                parseMetadata(metaMap, cwMap)
            } else {
                null
            }"""

if target1 in content:
    content = content.replace(target1, replacement1)
    print("Replaced inline metadata in parseDirectory")
else:
    print("Target1 not found")
    
# Replace inline metadata parsing in parseSearchResult
target2 = """            val metadata = if (metaMap != null) {
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
                    
                ApiMediaMetadata(size, creationDate)
            } else {
                null
            }"""

replacement2 = """            val metadata = if (metaMap != null) {
                parseMetadata(metaMap, mapObj)
            } else {
                null
            }"""

if target2 in content:
    content = content.replace(target2, replacement2)
    print("Replaced inline metadata in parseSearchResult")
else:
    print("Target2 not found")
    
with open(file_path, "w") as f:
    f.write(content)
