package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE parentPath = :path")
    suspend fun getMediaByPath(path: String): List<MediaEntity>

    @Query("SELECT * FROM media")
    suspend fun getAllMedia(): List<MediaEntity>

    @Query(
        "SELECT * FROM media WHERE creationDate IS NOT NULL AND (" +
            "(creationDate >= :minMs AND creationDate <= :maxMs) OR " +
            "(creationDate >= :minSec AND creationDate <= :maxSec)" +
            ")"
    )
    suspend fun getMediaInDateRange(
        minMs: Long,
        maxMs: Long,
        minSec: Long,
        maxSec: Long
    ): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: List<MediaEntity>)

    @Query("DELETE FROM media WHERE parentPath = :path")
    suspend fun deleteMediaByPath(path: String)

    @Query("DELETE FROM media")
    suspend fun clearAllMedia()
}
