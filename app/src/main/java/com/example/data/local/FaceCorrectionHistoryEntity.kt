package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Reversible local correction, following Ente's persisted face-feedback workflow. */
@Entity(tableName = "face_correction_history")
data class FaceCorrectionHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val photoId: Long,
  val faceIndex: Int,
  val previousPersonClusterId: String? = null,
  val nextPersonClusterId: String? = null,
  val wasExcluded: Boolean = false,
  val isExcluded: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
  val undoneAt: Long? = null,
)
