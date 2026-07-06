import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

person_classes = """
@JsonClass(generateAdapter = true)
data class ApiPerson(
    val id: Int,
    val name: String,
    val missingThumbnail: Boolean? = null,
    val isFavourite: Boolean? = null,
    val cache: ApiPersonCache? = null
)

@JsonClass(generateAdapter = true)
data class ApiPersonCache(
    val count: Int? = null
)
"""

if "data class ApiPerson" not in content:
    idx = content.find("data class ApiAlbum")
    if idx != -1:
        content = content[:idx] + person_classes + "\n" + content[idx:]
        
person_api = """
    @GET("/api/person")
    suspend fun getPersons(
        @Header("Cookie") cookies: String
    ): ResultResponse<List<ApiPerson>>
"""

if "fun getPersons" not in content:
    idx = content.find("fun getAlbums")
    if idx != -1:
        content = content[:idx] + person_api[1:] + "\n" + content[idx:]

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
