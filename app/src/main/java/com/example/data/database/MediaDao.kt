package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE parentPath = :path")
    suspend fun getMediaByPath(path: String): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: List<MediaEntity>)

    @Query("DELETE FROM media WHERE parentPath = :path")
    suspend fun deleteMediaByPath(path: String)

    @Query("DELETE FROM media")
    suspend fun clearAllMedia()
}
