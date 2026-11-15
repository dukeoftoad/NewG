package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A locally excluded detected face, keyed by the photo row and detector result index. */
@Entity(tableName = "face_exclusions", primaryKeys = ["photoId", "faceIndex"])
data class FaceExclusionEntity(
  val photoId: Long,
  val faceIndex: Int,
  /** Stable detector-independent identity; nullable for legacy rows. */
  val faceId: String? = null,
  val updatedAt: Long = System.currentTimeMillis(),
)
