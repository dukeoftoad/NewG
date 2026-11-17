package com.toadgallery.app

import androidx.room.Room
import com.toadgallery.app.data.local.GalleryDao
import com.toadgallery.app.data.local.MediaItemEntity
import com.toadgallery.app.data.local.ToadGalleryDatabase
import com.toadgallery.app.data.model.GalleryMediaItem
import com.toadgallery.app.data.model.MediaCategory
import com.toadgallery.app.data.model.MediaType
import com.toadgallery.app.data.repository.GalleryRepository
import com.toadgallery.app.data.video.VideoEditPlan
import java.io.File
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
 * v2.2.0 E1, plan layer + save path. The Transformer execution itself needs
 * real device codecs, so what is verified here is everything around it:
 * plan validation and duration math (pure JVM) and the repository save path
 * (new VIDEO row, "(Edited)" title, video duration label, app-owned file).
 */
class VideoEditPlanTest {

  @Test
  fun `rejects invalid trim windows and speeds`() {
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      VideoEditPlan(trimStartMs = -1L)
    }
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      VideoEditPlan(trimStartMs = 5_000L, trimEndMs = 5_000L)
    }
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      VideoEditPlan(speed = 0f)
    }
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      VideoEditPlan(speed = 5f)
    }
  }

  @Test
  fun `bounds match the approved v2_2_0 scope`() {
    VideoEditPlan(speed = VideoEditPlan.MIN_SPEED)
    VideoEditPlan(speed = VideoEditPlan.MAX_SPEED)
  }

  @Test
  fun `output duration divides the clipped window by speed`() {
    val trimOnly = VideoEditPlan(trimStartMs = 1_000L, trimEndMs = 9_000L)
    assertEquals(8_000L, trimOnly.outputDurationMs(20_000L))

    val slowMo = VideoEditPlan(trimStartMs = 1_000L, trimEndMs = 9_000L, speed = 0.5f)
    assertEquals(16_000L, slowMo.outputDurationMs(20_000L))

    val fastMo = VideoEditPlan(trimStartMs = 0L, speed = 4f)
    assertEquals(5_000L, fastMo.outputDurationMs(20_000L))
  }

  @Test
  fun `noop plan passes through`() {
    val plan = VideoEditPlan()
    assertTrue(plan.isNoOp)
    assertEquals(20_000L, plan.outputDurationMs(20_000L))
  }

  @Test
  fun `duration label mirrors the scanner format`() {
    val plan = VideoEditPlan(trimStartMs = 1_000L, trimEndMs = 61_000L, speed = 0.5f)
    // 60_000 ms / 0.5 = 120_000 ms = 02:00
    assertEquals("02:00", plan.outputDurationLabel(90_000L))
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VideoEditRepositoryTest {
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

  private suspend fun insertSourceVideo(): GalleryMediaItem {
    dao.insertMedia(
      listOf(
        MediaItemEntity(
          title = "holiday.mp4",
          category = "CAMERA",
          mediaType = MediaType.VIDEO.name,
          durationLabel = "01:00",
          dateLabel = "01 Jan 2026",
          weekGroup = "01 Jan – 07 Jan, 2026",
          monthGroup = "January 2026",
          timestamp = 1_767_225_600_000L,
          locationName = "",
          hasGps = false,
          mapX = 0f,
          mapY = 0f,
          aspectRatio = 1.7778f,
          tagsCsv = "",
          ocrText = "",
          exifSummary = "",
        ),
      ),
    )
    val row = dao.observeVisibleMedia().first().single { it.title == "holiday.mp4" }
    return row.toDomain()
  }

  @Test
  fun saveEditedVideoCopyInsertsNewVideoRow() = runBlocking {
    val source = insertSourceVideo()
    val editedFile = File(
      RuntimeEnvironment.getApplication().filesDir,
      "edited_media/test_${System.currentTimeMillis()}.mp4",
    ).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }

    val plan = VideoEditPlan(trimStartMs = 10_000L, trimEndMs = 30_000L, speed = 0.5f)
    val newId = repo.saveEditedVideoCopy(source, editedFile, sourceDurationMs = 60_000L, plan = plan)

    val saved = dao.getMediaById(newId)
    assertNotNull("new row exists", saved)
    assertEquals("is a VIDEO row", MediaType.VIDEO.name, saved!!.mediaType)
    assertEquals("title carries the Edited suffix", "holiday.mp4 (Edited)", saved.title)
    assertEquals(
      "duration label reflects the plan (20s window / 0.5 = 40s)",
      "00:40",
      saved.durationLabel,
    )
    assertTrue("points at the app-owned file", saved.uriString!!.contains(editedFile.name))
    assertTrue("edited file exists on disk", editedFile.exists())
    assertTrue("inherits the edited tag", saved.toDomain().tags.contains("edited"))
  }

  @Test
  fun saveVideoFrameAsPhotoInsertsPhotoRowInheritingSourceMoment() = runBlocking {
    val source = insertSourceVideo()

    val frame = android.graphics.Bitmap.createBitmap(320, 180, android.graphics.Bitmap.Config.ARGB_8888)
    val positionMs = 5_000L
    val newId = repo.saveVideoFrameAsPhoto(source, frame, positionMs)

    val saved = dao.getMediaById(newId)
    assertNotNull("new row exists", saved)
    assertEquals("is a PHOTO row", MediaType.PHOTO.name, saved!!.mediaType)
    assertEquals(
      "title carries the frame provenance",
      "holiday.mp4 (Frame at 00:05)",
      saved.title,
    )
    assertEquals(
      "frame inherits the source moment (timeline grouping)",
      source.timestamp,
      saved.timestamp,
    )
    assertEquals("source date fields inherited", source.dateLabel, saved.dateLabel)
    assertNull("photos carry no duration label", saved.durationLabel)
    assertTrue("frame tag set", saved.toDomain().tags.contains("frame"))
    assertTrue("frame file name is app-owned", saved.uriString!!.contains("toad_frame_"))
  }

  @Test
  fun `frame label mirrors the duration format`() {
    assertEquals("00:05", com.toadgallery.app.data.video.VideoFrameExtractor.frameLabel(5_000L))
    assertEquals("02:00", com.toadgallery.app.data.video.VideoFrameExtractor.frameLabel(120_000L))
    assertEquals("00:00", com.toadgallery.app.data.video.VideoFrameExtractor.frameLabel(-1L))
  }
}
