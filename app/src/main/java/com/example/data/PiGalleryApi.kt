package com.example.data
import android.content.Context
import okhttp3.Cache
import java.io.File

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import android.util.Log
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier

// Moshi Models
data class PersonsResponse(
    val error: String?,
    val result: List<ApiPerson>?
)

data class LoginResponse(
    val error: String?,
    val result: LoginResult?
)

data class LoginResult(
    val username: String?,
    val role: Int?,
    val csrfToken: String?
)

data class GalleryResponse(
    val error: String?,
    val result: GalleryResult?
)

data class GalleryResult(
    val directory: ApiDirectory?
)

data class ApiDirectory(
    val id: Int?,
    val name: String,
    val path: String,
    val directories: List<ApiSubFolder>?,
    val media: List<ApiMedia>?
)

data class ApiSubFolder(
    val id: Int?,
    val name: String,
    val path: String,
    val mediaCount: Int?,
    val cache: ApiSubFolderCache? = null
)

data class ApiSubFolderCache(
    val cover: ApiCoverPhoto? = null
)

data class ApiCoverPhoto(
    val name: String,
    val directory: ApiCoverPhotoDirectory
)

data class ApiCoverPhotoDirectory(
    val name: String,
    val path: String
)

data class ApiMedia(
    val id: Int?,
    val name: String,
    val metadata: ApiMediaMetadata?,
    val parentPath: String? = null
) {
    val isVideo: Boolean
        get() = name.endsWith(".mp4", ignoreCase = true) ||
                name.endsWith(".mov", ignoreCase = true) ||
                name.endsWith(".webm", ignoreCase = true) ||
                name.endsWith(".avi", ignoreCase = true) ||
                name.endsWith(".mkv", ignoreCase = true)
}

// Album DTO Classes

data class ApiPerson(
    val id: Int? = null,
    val name: String = "",
    val missingThumbnail: Boolean? = null,
    val isFavourite: Boolean? = null,
    val cache: ApiPersonCache? = null
)

data class ApiPersonCache(
    val count: Int? = null
)

data class ApiAlbum(
    val id: Int,
    val name: String,
    val locked: Boolean,
    val searchQuery: Map<String, Any?>?,
    val cache: ApiAlbumCache?
)

data class ApiAlbumCache(
    val itemCount: Int,
    val oldestMedia: Long?,
    val youngestMedia: Long?,
    val coverName: String?,
    val coverDirectory: String?
)

data class ApiMediaMetadata(
    val size: ApiSize? = null,
    val creationDate: Long? = null,
    val cameraData: ApiCameraData? = null,
    val keywords: List<String>? = null,
    val faces: List<ApiFace>? = null,
    val duration: Double? = null
)

data class ApiCameraData(
    val ISO: Int?,
    val make: String?,
    val model: String?,
    val fStop: Double?,
    val exposure: Double?,
    val focalLength: Double?,
    val lens: String?
)

data class ApiFaceBox(
    val width: Int,
    val height: Int,
    val left: Int,
    val top: Int
)

data class ApiFace(
    val name: String?,
    val box: ApiFaceBox? = null
)

data class ApiSize(
    val width: Int?,
    val height: Int?
)

class PiGalleryApi(private val context: android.content.Context) {
    private fun parseMetadata(metaMap: Map<*, *>, cwMap: Map<String, Any?>?): ApiMediaMetadata {
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
                val name = item["name"] as? String
                val boxMap = item["box"] as? Map<*, *>
                val box = if (boxMap != null) {
                    val w = (boxMap["width"] as? Number)?.toInt() ?: 0
                    val h = (boxMap["height"] as? Number)?.toInt() ?: 0
                    val l = (boxMap["left"] as? Number)?.toInt() ?: 0
                    val t = (boxMap["top"] as? Number)?.toInt() ?: 0
                    ApiFaceBox(w, h, l, t)
                } else null
                ApiFace(name, box)
            } else if (item is List<*>) {
                val t = (item.getOrNull(0) as? Number)?.toInt() ?: 0
                val l = (item.getOrNull(1) as? Number)?.toInt() ?: 0
                val h = (item.getOrNull(2) as? Number)?.toInt() ?: 0
                val w = (item.getOrNull(3) as? Number)?.toInt() ?: 0
                val box = if (w > 0 && h > 0) ApiFaceBox(width = w, height = h, left = l, top = t) else null
                
                val faceIndex = item.getOrNull(4) as? Number
                if (faceIndex != null) {
                    ApiFace(name = cwFaces?.getOrNull(faceIndex.toInt()) as? String, box = box)
                } else {
                    ApiFace(name = null, box = box)
                }
            } else null
        }

        val duration = (metaMap["duration"] as? Number ?: metaMap["u"] as? Number)?.toDouble()

        return ApiMediaMetadata(size, creationDate, cameraData, keywords, faces, duration)
    }

    private val prefs = PreferencesManager(context)
    private val client: OkHttpClient

    init {
        val builder = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024L * 1024L)) // 50MB
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                        if (!prefs.allowInsecureSsl) {
                            val defaultTrustManagerFactory = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
                            defaultTrustManagerFactory.init(null as java.security.KeyStore?)
                            val defaultTrustManager = defaultTrustManagerFactory.trustManagers[0] as X509TrustManager
                            defaultTrustManager.checkServerTrusted(chain, authType)
                        }
                    }
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { hostname, session -> 
                if (prefs.allowInsecureSsl) true else javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        client = builder.build()
    }

    fun updateClientSsl(allowInsecure: Boolean) {
        // Now handled dynamically via prefs inside the TrustManager
    }
        
    fun clearCache() {
        try {
            client.cache?.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loginAdapter = moshi.adapter(LoginResponse::class.java)
    private val galleryAdapter = moshi.adapter(GalleryResponse::class.java)

    /**
     * Performs a login request on the PiGallery2 server.
     * Returns the cookie string and detected prefix if successful, throws exception on failure.
     */
    fun login(serverUrl: String, username: String, password: String): Pair<String, String> {
        val sanitizedUrl = serverUrl.trimEnd('/')
        // Try the official default prefix "/api" first, then fallback to "/pgapi"
        val prefixes = listOf("/api", "/pgapi")
        var lastException: Exception? = null

        // 1. Try normal POST login for each prefix (if username is not blank)
        if (username.isNotBlank()) {
            for (prefix in prefixes) {
                val endpoint = "$sanitizedUrl$prefix/user/login"

                // Build login credential body
                val jsonBody = """
                    {
                      "loginCredential": {
                        "username": "$username",
                        "password": "$password",
                        "rememberMe": true
                      }
                    }
                """.trimIndent()

                val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        if (response.code == 404) {
                            throw IOException("404 Not Found")
                        }

                        val bodyString = response.body?.string() ?: throw IOException("Empty response from server")
                        
                        if (!response.isSuccessful) {
                            throw IOException("Server returned error code: ${response.code}")
                        }

                        val loginResponse = loginAdapter.fromJson(bodyString)
                        if (loginResponse?.error != null) {
                            throw IOException(loginResponse.error)
                        }

                        // Extract pigallery cookies from Set-Cookie headers
                        val cookieHeaders = response.headers("Set-Cookie")
                        val filteredCookies = cookieHeaders
                            .flatMap { it.split(";") }
                            .map { it.trim() }
                            .filter { it.startsWith("pigallery") }
                            .joinToString("; ")

                        if (filteredCookies.isEmpty()) {
                            throw IOException("No session cookie returned by the server. Check your configuration.")
                        }

                        return Pair(filteredCookies, prefix)
                    }
                } catch (e: Exception) {
                    lastException = e
                    // Continue loop to try other prefixes (e.g. on 404 or connection failures)
                }
            }
        }

        // 2. Fallback: check for Public/Anonymous access if POST login failed or no username was specified
        for (prefix in prefixes) {
            val endpoint = "$sanitizedUrl$prefix/gallery/content/"
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                        val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)
                        val responseMap = try {
                            mapAdapter.fromJson(bodyString)
                        } catch (e: Exception) {
                            null
                        }
                        
                        val result = responseMap?.get("result") as? Map<String, Any?>
                        val directory = result?.get("directory") as? Map<String, Any?>
                        
                        if (directory != null) {
                            // Extract any pigallery cookies from the public access response
                            val cookieHeaders = response.headers("Set-Cookie")
                            val filteredCookies = cookieHeaders
                                .flatMap { it.split(";") }
                                .map { it.trim() }
                                .filter { it.startsWith("pigallery") }
                                .joinToString("; ")

                            return Pair(filteredCookies, prefix)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore and try next prefix
            }
        }

        throw lastException ?: IOException("Failed to login or access public gallery. Check connection and credentials.")
    }

    /**
     * Serializes search query map to JSON string.
     */
    fun serializeQuery(query: Map<String, Any?>): String {
        fun cleanMap(map: Map<String, Any?>): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()
            for ((k, v) in map) {
                if (v is Double && v % 1.0 == 0.0) {
                    result[k] = v.toLong()
                } else if (v is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    result[k] = cleanMap(v as Map<String, Any?>)
                } else {
                    result[k] = v
                }
            }
            return result
        }
        val cleanedQuery = cleanMap(query)
        val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val adapter = moshi.adapter<Map<String, Any?>>(mapType)
        return adapter.toJson(cleanedQuery)
    }

    /**
     * Fetches gallery directory contents (including subfolders and media files).
     */
    fun getGalleryContent(serverUrl: String, relativePath: String, cookies: String, apiPrefix: String): ApiDirectory {
        val sanitizedUrl = serverUrl.trimEnd('/')
        // URL encode the path segments
        val encodedPath = relativePath.split('/')
            .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        
        val endpoint = "$sanitizedUrl$apiPrefix/gallery/content/$encodedPath"

        val builder = Request.Builder()
            .url(endpoint)
            .get()

        if (cookies.isNotEmpty()) {
            builder.addHeader("Cookie", cookies)
        }

        val request = builder.build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: throw IOException("Empty response from gallery content endpoint")
            
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    throw IOException("Unauthorized. Session might have expired.")
                }
                throw IOException("Server returned error code: ${response.code}")
            }

            val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)
            val responseMap = try {
                mapAdapter.fromJson(bodyString)
            } catch (e: Exception) {
                throw IOException("Failed to parse server response as JSON: ${e.message}")
            }
            
            val errorObj = responseMap?.get("error")
            if (errorObj != null) {
                throw IOException("Server returned error: $errorObj")
            }

            val result = responseMap?.get("result") as? Map<String, Any?>
            val directoryMap = result?.get("directory") as? Map<String, Any?>
                ?: throw IOException("Invalid response structure: 'directory' is missing")
                
            val cwMap = result?.get("map") as? Map<String, Any?>
            return parseDirectory(directoryMap, cwMap)
        }
    }

    private fun parseDirectory(map: Map<String, Any?>, cwMap: Map<String, Any?>?): ApiDirectory {
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
                parseMetadata(metaMap, cwMap)
            } else {
                null
            }
            
            ApiMedia(medId, medName, metadata, directoryFullPath)
        } ?: emptyList()
        
        return ApiDirectory(id, name, path, directories, media)
    }

    /**
     * Executes a search query on the server and returns search results parsed as an ApiDirectory.
     */
    fun search(serverUrl: String, searchQueryJson: String, cookies: String, apiPrefix: String): ApiDirectory {
        val sanitizedUrl = serverUrl.trimEnd('/')
        val encodedQuery = URLEncoder.encode(searchQueryJson, "UTF-8").replace("+", "%20")
        val endpoint = "$sanitizedUrl$apiPrefix/search/$encodedQuery"
        
        Log.d("PiGalleryApi", "Search endpoint: $endpoint")
        Log.d("PiGalleryApi", "Search query JSON: $searchQueryJson")

        val builder = Request.Builder()
            .url(endpoint)
            .get()

        if (cookies.isNotEmpty()) {
            builder.addHeader("Cookie", cookies)
        }

        val request = builder.build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: throw IOException("Empty response from search endpoint")
            
            if (!response.isSuccessful) {
                throw IOException("Server returned error code: ${response.code}")
            }

            val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)
            val responseMap = try {
                mapAdapter.fromJson(bodyString)
            } catch (e: Exception) {
                throw IOException("Failed to parse server response as JSON: ${e.message}")
            }
            
            val errorObj = responseMap?.get("error")
            if (errorObj != null) {
                throw IOException("Server returned error: $errorObj")
            }

            val nonNullResponseMap = responseMap ?: throw IOException("Empty or invalid response from server")
            return parseSearchResult(nonNullResponseMap)
        }
    }

    fun download(url: String, cookies: String): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .get()
        
        if (cookies.isNotEmpty()) {
            request.addHeader("Cookie", cookies)
        }
        
        return client.newCall(request.build()).execute()
    }

    private fun parseSearchResult(map: Map<String, Any?>): ApiDirectory {
        val result = map["result"] as? Map<String, Any?> ?: throw IOException("Invalid response structure")
        val mapObj = result["map"] as? Map<String, Any?>
        val mapDirectories = mapObj?.get("directories") as? List<*>
        
        val searchResult = result["searchResult"] as? Map<String, Any?>
            ?: throw IOException("No searchResult found in response")
            
        val mediaList = searchResult["media"] as? List<*>
        val media = mediaList?.mapNotNull { item ->
            val mediaMap = item as? Map<*, *> ?: return@mapNotNull null
            val medId = (mediaMap["id"] as? Number)?.toInt() ?: kotlin.random.Random.nextInt()
            val medName = (mediaMap["name"] as? String) ?: (mediaMap["n"] as? String) ?: ""
            
            // Find parent path from map directories index
            val dIndex = (mediaMap["d"] as? Number)?.toInt()
            val parentPath = if (dIndex != null && mapDirectories != null && dIndex in mapDirectories.indices) {
                val dirMap = mapDirectories[dIndex] as? Map<*, *>
                val dirName = (dirMap?.get("name") as? String) ?: ""
                val dirPath = (dirMap?.get("path") as? String) ?: ""
                val joined = if (dirPath.isEmpty()) dirName else if (dirName.isEmpty()) dirPath else "$dirPath/$dirName"
                joined.replace("./", "").trim('/')
            } else {
                ""
            }
            
            val metaMap = (mediaMap["metadata"] as? Map<*, *>) ?: (mediaMap["m"] as? Map<*, *>)
            val metadata = if (metaMap != null) {
                parseMetadata(metaMap, mapObj)
            } else {
                null
            }
            
            ApiMedia(medId, medName, metadata, parentPath)
        } ?: emptyList()
        
        val directoriesList = searchResult["directories"] as? List<*>
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
                
            ApiSubFolder(subId, subName, subPath, mediaCount, parseSubFolderCache(cache))
        } ?: emptyList()
        
        return ApiDirectory(-1, "Search Results", "", directories, media)
    }

    private fun parseSubFolderCache(cacheMap: Map<*, *>?): ApiSubFolderCache? {
        if (cacheMap == null) return null
        val coverMap = cacheMap["cover"] as? Map<*, *> ?: return null
        val coverName = (coverMap["name"] as? String) ?: (coverMap["n"] as? String) ?: return null
        val directoryMap = (coverMap["directory"] as? Map<*, *>) ?: (coverMap["d"] as? Map<*, *>) ?: return null
        val dirName = (directoryMap["name"] as? String) ?: (directoryMap["n"] as? String) ?: ""
        val dirPath = (directoryMap["path"] as? String) ?: (directoryMap["p"] as? String) ?: ""
        return ApiSubFolderCache(
            cover = ApiCoverPhoto(
                name = coverName,
                directory = ApiCoverPhotoDirectory(
                    name = dirName,
                    path = dirPath
                )
            )
        )
    }

    /**
     * Fetches search autocomplete suggestions from the server.
     */
    fun getAutocompleteSuggestions(serverUrl: String, text: String, cookies: String, apiPrefix: String, type: Int = 100): List<String> {
        val sanitizedUrl = serverUrl.trimEnd('/')
        val encodedText = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        val endpoint = "$sanitizedUrl$apiPrefix/autocomplete/$encodedText?type=$type"

        val builder = Request.Builder()
            .url(endpoint)
            .get()

        if (cookies.isNotEmpty()) {
            builder.addHeader("Cookie", cookies)
        }

        val request = builder.build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyString = response.body?.string() ?: return emptyList()

                val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)
                val responseMap = mapAdapter.fromJson(bodyString)
                val result = responseMap?.get("result")

                if (result is List<*>) {
                    return result.mapNotNull { item ->
                        if (item is String) {
                            item
                        } else if (item is Map<*, *>) {
                            val text = (item["text"] as? String) ?: (item["t"] as? String) ?: (item["value"] as? String)
                            val type = (item["type"] as? Number)?.toInt() ?: (item["type"] as? String)?.toIntOrNull()
                            
                            if (text != null && type != null && type != 100) {
                                val prefix = when (type) {
                                    104 -> "keyword:"
                                    105 -> "person:"
                                    106 -> "position:"
                                    101 -> "caption:"
                                    103 -> "file-name:"
                                    102 -> "directory:"
                                    else -> ""
                                }
                                "$prefix$text"
                            } else {
                                text
                            }
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Suppress and return empty
        }
        return emptyList()
    }

    /**
     * Fetches saved albums from the server.
     */
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
        
        val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)
        val responseMap = try {
            mapAdapter.fromJson(bodyString)
        } catch (e: Exception) {
            val shortBody = if (bodyString.length > 300) bodyString.take(300) + "..." else bodyString
            throw IOException("Failed to parse server response as JSON: ${e.message} | Body: $shortBody")
        }
           
        // Try getting error as string, if it's an object, it'll just be ignored or we can check
        val errorObj = responseMap?.get("error")
        if (errorObj != null) {
            throw IOException("Server returned error: $errorObj")
        }

        val resultList = responseMap?.get("result") as? List<*> ?: return emptyList()
        return resultList.mapNotNull { item ->
            val personMap = item as? Map<*, *> ?: return@mapNotNull null
            val id = (personMap["id"] as? Number)?.toInt()
            val name = (personMap["name"] as? String) ?: ""
            val missingThumbnail = personMap["missingThumbnail"] as? Boolean
            val isFavourite = personMap["isFavourite"] as? Boolean
            
            val cacheMap = personMap["cache"] as? Map<*, *>
            val count = (cacheMap?.get("count") as? Number)?.toInt()
            
            val cache = if (count != null) ApiPersonCache(count) else null
            
            ApiPerson(
                id = id,
                name = name,
                missingThumbnail = missingThumbnail,
                isFavourite = isFavourite,
                cache = cache
            )
        }
    }
}

fun getAlbums(serverUrl: String, cookies: String, apiPrefix: String): List<ApiAlbum> {
        val sanitizedUrl = serverUrl.trimEnd('/')
        val endpoint = "$sanitizedUrl$apiPrefix/albums"

        val builder = Request.Builder()
            .url(endpoint)
            .get()

        if (cookies.isNotEmpty()) {
            builder.addHeader("Cookie", cookies)
        }

        val request = builder.build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: throw IOException("Empty response from albums endpoint")
            
            if (!response.isSuccessful) {
                throw IOException("Server returned error code: ${response.code}")
            }

            val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)
            val responseMap = try {
                mapAdapter.fromJson(bodyString)
            } catch (e: Exception) {
                throw IOException("Failed to parse server response as JSON: ${e.message}")
            }
            
            val errorObj = responseMap?.get("error")
            if (errorObj != null) {
                throw IOException("Server returned error: $errorObj")
            }

            val resultList = responseMap?.get("result") as? List<*> ?: return emptyList()
            return resultList.mapNotNull { item ->
                val albumMap = item as? Map<*, *> ?: return@mapNotNull null
                val id = (albumMap["id"] as? Number)?.toInt() ?: return@mapNotNull null
                val name = (albumMap["name"] as? String) ?: ""
                val locked = (albumMap["locked"] as? Boolean) ?: false
                
                @Suppress("UNCHECKED_CAST")
                val searchQuery = albumMap["searchQuery"] as? Map<String, Any?>
                
                val cacheMap = albumMap["cache"] as? Map<*, *>
                val cache = if (cacheMap != null) {
                    val itemCount = (cacheMap["itemCount"] as? Number)?.toInt() ?: 0
                    val oldestMedia = (cacheMap["oldestMedia"] as? Number)?.toLong()
                    val youngestMedia = (cacheMap["youngestMedia"] as? Number)?.toLong()
                    
                    val coverMap = cacheMap["cover"] as? Map<*, *>
                    val coverName = coverMap?.get("name") as? String
                    val coverDirMap = coverMap?.get("directory") as? Map<*, *>
                    val coverDirName = coverDirMap?.get("name") as? String ?: ""
                    val coverDirPath = coverDirMap?.get("path") as? String ?: ""
                    val coverDirectory = if (coverDirPath.isEmpty()) coverDirName else if (coverDirName.isEmpty()) coverDirPath else "$coverDirPath/$coverDirName"
                    val coverDirCleaned = coverDirectory.replace("./", "").trim('/')
                    
                    ApiAlbumCache(itemCount, oldestMedia, youngestMedia, coverName, coverDirCleaned)
                } else {
                    null
                }
                
                ApiAlbum(id, name, locked, searchQuery, cache)
            }
        }
    }

    /**
     * Attempts to update a person's favourite status on the PiGallery2 server.
     * Uses a multi-tiered fallback strategy (PATCH, POST, PUT) across different path forms
     * to guarantee synchronization compatibility.
     */
    fun updatePersonFavourite(serverUrl: String, personName: String, isFavourite: Boolean, cookies: String, apiPrefix: String): Boolean {
        val sanitizedUrl = serverUrl.trimEnd('/')
        val encodedName = URLEncoder.encode(personName, "UTF-8").replace("+", "%20")
        
        val jsonBody = """
            {
              "isFavourite": $isFavourite
            }
        """.trimIndent()
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        // 1. Path variant: api/person/{name}
        val urlWithName = "$sanitizedUrl$apiPrefix/person/$encodedName"
        
        // Try PATCH on api/person/{name}
        val patchReq1 = Request.Builder()
            .url(urlWithName)
            .patch(requestBody)
            .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
            .build()
        try {
            client.newCall(patchReq1).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("PiGalleryApi", "Successfully synced favorite status via PATCH on api/person/{name}")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.d("PiGalleryApi", "PATCH api/person/{name} failed: ${e.message}")
        }

        // Try POST on api/person/{name}
        val postReq1 = Request.Builder()
            .url(urlWithName)
            .post(requestBody)
            .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
            .build()
        try {
            client.newCall(postReq1).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("PiGalleryApi", "Successfully synced favorite status via POST on api/person/{name}")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.d("PiGalleryApi", "POST api/person/{name} failed: ${e.message}")
        }

        // Try PUT on api/person/{name}
        val putReq1 = Request.Builder()
            .url(urlWithName)
            .put(requestBody)
            .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
            .build()
        try {
            client.newCall(putReq1).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("PiGalleryApi", "Successfully synced favorite status via PUT on api/person/{name}")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.d("PiGalleryApi", "PUT api/person/{name} failed: ${e.message}")
        }

        // 2. Path variant: api/person
        val urlRoot = "$sanitizedUrl$apiPrefix/person"
        val rootJsonBody = """
            {
              "name": "$personName",
              "isFavourite": $isFavourite
            }
        """.trimIndent()
        val rootRequestBody = rootJsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        // Try PATCH on api/person
        val patchReq2 = Request.Builder()
            .url(urlRoot)
            .patch(rootRequestBody)
            .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
            .build()
        try {
            client.newCall(patchReq2).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("PiGalleryApi", "Successfully synced favorite status via PATCH on api/person")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.d("PiGalleryApi", "PATCH api/person failed: ${e.message}")
        }

        // Try POST on api/person
        val postReq2 = Request.Builder()
            .url(urlRoot)
            .post(rootRequestBody)
            .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
            .build()
        try {
            client.newCall(postReq2).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("PiGalleryApi", "Successfully synced favorite status via POST on api/person")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.d("PiGalleryApi", "POST api/person failed: ${e.message}")
        }

        // Try PUT on api/person
        val putReq2 = Request.Builder()
            .url(urlRoot)
            .put(rootRequestBody)
            .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
            .build()
        try {
            client.newCall(putReq2).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("PiGalleryApi", "Successfully synced favorite status via PUT on api/person")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.d("PiGalleryApi", "PUT api/person failed: ${e.message}")
        }

        Log.e("PiGalleryApi", "All attempts to sync favorite status with backend failed.")
        return false
    }
}
