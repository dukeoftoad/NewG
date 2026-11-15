package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** User-authored metadata for a locally detected face cluster. Stored only on this device. */
@Entity(tableName = "face_people")
data class FacePersonEntity(
  @PrimaryKey val clusterId: String,
  val name: String? = null,
  val coverPhotoId: Long? = null,
  val coverFaceIndex: Int? = null,
  @ColumnInfo(defaultValue = "0") val isIgnored: Boolean = false,
  val updatedAt: Long = System.currentTimeMillis(),
)
