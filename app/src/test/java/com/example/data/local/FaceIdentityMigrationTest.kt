package com.example.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FaceIdentityMigrationTest {

  @Test
  fun migration8To9AddsStableIdentityColumnsWithoutDroppingLegacyRows() {
    val context = RuntimeEnvironment.getApplication()
    val databaseName = "face-identity-migration-test.db"
    context.deleteDatabase(databaseName)
    val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
      .name(databaseName)
      .callback(
        object : SupportSQLiteOpenHelper.Callback(8) {
          override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
              "CREATE TABLE face_assignments (photoId INTEGER NOT NULL, faceIndex INTEGER NOT NULL, personClusterId TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(photoId, faceIndex))"
            )
            db.execSQL(
              "CREATE TABLE face_exclusions (photoId INTEGER NOT NULL, faceIndex INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(photoId, faceIndex))"
            )
            db.execSQL(
              "CREATE TABLE face_correction_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, photoId INTEGER NOT NULL, faceIndex INTEGER NOT NULL, previousPersonClusterId TEXT, nextPersonClusterId TEXT, wasExcluded INTEGER NOT NULL, isExcluded INTEGER NOT NULL, createdAt INTEGER NOT NULL, undoneAt INTEGER)"
            )
            db.execSQL(
              "CREATE TABLE face_people (clusterId TEXT NOT NULL PRIMARY KEY, name TEXT, coverPhotoId INTEGER, coverFaceIndex INTEGER, isIgnored INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL)"
            )
            db.execSQL(
              "INSERT INTO face_assignments (photoId, faceIndex, personClusterId, updatedAt) VALUES (5, 1, 'person-5', 10)"
            )
            db.execSQL(
              "INSERT INTO face_exclusions (photoId, faceIndex, updatedAt) VALUES (6, 0, 11)"
            )
            db.execSQL(
              "INSERT INTO face_correction_history (photoId, faceIndex, previousPersonClusterId, nextPersonClusterId, wasExcluded, isExcluded, createdAt) VALUES (5, 1, NULL, 'person-5', 0, 0, 12)"
            )
            db.execSQL(
              "INSERT INTO face_people (clusterId, name, coverPhotoId, coverFaceIndex, isIgnored, updatedAt) VALUES ('person-5', 'Five', 5, 1, 0, 13)"
            )
          }

          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
      )
      .build()
    val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
    try {
      val db = helper.writableDatabase
      ToadGalleryDatabase.MIGRATION_8_9.migrate(db)

      assertTrue("face_assignments.faceId", hasColumn(db, "face_assignments", "faceId"))
      assertTrue("face_exclusions.faceId", hasColumn(db, "face_exclusions", "faceId"))
      assertTrue("face_correction_history.faceId", hasColumn(db, "face_correction_history", "faceId"))
      assertTrue("face_people.coverFaceId", hasColumn(db, "face_people", "coverFaceId"))
      assertTrue(db.query("SELECT * FROM face_assignments WHERE photoId = 5").use { it.moveToFirst() })
      assertTrue(db.query("SELECT * FROM face_exclusions WHERE photoId = 6").use { it.moveToFirst() })
      assertTrue(db.query("SELECT * FROM face_correction_history WHERE photoId = 5").use { it.moveToFirst() })
      assertTrue(db.query("SELECT * FROM face_people WHERE clusterId = 'person-5'").use { it.moveToFirst() })
    } finally {
      helper.close()
      context.deleteDatabase(databaseName)
    }
  }

  private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
    db.query("PRAGMA table_info($table)").use { cursor ->
      val nameColumn = cursor.getColumnIndexOrThrow("name")
      generateSequence { if (cursor.moveToNext()) cursor.getString(nameColumn) else null }
        .any { it == column }
    }
}
