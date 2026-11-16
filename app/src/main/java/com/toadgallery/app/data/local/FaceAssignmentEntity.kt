package com.toadgallery.app.data.local

import androidx.room.Entity

/**
 * Explicit user assignment of one detected face to a stable local person cluster.
 * Mirrors Ente's explicit cluster/person assignment rather than relying on name equality.
 */
@Entity(tableName = "face_assignments", primaryKeys = ["photoId", "faceIndex"])
data class FaceAssignmentEntity(
  val photoId: Long,
  val faceIndex: Int,
  val personClusterId: String,
  /** Stable detector-independent identity; nullable for legacy rows. */
  val faceId: String? = null,
  val updatedAt: Long = System.currentTimeMillis(),
)
