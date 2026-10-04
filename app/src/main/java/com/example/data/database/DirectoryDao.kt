package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DirectoryDao {
    @Query("SELECT * FROM directories WHERE path = :path")
    suspend fun getDirectoryByPath(path: String): DirectoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectory(directory: DirectoryEntity)

    @Query("DELETE FROM directories")
    suspend fun clearAllDirectories()
}
