import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

person_api = """
    fun getPersons(serverUrl: String, cookies: String, apiPrefix: String): List<ApiPerson> {
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
                // Response is typically { error: null, result: [...] }
                val type = Types.newParameterizedType(ResultResponse::class.java, Types.newParameterizedType(List::class.java, ApiPerson::class.java))
                val adapter = moshi.adapter<ResultResponse<List<ApiPerson>>>(type)
                val result = adapter.fromJson(bodyString)
                
                return result?.result ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                throw IOException("Failed to parse persons response: ${e.message}")
            }
        }
    }
"""

if "fun getPersons(" not in content:
    idx = content.find("fun getAlbums(")
    if idx != -1:
        content = content[:idx] + person_api[1:] + "\n" + content[idx:]

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
