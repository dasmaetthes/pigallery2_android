import sys

file_path = "/app/applet/app/src/main/java/com/example/data/PiGalleryApi.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """    private fun parseDirectory(map: Map<String, Any?>, cwMap: Map<String, Any?>?): ApiDirectory {
        val id = (map["id"] as? Number)?.toInt()
        val name = (map["name"] as? String) ?: (map["n"] as? String) ?: ""
        val path = (map["path"] as? String) ?: (map["p"] as? String) ?: ""
        
        val directoryFullPath = if (path.isEmpty() || path == "./") {
            name
        } else if (name.isEmpty() || name == ".") {
            path.replace("./", "").trim('/')
        } else {
            "${path.replace("./", "").trim('/')}/$name"
        }
        
        val directoriesList = map["directories"] as? List<*>
        val directories = directoriesList?.mapNotNull { item ->
            val dirMap = item as? Map<*, *> ?: return@mapNotNull null
            val subId = (dirMap["id"] as? Number)?.toInt()
            val subName = (dirMap["name"] as? String) ?: (dirMap["n"] as? String) ?: ""
            val subPath = (dirMap["path"] as? String) ?: (dirMap["p"] as? String) ?: ""
            
            val cache = dirMap["cache"] as? Map<*, *>
            val mediaCount = (dirMap["mediaCount"] as? Number)?.toInt()
                ?: (cache?.get("recursiveMediaCount") as? Number)?.toInt()
                ?: (cache?.get("mediaCount") as? Number)?.toInt()
                ?: 0
                
            val subFullPath = if (subPath.isEmpty() || subPath == "." || subPath == "./") {
                subName
            } else {
                "${subPath.replace("./", "").trim('/')}/$subName"
            }
                
            ApiSubFolder(subId, subName, subFullPath, mediaCount, parseSubFolderCache(cache))
        } ?: emptyList()
        
        val mediaList = map["media"] as? List<*>
        val media = mediaList?.mapNotNull { item ->
            val mediaMap = item as? Map<*, *> ?: return@mapNotNull null
            val medId = (mediaMap["id"] as? Number)?.toInt() ?: kotlin.random.Random.nextInt()
            val medName = (mediaMap["name"] as? String) ?: (mediaMap["n"] as? String) ?: ""
            
            val metaMap = (mediaMap["metadata"] as? Map<*, *>) ?: (mediaMap["m"] as? Map<*, *>)
            val metadata = if (metaMap != null) {
                parseMetadata(metaMap, mapObj)
            } else {"""

replacement = target.replace("parseMetadata(metaMap, mapObj)", "parseMetadata(metaMap, cwMap)")

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, "w") as f:
        f.write(content)
    print("Fixed parseDirectory cwMap")
else:
    print("Target not found")
