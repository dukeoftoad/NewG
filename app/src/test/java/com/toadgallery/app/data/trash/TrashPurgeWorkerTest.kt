package com.toadgallery.app.data.trash

import android.content.Context
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
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
    val id = dao.insertOne("img-1")
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
    val oldId = dao.insertOne("old", trashedAt = now - 31 * DAY_MS)
    val freshId = dao.insertOne("fresh", trashedAt = now - 1_000L)
    val noTsId = dao.insertOne("no-ts", trashedAt = null, isTrashed = true)

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
    dao.insertOne("old-1", trashedAt = now - 40 * DAY_MS)
    dao.insertOne("old-2", trashedAt = now - 31 * DAY_MS)
    dao.insertOne("fresh", trashedAt = now - 1_000L)

    val cutoff = now - 30 * DAY_MS
    assertEquals("count matches what the next purge would delete", 2, repo.countTrashedOlderThan(cutoff))
    assertEquals("purge deletes the same count", 2, repo.purgeTrashedOlderThan(cutoff))
    assertEquals("after purge, count is zero", 0, repo.countTrashedOlderThan(cutoff))
  }

  @Test
  fun workerPurgesExpiredRowsAndLeavesFreshOnesAlone() = runBlocking {
    val now = System.currentTimeMillis()
    dao.insertOne("expired", trashedAt = now - 31 * DAY_MS)
    dao.insertOne("fresh", trashedAt = now - 1_000L)

    val worker = TestListenableWorkerBuilder<TrashPurgeWorker>(
      context = RuntimeEnvironment.getApplication(),
      input = workDataOf(),
    ).setWorkerFactory { appContext, params ->
      TrashPurgeWorker(appContext, params) { _ -> repo }
    }.build()

    val result = worker.doWork()
    assertTrue("worker succeeds", result is ListenableWorker.Result.Success)

    val remaining = dao.observeTrashedMedia().first()
    assertEquals("only the fresh trashed row remains", 1, remaining.size)
    assertEquals("the expired row was deleted by the worker", "fresh", remaining.first().title)
  }

  private companion object {
    const val DAY_MS = 24L * 60 * 60 * 1000
  }

  /** Minimal MediaItemEntity with only the fields the test cares about set. */
  private suspend fun GalleryDao.insertOne(
    title: String,
    isTrashed: Boolean = false,
    trashedAt: Long? = null,
  ): Long {
    insertMedia(
      listOf(
        MediaItemEntity(
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
        ),
      ),
    )
    // autoGenerate = true; find by rowContent. No general byTitle query exists,
    // so look it up in the trashed flow (every test inserts trashed rows).
    return observeTrashedMedia().first().firstOrNull { it.title == title }?.id
      ?: byIdViaAll(title) ?: error("insert failed for $title")
  }

  /** Insert-without-trash lookup: scan visibleMedia-shaped rows. Used only when
   *  the row was not trashed at insert time (the "setTrashedWritesTimestamp"
   *  test inserts a clean row and trashes it later, so the post-trash lookup
   *  goes through observeTrashedMedia; this branch covers the insert step). */
  private suspend fun GalleryDao.byIdViaAll(title: String): Long? =
    observeVisibleMedia().first().firstOrNull { it.title == title }?.id
