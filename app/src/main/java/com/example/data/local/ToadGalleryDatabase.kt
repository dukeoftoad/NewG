package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [MediaItemEntity::class, AlbumEntity::class],
  version = 2,
  exportSchema = false,
)
abstract class ToadGalleryDatabase : RoomDatabase() {
  abstract fun galleryDao(): GalleryDao

  companion object {
    @Volatile private var instance: ToadGalleryDatabase? = null

    fun getInstance(context: Context): ToadGalleryDatabase =
      instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                ToadGalleryDatabase::class.java,
                "toad_gallery_db",
              )
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { instance = it }
        }
  }
}
