@file:Suppress("UNCHECKED_CAST")
package com.example.data

import android.util.Log
import com.example.util.AppImageLoaderFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class UnauthorizedSessionException(
    message: String = "Unauthorized. Session might have expired."
) : IOException(message)

// Moshi Models
data class LoginResult(
    val username: String?,
    val role: Int?,
    val csrfToken: String?
)

object UserRoles {
    const val LIMITED_GUEST = 1
    const val GUEST = 2
    const val USER = 3
    const val ADMIN = 4
    const val DEVELOPER = 5

    fun fromValue(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt()
            is String -> when (value.trim().lowercase()) {
                "limitedguest", "limited_guest", "limited guest" -> LIMITED_GUEST
                "guest" -> GUEST
                "user" -> USER
                "admin" -> ADMIN
                "developer", "dev" -> DEVELOPER
                else -> value.trim().toIntOrNull()
            }
            else -> null
        }
    }

    fun canManageAlbums(
        role: Int,
        albumWriteMinRole: Int = ADMIN,
        albumFeatureEnabled: Boolean = true,
        userCanCreateAlbumsOverride: Int = -1
    ): Boolean {
        if (!albumFeatureEnabled) return false
        if (role < LIMITED_GUEST) return false
        if (role >= ADMIN) return true
        return when (userCanCreateAlbumsOverride) {
            1 -> true
            0 -> false
            else -> role >= albumWriteMinRole
        }
    }

    fun canCreateShareLinks(
        role: Int,
        sharingMinRole: Int = USER,
        sharingFeatureEnabled: Boolean = true
    ): Boolean {
        if (!sharingFeatureEnabled) return false
        if (role < USER) return false
        return role >= sharingMinRole
    }
}

data class ApiDirectory(
    val id: Int?,
    val name: String,
    val path: String,
    val directories: List<ApiSubFolder>?,
    val media: List<ApiMedia>?,
    val resultOverflow: Boolean = false
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

    val isLivePhoto: Boolean
        get() = !isVideo && (
            metadata?.isLivePhoto == true ||
            !metadata?.liveVideoPath.isNullOrBlank() ||
            name.endsWith(".live", ignoreCase = true)
        )
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

data class ApiGPSData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null
)

data class ApiMediaMetadata(
    val size: ApiSize? = null,
    val creationDate: Long? = null,
    val creationDateOffset: String? = null,
    val cameraData: ApiCameraData? = null,
    val keywords: List<String>? = null,
    val faces: List<ApiFace>? = null,
    val duration: Double? = null,
    val gps: ApiGPSData? = null,
    val fileSize: Long? = null,
    val isLivePhoto: Boolean? = null,
    val liveVideoPath: String? = null
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

enum class ShareTargetKind {
    DIRECTORY,
    SEARCH,
    ALBUM,
    PERSON
}

sealed class ShareTarget {
    data class Directory(val path: String) : ShareTarget()
    data class Search(
        val label: String,
        val searchQuery: Map<String, Any?>,
        val kind: ShareTargetKind
    ) : ShareTarget()
}

data class ApiShareLink(
    val id: Int? = null,
    val sharingKey: String,
    val path: String,
    val shareUrl: String? = null,
    val searchQuery: Map<String, Any?>? = null,
    val hasPassword: Boolean = false,
    val expires: Long = -1L,
    val includeSubfolders: Boolean = true,
    val preventDownload: Boolean = false,
    val timeStamp: Long? = null,
    val creatorName: String? = null
)

class PiGalleryApi(private val context: android.content.Context) {
    companion object {
        fun ensureHttpUrl(url: String): String {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return ""
            val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                trimmed
            } else {
                "http://$trimmed"
            }
            return withScheme.trimEnd('/')
        }

        fun cleanServerPath(path: String?): String {
            if (path.isNullOrBlank()) return ""
            return path.replace('\\', '/').trim().trim('/').let {
                if (it == "." || it == "./") "" else it.removePrefix("./").trim('/')
            }
        }
    }

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

        val offsetNumber = (metaMap["o"] as? Number)?.toInt()
        val creationDateOffset = if (offsetNumber != null) {
            val sign = if (offsetNumber < 0) "-" else "+"
            val absOffset = Math.abs(offsetNumber)
            val hours = absOffset / 60
            val minutes = absOffset % 60
            String.format(java.util.Locale.US, "%s%02d:%02d", sign, hours, minutes)
        } else {
            metaMap["creationDateOffset"] as? String
        }

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

        val gps = extractGpsFromMetadata(metaMap)

        val fileSize = (metaMap["fileSize"] as? Number ?: metaMap["file_size"] as? Number ?: metaMap["s"] as? Number ?: metaMap["fs"] as? Number)?.toLong()

        val isLivePhoto = (metaMap["isLivePhoto"] as? Boolean)
            ?: (metaMap["livePhoto"] as? Boolean)
            ?: (metaMap["lp"] as? Boolean)
            ?: (metaMap["live"] as? Boolean)
            ?: (metaMap["motionPhoto"] as? Boolean)
        val liveVideoPath = (metaMap["liveVideoPath"] as? String)
            ?: (metaMap["livePhotoVideo"] as? String)
            ?: (metaMap["liveVideo"] as? String)
            ?: (metaMap["pairedVideo"] as? String)
            ?: (metaMap["companion"] as? String)
            ?: (metaMap["contentIdentifier"] as? String)

        return ApiMediaMetadata(size, creationDate, creationDateOffset, cameraData, keywords, faces, duration, gps, fileSize, isLivePhoto, liveVideoPath)
    }

    private fun toFiniteDouble(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble().takeIf { it.isFinite() }
            is String -> value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
            else -> null
        }
    }

    private fun extractGpsFromRaw(raw: Any?): ApiGPSData? {
        return when (raw) {
            is Map<*, *> -> {
                val nested = raw["GPSData"] ?: raw["gpsData"] ?: raw["GPS"] ?: raw["gps"] ?: raw["g"] ?: raw["coords"]
                if (nested != null) {
                    extractGpsFromRaw(nested)?.let { return it }
                }
                val lat = toFiniteDouble(raw["latitude"] ?: raw["lat"] ?: raw["la"])
                val lon = toFiniteDouble(raw["longitude"] ?: raw["lng"] ?: raw["lon"] ?: raw["long"] ?: raw["lo"])
                val alt = toFiniteDouble(raw["altitude"] ?: raw["alt"] ?: raw["al"])
                if (lat != null && lon != null) ApiGPSData(lat, lon, alt) else null
            }
            is List<*> -> {
                val lat = toFiniteDouble(raw.getOrNull(0))
                val lon = toFiniteDouble(raw.getOrNull(1))
                val alt = toFiniteDouble(raw.getOrNull(2))
                if (lat != null && lon != null) ApiGPSData(lat, lon, alt) else null
            }
            else -> null
        }
    }

    private fun extractGpsFromMetadata(metaMap: Map<*, *>): ApiGPSData? {
        return extractGpsFromRaw(metaMap["positionData"])
            ?: extractGpsFromRaw(metaMap["p"])
            ?: extractGpsFromRaw(metaMap["GPSData"])
            ?: extractGpsFromRaw(metaMap["gpsData"])
            ?: extractGpsFromRaw(metaMap["GPS"])
            ?: extractGpsFromRaw(metaMap["gps"])
            ?: extractGpsFromRaw(metaMap["g"])
            ?: run {
                val lat = toFiniteDouble(metaMap["latitude"])
                val lon = toFiniteDouble(metaMap["longitude"])
                val alt = toFiniteDouble(metaMap["altitude"])
                if (lat != null && lon != null) ApiGPSData(lat, lon, alt) else null
            }
    }

    private val prefs = PreferencesManager(context)
    private var currentAllowInsecureSsl: Boolean = prefs.allowInsecureSsl
    var client: OkHttpClient

    init {
        client = buildHttpClient(currentAllowInsecureSsl)
    }

    private fun buildHttpClient(allowInsecure: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            
        try {
            val cacheDir = context.cacheDir
            if (cacheDir != null) {
                builder.cache(Cache(File(cacheDir, "http_cache"), 50L * 1024L * 1024L)) // 50MB
            }
        } catch (e: Exception) {
            // Ignore cache error
        }
            
        if (allowInsecure) {
            AppImageLoaderFactory.applyInsecureSsl(builder)
        }

        return builder.build()
    }

    fun updateClientSsl(allowInsecure: Boolean) {
        if (allowInsecure == currentAllowInsecureSsl) return
        currentAllowInsecureSsl = allowInsecure
        val existingInterceptors = client.interceptors
        val existingNetworkInterceptors = client.networkInterceptors
        val builder = buildHttpClient(allowInsecure).newBuilder()
        existingInterceptors.forEach { builder.addInterceptor(it) }
        existingNetworkInterceptors.forEach { builder.addNetworkInterceptor(it) }
        client = builder.build()
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

    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    @Volatile
    private var cachedShareServiceKey: Pair<OkHttpClient, String>? = null
    @Volatile
    private var cachedShareService: ShareApiService? = null
    internal var shareServiceOverride: ShareApiService? = null

    internal fun getShareApiService(serverUrl: String): ShareApiService {
        shareServiceOverride?.let { return it }
        val normalizedBase = ensureHttpUrl(serverUrl)
        val key = Pair(client, normalizedBase)
        val existing = cachedShareService
        if (existing != null && cachedShareServiceKey == key) {
            return existing
        }
        val created = ShareRetrofitFactory.create(normalizedBase, client, moshi)
        cachedShareServiceKey = key
        cachedShareService = created
        return created
    }

    private fun buildShareHeaders(cookies: String, csrfToken: String = ""): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        if (cookies.isNotEmpty()) headers["Cookie"] = cookies
        if (csrfToken.isNotEmpty()) headers["CSRF-Token"] = csrfToken
        return headers
    }

    private fun readRetrofitBody(response: retrofit2.Response<okhttp3.ResponseBody>): String {
        return if (response.isSuccessful) {
            response.body()?.use { it.string() }.orEmpty()
        } else {
            response.errorBody()?.use { it.string() }.orEmpty()
        }
    }

    private fun extractSessionCookies(cookieHeaders: List<String>): String {
        val pairs = cookieHeaders.mapNotNull { header ->
            val nameValue = header.substringBefore(";").trim()
            val eqIdx = nameValue.indexOf('=')
            if (eqIdx <= 0) return@mapNotNull null
            val name = nameValue.substring(0, eqIdx).trim()
            val value = nameValue.substring(eqIdx + 1).trim()
            if (name.isEmpty() || value.isEmpty()) null else "$name=$value"
        }
        val preferred = pairs.filter {
            val lower = it.lowercase()
            lower.startsWith("pigallery") || lower.startsWith("connect.sid=")
        }
        return (if (preferred.isNotEmpty()) preferred else pairs).joinToString("; ")
    }

    private fun encodeUrlPath(path: String): String {
        val cleaned = cleanServerPath(path)
        if (cleaned.isEmpty()) return ""
        return cleaned.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    }

    private fun isHtmlResponse(body: String): Boolean {
        val trimmed = body.trimStart()
        return trimmed.startsWith("<")
    }

    private fun extractErrorMessage(bodyString: String): String? {
        return try {
            val errorObj = mapAdapter.fromJson(bodyString)?.get("error") ?: return null
            when (errorObj) {
                is Map<*, *> -> (errorObj["message"] ?: errorObj["details"] ?: errorObj["code"])
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: errorObj.toString()
                else -> errorObj.toString().takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Performs a login request on the PiGallery2 server.
     * Returns the cookie string and detected prefix if successful, throws exception on failure.
     */
    fun login(serverUrl: String, username: String, password: String): Pair<String, String> {
        val sanitizedUrl = serverUrl.trimEnd('/')
        // Modern PiGallery2 (v2/v3+) uses "/pgapi" by default; keep "/api" as fallback
        val prefixes = listOf("/pgapi", "/api")
        var lastException: Exception? = null

        // 1. Try normal POST login for each prefix (if username is not blank)
        if (username.isNotBlank()) {
            val loginPayload = mapOf(
                "loginCredential" to mapOf(
                    "username" to username,
                    "password" to password,
                    "rememberMe" to true
                )
            )
            val jsonBody = mapAdapter.toJson(loginPayload)

            for (prefix in prefixes) {
                val endpoint = "$sanitizedUrl$prefix/user/login"
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

                        if (isHtmlResponse(bodyString)) {
                            throw IOException("Server returned HTML instead of JSON on $prefix/user/login")
                        }

                        val responseMap = try {
                            mapAdapter.fromJson(bodyString)
                        } catch (e: Exception) {
                            throw IOException("Invalid JSON response from server")
                        } ?: throw IOException("Invalid JSON response from server")

                        val errorObj = responseMap["error"]
                        if (errorObj != null) {
                            val errMsg = (errorObj as? Map<*, *>)?.get("message")?.toString() ?: errorObj.toString()
                            throw IOException(errMsg)
                        }
                        val resultMap = responseMap["result"] as? Map<*, *>
                            ?: throw IOException("Login response missing result object")

                        prefs.csrfToken = ""
                        prefs.userRole = -1
                        prefs.userCanCreateAlbumsOverride = -1
                        applyUserSessionMap(resultMap)

                        val filteredCookies = extractSessionCookies(response.headers("Set-Cookie"))
                        if (filteredCookies.isEmpty()) {
                            throw IOException("No session cookie returned by the server. Check your configuration.")
                        }

                        if (prefs.userRole == -1 || prefs.csrfToken.isBlank()) {
                            try {
                                fetchCurrentUserSession(sanitizedUrl, filteredCookies, prefix)
                            } catch (_: Exception) {
                                // Best-effort session role/CSRF refresh
                            }
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
                        if (isHtmlResponse(bodyString)) return@use
                        val responseMap = try {
                            mapAdapter.fromJson(bodyString)
                        } catch (e: Exception) {
                            null
                        }

                        val result = responseMap?.get("result") as? Map<String, Any?>
                        val directory = result?.get("directory") as? Map<String, Any?>

                        if (directory != null) {
                            prefs.csrfToken = ""
                            prefs.userRole = -1
                            prefs.userCanCreateAlbumsOverride = -1
                            val filteredCookies = extractSessionCookies(response.headers("Set-Cookie"))
                            try {
                                fetchCurrentUserSession(sanitizedUrl, filteredCookies, prefix)
                            } catch (_: Exception) {
                                // Keep role unknown if /user/me cannot be fetched
                            }
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

    private fun applyUserSessionMap(resultMap: Map<*, *>): LoginResult {
        val nestedUser = resultMap["user"] as? Map<*, *>
        val userObj = nestedUser ?: resultMap

        val username = (userObj["username"] as? String) ?: (userObj["name"] as? String)
        val role = UserRoles.fromValue(userObj["role"])
            ?: UserRoles.fromValue(resultMap["role"])
        val csrfToken = (resultMap["csrfToken"] as? String)
            ?: (userObj["csrfToken"] as? String)

        if (role != null) {
            prefs.userRole = role
        }
        if (!csrfToken.isNullOrBlank()) {
            prefs.csrfToken = csrfToken
        }

        var overrideFound: Int? = null
        val nestedPermissions = (userObj["permissions"] as? Map<*, *>)
            ?: (resultMap["permissions"] as? Map<*, *>)
        for (map in listOfNotNull(nestedPermissions, nestedUser, resultMap)) {
            for ((k, v) in map) {
                val key = k?.toString()?.lowercase() ?: continue
                if ((key.contains("album") || key.contains("savedsearch") || key.contains("savesearch")) &&
                    (key.contains("allow") || key.contains("create") || key.contains("save") ||
                        key.contains("write") || key.contains("manage") || key.contains("edit") || key.contains("can")) &&
                    v is Boolean
                ) {
                    overrideFound = if (v) 1 else 0
                    break
                }
            }
            if (overrideFound != null) break
        }
        if (overrideFound != null) {
            prefs.userCanCreateAlbumsOverride = overrideFound
        }

        return LoginResult(username = username, role = role, csrfToken = csrfToken)
    }

    /**
     * Fetches the current authenticated/unauthenticated session info from /user/me
     * and client album permissions from /settings or HTML ServerInject.
     */
    fun fetchCurrentUserSession(
        serverUrl: String,
        cookies: String,
        apiPrefix: String,
        throwOnUnauthorized: Boolean = false
    ): LoginResult? {
        val sanitizedUrl = serverUrl.trimEnd('/')
        if (sanitizedUrl.isEmpty()) return null
        val endpoint = "$sanitizedUrl$apiPrefix/user/me"

        val builder = Request.Builder()
            .url(endpoint)
            .get()
        if (cookies.isNotEmpty()) {
            builder.addHeader("Cookie", cookies)
        }

        var loginResult: LoginResult? = null
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 401) {
                prefs.userRole = -1
                prefs.userCanCreateAlbumsOverride = -1
                if (throwOnUnauthorized) {
                    throw UnauthorizedSessionException("Unauthorized. Session expired.")
                }
                return null
            }
            if (response.code == 403) {
                if (prefs.userRole < UserRoles.LIMITED_GUEST) {
                    prefs.userRole = -1
                    prefs.userCanCreateAlbumsOverride = -1
                }
                return null
            }
            if (response.isSuccessful) {
                val bodyString = response.body?.string() ?: ""
                if (bodyString.isNotBlank() && !isHtmlResponse(bodyString)) {
                    val responseMap = try {
                        mapAdapter.fromJson(bodyString)
                    } catch (_: Exception) {
                        null
                    }
                    if (responseMap != null && responseMap["error"] == null) {
                        val resultMap = responseMap["result"] as? Map<*, *>
                        if (resultMap != null) {
                            loginResult = applyUserSessionMap(resultMap)
                        }
                    }
                }
            }
        }

        val configLoaded = try {
            fetchClientAlbumConfig(sanitizedUrl, cookies, apiPrefix)
        } catch (_: Exception) {
            false
        }
        if (!configLoaded && !prefs.sharingConfigLoaded && prefs.userRole >= UserRoles.USER) {
            probeSharingSupport(sanitizedUrl, cookies, apiPrefix)
        }
        return loginResult
    }

    /**
     * Fallback probe used only when the server does not expose ClientConfig (/settings or index.html).
     * Confirms whether the share endpoint is supported and enabled, hiding the UI item on 403/404/405.
     */
    internal fun probeSharingSupport(serverUrl: String, cookies: String, apiPrefix: String) {
        val sanitizedUrl = serverUrl.trimEnd('/')
        if (sanitizedUrl.isEmpty()) return
        try {
            val headers = buildShareHeaders(cookies = cookies)
            val service = getShareApiService(sanitizedUrl)
            val response = service.getShareLinks("$sanitizedUrl$apiPrefix/share/list", headers).execute()
            val bodyString = readRetrofitBody(response)
            when {
                response.code() == 403 -> {
                    prefs.sharingConfigLoaded = true
                    prefs.sharingSupportedByServer = true
                    prefs.sharingFeatureEnabled = false
                }
                response.code() == 404 || response.code() == 405 -> {
                    prefs.sharingConfigLoaded = true
                    prefs.sharingSupportedByServer = false
                    prefs.sharingFeatureEnabled = false
                }
                response.isSuccessful && !isHtmlResponse(bodyString) -> {
                    val parsed = if (bodyString.isBlank() || response.code() == 204) {
                        null
                    } else {
                        try {
                            mapAdapter.fromJson(bodyString)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (parsed == null || parsed["error"] == null) {
                        prefs.sharingConfigLoaded = true
                        prefs.sharingSupportedByServer = true
                        prefs.sharingFeatureEnabled = true
                    }
                }
            }
        } catch (_: Exception) {
            // Keep fail-closed state on transient network failure
        }
    }

    /**
     * Fetches PiGallery2 ClientConfig from /$apiPrefix/settings or index.html ServerInject
     * to determine whether the Admin enabled album creation / saved searches for normal users (PR #1400).
     */
    fun fetchClientAlbumConfig(serverUrl: String, cookies: String, apiPrefix: String): Boolean {
        val sanitizedUrl = serverUrl.trimEnd('/')
        if (sanitizedUrl.isEmpty()) return false

        // 1. Try JSON ClientConfig endpoint: GET $apiPrefix/settings
        try {
            val settingsBuilder = Request.Builder()
                .url("$sanitizedUrl$apiPrefix/settings")
                .get()
            if (cookies.isNotEmpty()) {
                settingsBuilder.addHeader("Cookie", cookies)
            }
            client.newCall(settingsBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    if (bodyString.isNotBlank() && !isHtmlResponse(bodyString)) {
                        val responseMap = try {
                            mapAdapter.fromJson(bodyString)
                        } catch (_: Exception) {
                            null
                        }
                        if (responseMap != null && responseMap["error"] == null) {
                            val candidate = (responseMap["result"] as? Map<*, *>) ?: responseMap
                            if (applyClientConfigMap(candidate)) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to HTML ServerInject lookup
        }

        // 2. Fallback: extract ServerInject.ConfigInject (and user if needed) from index.html
        try {
            val htmlBuilder = Request.Builder()
                .url("$sanitizedUrl/")
                .get()
            if (cookies.isNotEmpty()) {
                htmlBuilder.addHeader("Cookie", cookies)
            }
            client.newCall(htmlBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return false
                val htmlBody = response.body?.string() ?: return false

                extractEmbeddedJsonObject(htmlBody, "user")?.let { userJson ->
                    val userMap = try {
                        mapAdapter.fromJson(userJson)
                    } catch (_: Exception) {
                        null
                    }
                    if (userMap != null) {
                        applyUserSessionMap(userMap)
                    }
                }

                val configJson = extractEmbeddedJsonObject(htmlBody, "ConfigInject") ?: return false
                val configMap = try {
                    mapAdapter.fromJson(configJson)
                } catch (_: Exception) {
                    null
                } ?: return false

                return applyClientConfigMap(configMap)
            }
        } catch (_: Exception) {
            return false
        }
    }

    private fun extractEmbeddedJsonObject(html: String, keyName: String): String? {
        val pattern = Regex("""\b${Regex.escape(keyName)}\s*[:=]\s*""")
        val match = pattern.find(html) ?: return null
        var idx = match.range.last + 1
        while (idx < html.length && html[idx].isWhitespace()) {
            idx++
        }
        if (idx >= html.length || html[idx] != '{') return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in idx until html.length) {
            val ch = html[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (inString) {
                if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return html.substring(idx, i + 1)
                    }
                }
            }
        }
        return null
    }

    internal fun applyClientConfigMap(configMap: Map<*, *>): Boolean {
        val effectiveMap = (configMap["Client"] as? Map<*, *>)
            ?: (configMap["client"] as? Map<*, *>)
            ?: configMap

        val recognizedConfigKeys = setOf(
            "album", "albums", "savedsearch", "savedsearches",
            "search", "users", "user", "faces", "sharing", "share",
            "gallery", "server", "client", "metafile", "media"
        )
        val hasKnownSection = configMap.keys.any { k ->
            val lower = k?.toString()?.lowercase() ?: ""
            lower in recognizedConfigKeys || lower.contains("album") || lower.contains("shar")
        } || effectiveMap.keys.any { k ->
            val lower = k?.toString()?.lowercase() ?: ""
            lower in recognizedConfigKeys || lower.contains("album") || lower.contains("shar")
        }
        if (!hasKnownSection) return false

        var parsedMinRole = UserRoles.USER
        var albumEnabled = true
        var foundExplicitMinRole = false
        var albumReadOnly = false

        var sharingEnabled = true
        var foundExplicitSharingEnabled = false
        var sharingPasswordProtected = prefs.sharingPasswordProtected
        var sharingMinRole = UserRoles.USER
        var foundSharingSection = false
        var authenticationRequired = true

        fun parseAlbumSection(section: Map<*, *>) {
            for ((k, v) in section) {
                val kLower = k?.toString()?.lowercase() ?: continue
                when {
                    kLower == "enabled" && v is Boolean -> {
                        albumEnabled = v
                    }
                    (kLower == "readonly" || kLower == "read_only") && v is Boolean -> {
                        if (v) {
                            albumReadOnly = true
                            parsedMinRole = UserRoles.ADMIN
                            foundExplicitMinRole = true
                        }
                    }
                    kLower.contains("role") && !kLower.contains("read") -> {
                        UserRoles.fromValue(v)?.let { roleVal ->
                            parsedMinRole = roleVal
                            foundExplicitMinRole = true
                        }
                    }
                    v is Boolean && (
                        kLower.contains("user") ||
                            kLower.contains("nonadmin") ||
                            kLower.contains("savedsearch") ||
                            (kLower.contains("allow") && (
                                kLower.contains("create") ||
                                    kLower.contains("save") ||
                                    kLower.contains("write") ||
                                    kLower.contains("edit") ||
                                    kLower.contains("add") ||
                                    kLower.contains("manage") ||
                                    kLower.contains("album")
                                ))
                        ) -> {
                        foundExplicitMinRole = true
                        parsedMinRole = if (v) UserRoles.USER else UserRoles.ADMIN
                    }
                }
            }
        }

        fun parseSharingSection(section: Map<*, *>) {
            foundSharingSection = true
            for ((k, v) in section) {
                val kLower = k?.toString()?.lowercase() ?: continue
                when {
                    kLower == "enabled" && v is Boolean -> {
                        sharingEnabled = v
                        foundExplicitSharingEnabled = true
                    }
                    (kLower == "passwordprotected" || kLower == "password_protected" || kLower == "password") && v is Boolean -> {
                        sharingPasswordProtected = v
                    }
                    kLower.contains("role") -> {
                        UserRoles.fromValue(v)?.let { roleVal ->
                            sharingMinRole = maxOf(UserRoles.USER, roleVal)
                        }
                    }
                }
            }
        }

        for (sourceMap in listOf(effectiveMap, configMap)) {
            for ((rawKey, rawValue) in sourceMap) {
                val key = rawKey?.toString()?.lowercase() ?: continue
                if ((key == "authenticationrequired" || key == "authentication_required") && rawValue is Boolean) {
                    authenticationRequired = rawValue
                    continue
                }
                val section = rawValue as? Map<*, *>
                when {
                    section != null && (key == "album" || key == "albums" || key == "savedsearch" || key == "savedsearches") -> {
                        parseAlbumSection(section)
                    }
                    section != null && (key == "sharing" || key == "share") -> {
                        parseSharingSection(section)
                    }
                    section != null && (key == "users" || key == "user" || key == "search") -> {
                        for ((subKeyRaw, subVal) in section) {
                            val subKey = subKeyRaw?.toString()?.lowercase() ?: continue
                            if ((subKey == "authenticationrequired" || subKey == "authentication_required") && subVal is Boolean) {
                                authenticationRequired = subVal
                                continue
                            }
                            if (subKey.contains("shar")) {
                                foundSharingSection = true
                                if (subKey.contains("role")) {
                                    UserRoles.fromValue(subVal)?.let { roleVal ->
                                        sharingMinRole = maxOf(UserRoles.USER, roleVal)
                                    }
                                } else if (subVal is Boolean && (subKey.contains("enable") || subKey.contains("allow"))) {
                                    sharingEnabled = subVal
                                    foundExplicitSharingEnabled = true
                                }
                                continue
                            }
                            if (!subKey.contains("album") && !subKey.contains("savedsearch") && !subKey.contains("savesearch")) {
                                continue
                            }
                            if (subKey.contains("role") && !subKey.contains("read")) {
                                UserRoles.fromValue(subVal)?.let { roleVal ->
                                    if (!foundExplicitMinRole) {
                                        parsedMinRole = roleVal
                                        foundExplicitMinRole = true
                                    }
                                }
                            } else if (subVal is Boolean && (
                                    subKey.contains("allow") ||
                                        subKey.contains("create") ||
                                        subKey.contains("save") ||
                                        subKey.contains("write") ||
                                        subKey.contains("manage") ||
                                        subKey.contains("edit")
                                    )
                            ) {
                                if (!foundExplicitMinRole) {
                                    parsedMinRole = if (subVal) UserRoles.USER else UserRoles.ADMIN
                                    foundExplicitMinRole = true
                                }
                            }
                        }
                    }
                }
            }
        }

        if (albumReadOnly) {
            parsedMinRole = maxOf(parsedMinRole, UserRoles.ADMIN)
        }
        prefs.albumFeatureEnabled = albumEnabled
        prefs.albumWriteMinRole = parsedMinRole
        prefs.sharingConfigLoaded = true
        prefs.sharingAuthenticationRequired = authenticationRequired
        if (foundSharingSection) {
            prefs.sharingSupportedByServer = true
            prefs.sharingFeatureEnabled = (if (foundExplicitSharingEnabled) sharingEnabled else true) && authenticationRequired
            prefs.sharingPasswordProtected = sharingPasswordProtected
            prefs.sharingMinRole = sharingMinRole
        } else {
            prefs.sharingSupportedByServer = false
            prefs.sharingFeatureEnabled = false
        }
        return true
    }

    private fun cleanJsonValue(value: Any?): Any? {
        return when (value) {
            is Double -> if (value % 1.0 == 0.0) value.toLong() else value
            is Map<*, *> -> {
                val result = mutableMapOf<String, Any?>()
                for ((k, v) in value) {
                    if (k != null) {
                        result[k.toString()] = cleanJsonValue(v)
                    }
                }
                result
            }
            is List<*> -> value.map { cleanJsonValue(it) }
            else -> value
        }
    }

    private fun canonicalizeJsonValue(value: Any?): Any? {
        return when (value) {
            is Double -> if (value % 1.0 == 0.0) value.toLong() else value
            is Map<*, *> -> {
                val sorted = java.util.TreeMap<String, Any?>()
                for ((k, v) in value) {
                    if (k != null) {
                        sorted[k.toString()] = canonicalizeJsonValue(v)
                    }
                }
                sorted
            }
            is List<*> -> value.map { canonicalizeJsonValue(it) }
            else -> value
        }
    }

    /**
     * Serializes search query map to JSON string.
     */
    fun serializeQuery(query: Map<String, Any?>): String {
        @Suppress("UNCHECKED_CAST")
        val cleanedQuery = cleanJsonValue(query) as Map<String, Any?>
        return mapAdapter.toJson(cleanedQuery)
    }

    internal fun serializeCanonicalQuery(query: Map<String, Any?>): String {
        @Suppress("UNCHECKED_CAST")
        val canonicalQuery = canonicalizeJsonValue(query) as Map<String, Any?>
        return mapAdapter.toJson(canonicalQuery)
    }

    /**
     * Fetches gallery directory contents (including subfolders and media files).
     */
    fun getGalleryContent(serverUrl: String, relativePath: String, cookies: String, apiPrefix: String): ApiDirectory {
        val sanitizedUrl = serverUrl.trimEnd('/')
        val encodedPath = encodeUrlPath(relativePath)
        val endpoint = "$sanitizedUrl$apiPrefix/gallery/content/$encodedPath"

        val builder = Request.Builder()
            .url(endpoint)
            .get()

        if (cookies.isNotEmpty()) {
            builder.addHeader("Cookie", cookies)
        }

        val request = builder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    throw UnauthorizedSessionException("Unauthorized. Session might have expired.")
                }
                throw IOException("Server returned error code: ${response.code}")
            }
            val bodyString = response.body?.string() ?: throw IOException("Empty response from gallery content endpoint")
            if (isHtmlResponse(bodyString)) {
                throw IOException("Server returned HTML instead of JSON. Check your API prefix or session.")
            }

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

        val cleanParent = cleanServerPath(path)
        val cleanName = cleanServerPath(name)

        val directoryFullPath = when {
            cleanParent.isEmpty() -> cleanName
            cleanName.isEmpty() -> cleanParent
            else -> "$cleanParent/$cleanName"
        }

        val directoriesList = map["directories"] as? List<*>
        val directories = directoriesList?.mapNotNull { item ->
            val dirMap = item as? Map<*, *> ?: return@mapNotNull null
            parseSubFolder(dirMap, directoryFullPath)
        } ?: emptyList()

        val mediaList = map["media"] as? List<*>
        val media = mediaList?.mapNotNull { item ->
            val mediaMap = item as? Map<*, *> ?: return@mapNotNull null
            val medName = (mediaMap["name"] as? String) ?: (mediaMap["n"] as? String) ?: ""
            val medId = (mediaMap["id"] as? Number)?.toInt() ?: ("$directoryFullPath/$medName").hashCode()
            val medFileSize = (mediaMap["fileSize"] as? Number ?: mediaMap["file_size"] as? Number ?: mediaMap["s"] as? Number ?: mediaMap["fs"] as? Number)?.toLong()

            val metaMap = (mediaMap["metadata"] as? Map<*, *>) ?: (mediaMap["m"] as? Map<*, *>)
            val metadata = if (metaMap != null) {
                val parsed = parseMetadata(metaMap, cwMap)
                if (parsed.fileSize == null && medFileSize != null) parsed.copy(fileSize = medFileSize) else parsed
            } else if (medFileSize != null) {
                ApiMediaMetadata(fileSize = medFileSize)
            } else {
                null
            }

            ApiMedia(medId, medName, metadata, directoryFullPath)
        } ?: emptyList()

        val pairedMedia = LivePhotoHelper.pairLivePhotos(media)
        return ApiDirectory(id, name, directoryFullPath, directories, pairedMedia)
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
            if (response.code == 204) {
                return ApiDirectory(-1, "Search Results", "", emptyList(), emptyList())
            }
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    throw UnauthorizedSessionException("Unauthorized. Session might have expired.")
                }
                throw IOException("Server returned error code: ${response.code}")
            }
            val bodyString = response.body?.string() ?: throw IOException("Empty response from search endpoint")
            if (isHtmlResponse(bodyString)) {
                throw IOException("Server returned HTML instead of JSON. Check your API prefix or session.")
            }

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
        val searchResult = result["searchResult"] as? Map<String, Any?>
            ?: throw IOException("No searchResult found in response")
        val mapObj = (result["map"] as? Map<String, Any?>)
            ?: (searchResult["map"] as? Map<String, Any?>)
        val mapDirectories = mapObj?.get("directories") as? List<*>

        val mediaList = searchResult["media"] as? List<*>
        val media = mediaList?.mapNotNull { item ->
            val mediaMap = item as? Map<*, *> ?: return@mapNotNull null
            val medName = (mediaMap["name"] as? String) ?: (mediaMap["n"] as? String) ?: ""

            // Find parent path from map directories index, directory object, or direct string
            val dirStr = (mediaMap["directory"] as? String)
                ?: (mediaMap["dir"] as? String)
                ?: (mediaMap["parentPath"] as? String)
                ?: (mediaMap["path"] as? String)
                ?: (mediaMap["p"] as? String)

            val dirObj = mediaMap["directory"] as? Map<*, *> ?: mediaMap["dir"] as? Map<*, *>
            val dirNameFromObj = cleanServerPath((dirObj?.get("name") as? String) ?: (dirObj?.get("n") as? String))
            val dirPathFromObj = cleanServerPath((dirObj?.get("path") as? String) ?: (dirObj?.get("p") as? String))
            val pathFromObj = when {
                dirPathFromObj.isEmpty() -> dirNameFromObj
                dirNameFromObj.isEmpty() -> dirPathFromObj
                else -> "$dirPathFromObj/$dirNameFromObj"
            }

            val dIndex = (mediaMap["d"] as? Number)?.toInt()

            val parentPath = if (!dirStr.isNullOrBlank()) {
                cleanServerPath(dirStr)
            } else if (pathFromObj.isNotBlank()) {
                pathFromObj
            } else if (dIndex != null && mapDirectories != null && dIndex in mapDirectories.indices) {
                when (val dirEntry = mapDirectories[dIndex]) {
                    is String -> cleanServerPath(dirEntry)
                    is Map<*, *> -> {
                        val dirName = cleanServerPath((dirEntry["name"] as? String) ?: (dirEntry["n"] as? String))
                        val dirPath = cleanServerPath((dirEntry["path"] as? String) ?: (dirEntry["p"] as? String))
                        when {
                            dirPath.isEmpty() -> dirName
                            dirName.isEmpty() -> dirPath
                            else -> "$dirPath/$dirName"
                        }
                    }
                    else -> ""
                }
            } else {
                ""
            }
            val medId = (mediaMap["id"] as? Number)?.toInt() ?: ("$parentPath/$medName").hashCode()
            val medFileSize = (mediaMap["fileSize"] as? Number ?: mediaMap["file_size"] as? Number ?: mediaMap["s"] as? Number ?: mediaMap["fs"] as? Number)?.toLong()

            val metaMap = (mediaMap["metadata"] as? Map<*, *>) ?: (mediaMap["m"] as? Map<*, *>)
            val fallbackGps = extractGpsFromRaw(mediaMap["positionData"] ?: mediaMap["gps"] ?: mediaMap["GPSData"])
            val metadata = if (metaMap != null) {
                val parsed = parseMetadata(metaMap, mapObj)
                val withSize = if (parsed.fileSize == null && medFileSize != null) parsed.copy(fileSize = medFileSize) else parsed
                if (withSize.gps == null && fallbackGps != null) withSize.copy(gps = fallbackGps) else withSize
            } else if (medFileSize != null || fallbackGps != null) {
                ApiMediaMetadata(fileSize = medFileSize, gps = fallbackGps)
            } else {
                null
            }

            ApiMedia(medId, medName, metadata, parentPath)
        } ?: emptyList()

        val directoriesList = searchResult["directories"] as? List<*>
        val directories = directoriesList?.mapNotNull { item ->
            val dirMap = item as? Map<*, *> ?: return@mapNotNull null
            parseSubFolder(dirMap, parentDirectoryPath = null)
        } ?: emptyList()

        val resultOverflow = (searchResult["resultOverflow"] as? Boolean) ?: false
        val pairedMedia = LivePhotoHelper.pairLivePhotos(media)
        return ApiDirectory(-1, "Search Results", "", directories, pairedMedia, resultOverflow = resultOverflow)
    }

    private fun parseSubFolder(dirMap: Map<*, *>, parentDirectoryPath: String?): ApiSubFolder {
        val subId = (dirMap["id"] as? Number)?.toInt()
        val subName = (dirMap["name"] as? String) ?: (dirMap["n"] as? String) ?: ""
        val subPath = (dirMap["path"] as? String) ?: (dirMap["p"] as? String) ?: ""

        val cache = dirMap["cache"] as? Map<*, *>
        val mediaCount = (dirMap["mediaCount"] as? Number)?.toInt()
            ?: (cache?.get("recursiveMediaCount") as? Number)?.toInt()
            ?: (cache?.get("mediaCount") as? Number)?.toInt()
            ?: 0

        val cleanSub = cleanServerPath(subPath)
        val cleanSubName = cleanServerPath(subName)
        val subFullPath = when {
            !parentDirectoryPath.isNullOrEmpty() -> if (cleanSubName.isNotEmpty()) "$parentDirectoryPath/$cleanSubName" else parentDirectoryPath
            cleanSub.isNotEmpty() -> if (cleanSubName.isNotEmpty()) "$cleanSub/$cleanSubName" else cleanSub
            else -> cleanSubName
        }

        return ApiSubFolder(subId, subName, subFullPath, mediaCount, parseSubFolderCache(cache))
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
            if (response.code == 204) {
                return emptyList()
            }
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    throw UnauthorizedSessionException("Unauthorized. Session might have expired.")
                }
                throw IOException("Server returned error code: ${response.code}")
            }

            val bodyString = response.body?.string() ?: throw IOException("Empty response from person endpoint")
            if (isHtmlResponse(bodyString)) {
                throw IOException("Server returned HTML instead of JSON. Check your API prefix or session.")
            }

            val responseMap = try {
                mapAdapter.fromJson(bodyString)
            } catch (e: Exception) {
                val shortBody = if (bodyString.length > 300) bodyString.take(300) + "..." else bodyString
                throw IOException("Failed to parse server response as JSON: ${e.message} | Body: $shortBody")
            }

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
            if (response.code == 204) {
                return emptyList()
            }
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    throw UnauthorizedSessionException("Unauthorized. Session might have expired.")
                }
                throw IOException("Server returned error code: ${response.code}")
            }

            val bodyString = response.body?.string() ?: throw IOException("Empty response from albums endpoint")
            if (isHtmlResponse(bodyString)) {
                throw IOException("Server returned HTML instead of JSON. Check your API prefix or session.")
            }

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
                    val coverName = (coverMap?.get("name") as? String) ?: (coverMap?.get("n") as? String)
                    val coverDirMap = (coverMap?.get("directory") as? Map<*, *>) ?: (coverMap?.get("d") as? Map<*, *>)
                    val coverDirName = cleanServerPath((coverDirMap?.get("name") as? String) ?: (coverDirMap?.get("n") as? String))
                    val coverDirPath = cleanServerPath((coverDirMap?.get("path") as? String) ?: (coverDirMap?.get("p") as? String))
                    val coverDirCleaned = when {
                        coverDirPath.isEmpty() -> coverDirName
                        coverDirName.isEmpty() -> coverDirPath
                        else -> "$coverDirPath/$coverDirName"
                    }

                    ApiAlbumCache(itemCount, oldestMedia, youngestMedia, coverName, coverDirCleaned)
                } else {
                    null
                }

                ApiAlbum(id, name, locked, searchQuery, cache)
            }
        }
    }

    /**
     * Saves a search query as an album on the PiGallery2 server.
     * Requires Admin role (UserRoles.ADMIN) on the server.
     */
    fun createSavedSearchAlbum(
        serverUrl: String,
        name: String,
        searchQuery: Map<String, Any?>,
        cookies: String,
        apiPrefix: String
    ): ApiAlbum? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw IOException("Album name cannot be empty.")
        }
        if (searchQuery.isEmpty()) {
            throw IOException("Search query cannot be empty.")
        }

        val sanitizedUrl = serverUrl.trimEnd('/')
        if (prefs.csrfToken.isBlank() && sanitizedUrl.isNotEmpty()) {
            try {
                fetchCurrentUserSession(sanitizedUrl, cookies, apiPrefix)
            } catch (_: Exception) {
                // Continue even if /user/me fails
            }
        }
        val csrfToken = prefs.csrfToken

        @Suppress("UNCHECKED_CAST")
        val cleanedQuery = cleanJsonValue(searchQuery) as Map<String, Any?>
        val jsonBody = mapAdapter.toJson(
            mapOf(
                "name" to trimmedName,
                "searchQuery" to cleanedQuery
            )
        )
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val attempts = listOf(
            "PUT" to "$sanitizedUrl$apiPrefix/albums/saved-searches",
            "PUT" to "$sanitizedUrl$apiPrefix/albums",
            "POST" to "$sanitizedUrl$apiPrefix/albums/saved-searches",
            "POST" to "$sanitizedUrl$apiPrefix/albums"
        )

        var lastError: IOException? = null
        for ((method, endpoint) in attempts) {
            val builder = Request.Builder().url(endpoint)
            if (cookies.isNotEmpty()) builder.addHeader("Cookie", cookies)
            if (csrfToken.isNotEmpty()) builder.addHeader("CSRF-Token", csrfToken)
            if (method == "PUT") builder.put(requestBody) else builder.post(requestBody)

            client.newCall(builder.build()).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()

                if (response.code == 401) {
                    throw UnauthorizedSessionException(extractErrorMessage(bodyString) ?: "Unauthorized. Session might have expired.")
                }

                if (response.code == 403) {
                    throw IOException(extractErrorMessage(bodyString) ?: "Not authorized to save albums.")
                }

                if (response.code == 404 || response.code == 405) {
                    lastError = IOException("Endpoint $method $endpoint not available (${response.code})")
                    return@use
                }

                if (!response.isSuccessful) {
                    throw IOException(extractErrorMessage(bodyString) ?: "Server returned error code: ${response.code}")
                }

                if (bodyString.isBlank() || response.code == 204) {
                    return null
                }

                if (isHtmlResponse(bodyString)) {
                    throw IOException("Server returned HTML instead of JSON. Check your API prefix or session.")
                }

                val responseMap = try {
                    mapAdapter.fromJson(bodyString)
                } catch (e: Exception) {
                    throw IOException("Failed to parse server response as JSON: ${e.message}")
                }

                val errorObj = responseMap?.get("error")
                if (errorObj != null) {
                    val msg = (errorObj as? Map<*, *>)?.get("message")?.toString() ?: errorObj.toString()
                    throw IOException(msg)
                }

                val albumMap = responseMap?.get("result") as? Map<*, *> ?: return null
                val id = (albumMap["id"] as? Number)?.toInt() ?: return null
                val albumName = (albumMap["name"] as? String) ?: trimmedName
                val locked = (albumMap["locked"] as? Boolean) ?: false
                @Suppress("UNCHECKED_CAST")
                val returnedQuery = albumMap["searchQuery"] as? Map<String, Any?>
                return ApiAlbum(id, albumName, locked, returnedQuery, null)
            }
        }

        throw lastError ?: IOException("Failed to save search as album.")
    }

    /**
     * Deletes a saved-search album by ID on the PiGallery2 server.
     * Requires permission to manage albums on the server.
     */
    fun deleteAlbum(
        serverUrl: String,
        albumId: Int,
        cookies: String,
        apiPrefix: String
    ) {
        val sanitizedUrl = serverUrl.trimEnd('/')
        if (prefs.csrfToken.isBlank() && sanitizedUrl.isNotEmpty()) {
            try {
                fetchCurrentUserSession(sanitizedUrl, cookies, apiPrefix)
            } catch (_: Exception) {
                // Continue even if /user/me fails
            }
        }
        val csrfToken = prefs.csrfToken

        val attempts = listOf(
            "$sanitizedUrl$apiPrefix/albums/$albumId",
            "$sanitizedUrl$apiPrefix/albums/saved-searches/$albumId"
        )

        var lastError: IOException? = null
        for (endpoint in attempts) {
            val builder = Request.Builder()
                .url(endpoint)
                .delete()
            if (cookies.isNotEmpty()) builder.addHeader("Cookie", cookies)
            if (csrfToken.isNotEmpty()) builder.addHeader("CSRF-Token", csrfToken)

            client.newCall(builder.build()).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()

                if (response.code == 401) {
                    throw UnauthorizedSessionException(extractErrorMessage(bodyString) ?: "Unauthorized. Session might have expired.")
                }

                if (response.code == 403) {
                    throw IOException(extractErrorMessage(bodyString) ?: "Not authorized to delete albums.")
                }

                if (response.code == 404 || response.code == 405) {
                    lastError = IOException("Endpoint DELETE $endpoint not available (${response.code})")
                    return@use
                }

                if (!response.isSuccessful) {
                    throw IOException(extractErrorMessage(bodyString) ?: "Server returned error code: ${response.code}")
                }

                if (bodyString.isBlank() || response.code == 204) {
                    return
                }

                if (isHtmlResponse(bodyString)) {
                    throw IOException("Server returned HTML instead of JSON. Check your API prefix or session.")
                }

                val responseMap = try {
                    mapAdapter.fromJson(bodyString)
                } catch (_: Exception) {
                    null
                }
                val errorObj = responseMap?.get("error")
                if (errorObj != null) {
                    val msg = (errorObj as? Map<*, *>)?.get("message")?.toString() ?: errorObj.toString()
                    throw IOException(msg)
                }
                return
            }
        }

        throw lastError ?: IOException("Failed to delete album.")
    }

    /**
     * Attempts to update a person's favourite status on the PiGallery2 server.
     * Uses a multi-tiered fallback strategy (PATCH, POST, PUT) across different path forms
     * to guarantee synchronization compatibility.
     */
    fun updatePersonFavourite(serverUrl: String, personName: String, isFavourite: Boolean, cookies: String, apiPrefix: String): Boolean {
        val sanitizedUrl = serverUrl.trimEnd('/')
        val encodedName = URLEncoder.encode(personName, "UTF-8").replace("+", "%20")
        val csrfToken = prefs.csrfToken
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        val nameBody = mapAdapter.toJson(mapOf("isFavourite" to isFavourite)).toRequestBody(jsonMediaType)
        val rootBody = mapAdapter.toJson(mapOf("name" to personName, "isFavourite" to isFavourite)).toRequestBody(jsonMediaType)

        val targets = listOf(
            "$sanitizedUrl$apiPrefix/person/$encodedName" to nameBody,
            "$sanitizedUrl$apiPrefix/person" to rootBody
        )
        val methods = listOf("PATCH", "POST", "PUT")

        for ((url, body) in targets) {
            for (method in methods) {
                val reqBuilder = Request.Builder().url(url).method(method, body)
                if (cookies.isNotEmpty()) reqBuilder.addHeader("Cookie", cookies)
                if (csrfToken.isNotEmpty()) reqBuilder.addHeader("CSRF-Token", csrfToken)
                try {
                    client.newCall(reqBuilder.build()).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d("PiGalleryApi", "Successfully synced favorite status via $method on $url")
                            return true
                        }
                    }
                } catch (e: Exception) {
                    Log.d("PiGalleryApi", "$method $url failed: ${e.message}")
                }
            }
        }

        Log.e("PiGalleryApi", "All attempts to sync favorite status with backend failed.")
        return false
    }

    internal fun parseShareLinkMap(
        map: Map<*, *>,
        fallbackPath: String = "",
        fallbackSearchQuery: Map<String, Any?>? = null,
        fallbackIncludeSubfolders: Boolean = true,
        fallbackHasPassword: Boolean = false,
        fallbackPreventDownload: Boolean = false
    ): ApiShareLink? {
        val key = (map["sharingKey"] as? String)?.trim()
            ?: (map["key"] as? String)?.trim()
            ?: return null
        if (key.isEmpty()) return null

        val rawPath = (map["path"] as? String) ?: fallbackPath
        val normalizedPath = cleanServerPath(rawPath)
        val serverShareUrl = (map["url"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: (map["shareUrl"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val parsedSearchQuery: Map<String, Any?>? = when (val rawQuery = map["searchQuery"]) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                cleanJsonValue(rawQuery) as? Map<String, Any?>
            }
            is String -> {
                if (rawQuery.isNotBlank()) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        cleanJsonValue(mapAdapter.fromJson(rawQuery)) as? Map<String, Any?>
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
            else -> null
        } ?: fallbackSearchQuery

        val rawPassword = map["password"]
        val hasPassword = when (rawPassword) {
            is Boolean -> rawPassword
            is String -> rawPassword.isNotBlank() && !rawPassword.equals("false", ignoreCase = true)
            else -> (map["passwordProtected"] as? Boolean) ?: fallbackHasPassword
        }
        val expires = (map["expires"] as? Number)?.toLong() ?: -1L
        val includeSubfolders = (map["includeSubfolders"] as? Boolean) ?: fallbackIncludeSubfolders
        val preventDownload = (map["preventDownload"] as? Boolean) ?: fallbackPreventDownload
        val timeStamp = (map["timeStamp"] as? Number)?.toLong()
        val id = (map["id"] as? Number)?.toInt()
        val creatorName = when (val creator = map["creator"]) {
            is Map<*, *> -> (creator["name"] as? String) ?: (creator["username"] as? String)
            is String -> creator
            else -> null
        }

        return ApiShareLink(
            id = id,
            sharingKey = key,
            path = normalizedPath,
            shareUrl = serverShareUrl,
            searchQuery = parsedSearchQuery,
            hasPassword = hasPassword,
            expires = expires,
            includeSubfolders = includeSubfolders,
            preventDownload = preventDownload,
            timeStamp = timeStamp,
            creatorName = creatorName
        )
    }

    /**
     * Checks whether a server share link corresponds to the given share target
     * (directory path or search/album/person SearchQueryDTO).
     */
    fun matchesShareTarget(share: ApiShareLink, target: ShareTarget): Boolean {
        return when (target) {
            is ShareTarget.Directory -> {
                val cleanTarget = cleanServerPath(target.path)
                val query = share.searchQuery
                if (query == null) {
                    cleanServerPath(share.path) == cleanTarget
                } else {
                    val queryType = (query["type"] as? Number)?.toInt()
                    val queryVal = query["value"] as? String
                    queryType == 102 && cleanServerPath(queryVal) == cleanTarget
                }
            }
            is ShareTarget.Search -> {
                val shareQuery = share.searchQuery ?: return false
                if (target.searchQuery.isEmpty()) return false
                try {
                    serializeCanonicalQuery(shareQuery) == serializeCanonicalQuery(target.searchQuery)
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    private fun buildValidityPayloads(validity: ShareValidity): List<ShareValidityRequest> {
        return if (validity.unit == ShareValidityUnit.NONE) {
            listOf(
                ShareValidityRequest(amount = 0, type = ShareValidityUnit.NONE.apiType),
                ShareValidityRequest(amount = 36500, type = ShareValidityUnit.DAYS.apiType)
            )
        } else {
            val safeAmount = validity.amount.coerceAtLeast(1)
            listOf(ShareValidityRequest(amount = safeAmount, type = validity.unit.apiType))
        }
    }

    /**
     * Creates a PiGallery2 share link for a specific directory path via the Retrofit ShareApiService.
     * Supports Minutes, Hours, Days, Months, or None (unlimited/forever) validity and preventDownload.
     */
    fun createShareLink(
        serverUrl: String,
        path: String,
        includeSubfolders: Boolean,
        validity: ShareValidity,
        password: String?,
        cookies: String,
        apiPrefix: String,
        preventDownload: Boolean = false
    ): ApiShareLink {
        val sanitizedUrl = ensureHttpUrl(serverUrl).trimEnd('/')
        if (sanitizedUrl.isEmpty()) throw IOException("No server configured.")

        if (prefs.csrfToken.isBlank()) {
            try {
                fetchCurrentUserSession(sanitizedUrl, cookies, apiPrefix)
            } catch (_: Exception) {
                // Continue even if /user/me fails
            }
        }
        val headers = buildShareHeaders(cookies = cookies, csrfToken = prefs.csrfToken)
        val service = getShareApiService(sanitizedUrl)
        val cleanPath = cleanServerPath(path)
        val encodedPath = encodeUrlPath(cleanPath)

        val endpoints = if (encodedPath.isEmpty()) {
            listOf(
                "POST" to "$sanitizedUrl$apiPrefix/share/",
                "POST" to "$sanitizedUrl$apiPrefix/share",
                "PUT" to "$sanitizedUrl$apiPrefix/share/",
                "PUT" to "$sanitizedUrl$apiPrefix/share"
            )
        } else {
            listOf(
                "POST" to "$sanitizedUrl$apiPrefix/share/$encodedPath",
                "PUT" to "$sanitizedUrl$apiPrefix/share/$encodedPath"
            )
        }

        val payloads = buildValidityPayloads(validity)
        var lastError: IOException? = null
        var allPathEndpointsUnsupported = true

        for (validObj in payloads) {
            val requestBody = CreateSharingRequestBody(
                createSharing = CreateSharingPayload(
                    includeSubfolders = includeSubfolders,
                    password = password?.takeIf { it.isNotEmpty() },
                    preventDownload = preventDownload,
                    valid = validObj
                )
            )
            var shouldRetryFallbackPayload = false

            for ((method, endpoint) in endpoints) {
                val call = if (method == "PUT") {
                    service.putShare(endpoint, headers, requestBody)
                } else {
                    service.postShare(endpoint, headers, requestBody)
                }
                val response = call.execute()
                val bodyString = readRetrofitBody(response)

                if (response.code() == 401) {
                    throw UnauthorizedSessionException(
                        extractErrorMessage(bodyString) ?: "Unauthorized. Session might have expired."
                    )
                }

                if (response.code() == 403) {
                    prefs.sharingConfigLoaded = true
                    prefs.sharingFeatureEnabled = false
                    throw IOException(
                        extractErrorMessage(bodyString) ?: "Sharing is disabled or not permitted for this user."
                    )
                }

                if (response.code() == 400 && validObj == payloads.first() && payloads.size > 1) {
                    allPathEndpointsUnsupported = false
                    lastError = IOException(extractErrorMessage(bodyString) ?: "Invalid share validity parameter.")
                    shouldRetryFallbackPayload = true
                    break
                }

                if (response.code() == 404 || response.code() == 405) {
                    lastError = IOException("Endpoint $method $endpoint not available (${response.code()})")
                    continue
                }

                allPathEndpointsUnsupported = false

                if (!response.isSuccessful) {
                    throw IOException(extractErrorMessage(bodyString) ?: "Server returned error code: ${response.code()}")
                }

                if (bodyString.isBlank()) {
                    throw IOException("Server returned empty response while creating share link.")
                }

                if (isHtmlResponse(bodyString)) {
                    throw IOException("Server returned HTML instead of JSON. Check your API prefix or session.")
                }

                val responseMap = try {
                    mapAdapter.fromJson(bodyString)
                } catch (e: Exception) {
                    throw IOException("Failed to parse server response as JSON: ${e.message}")
                }

                val errorObj = responseMap?.get("error")
                if (errorObj != null) {
                    val msg = (errorObj as? Map<*, *>)?.get("message")?.toString() ?: errorObj.toString()
                    throw IOException(msg)
                }

                val shareMap = (responseMap?.get("result") as? Map<*, *>) ?: responseMap
                val parsed = shareMap?.let {
                    parseShareLinkMap(
                        map = it,
                        fallbackPath = cleanPath,
                        fallbackIncludeSubfolders = includeSubfolders,
                        fallbackHasPassword = !password.isNullOrEmpty(),
                        fallbackPreventDownload = preventDownload
                    )
                }
                if (parsed != null) return parsed
                throw IOException("Server did not return a valid sharingKey.")
            }

            if (!shouldRetryFallbackPayload && !allPathEndpointsUnsupported) break
        }

        // Fallback for PiGallery2 versions that treat directory shares strictly as search queries
        if (allPathEndpointsUnsupported) {
            val directoryQuery = mapOf<String, Any?>(
                "type" to 102, // SearchQueryTypes.DIRECTORY
                "value" to if (cleanPath.isEmpty()) "/" else "/$cleanPath",
                "matchType" to if (includeSubfolders) 2 else 1
            )
            return createSearchShareLink(
                serverUrl = serverUrl,
                searchQuery = directoryQuery,
                validity = validity,
                password = password,
                cookies = cookies,
                apiPrefix = apiPrefix,
                preventDownload = preventDownload
            )
        }

        throw lastError ?: IOException("Failed to create share link.")
    }

    /**
     * Creates a PiGallery2 share link for a SearchQueryDTO (active search, saved album, or person)
     * via the Retrofit ShareApiService.
     * Includes a safety guard against older server versions that ignore searchQuery on POST /share/
     * and accidentally create a root-gallery share: any such accidental root share is immediately revoked.
     */
    fun createSearchShareLink(
        serverUrl: String,
        searchQuery: Map<String, Any?>,
        validity: ShareValidity,
        password: String?,
        cookies: String,
        apiPrefix: String,
        preventDownload: Boolean = false
    ): ApiShareLink {
        val sanitizedUrl = ensureHttpUrl(serverUrl).trimEnd('/')
        if (sanitizedUrl.isEmpty()) throw IOException("No server configured.")
        if (searchQuery.isEmpty()) throw IOException("Search query cannot be empty.")

        if (prefs.csrfToken.isBlank()) {
            try {
                fetchCurrentUserSession(sanitizedUrl, cookies, apiPrefix)
            } catch (_: Exception) {
                // Continue even if /user/me fails
            }
        }
        val headers = buildShareHeaders(cookies = cookies, csrfToken = prefs.csrfToken)
        val service = getShareApiService(sanitizedUrl)

        @Suppress("UNCHECKED_CAST")
        val cleanedQuery = cleanJsonValue(searchQuery) as Map<String, Any?>
        val queryJson = mapAdapter.toJson(cleanedQuery)
        val encodedQueryJson = URLEncoder.encode(queryJson, "UTF-8").replace("+", "%20")

        // Triple of (HTTP method, URL, isGenericRootFallback)
        val endpoints = listOf(
            Triple("POST", "$sanitizedUrl$apiPrefix/share/search/$encodedQueryJson", false),
            Triple("PUT", "$sanitizedUrl$apiPrefix/share/search/$encodedQueryJson", false),
            Triple("POST", "$sanitizedUrl$apiPrefix/share/search", false),
            Triple("PUT", "$sanitizedUrl$apiPrefix/share/search", false),
            Triple("POST", "$sanitizedUrl$apiPrefix/share/", true)
        )

        val payloads = buildValidityPayloads(validity)
        var lastError: IOException? = null

        for (validObj in payloads) {
            val requestBody = CreateSharingRequestBody(
                createSharing = CreateSharingPayload(
                    searchQuery = cleanedQuery,
                    password = password?.takeIf { it.isNotEmpty() },
                    preventDownload = preventDownload,
                    valid = validObj
                )
            )
            var shouldRetryFallbackPayload = false

            for ((method, endpoint, isGenericRootFallback) in endpoints) {
                val call = if (method == "PUT") {
                    service.putShare(endpoint, headers, requestBody)
                } else {
                    service.postShare(endpoint, headers, requestBody)
                }
                val response = call.execute()
                val bodyString = readRetrofitBody(response)

                if (response.code() == 401) {
                    throw UnauthorizedSessionException(
                        extractErrorMessage(bodyString) ?: "Unauthorized. Session might have expired."
                    )
                }

                if (response.code() == 403) {
                    prefs.sharingConfigLoaded = true
                    prefs.sharingFeatureEnabled = false
                    throw IOException(
                        extractErrorMessage(bodyString) ?: "Sharing is disabled or not permitted for this user."
                    )
                }

                if (response.code() == 400 && validObj == payloads.first() && payloads.size > 1) {
                    lastError = IOException(extractErrorMessage(bodyString) ?: "Invalid share validity parameter.")
                    shouldRetryFallbackPayload = true
                    break
                }

                if (response.code() == 404 || response.code() == 405) {
                    lastError = IOException("Endpoint $method $endpoint not available (${response.code()})")
                    continue
                }

                if (!response.isSuccessful) {
                    throw IOException(extractErrorMessage(bodyString) ?: "Server returned error code: ${response.code()}")
                }

                if (bodyString.isBlank()) {
                    throw IOException("Server returned empty response while creating share link.")
                }

                if (isHtmlResponse(bodyString)) {
                    throw IOException("Server returned HTML instead of JSON. Check your API prefix or session.")
                }

                val responseMap = try {
                    mapAdapter.fromJson(bodyString)
                } catch (e: Exception) {
                    throw IOException("Failed to parse server response as JSON: ${e.message}")
                }

                val errorObj = responseMap?.get("error")
                if (errorObj != null) {
                    val msg = (errorObj as? Map<*, *>)?.get("message")?.toString() ?: errorObj.toString()
                    throw IOException(msg)
                }

                val shareMap = (responseMap?.get("result") as? Map<*, *>) ?: responseMap
                val parsed = shareMap?.let {
                    parseShareLinkMap(
                        map = it,
                        fallbackPath = "",
                        // On the generic /share/ fallback, do NOT supply fallbackSearchQuery so we can detect
                        // older servers that ignored searchQuery and created a root-directory share instead.
                        fallbackSearchQuery = if (isGenericRootFallback) null else cleanedQuery,
                        fallbackIncludeSubfolders = false,
                        fallbackHasPassword = !password.isNullOrEmpty(),
                        fallbackPreventDownload = preventDownload
                    )
                } ?: throw IOException("Server did not return a valid sharingKey.")

                if (isGenericRootFallback && parsed.searchQuery == null && cleanServerPath(parsed.path).isEmpty()) {
                    // CRITICAL SAFETY GUARD: Older PiGallery2 server ignored searchQuery and created a root share!
                    // Immediately revoke it so we never leak the entire gallery.
                    try {
                        deleteShareLink(
                            serverUrl = sanitizedUrl,
                            sharingKey = parsed.sharingKey,
                            cookies = cookies,
                            apiPrefix = apiPrefix
                        )
                    } catch (revokeErr: Exception) {
                        Log.w("PiGalleryApi", "Failed to auto-revoke accidental root share: ${revokeErr.message}")
                    }
                    throw IOException(context.getString(com.example.R.string.share_search_unsupported_server))
                }

                return parsed
            }

            if (!shouldRetryFallbackPayload) break
        }

        throw lastError ?: IOException(context.getString(com.example.R.string.share_search_unsupported_server))
    }

    /**
     * Fetches active share links visible to the current user from the PiGallery2 server
     * via the Retrofit ShareApiService.
     */
    fun listShareLinks(
        serverUrl: String,
        cookies: String,
        apiPrefix: String
    ): List<ApiShareLink> {
        val sanitizedUrl = ensureHttpUrl(serverUrl).trimEnd('/')
        if (sanitizedUrl.isEmpty()) return emptyList()

        val headers = buildShareHeaders(cookies = cookies)
        val service = getShareApiService(sanitizedUrl)
        val endpoints = listOf(
            "$sanitizedUrl$apiPrefix/share/list",
            "$sanitizedUrl$apiPrefix/share"
        )
        var lastError: IOException? = null

        for (endpoint in endpoints) {
            val response = service.getShareLinks(endpoint, headers).execute()
            val bodyString = readRetrofitBody(response)

            if (response.code() == 401) {
                throw UnauthorizedSessionException(
                    extractErrorMessage(bodyString) ?: "Unauthorized. Session might have expired."
                )
            }

            if (response.code() == 403) {
                prefs.sharingConfigLoaded = true
                prefs.sharingFeatureEnabled = false
                throw IOException(
                    extractErrorMessage(bodyString) ?: "Sharing is disabled or not permitted for this user."
                )
            }

            if (response.code() == 404 || response.code() == 405) {
                lastError = IOException("Endpoint GET $endpoint not available (${response.code()})")
                continue
            }

            if (!response.isSuccessful) {
                throw IOException(extractErrorMessage(bodyString) ?: "Server returned error code: ${response.code()}")
            }

            if (bodyString.isBlank() || response.code() == 204) {
                return emptyList()
            }

            if (isHtmlResponse(bodyString)) {
                throw IOException("Server returned HTML instead of JSON. Check your API prefix or session.")
            }

            val responseMap = try {
                mapAdapter.fromJson(bodyString)
            } catch (e: Exception) {
                throw IOException("Failed to parse server response as JSON: ${e.message}")
            }

            val errorObj = responseMap?.get("error")
            if (errorObj != null) {
                val msg = (errorObj as? Map<*, *>)?.get("message")?.toString() ?: errorObj.toString()
                throw IOException(msg)
            }

            val resultList = responseMap?.get("result") as? List<*> ?: emptyList<Any>()
            return resultList.mapNotNull { item ->
                (item as? Map<*, *>)?.let { parseShareLinkMap(it) }
            }
        }

        prefs.sharingConfigLoaded = true
        prefs.sharingSupportedByServer = false
        prefs.sharingFeatureEnabled = false
        throw lastError ?: IOException("Failed to load active share links.")
    }

    /**
     * Revokes/deletes an active share link by its sharingKey on the PiGallery2 server
     * via the Retrofit ShareApiService.
     */
    fun deleteShareLink(
        serverUrl: String,
        sharingKey: String,
        cookies: String,
        apiPrefix: String
    ) {
        val sanitizedUrl = ensureHttpUrl(serverUrl).trimEnd('/')
        if (sanitizedUrl.isEmpty()) throw IOException("No server configured.")

        if (prefs.csrfToken.isBlank()) {
            try {
                fetchCurrentUserSession(sanitizedUrl, cookies, apiPrefix)
            } catch (_: Exception) {
                // Continue even if /user/me fails
            }
        }
        val headers = buildShareHeaders(cookies = cookies, csrfToken = prefs.csrfToken)
        val service = getShareApiService(sanitizedUrl)
        val encodedKey = URLEncoder.encode(sharingKey, "UTF-8").replace("+", "%20")
        val endpoint = "$sanitizedUrl$apiPrefix/share/$encodedKey"

        val response = service.deleteShareLink(endpoint, headers).execute()
        val bodyString = readRetrofitBody(response)

        if (response.code() == 401) {
            throw UnauthorizedSessionException(
                extractErrorMessage(bodyString) ?: "Unauthorized. Session might have expired."
            )
        }

        if (response.code() == 403) {
            throw IOException(
                extractErrorMessage(bodyString) ?: "Not authorized to revoke share links."
            )
        }

        if (!response.isSuccessful) {
            throw IOException(
                extractErrorMessage(bodyString) ?: "Server returned error code: ${response.code()}"
            )
        }

        if (bodyString.isBlank() || response.code() == 204) {
            return
        }

        if (isHtmlResponse(bodyString)) {
            throw IOException("Server returned HTML instead of JSON. Check your API prefix or session.")
        }

        val responseMap = try {
            mapAdapter.fromJson(bodyString)
        } catch (_: Exception) {
            null
        }
        val errorObj = responseMap?.get("error")
        if (errorObj != null) {
            val msg = (errorObj as? Map<*, *>)?.get("message")?.toString() ?: errorObj.toString()
            throw IOException(msg)
        }
    }
}
