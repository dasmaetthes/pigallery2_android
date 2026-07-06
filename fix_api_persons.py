import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

bad_api = """    @GET("/api/person")
    suspend fun getPersons(
        @Header("Cookie") cookies: String
    ): ResultResponse<List<ApiPerson>>"""

good_api = """fun getPersons(serverUrl: String, cookies: String, apiPrefix: String): List<ApiPerson> {
    val sanitizedUrl = serverUrl.trimEnd('/')
    val endpoint = "$sanitizedUrl$apiPrefix/person"
    val builder = Request.Builder()
        .url(endpoint)
        .get()
        
    if (cookies.isNotEmpty()) {
        builder.addHeader("Cookie", cookies)
    }
    
    val request = builder.build()
    client.newCall(request).execute().use { response ->
        val bodyString = response.body?.string() ?: throw IOException("Empty response from person endpoint")
        
        if (!response.isSuccessful) {
            throw IOException("Server returned error code: ${response.code}")
        }
        
        try {
            val type = com.squareup.moshi.Types.newParameterizedType(ResultResponse::class.java, com.squareup.moshi.Types.newParameterizedType(List::class.java, ApiPerson::class.java))
            val adapter = moshi.adapter<ResultResponse<List<ApiPerson>>>(type)
            val result = adapter.fromJson(bodyString)
            
            return result?.result ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            throw IOException("Failed to parse persons response: ${e.message}")
        }
    }
}"""

if bad_api in content:
    content = content.replace(bad_api, good_api)
else:
    # try Regex
    content = re.sub(r'@GET\("/api/person"\)\s*suspend fun getPersons\(\s*@Header\("Cookie"\) cookies: String\s*\): ResultResponse<List<ApiPerson>>', good_api, content)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
