package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "directories")
data class DirectoryEntity(
    @PrimaryKey val path: String,
    val name: String,
    val lastUpdated: Long
)
