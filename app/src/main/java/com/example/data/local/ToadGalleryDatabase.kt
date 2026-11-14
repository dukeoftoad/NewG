package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.ref.WeakReference

@Database(
  entities = [MediaItemEntity::class, AlbumEntity::class],
  version = 3,
  exportSchema = false,
)
abstract class ToadGalleryDatabase : RoomDatabase() {
  abstract fun galleryDao(): GalleryDao

  companion object {
    @Volatile private var instance: ToadGalleryDatabase? = null
    private var instanceContextRef: WeakReference<Context>? = null

    private val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE media_items ADD COLUMN editedUriString TEXT")
          db.execSQL("ALTER TABLE media_items ADD COLUMN editRecipeJson TEXT")
        }
      }

    fun getInstance(context: Context): ToadGalleryDatabase {
      val appContext = context.applicationContext
      val current = instance
      if (current != null && instanceContextRef?.get() === appContext) {
        return current
      }
      return synchronized(this) {
        val synced = instance
        if (synced != null && instanceContextRef?.get() === appContext) {
          synced
        } else {
          synced?.close()
          Room.databaseBuilder(
              appContext,
              ToadGalleryDatabase::class.java,
              "toad_gallery_db",
            )
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
            .also {
              instance = it
              instanceContextRef = WeakReference(appContext)
            }
        }
      }
    }
  }
}
