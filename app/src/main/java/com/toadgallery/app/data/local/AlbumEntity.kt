package com.toadgallery.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val coverUriString: String? = null,
  val coverDrawableName: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
)
