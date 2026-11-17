package com.toadgallery.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.ref.WeakReference

@Database(
  entities = [MediaItemEntity::class, AlbumEntity::class, FacePersonEntity::class, FaceExclusionEntity::class, FaceAssignmentEntity::class, FaceCorrectionHistoryEntity::class],
  version = 10,
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

    private val MIGRATION_3_4 =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE media_items ADD COLUMN faceDataJson TEXT")
          db.execSQL("ALTER TABLE media_items ADD COLUMN faceIndexedAt INTEGER")
        }
      }

    private val MIGRATION_4_5 =
      object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS face_people (clusterId TEXT NOT NULL PRIMARY KEY, name TEXT, coverPhotoId INTEGER, isIgnored INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL)"
          )
        }
      }

    private val MIGRATION_5_6 =
      object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS face_exclusions (photoId INTEGER NOT NULL, faceIndex INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(photoId, faceIndex))"
          )
        }
      }

    private val MIGRATION_6_7 =
      object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS face_assignments (photoId INTEGER NOT NULL, faceIndex INTEGER NOT NULL, personClusterId TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(photoId, faceIndex))"
          )
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS face_correction_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, photoId INTEGER NOT NULL, faceIndex INTEGER NOT NULL, previousPersonClusterId TEXT, nextPersonClusterId TEXT, wasExcluded INTEGER NOT NULL, isExcluded INTEGER NOT NULL, createdAt INTEGER NOT NULL, undoneAt INTEGER)"
          )
        }
      }

    private val MIGRATION_7_8 =
      object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE face_people ADD COLUMN coverFaceIndex INTEGER")
        }
      }

    internal val MIGRATION_8_9 =
      object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE face_assignments ADD COLUMN faceId TEXT")
          db.execSQL("ALTER TABLE face_exclusions ADD COLUMN faceId TEXT")
          db.execSQL("ALTER TABLE face_correction_history ADD COLUMN faceId TEXT")
          db.execSQL("ALTER TABLE face_people ADD COLUMN coverFaceId TEXT")
        }
      }

    /**
     * Adds `trashedAt` to `media_items` so the Recycle Bin's 30-day auto-purge
     * promise (TrashPurgeWorker, v2.1.0) can be enforced. Pre-existing trashed
     * rows are seeded with the migration timestamp — they get a full 30-day
     * window from upgrade day, not an immediate purge on the first worker run.
     * Un-trashed rows stay NULL.
     */
    internal val MIGRATION_9_10 =
      object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE media_items ADD COLUMN trashedAt INTEGER")
          db.execSQL("UPDATE media_items SET trashedAt = ${System.currentTimeMillis()} WHERE isTrashed = 1 AND trashedAt IS NULL")
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
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
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
