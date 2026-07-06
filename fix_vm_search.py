import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

bad_search = """val searchQuery = mapOf("type" to 105, "text" to person.name, "matchType" to 1)
                val searchResult = api.searchMedia(server, cookies, apiPrefix, searchQuery)
                // searchMedia returns an ApiSearchResult which has media. We wrap it in an ApiDirectory
                val dummyDir = com.example.data.ApiDirectory(
                    name = person.name,
                    path = person.name,
                    lastModified = 0,
                    lastScanned = 0,
                    mediaCount = searchResult.media?.size ?: 0,
                    directories = emptyList(),
                    media = searchResult.media ?: emptyList()
                )"""

good_search = """val searchQuery = mapOf("type" to 105, "text" to person.name, "matchType" to 1)
                // Need moshi adapter
                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = moshi.adapter<Map<String, Any>>(type)
                val searchQueryJson = adapter.toJson(searchQuery)
                val dummyDir = api.search(server, searchQueryJson, cookies, apiPrefix)"""

content = content.replace(bad_search, good_search)

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
