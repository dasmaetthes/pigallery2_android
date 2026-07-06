import sys

file_path = "/app/applet/app/src/main/java/com/example/data/PiGalleryApi.kt"
with open(file_path, "r") as f:
    content = f.read()

target1 = """                val creationDate = (metaMap["creationDate"] as? Number)?.toLong()
                    ?: (metaMap["t"] as? Number)?.toLong()
                    
                ApiMediaMetadata(size, creationDate)"""

replacement1 = """                val creationDate = (metaMap["creationDate"] as? Number)?.toLong()
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
                    
                ApiMediaMetadata(size, creationDate, cameraData, keywords, faces)"""

if target1 in content:
    content = content.replace(target1, replacement1)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched manual parser")
else:
    print("Target not found")
