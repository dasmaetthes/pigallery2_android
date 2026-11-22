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
    val creationDateOffset: String? = null,
    val fileSize: Long? = null,
    val duration: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val iso: Int? = null,
    val fStop: Double? = null,
    val exposure: Double? = null,
    val focalLength: Double? = null,
    val lens: String? = null,
    val isLivePhoto: Boolean? = null,
    val liveVideoPath: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val keywordsJson: String? = null,
    val facesJson: String? = null
)
