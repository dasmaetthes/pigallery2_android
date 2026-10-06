package com.example.data

import com.example.data.database.AppDatabase
import com.example.data.database.MediaEntity
import com.example.data.database.DirectoryEntity

class GalleryRepository(private val database: AppDatabase) {

    suspend fun getDirectory(path: String): List<ApiMedia>? {
        val mediaEntities = database.mediaDao().getMediaByPath(path)
        if (mediaEntities.isEmpty()) return null
        return mediaEntities.map { 
            ApiMedia(
                id = it.id,
                name = it.name,
                parentPath = it.parentPath,
                metadata = ApiMediaMetadata(creationDate = it.creationDate, fileSize = it.fileSize)
            )
        }
    }

    suspend fun getAllMedia(): List<ApiMedia>? {
        val mediaEntities = database.mediaDao().getAllMedia()
        if (mediaEntities.isEmpty()) return null
        return mediaEntities.map { 
            ApiMedia(
                id = it.id,
                name = it.name,
                parentPath = it.parentPath,
                metadata = ApiMediaMetadata(creationDate = it.creationDate, fileSize = it.fileSize)
            )
        }
    }

    suspend fun saveAllMedia(mediaList: List<ApiMedia>) {
        val existingMap = database.mediaDao().getAllMedia().associateBy { it.id }
        val entities = mediaList.map { media ->
            val parentPath = if (!media.parentPath.isNullOrEmpty()) {
                media.parentPath
            } else ""
            val mediaId = if (media.id != null && media.id != 0) {
                media.id
            } else {
                ("$parentPath/${media.name}").hashCode()
            }
            val existing = existingMap[mediaId]
            val creationDate = media.metadata?.creationDate ?: existing?.creationDate
            val fileSize = media.metadata?.fileSize ?: existing?.fileSize

            MediaEntity(
                id = mediaId,
                name = media.name,
                parentPath = parentPath,
                creationDate = creationDate,
                fileSize = fileSize
            )
        }
        database.mediaDao().insertMedia(entities)
    }

    suspend fun saveDirectory(path: String, media: List<ApiMedia>) {
        val entities = media.map {
            val mediaId = if (it.id != null && it.id != 0) {
                it.id
            } else {
                ("$path/${it.name}").hashCode()
            }
            MediaEntity(
                id = mediaId,
                name = it.name,
                parentPath = path,
                creationDate = it.metadata?.creationDate,
                fileSize = it.metadata?.fileSize
            )
        }
        database.mediaDao().deleteMediaByPath(path)
        database.mediaDao().insertMedia(entities)
        database.directoryDao().insertDirectory(DirectoryEntity(path, path.substringAfterLast('/'), System.currentTimeMillis()))
    }

    suspend fun clearAllCache() {
        database.mediaDao().clearAllMedia()
        database.directoryDao().clearAllDirectories()
    }
}
