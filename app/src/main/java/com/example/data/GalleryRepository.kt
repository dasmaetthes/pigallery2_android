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
                metadata = ApiMediaMetadata(creationDate = it.creationDate)
            )
        }
    }

    suspend fun saveDirectory(path: String, media: List<ApiMedia>) {
        val entities = media.map {
            MediaEntity(
                id = it.id ?: 0,
                name = it.name,
                parentPath = path,
                creationDate = it.metadata?.creationDate
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
