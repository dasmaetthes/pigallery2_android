import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

response_class = """@JsonClass(generateAdapter = true)
data class PersonsResponse(
    val error: String?,
    val result: List<ApiPerson>?
)
"""

if "data class PersonsResponse" not in content:
    content = content.replace("data class LoginResponse", response_class + "\ndata class LoginResponse")

bad_parse = """val type = com.squareup.moshi.Types.newParameterizedType(ResultResponse::class.java, com.squareup.moshi.Types.newParameterizedType(List::class.java, ApiPerson::class.java))
            val adapter = moshi.adapter<ResultResponse<List<ApiPerson>>>(type)
            val result = adapter.fromJson(bodyString)"""

good_parse = """val adapter = moshi.adapter(PersonsResponse::class.java)
            val result = adapter.fromJson(bodyString)
            if (result?.error != null) {
                throw IOException(result.error)
            }"""

content = content.replace(bad_parse, good_parse)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
