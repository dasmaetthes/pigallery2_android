import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

person_old = """data class ApiPerson(
    val id: Int? = null,
    val name: String,
    val missingThumbnail: Boolean? = null,
    val isFavourite: Boolean? = null,
    val cache: ApiPersonCache? = null
)"""

person_new = """data class ApiPerson(
    val id: Int? = null,
    val name: String = "",
    val missingThumbnail: Boolean? = null,
    val isFavourite: Boolean? = null,
    val cache: ApiPersonCache? = null
)"""

content = content.replace(person_old, person_new)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
