package com.example.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
    indices = [Index(value = ["parentPath"])]
)
data class MediaEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val parentPath: String,
    val creationDate: Long?,
    val fileSize: Long? = null
)
