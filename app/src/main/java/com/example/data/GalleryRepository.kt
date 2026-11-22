package com.example.data

import com.example.data.database.AppDatabase
import com.example.data.database.MediaEntity
import com.example.data.database.DirectoryEntity
import org.json.JSONArray
import org.json.JSONObject

class GalleryRepository(private val database: AppDatabase) {

    private fun serializeKeywords(keywords: List<String>?): String? {
        if (keywords.isNullOrEmpty()) return null
        return try {
            JSONArray().apply {
                keywords.forEach { put(it) }
            }.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun deserializeKeywords(json: String?): List<String>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun serializeFaces(faces: List<ApiFace>?): String? {
        if (faces.isNullOrEmpty()) return null
        return try {
            JSONArray().apply {
                faces.forEach { face ->
                    val obj = JSONObject()
                    if (face.name != null) obj.put("n", face.name)
                    face.box?.let { box ->
                        val boxObj = JSONObject().apply {
                            put("w", box.width)
                            put("h", box.height)
                            put("l", box.left)
                            put("t", box.top)
                        }
                        obj.put("b", boxObj)
                    }
                    put(obj)
                }
            }.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun deserializeFaces(json: String?): List<ApiFace>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            val result = ArrayList<ApiFace>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = if (obj.has("n") && !obj.isNull("n")) obj.optString("n") else null
                val boxObj = obj.optJSONObject("b")
                val box = if (boxObj != null) {
                    ApiFaceBox(
                        width = boxObj.optInt("w"),
                        height = boxObj.optInt("h"),
                        left = boxObj.optInt("l"),
                        top = boxObj.optInt("t")
                    )
                } else null
                result.add(ApiFace(name = name, box = box))
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    private fun mapEntityToMedia(it: MediaEntity): ApiMedia {
        val size = if (it.width != null || it.height != null) {
            ApiSize(width = it.width, height = it.height)
        } else null

        val gps = if (it.latitude != null && it.longitude != null) {
            ApiGPSData(latitude = it.latitude, longitude = it.longitude, altitude = it.altitude)
        } else null

        val cameraData = if (it.cameraMake != null || it.cameraModel != null || it.iso != null || it.fStop != null || it.exposure != null || it.focalLength != null || it.lens != null) {
            ApiCameraData(
                ISO = it.iso,
                make = it.cameraMake,
                model = it.cameraModel,
                fStop = it.fStop,
                exposure = it.exposure,
                focalLength = it.focalLength,
                lens = it.lens
            )
        } else null

        val keywords = deserializeKeywords(it.keywordsJson)
        val faces = deserializeFaces(it.facesJson)

        val metadata = ApiMediaMetadata(
            size = size,
            creationDate = it.creationDate,
            creationDateOffset = it.creationDateOffset,
            fileSize = it.fileSize,
            duration = it.duration,
            gps = gps,
            cameraData = cameraData,
            keywords = keywords,
            faces = faces,
            isLivePhoto = it.isLivePhoto,
            liveVideoPath = it.liveVideoPath
        )

        return ApiMedia(
            id = it.id,
            name = it.name,
            parentPath = it.parentPath,
            metadata = metadata
        )
    }

    private fun mapMediaToEntity(media: ApiMedia, parentPathOverride: String? = null): MediaEntity {
        val parentPath = parentPathOverride ?: if (!media.parentPath.isNullOrEmpty()) {
            media.parentPath
        } else ""
        val mediaId = if (media.id != null && media.id != 0) {
            media.id
        } else {
            ("$parentPath/${media.name}").hashCode()
        }
        val meta = media.metadata
        return MediaEntity(
            id = mediaId,
            name = media.name,
            parentPath = parentPath,
            creationDate = meta?.creationDate,
            creationDateOffset = meta?.creationDateOffset,
            fileSize = meta?.fileSize,
            duration = meta?.duration,
            latitude = meta?.gps?.latitude,
            longitude = meta?.gps?.longitude,
            altitude = meta?.gps?.altitude,
            cameraMake = meta?.cameraData?.make,
            cameraModel = meta?.cameraData?.model,
            iso = meta?.cameraData?.ISO,
            fStop = meta?.cameraData?.fStop,
            exposure = meta?.cameraData?.exposure,
            focalLength = meta?.cameraData?.focalLength,
            lens = meta?.cameraData?.lens,
            isLivePhoto = meta?.isLivePhoto,
            liveVideoPath = meta?.liveVideoPath,
            width = meta?.size?.width,
            height = meta?.size?.height,
            keywordsJson = serializeKeywords(meta?.keywords),
            facesJson = serializeFaces(meta?.faces)
        )
    }

    suspend fun getDirectory(path: String): List<ApiMedia>? {
        return try {
            val mediaEntities = database.mediaDao().getMediaByPath(path)
            if (mediaEntities.isEmpty()) return null
            mediaEntities.map { mapEntityToMedia(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAllMedia(): List<ApiMedia>? {
        return try {
            val mediaEntities = database.mediaDao().getAllMedia()
            if (mediaEntities.isEmpty()) return null
            mediaEntities.map { mapEntityToMedia(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveAllMedia(mediaList: List<ApiMedia>) {
        if (mediaList.isEmpty()) return
        try {
            val entities = mediaList.map { media -> mapMediaToEntity(media) }
            entities.chunked(500).forEach { chunk ->
                database.mediaDao().insertMedia(chunk)
            }
        } catch (e: Exception) {
            // Prevent fatal crash on SQLite write/disk failure
        }
    }

    suspend fun saveDirectory(path: String, media: List<ApiMedia>) {
        try {
            val entities = media.map { item -> mapMediaToEntity(item, parentPathOverride = path) }
            database.mediaDao().deleteMediaByPath(path)
            entities.chunked(500).forEach { chunk ->
                database.mediaDao().insertMedia(chunk)
            }
            database.directoryDao().insertDirectory(DirectoryEntity(path, path.substringAfterLast('/'), System.currentTimeMillis()))
        } catch (e: Exception) {
            // Prevent fatal crash on SQLite write/disk failure
        }
    }

    suspend fun clearAllCache() {
        try {
            database.mediaDao().clearAllMedia()
            database.directoryDao().clearAllDirectories()
        } catch (e: Exception) {
            // Prevent fatal crash
        }
    }
}
