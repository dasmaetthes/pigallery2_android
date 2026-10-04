package com.example.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MediaEntity::class, DirectoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun directoryDao(): DirectoryDao
}
