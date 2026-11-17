package com.toadgallery.app.data.trash

import android.content.Context
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.toadgallery.app.data.local.GalleryDao
import com.toadgallery.app.data.local.MediaItemEntity
import com.toadgallery.app.data.local.ToadGalleryDatabase
import com.toadgallery.app.data.repository.GalleryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Covers the 30-day auto-purge rule end-to-end at the data layer: DAO query,
 * repository wrapper, and [TrashPurgeWorker]. The worker is the piece that
 * turns "Auto-deletes in 30 days" (Recycle Bin dialog copy) from a promise
 * into behaviour.
 *
 * Pure JVM via Robolectric + Room in-memory; no device, no WorkManager runtime
 * (the worker is exercised via [TestListenableWorkerBuilder] with a stubbed repo).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrashPurgeWorkerTest {
  private lateinit var database: ToadGalleryDatabase
  private lateinit var dao: GalleryDao
  private lateinit var repo: GalleryRepository

  @Before
  fun setUp() {
    database = Room.inMemoryDatabaseBuilder(
      RuntimeEnvironment.getApplication(),
      ToadGalleryDatabase::class.java,
    ).allowMainThreadQueries().build()
    dao = database.galleryDao()
    repo = GalleryRepository(RuntimeEnvironment.getApplication(), dao)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun setTrashedWritesTimestampAndRestoreClearsIt() = runBlocking {
    // Insert a clean (non-trashed) row, then trash it via the repo — the path
    // the app actually uses. Verified by re-querying the row.
    val id = dao.insertCleanRow("img-1")
    repo.setTrashed(id, trashed = true)

    val row = dao.getMediaById(id)!!
    assertNotNull("trashedAt must be set when trashed", row.trashedAt)
    assertTrue("trashedAt must be ~now", System.currentTimeMillis() - row.trashedAt!! < 5_000)

    repo.setTrashed(id, trashed = false)
    assertNull("trashedAt must be cleared on restore", dao.getMediaById(id)!!.trashedAt)
  }

  @Test
  fun purgeDeletesOnlyRowsOlderThanCutoff() = runBlocking {
    val now = System.currentTimeMillis()
    val oldId = dao.insertTrashed("old", trashedAt = now - 31 * DAY_MS)
    val freshId = dao.insertTrashed("fresh", trashedAt = now - 1_000L)
    val noTsId = dao.insertTrashed("no-ts", trashedAt = null)

    val cutoff = now - 30 * DAY_MS
    val purged = repo.purgeTrashedOlderThan(cutoff)

    assertEquals("exactly the one old row purged", 1, purged)
    assertEquals("old row gone", null, dao.getMediaById(oldId))
    assertEquals("fresh row survives", "fresh", dao.getMediaById(freshId)!!.title)
    assertEquals(
      "null-timestamp row survives (user resolves via Empty bin)",
      "no-ts",
      dao.getMediaById(noTsId)!!.title,
    )
  }

  @Test
  fun countMatchesPurgeEligibleSet() = runBlocking {
    val now = System.currentTimeMillis()
    dao.insertTrashed("old-1", trashedAt = now - 40 * DAY_MS)
    dao.insertTrashed("old-2", trashedAt = now - 31 * DAY_MS)
    dao.insertTrashed("fresh", trashedAt = now - 1_000L)

    val cutoff = now - 30 * DAY_MS
    assertEquals("count matches what the next purge would delete", 2, repo.countTrashedOlderThan(cutoff))
    assertEquals("purge deletes the same count", 2, repo.purgeTrashedOlderThan(cutoff))
    assertEquals("after purge, count is zero", 0, repo.countTrashedOlderThan(cutoff))
  }

  @Test
  fun workerPurgesExpiredRowsAndLeavesFreshOnesAlone() = runBlocking {
    val now = System.currentTimeMillis()
    dao.insertTrashed("expired", trashedAt = now - 31 * DAY_MS)
    dao.insertTrashed("fresh", trashedAt = now - 1_000L)

    val worker = TestListenableWorkerBuilder
      .from<TrashPurgeWorker>(RuntimeEnvironment.getApplication(), TrashPurgeWorker::class.java)
      .setWorkerFactory(
        object : WorkerFactory() {
          override fun createWorker(
            appContext: Context,
            workerClassName: String,
            params: WorkerParameters,
          ): ListenableWorker = TrashPurgeWorker(appContext, params) { _ -> repo }
        },
      )
      .build()

    val result = worker.doWork()
    assertTrue("worker succeeds", result is ListenableWorker.Result.Success)

    val remaining = dao.observeTrashedMedia().first()
    assertEquals("only the fresh trashed row remains", 1, remaining.size)
    assertEquals("the expired row was deleted by the worker", "fresh", remaining.first().title)
  }

  private companion object {
    const val DAY_MS = 24L * 60 * 60 * 1000
  }

  /** A clean (not-trashed) row, used by the trash-via-repo test. */
  private suspend fun GalleryDao.insertCleanRow(title: String): Long {
    insertMedia(listOf(baseEntity(title, isTrashed = false, trashedAt = null)))
    return findByTitle(title)
  }

  /** A row already in the trashed state at the given timestamp. */
  private suspend fun GalleryDao.insertTrashed(title: String, trashedAt: Long?): Long {
    insertMedia(listOf(baseEntity(title, isTrashed = true, trashedAt = trashedAt)))
    return findByTitle(title)
  }

  private fun baseEntity(
    title: String,
    isTrashed: Boolean,
    trashedAt: Long?,
  ) = MediaItemEntity(
    title = title,
    category = "CAMERA",
    mediaType = "PHOTO",
    dateLabel = "2026-01-01",
    weekGroup = "2026-W01",
    monthGroup = "2026-01",
    timestamp = 0L,
    locationName = "",
    hasGps = false,
    mapX = 0f,
    mapY = 0f,
    aspectRatio = 1f,
    tagsCsv = "",
    ocrText = "",
    exifSummary = "",
    isTrashed = isTrashed,
    trashedAt = trashedAt,
  )

  /** Look up a row by title across the trashed + visible flows. */
  private suspend fun GalleryDao.findByTitle(title: String): Long {
    observeTrashedMedia().first().firstOrNull { it.title == title }?.let { return it.id }
    observeVisibleMedia().first().firstOrNull { it.title == title }?.let { return it.id }
    error("row not found after insert: $title")
  }
}
